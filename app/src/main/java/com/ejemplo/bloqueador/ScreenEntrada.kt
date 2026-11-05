package com.ejemplo.bloqueador

import android.app.Activity
import android.app.KeyguardManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// -------------------------------------------------------------------------------------------
// Pantalla de NIP al abrir la app
// -------------------------------------------------------------------------------------------

/**
 * Se muestra al abrir la app cuando el NIP de entrada está activo. Con el NIP correcto se entra normal;
 * con otro, la app abre igual pero sin los contactos de la lista "a ocultar" y sin avisar del error.
 * Si esa lista está vacía, un NIP incorrecto no deja entrar (no habría nada que ocultar).
 */
@Composable
fun PantallaNip(onEntrar: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = remember { EntradaStore(context) }
    var nip by remember { mutableStateOf("") }
    var mensaje by remember { mutableStateOf<String?>(null) }
    var comprobando by remember { mutableStateOf(false) }

    // "Olvidé mi NIP": solo con el bloqueo de pantalla del teléfono (PIN, patrón, huella o rostro).
    val llavero = remember { context.getSystemService(KeyguardManager::class.java) }
    val puedeRestablecer = remember { llavero?.isDeviceSecure == true }
    val restablecer = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { resultado ->
        if (resultado.resultCode == Activity.RESULT_OK) {
            store.quitarNip()
            SesionEntrada.abrirNormal()
            onEntrar()
        }
    }

    PantallaSegura()

    fun intentar() {
        if (!HiddenRules.validPin(nip) || comprobando) return
        val intento = nip
        comprobando = true
        scope.launch {
            val (correcto, lista) = withContext(Dispatchers.Default) { store.verificar(intento) to store.aOcultar() }
            comprobando = false
            nip = ""
            when (EntradaRules.modoTrasNip(correcto, lista.isNotEmpty())) {
                ModoEntrada.NORMAL -> { SesionEntrada.abrirNormal(); onEntrar() }
                ModoEntrada.FALSO -> { SesionEntrada.entrarFalso(lista); onEntrar() }
                else -> mensaje = "NIP incorrecto."
            }
        }
    }

    Column(
        Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Esp.xl, vertical = Esp.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Esp.m)
    ) {
        Spacer(Modifier.height(Esp.xl))
        IconoCirculo(
            Icons.Filled.Lock,
            fondo = MaterialTheme.colorScheme.primaryContainer,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            tamano = 80.dp
        )
        Text("Escribe tu NIP", style = MaterialTheme.typography.headlineSmall)
        CampoPin(nip, { nip = it; mensaje = null }, "NIP", error = mensaje, onListo = { intentar() })
        Button(
            onClick = { intentar() },
            enabled = HiddenRules.validPin(nip) && !comprobando,
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
        ) { Text("Entrar") }
        if (puedeRestablecer) {
            TextButton(
                onClick = {
                    llavero?.createConfirmDeviceCredentialIntent(
                        "Quitar el NIP de entrada",
                        "Confirma el bloqueo de pantalla de tu teléfono para quitar el NIP de Teléfono."
                    )?.let { restablecer.launch(it) }
                },
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
            ) { Text("Olvidé mi NIP") }
        }
    }
}

// -------------------------------------------------------------------------------------------
// Configuración (Premium → NIP de entrada)
// -------------------------------------------------------------------------------------------

