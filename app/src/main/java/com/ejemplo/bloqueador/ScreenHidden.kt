package com.ejemplo.bloqueador

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/** Evita capturas de pantalla y miniaturas en recientes mientras se ve esta sección. */
@Composable
internal fun PantallaSegura() {
    val context = LocalContext.current
    DisposableEffect(Unit) {
        val window = context.findActivity()?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose { window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) }
    }
}

/**
 * Contactos ocultos (Premium). Un contacto oculto tiene sus llamadas y SMS bloqueados y no aparece
 * en contactos, recientes ni mensajes. Todo se protege con un solo PIN de 4 dígitos.
 * La sección se vuelve a bloquear al salir de ella o al recrearse la pantalla.
 */
@Composable
fun PantallaOcultos(onCerrar: () -> Unit, onChanged: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = remember { HiddenStore(context) }
    var hayPin by remember { mutableStateOf(store.hasPin()) }
    var desbloqueado by remember { mutableStateOf(false) }
    var confirmandoReinicio by remember { mutableStateOf(false) }
    var trabajando by remember { mutableStateOf(false) }
    var informe by remember { mutableStateOf<WipeReport?>(null) }

    PantallaSegura()
    BackHandler(onBack = onCerrar)

    fun borrarTodo() {
        scope.launch {
            trabajando = true
            val resultado = withContext(Dispatchers.IO) { HiddenWipe.run(context) }
            trabajando = false
            informe = resultado
            hayPin = store.hasPin()
            desbloqueado = false
            onChanged()
        }
    }
    // Sin lectura y escritura no se puede verificar un restablecimiento seguro.
    val permisosBorrado = arrayOf(
        Manifest.permission.READ_CONTACTS, Manifest.permission.WRITE_CONTACTS,
        Manifest.permission.READ_CALL_LOG, Manifest.permission.WRITE_CALL_LOG,
        Manifest.permission.READ_SMS
    )
    val pedirRegistro = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { borrarTodo() }

    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).imePadding()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onCerrar, modifier = Modifier.size(48.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
            }
            Text("Contactos ocultos", style = MaterialTheme.typography.titleLarge)
        }
        when {
            trabajando -> Cargando()
            !hayPin -> CrearPin(
                titulo = "Crea tu PIN",
                texto = "Con este PIN de ${HiddenRules.PIN_LENGTH} dígitos podrás ocultar, ver y mostrar de nuevo tus contactos ocultos.",
                boton = "Guardar PIN",
                onGuardar = { pin ->
                    scope.launch {
                        withContext(Dispatchers.Default) { store.setPin(pin) }
                        hayPin = true
                        desbloqueado = true
                    }
                }
            )
            !desbloqueado -> PedirPin(store, onDesbloqueado = { desbloqueado = true }, onOlvide = { confirmandoReinicio = true })
            else -> Administrar(store, onChanged = onChanged, onBloquear = { desbloqueado = false })
        }
    }

    if (confirmandoReinicio) {
        DialogoReinicio(
            onConfirmar = {
                confirmandoReinicio = false
                if (permisosBorrado.all { PhoneData.has(context, it) }) borrarTodo()
                else pedirRegistro.launch(permisosBorrado)
            },
            onCancelar = { confirmandoReinicio = false }
        )
    }
    informe?.let { r -> DialogoInforme(r) { informe = null } }
}

// -------------------------------------------------------------------------------------------
// PIN
// -------------------------------------------------------------------------------------------

