package com.ejemplo.bloqueador

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.PhoneDisabled
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.outlined.Contacts
import androidx.compose.material.icons.outlined.Dialpad
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Sms
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/** Pestañas de la barra inferior (máximo 5, con ícono y texto siempre visibles). */
enum class Seccion(val titulo: String, val icono: ImageVector, val iconoActivo: ImageVector) {
    RECIENTES("Recientes", Icons.Outlined.History, Icons.Filled.History),
    CONTACTOS("Contactos", Icons.Outlined.Contacts, Icons.Filled.Contacts),
    MARCAR("Marcar", Icons.Outlined.Dialpad, Icons.Filled.Dialpad),
    MENSAJES("Mensajes", Icons.Outlined.Sms, Icons.Filled.Sms),
    PROTECCION("Protección", Icons.Outlined.Shield, Icons.Filled.Shield)
}

class MainActivity : ComponentActivity() {

    // Se incrementa al volver a la app para recargar registro, contactos y mensajes
    private val refresh = mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TemaBloqueador {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    App(refresh.intValue) { refresh.intValue++ }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refresh.intValue++
    }
}

@Composable
fun App(refreshKey: Int, onChanged: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { Prefs(context) }
    var seccion by rememberSaveable { mutableStateOf(Seccion.RECIENTES) }
    var bienvenida by rememberSaveable { mutableStateOf(!prefs.bienvenidaVista) }

    val pedir = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { onChanged() }
    val pedirPermisos = {
        val faltan = PhoneData.missing(context)
        if (faltan.isEmpty()) onChanged() else pedir.launch(faltan.toTypedArray())
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

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                Seccion.entries.forEach { s ->
                    val activo = seccion == s
                    NavigationBarItem(
                        selected = activo,
                        onClick = { seccion = s },
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
            when (seccion) {
                Seccion.RECIENTES -> PantallaRecientes(refreshKey, pedirPermisos) { seccion = Seccion.PROTECCION }
                Seccion.CONTACTOS -> PantallaContactos(refreshKey, pedirPermisos)
                Seccion.MARCAR -> PantallaTeclado(refreshKey)
                Seccion.MENSAJES -> PantallaMensajes(refreshKey, pedirPermisos)
                Seccion.PROTECCION -> PantallaProteccion(refreshKey, onChanged, pedirPermisos)
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
            "Bienvenido a Bloqueador",
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { heading() }
        )
        Text(
            "Tu teléfono, sin llamadas ni mensajes de desconocidos.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Tarjeta(Modifier.padding(top = Esp.s)) {
            Punto(Icons.Filled.PhoneDisabled, "Solo suenan tus contactos", "Las llamadas de números desconocidos se rechazan solas.")
            Separador()
            Punto(Icons.Filled.NotificationsOff, "Mensajes en silencio", "Los SMS de desconocidos llegan, pero sin sonar ni avisar.")
            Separador()
            Punto(Icons.Outlined.PhoneAndroid, "Todo en un lugar", "Llamadas recientes, contactos, marcador y mensajes.")
        }

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Esp.s)) {
            Icon(Icons.Outlined.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
            Text(
                "Pediremos permiso para ver tus contactos, llamadas y SMS. Nada sale de tu teléfono.",
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
