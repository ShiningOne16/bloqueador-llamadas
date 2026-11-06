package com.ejemplo.bloqueador

import android.os.Bundle
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.PhoneDisabled
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.outlined.WorkspacePremium
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Sms
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/** Navegación principal. Contactos, favoritos y marcador viven dentro de Recientes. */
enum class Seccion(val titulo: String, val icono: ImageVector, val iconoActivo: ImageVector) {
    RECIENTES("Recientes", Icons.Outlined.History, Icons.Filled.History),
    MENSAJES("Mensajes", Icons.Outlined.Sms, Icons.Filled.Sms),
    PROTECCION("Protección", Icons.Outlined.Shield, Icons.Filled.Shield),
    PREMIUM("Premium", Icons.Outlined.WorkspacePremium, Icons.Filled.WorkspacePremium)
}

/** Abre el formulario de contacto desde cualquier pantalla. */
val LocalAbrirFormulario = staticCompositionLocalOf<(PeticionFormulario) -> Unit> { {} }

class MainActivity : ComponentActivity() {

    // Se incrementa al volver a la app para recargar registro, contactos y mensajes
    private val refresh = mutableIntStateOf(0)
    private val smsRequest = mutableStateOf<SmsOpenRequest?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lifecycle.addObserver(RetrasoLlamada)
        SesionEntrada.iniciar(this)
        if (savedInstanceState == null) smsRequest.value = SmsNavigation.parse(intent)
        SmsInboxQueue.enqueue(this)
        enableEdgeToEdge()
        setContent {
            TemaBloqueador {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    // Con el NIP de entrada activo no se carga nada de la app hasta escribirlo.
                    if (SesionEntrada.modo == ModoEntrada.BLOQUEADA) {
                        PantallaNip(onEntrar = { refresh.intValue++ })
                    } else {
                        App(refresh.intValue, onChanged = { refresh.intValue++ }, smsRequest = smsRequest.value, onSmsConsumed = { smsRequest.value = null })
                        AvisoLlamada()
                    }
                }
            }
        }
        Anuncios.iniciar(this)
        SpamProgramacion.programar(this)
    }

    override fun onDestroy() {
        if (isFinishing) Anuncios.liberar()
        super.onDestroy()
    }

    override fun onStart() {
        super.onStart()
        SesionEntrada.alVolver(this)
    }

    override fun onStop() {
        SesionEntrada.alSalir()
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        refresh.intValue++
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        smsRequest.value = SmsNavigation.parse(intent)
        refresh.intValue++
    }
}