@Composable
internal fun CampoPin(
    valor: String,
    onCambio: (String) -> Unit,
    etiqueta: String,
    error: String? = null,
    ultimo: Boolean = true,
    onListo: () -> Unit = {}
) {
    OutlinedTextField(
        value = valor,
        onValueChange = { nuevo -> onCambio(nuevo.filter { it.isDigit() }.take(HiddenRules.PIN_LENGTH)) },
        label = { Text(etiqueta) },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.NumberPassword,
            imeAction = if (ultimo) ImeAction.Done else ImeAction.Next
        ),
        keyboardActions = KeyboardActions(onDone = { onListo() }),
        isError = error != null,
        supportingText = error?.let { { Text(it) } },
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun CrearPin(titulo: String, texto: String, boton: String, onGuardar: (String) -> Unit, onCancelar: (() -> Unit)? = null) {
    var pin by remember { mutableStateOf("") }
    var repetido by remember { mutableStateOf("") }
    val valido = HiddenRules.validPin(pin) && pin == repetido
    val error = if (repetido.length == HiddenRules.PIN_LENGTH && pin != repetido) "Los PIN no coinciden." else null

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Esp.l, vertical = Esp.s),
        verticalArrangement = Arrangement.spacedBy(Esp.m)
    ) {
        Text(titulo, style = MaterialTheme.typography.headlineSmall)
        Text(texto, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Tarjeta {
            Row(Modifier.padding(Esp.l), horizontalArrangement = Arrangement.spacedBy(Esp.m)) {
                Icon(Icons.Filled.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                Text(
                    "Si olvidas el PIN no hay otra forma de entrar: tendrás que restablecerlo, y eso borra todos los " +
                        "contactos ocultos, sus llamadas y sus mensajes.",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
        CampoPin(pin, { pin = it }, "PIN de ${HiddenRules.PIN_LENGTH} dígitos", ultimo = false)
        CampoPin(repetido, { repetido = it }, "Repite el PIN", error = error, onListo = { if (valido) onGuardar(pin) })
        Button(onClick = { onGuardar(pin) }, enabled = valido, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
            Text(boton)
        }
        if (onCancelar != null) {
            TextButton(onClick = onCancelar, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Cancelar") }
        }
    }
}

@Composable
private fun PedirPin(store: HiddenStore, onDesbloqueado: () -> Unit, onOlvide: () -> Unit) {
    val scope = rememberCoroutineScope()
    var pin by remember { mutableStateOf("") }
    var mensaje by remember { mutableStateOf<String?>(null) }
    var espera by remember { mutableLongStateOf(store.lockedSeconds()) }
    var comprobando by remember { mutableStateOf(false) }

    LaunchedEffect(espera) {
        if (espera > 0) {
            delay(1000)
            espera = store.lockedSeconds()
        }
    }

    fun intentar() {
        if (!HiddenRules.validPin(pin) || comprobando || espera > 0) return
        val intento = pin
        comprobando = true
        scope.launch {
            val resultado = withContext(Dispatchers.Default) { store.verify(intento) }
            comprobando = false
            pin = ""
            when (resultado) {
                PinResult.Ok -> onDesbloqueado()
                is PinResult.Wrong -> mensaje = "PIN incorrecto. Te quedan ${resultado.attemptsLeft} intentos antes de un bloqueo temporal."
                is PinResult.Locked -> { mensaje = null; espera = resultado.seconds }
                PinResult.NoPin -> mensaje = "No hay un PIN guardado."
            }
        }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Esp.l, vertical = Esp.s),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Esp.m)
    ) {
        Spacer(Modifier.height(Esp.l))
        IconoCirculo(
            Icons.Filled.Lock,
            fondo = MaterialTheme.colorScheme.primaryContainer,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            tamano = 72.dp
        )
        Text("Escribe tu PIN", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Necesitas tu PIN para ver, ocultar o mostrar contactos.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        CampoPin(pin, { pin = it }, "PIN", error = mensaje, onListo = { intentar() })
        if (espera > 0) {
            Text(
                "Demasiados intentos. Vuelve a intentar en $espera s.",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium
            )
        }
        Button(
            onClick = { intentar() },
            enabled = HiddenRules.validPin(pin) && !comprobando && espera == 0L,
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
        ) { Text("Entrar") }
        TextButton(onClick = onOlvide, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
            Text("Olvidé mi PIN")
        }
    }
}

// -------------------------------------------------------------------------------------------
// Administrar contactos ocultos
// -------------------------------------------------------------------------------------------

@Composable
private fun Administrar(store: HiddenStore, onChanged: () -> Unit, onBloquear: () -> Unit) {
    val scope = rememberCoroutineScope()
    var lista by remember { mutableStateOf(store.contacts()) }
    var eligiendo by remember { mutableStateOf(false) }
    var cambiandoPin by remember { mutableStateOf(false) }
    var porOcultar by remember { mutableStateOf<ContactEntry?>(null) }
    var porMostrar by remember { mutableStateOf<HiddenContact?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    if (cambiandoPin) {
        BackHandler { cambiandoPin = false }
        CrearPin(
            titulo = "Nuevo PIN",
            texto = "Elige un PIN de ${HiddenRules.PIN_LENGTH} dígitos. Reemplaza al anterior.",
            boton = "Cambiar PIN",
            onGuardar = { pin ->
                scope.launch {
                    withContext(Dispatchers.Default) { store.setPin(pin) }
                    cambiandoPin = false
                }
            },
            onCancelar = { cambiandoPin = false }
        )
        return
    }
    if (eligiendo) {
        SelectorOculto(onCerrar = { eligiendo = false }, onElegido = { porOcultar = it; eligiendo = false })
        return
    }
    BackHandler(onBack = onBloquear)

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = Esp.l, vertical = Esp.s),
        verticalArrangement = Arrangement.spacedBy(Esp.m)
    ) {
        item {
            Tarjeta {
                Column(Modifier.padding(Esp.l), verticalArrangement = Arrangement.spacedBy(Esp.s)) {
                    Icon(Icons.Filled.VisibilityOff, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text("Sin rastro en esta app", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Un contacto oculto no suena ni te avisa: sus llamadas se rechazan y sus mensajes se descartan " +
                            "(estos últimos, si esta app es tu app de SMS predeterminada). No aparece en contactos, " +
                            "recientes ni mensajes, y no deja registro.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "Otras apps, como la agenda o el teléfono del sistema, pueden seguir mostrando al contacto " +
                            "y sus llamadas anteriores.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        item {
            Button(onClick = { eligiendo = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                Icon(Icons.Filled.PersonAdd, contentDescription = null)
                Spacer(Modifier.width(Esp.s))
                Text("Ocultar un contacto")
            }
        }
        if (error != null) item { Text(error.orEmpty(), color = MaterialTheme.colorScheme.error) }
        if (lista.isEmpty()) {
            item {
                Text(
                    "Todavía no has ocultado a nadie.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            item { Text("Ocultos (${lista.size})", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary) }
            items(lista, key = { "${it.contactId}:${it.name}:${it.numbers.joinToString()}" }) { c ->
                ListItem(
                    leadingContent = { Avatar(c.name) },
                    headlineContent = { Text(c.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    supportingContent = { Text(c.numbers.joinToString(" · "), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    trailingContent = {
                        TextButton(onClick = { porMostrar = c }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Mostrar") }
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )
            }
        }
        item {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            OutlinedButton(onClick = { cambiandoPin = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Icon(Icons.Filled.Edit, contentDescription = null)
                Spacer(Modifier.width(Esp.s))
                Text("Cambiar PIN")
            }
        }
        item {
            OutlinedButton(onClick = onBloquear, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Icon(Icons.Filled.Lock, contentDescription = null)
                Spacer(Modifier.width(Esp.s))
                Text("Bloquear esta sección")
            }
        }
    }

    porOcultar?.let { c ->
        Confirmar(
            "¿Ocultar a ${c.name}?",
            "Se bloquearán sus llamadas y mensajes, y desaparecerá de contactos, recientes y mensajes. " +
                "Solo tú podrás volver a verlo con tu PIN.",
            "Ocultar",
            onConfirmar = {
                porOcultar = null
                try {
                    store.hide(HiddenContact(c.name, c.numbers, c.id, c.lookupKey))
                    lista = store.contacts()
                    error = null
                    onChanged()
                } catch (_: Exception) {
                    error = "No se pudo ocultar el contacto. Inténtalo de nuevo."
                }
            },
            onCancelar = { porOcultar = null }
        )
    }
    porMostrar?.let { c ->
        Confirmar(
            "¿Mostrar de nuevo a ${c.name}?",
            "Volverá a aparecer en tus listas y dejarán de bloquearse sus llamadas y mensajes. " +
                "Lo que se descartó mientras estuvo oculto no se recupera.",
            "Mostrar",
            onConfirmar = {
                porMostrar = null
                try {
                    store.unhide(c)
                    lista = store.contacts()
                    error = null
                    onChanged()
                } catch (_: Exception) {
                    error = "No se pudo mostrar el contacto. Inténtalo de nuevo."
                }
            },
            onCancelar = { porMostrar = null }
        )
    }
}

@Composable
internal fun SelectorOculto(onCerrar: () -> Unit, onElegido: (ContactEntry) -> Unit) {
    val context = LocalContext.current
    val permitido = remember { Contacts.hasPermission(context) }
    var contactos by remember { mutableStateOf<List<ContactEntry>?>(null) }
    var busqueda by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        if (permitido) contactos = withContext(Dispatchers.IO) { PhoneData.loadContacts(context) }
    }
    BackHandler(onBack = onCerrar)

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onCerrar, modifier = Modifier.size(48.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Cancelar")
            }
            Text("Elegir contacto", style = MaterialTheme.typography.titleLarge)
        }
        if (!permitido) {
            EstadoVacio(
                Icons.Filled.Contacts,
                "Falta el permiso de contactos",
                "Concédelo desde la pestaña Protección para elegir a quién ocultar."
            )
            return@Column
        }
        Buscador(busqueda, { busqueda = it }, "Buscar nombre o número")
        val lista = contactos
        if (lista == null) {
            Cargando()
            return@Column
        }
        val texto = simplificar(busqueda.trim())
        val digitos = busqueda.filter { it.isDigit() }
        val filtrados = lista.filter { c ->
            texto.isBlank() || simplificar(c.name).contains(texto) ||
                (digitos.length >= 2 && c.numbers.any { it.filter(Char::isDigit).contains(digitos) })
        }
        if (filtrados.isEmpty()) {
            EstadoVacio(Icons.Filled.SearchOff, "Sin resultados", "No hay contactos que coincidan.")
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                items(filtrados, key = { it.id }) { c ->
                    ListItem(
                        modifier = Modifier.clickable(onClickLabel = "Elegir contacto") { onElegido(c) },
                        leadingContent = { Avatar(c.name) },
                        headlineContent = { Text(c.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        supportingContent = { Text(c.numbers.firstOrNull().orEmpty(), maxLines = 1) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                    )
                }
            }
        }
    }
}

// -------------------------------------------------------------------------------------------
// Olvidé mi PIN
// -------------------------------------------------------------------------------------------

@Composable
private fun DialogoReinicio(onConfirmar: () -> Unit, onCancelar: () -> Unit) {
    var texto by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onCancelar,
        title = { Text("¿Restablecer y borrar todo?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Esp.m)) {
                Text(
                    "Se borrarán para siempre todos los contactos ocultos: el contacto de tu agenda (también de tu " +
                        "cuenta de Google), su registro de llamadas y sus mensajes. Esto no se puede deshacer."
                )
                OutlinedTextField(
                    value = texto,
                    onValueChange = { texto = it },
                    label = { Text("Escribe BORRAR para confirmar") },
                    singleLine = true
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirmar,
                enabled = texto.trim().equals("BORRAR", ignoreCase = true),
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) { Text("Borrar todo") }
        },
        dismissButton = { TextButton(onClick = onCancelar) { Text("Cancelar") } }
    )
}

@Composable
private fun DialogoInforme(r: WipeReport, onCerrar: () -> Unit) {
    AlertDialog(
        onDismissRequest = onCerrar,
        title = { Text(if (r.complete) "Todo se borró" else "Restablecimiento pendiente") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Esp.s)) {
                if (r.complete) {
                    Text("Contactos borrados: ${r.contactsDeleted}")
                    Text("Llamadas borradas: ${r.callsDeleted}")
                    Text("SMS borrados: ${r.smsMessagesDeleted}")
                    Text("La función quedó sin PIN y sin contactos ocultos.")
                } else {
                    Text(r.failure ?: "No se pudo completar el borrado.", color = MaterialTheme.colorScheme.error)
                    Text("El PIN y la lista privada se conservan. Algunos datos pueden haberse borrado ya; los restantes siguen ocultos. Usa \"Olvidé mi PIN\" para volver a intentarlo.")
                }
            }
        },
        confirmButton = { TextButton(onClick = onCerrar) { Text("Entendido") } }
    )
}
