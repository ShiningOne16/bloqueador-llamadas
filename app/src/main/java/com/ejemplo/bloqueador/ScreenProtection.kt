package com.ejemplo.bloqueador

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material.icons.filled.PhoneDisabled
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat

/** Qué pasos de configuración faltan. */
data class EstadoConfig(
    val permisos: Boolean,
    val filtroLlamadas: Boolean,
    val accesoNotificaciones: Boolean
) {
    val faltan get() = listOf(permisos, filtroLlamadas, accesoNotificaciones).count { !it }
}

fun estadoConfig(context: Context): EstadoConfig {
    val roleManager = context.getSystemService(RoleManager::class.java)
    return EstadoConfig(
        permisos = PhoneData.missing(context).isEmpty(),
        filtroLlamadas = roleManager.isRoleHeld(RoleManager.ROLE_CALL_SCREENING),
        accesoNotificaciones = NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)
    )
}

@Composable
fun PantallaProteccion(refreshKey: Int, onChanged: () -> Unit, pedirPermisos: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { Prefs(context) }
    val roleManager = remember { context.getSystemService(RoleManager::class.java) }
    val estado = remember(refreshKey) { estadoConfig(context) }

    var llamadasActivo by remember { mutableStateOf(prefs.enabled) }
    var ocultos by remember { mutableStateOf(prefs.blockHidden) }
    var insisten by remember { mutableStateOf(prefs.allowRepeat) }
    var smsActivo by remember { mutableStateOf(prefs.smsEnabled) }
    var empresas by remember { mutableStateOf(prefs.smsIncludeBusiness) }

    val llamadas = remember(refreshKey) { prefs.blockedCalls() }
    val mensajes = remember(refreshKey) { prefs.silencedSms() }
    var verHistorial by remember { mutableStateOf<Int?>(null) } // 0 = llamadas, 1 = mensajes

    val pedirRol = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { onChanged() }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = Esp.xl)
    ) {
        item { Titulo("Protección") }

        item {
            Resumen(
                estado, llamadasActivo, smsActivo,
                Modifier.padding(horizontal = Esp.l, vertical = Esp.s)
            )
        }

        if (estado.faltan > 0) {
            item { Rotulo("Termina de configurar") }
            item {
                Tarjeta(Modifier.padding(horizontal = Esp.l)) {
                    Paso(
                        1, estado.permisos,
                        "Permisos del teléfono",
                        "Para ver y guardar contactos, y ver llamadas y mensajes.",
                        "Permitir", onClick = pedirPermisos
                    )
                    Separador()
                    Paso(
                        2, estado.filtroLlamadas,
                        "Filtrar llamadas",
                        "Elige Bloqueador como app de filtrado.",
                        "Activar",
                        onClick = { pedirRol.launch(roleManager.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING)) }
                    )
                    Separador()
                    Paso(
                        3, estado.accesoNotificaciones,
                        "Silenciar mensajes",
                        "Activa Bloqueador en \"Acceso a notificaciones\".",
                        "Activar",
                        onClick = { abrir(context, Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
                    )
                    if (!estado.accesoNotificaciones) {
                        AyudaRestringida()
                    }
                }
            }
        }

        item { Rotulo("Llamadas") }
        item {
            Tarjeta(Modifier.padding(horizontal = Esp.l)) {
                FilaInterruptor(
                    Icons.Filled.PhoneDisabled, "Bloquear desconocidos",
                    "Rechaza llamadas de números que no están en tus contactos.", llamadasActivo
                ) { llamadasActivo = it; prefs.enabled = it }
                Separador()
                FilaInterruptor(
                    Icons.Filled.VisibilityOff, "Bloquear números ocultos",
                    "Llamadas privadas o sin número.", ocultos, habilitado = llamadasActivo
                ) { ocultos = it; prefs.blockHidden = it }
                Separador()
                FilaInterruptor(
                    Icons.Filled.Repeat, "Dejar pasar si insisten",
                    "Si cuelgan y vuelven a marcar en menos de 3 minutos, sonará. Útil para urgencias.",
                    insisten, habilitado = llamadasActivo
                ) { insisten = it; prefs.allowRepeat = it }
            }
        }

        item { Rotulo("Mensajes") }
        item {
            Tarjeta(Modifier.padding(horizontal = Esp.l)) {
                FilaInterruptor(
                    Icons.Filled.NotificationsOff, "Silenciar desconocidos",
                    "Los SMS llegan a tu app de mensajes, pero sin sonar ni avisar.", smsActivo
                ) { smsActivo = it; prefs.smsEnabled = it }
                Separador()
                FilaInterruptor(
                    Icons.Filled.Business, "Incluir empresas",
                    "Bancos, compañías y códigos cortos. Ojo: también silencia códigos de verificación.",
                    empresas, habilitado = smsActivo
                ) { empresas = it; prefs.smsIncludeBusiness = it }
            }
        }

        item { Rotulo("Actividad") }
        item {
            Tarjeta(Modifier.padding(horizontal = Esp.l)) {
                FilaAccion(
                    Icons.Filled.PhoneDisabled, "Llamadas bloqueadas",
                    valor = llamadas.size.toString()
                ) { verHistorial = 0 }
                Separador()
                FilaAccion(
                    Icons.Filled.NotificationsOff, "Mensajes silenciados",
                    valor = mensajes.size.toString()
                ) { verHistorial = 1 }
            }
        }
    }

    when (verHistorial) {
        0 -> Historial(
            titulo = "Llamadas bloqueadas",
            vacio = "Todavía no se ha bloqueado ninguna llamada.",
            filas = llamadas.map { FilaHistorial(it.number, null, it.timestamp) },
            onBorrar = { prefs.clearLog(); onChanged() },
            onCerrar = { verHistorial = null }
        )
        1 -> Historial(
            titulo = "Mensajes silenciados",
            vacio = "Todavía no se ha silenciado ningún mensaje.",
            filas = mensajes.map { FilaHistorial(it.sender, it.text, it.timestamp) },
            onBorrar = { prefs.clearSmsLog(); onChanged() },
            onCerrar = { verHistorial = null },
            onToque = { remitente ->
                if (esNumeroTelefono(remitente)) Acciones.mensaje(context, remitente)
            }
        )
    }
}

