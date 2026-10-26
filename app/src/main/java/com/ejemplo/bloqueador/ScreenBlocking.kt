package com.ejemplo.bloqueador

import android.Manifest
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.provider.Telephony
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import java.text.DateFormat
import java.util.Date

@Composable
fun PantallaBloqueo(refreshKey: Int, onChanged: () -> Unit) {
    var pestana by rememberSaveable { mutableIntStateOf(0) }

    Column(Modifier.fillMaxSize()) {
        Titulo("Bloqueo")
        Text(
            "Solo te avisan tus contactos.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 16.dp, bottom = 8.dp)
        )
        TabRow(selectedTabIndex = pestana) {
            Tab(selected = pestana == 0, onClick = { pestana = 0 }, text = { Text("Llamadas") })
            Tab(selected = pestana == 1, onClick = { pestana = 1 }, text = { Text("Mensajes") })
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {
            if (pestana == 0) pestanaLlamadas(refreshKey, onChanged)
            else pestanaMensajes(refreshKey, onChanged)
        }
    }
}

// -------------------------------------------------------------------------------------------
// Llamadas
// -------------------------------------------------------------------------------------------

private fun LazyListScope.pestanaLlamadas(refreshKey: Int, onChanged: () -> Unit) {
    item(key = "llamadas_config") { ConfigLlamadas(refreshKey, onChanged) }

    item(key = "llamadas_titulo") {
        val context = LocalContext.current
        val prefs = remember { Prefs(context) }
        val bloqueadas = remember(refreshKey) { prefs.blockedCalls() }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Encabezado("Bloqueadas (${bloqueadas.size})", bloqueadas.isNotEmpty()) { prefs.clearLog(); onChanged() }
            if (bloqueadas.isEmpty()) Vacio("Aún no se ha bloqueado ninguna llamada.")
            bloqueadas.forEach { call ->
                ListItem(
                    headlineContent = { Text(call.number) },
                    supportingContent = { Text(fecha(call.timestamp)) }
                )
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun ConfigLlamadas(refreshKey: Int, onChanged: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { Prefs(context) }
    val roleManager = remember { context.getSystemService(RoleManager::class.java) }

    val tieneRol = remember(refreshKey) { roleManager.isRoleHeld(RoleManager.ROLE_CALL_SCREENING) }
    val tieneContactos = remember(refreshKey) { Contacts.hasPermission(context) }

    var enabled by remember { mutableStateOf(prefs.enabled) }
    var blockHidden by remember { mutableStateOf(prefs.blockHidden) }
    var allowRepeat by remember { mutableStateOf(prefs.allowRepeat) }

    val pedirRol = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { onChanged() }
    val pedirContactos = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { onChanged() }

    val listo = tieneRol && tieneContactos

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Estado(listo, enabled) {
            if (!tieneRol) {
                Text("Elige esta app como tu app de filtrado de llamadas.")
                Button(onClick = {
                    pedirRol.launch(roleManager.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING))
                }) { Text("Activar filtrado") }
            }
            if (!tieneContactos) {
                Text("Permite leer contactos para saber quién es conocido. Sin esto no se bloquea nada.")
                Button(onClick = { pedirContactos.launch(Manifest.permission.READ_CONTACTS) }) {
                    Text("Permitir contactos")
                }
            }
            if (listo) Text("Todo listo. Puedes cerrar la app; el filtro sigue funcionando.")
        }

        Card {
            Column(Modifier.padding(vertical = 8.dp)) {
                Ajuste("Bloquear desconocidos", "Interruptor principal", enabled) {
                    enabled = it; prefs.enabled = it
                }
                Ajuste("Bloquear números ocultos", "Privados o sin identificador", blockHidden) {
                    blockHidden = it; prefs.blockHidden = it
                }
                Ajuste(
                    "Dejar pasar si insisten",
                    "Si el mismo número vuelve a llamar en menos de 3 min, suena (por si es urgente)",
                    allowRepeat
                ) { allowRepeat = it; prefs.allowRepeat = it }
            }
        }
    }
}

// -------------------------------------------------------------------------------------------
// Mensajes
// -------------------------------------------------------------------------------------------

private fun LazyListScope.pestanaMensajes(refreshKey: Int, onChanged: () -> Unit) {
    item(key = "sms_config") { ConfigMensajes(refreshKey, onChanged) }

    item(key = "sms_lista") {
        val context = LocalContext.current
        val prefs = remember { Prefs(context) }
        val mensajes = remember(refreshKey) { prefs.silencedSms() }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Encabezado("Silenciados (${mensajes.size})", mensajes.isNotEmpty()) { prefs.clearSmsLog(); onChanged() }
            if (mensajes.isEmpty()) {
                Vacio("Aún no se ha silenciado ningún mensaje.")
            } else {
                Text(
                    "Los mensajes siguen en tu app de SMS. Toca uno para abrirlo.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            mensajes.forEach { sms ->
                ListItem(
                    modifier = Modifier.clickable { abrirConversacion(context, sms.sender) },
                    headlineContent = { Text(sms.sender) },
                    supportingContent = {
                        Column {
                            if (sms.text.isNotBlank()) Text(sms.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(
                                fecha(sms.timestamp),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                )
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun ConfigMensajes(refreshKey: Int, onChanged: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { Prefs(context) }

    val tieneContactos = remember(refreshKey) { Contacts.hasPermission(context) }
    val tieneNotificaciones = remember(refreshKey) {
        NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)
    }

    var smsEnabled by remember { mutableStateOf(prefs.smsEnabled) }
    var incluirEmpresas by remember { mutableStateOf(prefs.smsIncludeBusiness) }

    val pedirContactos = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { onChanged() }

    val listo = tieneContactos && tieneNotificaciones

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Estado(listo, smsEnabled) {
            if (!tieneNotificaciones) {
                Text("Da acceso a las notificaciones para poder silenciar los SMS de desconocidos.")
                Button(onClick = {
                    context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                }) { Text("Dar acceso a notificaciones") }
                Text(
                    "Si Android dice \"Configuración restringida\": abre la info de la app, toca el menú ⋮ " +
                        "de arriba a la derecha → \"Permitir configuración restringida\" y vuelve a intentarlo.",
                    style = MaterialTheme.typography.bodySmall
                )
                OutlinedButton(onClick = {
                    context.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
                    )
                }) { Text("Abrir info de la app") }
            }
            if (!tieneContactos) {
                Text("Permite leer contactos para saber quién es conocido.")
                Button(onClick = { pedirContactos.launch(Manifest.permission.READ_CONTACTS) }) {
                    Text("Permitir contactos")
                }
            }
            if (listo) Text("Todo listo. Los SMS de desconocidos llegan sin sonido ni aviso.")
        }

        Card {
            Column(Modifier.padding(vertical = 8.dp)) {
                Ajuste(
                    "Silenciar SMS de desconocidos",
                    "Llegan a tu app de mensajes, pero sin sonido ni notificación",
                    smsEnabled
                ) { smsEnabled = it; prefs.smsEnabled = it }
                Ajuste(
                    "Incluir empresas y códigos cortos",
                    "BANCO, TELCEL, 2525… Ojo: también silencia códigos de verificación",
                    incluirEmpresas
                ) { incluirEmpresas = it; prefs.smsIncludeBusiness = it }
            }
        }
    }
}

private fun abrirConversacion(context: Context, sender: String) {
    val esNumero = sender.any { it.isDigit() } && sender.all { it.isDigit() || it in "+-() ." }
    val intent = if (esNumero) {
        Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:${Uri.encode(sender)}"))
    } else {
        Telephony.Sms.getDefaultSmsPackage(context)?.let { context.packageManager.getLaunchIntentForPackage(it) }
    } ?: return
    try {
        context.startActivity(intent)
    } catch (_: Exception) {
    }
}

// -------------------------------------------------------------------------------------------
// Componentes comunes
// -------------------------------------------------------------------------------------------

@Composable
private fun Estado(listo: Boolean, activo: Boolean, contenido: @Composable ColumnScope.() -> Unit) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (listo && activo) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                when {
                    !listo -> "Falta configurar"
                    activo -> "Protección activa"
                    else -> "Protección en pausa"
                },
                style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold
            )
            contenido()
        }
    }
}

@Composable
private fun Encabezado(titulo: String, mostrarBorrar: Boolean, onBorrar: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(titulo, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        if (mostrarBorrar) TextButton(onClick = onBorrar) { Text("Borrar") }
    }
}

@Composable
private fun Vacio(texto: String) {
    Text(texto, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun Ajuste(titulo: String, detalle: String, valor: Boolean, onCambio: (Boolean) -> Unit) {
    ListItem(
        headlineContent = { Text(titulo) },
        supportingContent = { Text(detalle) },
        trailingContent = { Switch(checked = valor, onCheckedChange = onCambio) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )
}

private fun fecha(ts: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(ts))