@Composable
fun App(refreshKey: Int, onChanged: () -> Unit, smsRequest: SmsOpenRequest?, onSmsConsumed: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { Prefs(context) }
    var seccion by rememberSaveable { mutableStateOf(Seccion.RECIENTES) }
    var bienvenida by rememberSaveable { mutableStateOf(!prefs.bienvenidaVista) }
    var regresoDePremium by rememberSaveable { mutableStateOf(Seccion.RECIENTES) }
    val pantallas = rememberSaveableStateHolder()

    val pedir = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { onChanged() }
    val pedirPermisos = {
        val faltan = PhoneData.missing(context)
        if (faltan.isEmpty()) onChanged() else pedir.launch(faltan.toTypedArray())
    }

    // Formulario de contacto (pantalla completa encima de las pestañas)
    var formAbierto by rememberSaveable { mutableStateOf(false) }
    var formContactId by rememberSaveable { mutableStateOf<Long?>(null) }
    var formNumero by rememberSaveable { mutableStateOf<String?>(null) }
    var pendiente by remember { mutableStateOf<PeticionFormulario?>(null) }

    fun mostrarFormulario(p: PeticionFormulario) {
        formContactId = p.contactId
        formNumero = p.numero
        formAbierto = true
    }

    // Si falta el permiso para escribir contactos, se pide primero y luego se abre el formulario
    val pedirParaFormulario = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        onChanged()
        pendiente?.let { p ->
            if (PhoneData.has(context, android.Manifest.permission.WRITE_CONTACTS)) mostrarFormulario(p)
            else android.widget.Toast.makeText(context, "Se necesita permiso de contactos para guardar.", android.widget.Toast.LENGTH_LONG).show()
        }
        pendiente = null
    }
    val abrirFormulario: (PeticionFormulario) -> Unit = { p ->
        if (PhoneData.has(context, android.Manifest.permission.WRITE_CONTACTS)) {
            mostrarFormulario(p)
        } else {
            pendiente = p
            pedirParaFormulario.launch(PhoneData.missing(context).toTypedArray())
        }
    }

    if (formAbierto) {
        Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))) {
            FormularioContacto(
                PeticionFormulario(formContactId, formNumero),
                onCerrar = { formAbierto = false },
                onGuardado = onChanged
            )
        }
        return
    }

    if (bienvenida) {
        Bienvenida(
            onContinuar = {
                prefs.bienvenidaVista = true
                bienvenida = false
                seccion = Seccion.PROTECCION // ahí se terminan los pasos
                pedirPermisos()
            },
            onOmitir = {
                prefs.bienvenidaVista = true
                bienvenida = false
            }
        )
        return
    }

    LaunchedEffect(smsRequest?.token) {
        if (smsRequest != null) seccion = Seccion.MENSAJES
    }
    CompositionLocalProvider(LocalAbrirFormulario provides abrirFormulario) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                Seccion.entries.forEach { s ->
                    val activo = seccion == s
                    NavigationBarItem(
                        selected = activo,
                        onClick = {
                            if (s == Seccion.PREMIUM && seccion != Seccion.PREMIUM) regresoDePremium = seccion
                            seccion = s
                        },
                        icon = { Icon(if (activo) s.iconoActivo else s.icono, contentDescription = null) },
                        label = { Text(s.titulo, maxLines = 1) },
                        alwaysShowLabel = true
                    )
                }
            }
        }
    ) { padding ->
        Box(
            Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)
        ) {
            pantallas.SaveableStateProvider(seccion.name) {
                when (seccion) {
                    Seccion.RECIENTES -> PantallaTelefono(refreshKey, pedirPermisos) { seccion = Seccion.PROTECCION }
                    Seccion.MENSAJES -> PantallaMensajes(refreshKey, pedirPermisos, smsRequest, onSmsConsumed)
                    Seccion.PROTECCION -> PantallaProteccion(refreshKey, onChanged, pedirPermisos) {
                        regresoDePremium = Seccion.PROTECCION
                        seccion = Seccion.PREMIUM
                    }
                    Seccion.PREMIUM -> PantallaPremium(onCerrar = { seccion = regresoDePremium }, onChanged = onChanged, refreshKey = refreshKey)
                }
            }
        }
    }
    }
}

// -------------------------------------------------------------------------------------------
// Bienvenida: explica qué hace la app y por qué pide permisos, antes de pedirlos
// -------------------------------------------------------------------------------------------

@Composable
private fun Bienvenida(onContinuar: () -> Unit, onOmitir: () -> Unit) {
    Column(
        Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Esp.xl, vertical = Esp.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Esp.l)
    ) {
        Spacer(Modifier.height(Esp.xl))
        IconoCirculo(
            Icons.Filled.Shield,
            fondo = MaterialTheme.colorScheme.primaryContainer,
            color = MaterialTheme.colorScheme.primary,
            tamano = 96.dp
        )
        Text(
            "Bienvenido a Teléfono",
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { heading() }
        )
        Text(
            "Tu teléfono, con el control de tus llamadas y mensajes.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Tarjeta(Modifier.padding(top = Esp.s)) {
            Punto(Icons.Filled.PhoneDisabled, "Desconocidos · Premium", "Configura el bloqueo de llamadas de números que no están en tus contactos desde Premium.")
            Separador()
            Punto(Icons.Filled.NotificationsOff, "Mensajes en silencio", "Los SMS de desconocidos llegan, pero sin sonar ni avisar.")
            Separador()
            Punto(Icons.Outlined.PhoneAndroid, "Todo en un lugar", "Llamadas recientes, contactos (en Google o la SIM), marcador y mensajes.")
        }

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Esp.s)) {
            Icon(Icons.Outlined.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
            Text(
                "Pediremos permiso para tus contactos, llamadas y SMS. Los mensajes se envían por tu operador; los MMS utilizan datos móviles.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(Modifier.weight(1f, fill = false).height(Esp.s))

        Button(
            onClick = onContinuar,
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
        ) { Text("Empezar") }
        TextButton(
            onClick = onOmitir,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
        ) { Text("Ahora no") }
    }
}

@Composable
private fun Punto(icono: ImageVector, titulo: String, texto: String) {
    ListItem(
        leadingContent = {
            IconoCirculo(icono, MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer)
        },
        headlineContent = { Text(titulo, style = MaterialTheme.typography.titleMedium) },
        supportingContent = { Text(texto) },
        colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent)
    )
}