// -------------------------------------------------------------------------------------------

/** Tarjeta grande que dice en una frase si estás protegido. */
@Composable
private fun Resumen(estado: EstadoConfig, llamadas: Boolean, sms: Boolean, modifier: Modifier) {
    val extra = LocalColoresExtra.current
    val cs = MaterialTheme.colorScheme

    data class Vista(val icono: ImageVector, val fondo: Color, val texto: Color, val titulo: String, val detalle: String)

    val v = when {
        estado.faltan > 0 -> Vista(
            Icons.Filled.Warning, extra.aviso, extra.onAviso,
            "Falta poco",
            if (estado.faltan == 1) "Completa 1 paso para activar la protección."
            else "Completa ${estado.faltan} pasos para activar la protección."
        )
        !llamadas && !sms -> Vista(
            Icons.Filled.PauseCircle, cs.surfaceContainerHigh, cs.onSurface,
            "Protección en pausa",
            "Activa los interruptores de abajo cuando quieras."
        )
        else -> Vista(
            Icons.Filled.Shield, cs.tertiaryContainer, cs.onTertiaryContainer,
            "Estás protegido",
            "Llamadas: ${if (llamadas) "activado" else "en pausa"} · Mensajes: ${if (sms) "activado" else "en pausa"}"
        )
    }

    Surface(modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, color = v.fondo, contentColor = v.texto) {
        Row(
            Modifier.padding(Esp.l),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Esp.l)
        ) {
            Icon(v.icono, contentDescription = null, modifier = Modifier.size(40.dp))
            Column {
                Text(v.titulo, style = MaterialTheme.typography.titleLarge)
                Text(v.detalle, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

/** Un paso de configuración: número, estado (hecho / pendiente) y botón. */
@Composable
private fun Paso(numero: Int, hecho: Boolean, titulo: String, detalle: String, boton: String, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    ListItem(
        modifier = Modifier.semantics {
            contentDescription = "Paso $numero: $titulo. ${if (hecho) "Listo" else "Pendiente"}"
        },
        leadingContent = {
            Icon(
                if (hecho) Icons.Filled.CheckCircle else Icons.Outlined.Circle,
                contentDescription = null,
                tint = if (hecho) cs.tertiary else cs.outline,
                modifier = Modifier.size(28.dp)
            )
        },
        headlineContent = { Text("$numero. $titulo", style = MaterialTheme.typography.bodyLarge) },
        supportingContent = { Text(if (hecho) "Listo" else detalle) },
        trailingContent = {
            if (!hecho) {
                Button(onClick = onClick, modifier = Modifier.heightIn(min = 48.dp)) { Text(boton) }
            }
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )
}

@Composable
private fun AyudaRestringida() {
    val context = LocalContext.current
    var abierto by remember { mutableStateOf(false) }
    TextButton(
        onClick = { abierto = !abierto },
        modifier = Modifier.padding(start = Esp.s, bottom = Esp.xs).heightIn(min = 48.dp)
    ) {
        Icon(Icons.Outlined.Info, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(Esp.s))
        Text(if (abierto) "Ocultar ayuda" else "¿Dice \"Configuración restringida\"?")
    }
    if (abierto) {
        Column(
            Modifier.padding(start = Esp.l, end = Esp.l, bottom = Esp.l),
            verticalArrangement = Arrangement.spacedBy(Esp.s)
        ) {
            Text("1. Abre la información de la app.")
            Text("2. Toca el menú ⋮ de arriba a la derecha.")
            Text("3. Elige \"Permitir configuración restringida\".")
            Text("4. Vuelve aquí y toca Activar otra vez.")
            OutlinedButton(onClick = { Acciones.ajustesDeLaApp(context) }, modifier = Modifier.heightIn(min = 48.dp)) {
                Text("Abrir información de la app")
            }
        }
    }
}

private data class FilaHistorial(val quien: String, val texto: String?, val cuando: Long)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Historial(
    titulo: String,
    vacio: String,
    filas: List<FilaHistorial>,
    onBorrar: () -> Unit,
    onCerrar: () -> Unit,
    onToque: ((String) -> Unit)? = null
) {
    var confirmar by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onCerrar, containerColor = MaterialTheme.colorScheme.surfaceContainerLowest) {
        Row(
            Modifier.fillMaxWidth().padding(start = Esp.l, end = Esp.s),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(titulo, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            if (filas.isNotEmpty()) {
                TextButton(
                    onClick = { confirmar = true },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text("Borrar historial") }
            }
        }
        if (filas.isEmpty()) {
            EstadoVacio(Icons.Filled.Shield, "Nada por aquí", vacio)
        } else {
            LazyColumn(Modifier.fillMaxWidth(), contentPadding = PaddingValues(bottom = Esp.xl)) {
                items(filas) { f ->
                    ListItem(
                        modifier = if (onToque != null) Modifier.clickable { onToque(f.quien) } else Modifier,
                        leadingContent = { Avatar(f.quien, 40.dp) },
                        headlineContent = { Text(f.quien) },
                        supportingContent = {
                            Column {
                                if (!f.texto.isNullOrBlank()) Text(f.texto, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Text(fechaCorta(f.cuando), color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                    )
                }
            }
        }
    }
    if (confirmar) {
        Confirmar(
            titulo = "¿Borrar historial?",
            texto = "Se borrará la lista de \"$titulo\" de esta app. No afecta tu registro de llamadas ni tus mensajes.",
            accion = "Borrar",
            onConfirmar = { confirmar = false; onBorrar() },
            onCancelar = { confirmar = false }
        )
    }
}

private fun abrir(context: Context, intent: Intent) {
    try {
        context.startActivity(intent)
    } catch (_: Exception) {
    }
}