@Composable
fun PantallaEntradaNip(onCerrar: () -> Unit, onChanged: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = remember { EntradaStore(context) }
    var activo by remember { mutableStateOf(store.activo()) }
    var lista by remember { mutableStateOf(store.aOcultar()) }
    var eligiendo by remember { mutableStateOf(false) }
    var cambiandoNip by remember { mutableStateOf(false) }
    var quitandoNip by remember { mutableStateOf(false) }
    var porQuitar by remember { mutableStateOf<HiddenContact?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    PantallaSegura()

    if (eligiendo) {
        SelectorOculto(
            onCerrar = { eligiendo = false },
            onElegido = { c ->
                eligiendo = false
                try {
                    store.agregar(HiddenContact(c.name, c.numbers, c.id, c.lookupKey))
                    lista = store.aOcultar()
                    error = null
                } catch (_: Exception) {
                    error = "No se pudo agregar el contacto. Inténtalo de nuevo."
                }
            }
        )
        return
    }

    if (!activo || cambiandoNip) {
        val volver: () -> Unit = { if (cambiandoNip) { cambiandoNip = false } else { onCerrar() } }
        BackHandler(onBack = volver)
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).imePadding()) {
            PremiumHeader("NIP de entrada", volver)
            CrearNip(
                titulo = if (activo) "Nuevo NIP" else "Crea tu NIP",
                texto = "Con este NIP de ${HiddenRules.PIN_LENGTH} dígitos entrarás normal a la app. Con cualquier otro, " +
                    "la app abre sin los contactos que elijas ocultar.",
                boton = if (activo) "Cambiar NIP" else "Activar NIP",
                onGuardar = { nuevo ->
                    scope.launch {
                        withContext(Dispatchers.Default) { store.configurarNip(nuevo) }
                        activo = true
                        cambiandoNip = false
                        SesionEntrada.abrirNormal()
                        onChanged()
                    }
                }
            )
        }
        return
    }

    BackHandler(onBack = onCerrar)
    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
        PremiumHeader("NIP de entrada", onCerrar)
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = Esp.l, vertical = Esp.s),
            verticalArrangement = Arrangement.spacedBy(Esp.m)
        ) {
            item {
                Tarjeta {
                    Column(Modifier.padding(Esp.l), verticalArrangement = Arrangement.spacedBy(Esp.s)) {
                        Icon(Icons.Filled.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text("Entrada con NIP", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Al abrir la app se pide tu NIP. Con el NIP correcto lo ves todo. Con otro, la app abre sin avisar " +
                                "del error y esconde a los contactos de esta lista: no aparecen en contactos, recientes, " +
                                "mensajes ni notas.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            "Solo se ocultan dentro de Teléfono: sus llamadas y mensajes se siguen tratando igual. Otras apps, " +
                                "como la agenda o el teléfono del sistema, pueden seguir mostrándolos. Si la lista está vacía, " +
                                "un NIP incorrecto no deja entrar. El NIP se vuelve a pedir al cerrar la app o tras " +
                                "${EntradaRules.RELOCK_MS / 1000} segundos fuera de ella.",
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
                    Text("Agregar contacto a ocultar")
                }
            }
            if (error != null) item { Text(error.orEmpty(), color = MaterialTheme.colorScheme.error) }
            if (lista.isEmpty()) {
                item {
                    Text(
                        "Todavía no hay contactos en la lista.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                item {
                    Text(
                        "Contactos a ocultar (${lista.size})",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                items(lista, key = { "${it.contactId}:${it.name}:${it.numbers.joinToString()}" }) { c ->
                    ListItem(
                        leadingContent = { Avatar(c.name) },
                        headlineContent = { Text(c.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        supportingContent = { Text(c.numbers.joinToString(" · "), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        trailingContent = {
                            TextButton(onClick = { porQuitar = c }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Quitar") }
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                    )
                }
            }
            item {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                OutlinedButton(onClick = { cambiandoNip = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Icon(Icons.Filled.Edit, contentDescription = null)
                    Spacer(Modifier.width(Esp.s))
                    Text("Cambiar NIP")
                }
            }
            item {
                OutlinedButton(
                    onClick = { quitandoNip = true },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Icon(Icons.Filled.LockOpen, contentDescription = null)
                    Spacer(Modifier.width(Esp.s))
                    Text("Quitar NIP")
                }
            }
        }
    }

    porQuitar?.let { c ->
        Confirmar(
            "¿Quitar a ${c.name} de la lista?",
            "Volverá a mostrarse aunque entres con un NIP incorrecto.",
            "Quitar",
            onConfirmar = {
                porQuitar = null
                try {
                    store.quitar(c)
                    lista = store.aOcultar()
                    error = null
                } catch (_: Exception) {
                    error = "No se pudo quitar el contacto. Inténtalo de nuevo."
                }
            },
            onCancelar = { porQuitar = null }
        )
    }
    if (quitandoNip) {
        Confirmar(
            "¿Quitar el NIP de entrada?",
            "La app dejará de pedir NIP al abrirse. La lista de contactos a ocultar se conserva por si lo vuelves a activar.",
            "Quitar NIP",
            onConfirmar = {
                quitandoNip = false
                store.quitarNip()
                activo = false
                SesionEntrada.abrirNormal()
                onChanged()
            },
            onCancelar = { quitandoNip = false }
        )
    }
}

@Composable
private fun CrearNip(titulo: String, texto: String, boton: String, onGuardar: (String) -> Unit) {
    var nip by remember { mutableStateOf("") }
    var repetido by remember { mutableStateOf("") }
    val valido = HiddenRules.validPin(nip) && nip == repetido
    val error = if (repetido.length == HiddenRules.PIN_LENGTH && nip != repetido) "Los NIP no coinciden." else null

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
                    "Si olvidas el NIP, podrás quitarlo confirmando el bloqueo de pantalla de tu teléfono. Si tu teléfono " +
                        "no tiene bloqueo de pantalla, no habrá forma de recuperarlo salvo borrar los datos de la app.",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
        CampoPin(nip, { nip = it }, "NIP de ${HiddenRules.PIN_LENGTH} dígitos", ultimo = false)
        CampoPin(repetido, { repetido = it }, "Repite el NIP", error = error, onListo = { if (valido) onGuardar(nip) })
        Button(
            onClick = { onGuardar(nip) },
            enabled = valido,
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
        ) { Text(boton) }
    }
}
