package com.ejemplo.bloqueador

import android.Manifest
import android.app.role.RoleManager
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.telephony.SmsMessage
import android.telephony.SubscriptionManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SmsSetupCard(refreshKey: Int, onChanged: () -> Unit = {}) {
    val context = LocalContext.current
    var revision by remember { mutableIntStateOf(0) }
    var explain by rememberSaveable { mutableStateOf(false) }
    val current = remember(refreshKey, revision) { SmsRepository.isDefault(context) }
    val ready = SmsRepository.permissions.all { PhoneData.has(context, it) }
    val role = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        revision++; onChanged(); SmsInboxQueue.enqueue(context)
    }
    val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { revision++; onChanged() }
    val notifications = NotificationManagerCompat.from(context).areNotificationsEnabled()

    if (!current && SmsRepository.roleAvailable(context)) {
        AvisoAppPredeterminada("Establecer Bloqueador como app de SMS predeterminada", onClick = { explain = true },
            modifier = Modifier.padding(vertical = Esp.s))
    }
    if (!current && !SmsRepository.roleAvailable(context)) {
        Text("Este dispositivo no ofrece el rol de app de SMS.", color = MaterialTheme.colorScheme.error)
    }
    if (current && (!ready || !notifications)) Tarjeta {
        Column(Modifier.padding(Esp.l), verticalArrangement = Arrangement.spacedBy(Esp.s)) {
            Text("Termina de activar tus SMS", style = MaterialTheme.typography.titleMedium)
            Text("Los mensajes se guardan aunque deniegues las notificaciones.", style = MaterialTheme.typography.bodyMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Esp.s)) {
                if (!ready) OutlinedButton(onClick = { permissions.launch(SmsRepository.permissions) }) { Text("Permitir SMS") }
                if (!notifications && Build.VERSION.SDK_INT >= 33) OutlinedButton(onClick = { permissions.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS)) }) { Text("Activar notificaciones") }
            }
        }
    }
    if (explain) AlertDialog(onDismissRequest = { explain = false }, title = { Text("Usar Bloqueador para tus SMS") },
        text = { Text("Tus SMS existentes seguirán en el teléfono. Bloqueador recibirá y enviará SMS de texto, guardará los mensajes y permitirá borrarlos. Puede recibir y abrir MMS; enviar fotos y RCS todavía no está incluido. Los SMS pueden tener costo según tu operador y los MMS usan datos móviles. Puedes cambiar de app predeterminada en los ajustes de Android.") },
        confirmButton = { TextButton(onClick = {
            explain = false
            role.launch(context.getSystemService(RoleManager::class.java).createRequestRoleIntent(RoleManager.ROLE_SMS))
        }) { Text("Elegir app SMS") } }, dismissButton = { TextButton(onClick = { explain = false }) { Text("Cancelar") } })
}

@Composable
fun SmsComposer(address: String, initialBody: String, refreshKey: Int, onClose: () -> Unit, onSent: (Long?) -> Unit) {
    val context = LocalContext.current
    val drafts = remember { context.getSharedPreferences("sms_drafts", android.content.Context.MODE_PRIVATE) }
    val draftKey = address.ifBlank { "new" }
    var recipient by rememberSaveable(address) { mutableStateOf(address.ifBlank { drafts.getString("$draftKey:to", "").orEmpty() }) }
    var body by rememberSaveable(address, initialBody) { mutableStateOf(initialBody.ifBlank { drafts.getString("$draftKey:body", "").orEmpty() }) }
    var revision by remember { mutableIntStateOf(0) }
    val lines = remember(refreshKey, revision) { SmsRepository.lines(context) }
    var selected by rememberSaveable { mutableIntStateOf(SubscriptionManager.getDefaultSmsSubscriptionId().takeIf { id -> lines.any { it.id == id } } ?: lines.singleOrNull()?.id ?: -1) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val parts = remember(body) { if (body.isEmpty()) 0 else SmsMessage.calculateLength(body, false)[0] }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { revision++ }

    fun saveDraft() { drafts.edit().putString("$draftKey:to", recipient).putString("$draftKey:body", body).apply() }
    fun close() { saveDraft(); onClose() }
    // Se guarda mientras se escribe, por si Android termina el proceso.
    LaunchedEffect(recipient, body) { saveDraft() }
    BackHandler(enabled = !busy) { close() }

    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).imePadding()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { close() }, enabled = !busy) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Guardar borrador y volver") }
            Text("Escribir SMS", style = MaterialTheme.typography.titleLarge)
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(Esp.l), verticalArrangement = Arrangement.spacedBy(Esp.m)) {
            SmsSetupCard(refreshKey + revision) { revision++ }
            OutlinedTextField(recipient, { recipient = it.take(100) }, label = { Text("Para: número de teléfono") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), modifier = Modifier.fillMaxWidth(),
                isError = recipient.isNotBlank() && SmsPolicy.recipient(recipient) == null, enabled = !busy,
                supportingText = { Text("Un destinatario por SMS. No se envían adjuntos ni mensajes de grupo.") })
            OutlinedTextField(body, { body = it.take(10_000) }, label = { Text("Mensaje") }, modifier = Modifier.fillMaxWidth().heightIn(min = 160.dp), enabled = !busy)
            Text("$parts partes SMS · El operador puede cobrar cada parte.", style = MaterialTheme.typography.bodyMedium)
            if (parts > 10) Text("Reduce el mensaje a un máximo de 10 partes.", color = MaterialTheme.colorScheme.error)
            Text("Enviar con", style = MaterialTheme.typography.titleMedium)
            lines.forEach { line ->
                FilterChip(selected = selected == line.id, onClick = { selected = line.id }, label = { Text(line.label) }, enabled = !busy)
            }
            if (!PhoneData.has(context, Manifest.permission.READ_PHONE_STATE)) OutlinedButton(onClick = { permission.launch(Manifest.permission.READ_PHONE_STATE) }) { Text("Mostrar mis SIM") }
            if (lines.isEmpty()) Text("No se encontró una SIM para SMS. Revisa tus SIM o permite mostrarlas.", color = MaterialTheme.colorScheme.error)
            Text("Enviado confirma que el teléfono aceptó todas las partes; no confirma que el destinatario las haya recibido.", style = MaterialTheme.typography.bodySmall)
        }
        Button(
            enabled = !busy && SmsRepository.isDefault(context) && PhoneData.has(context, Manifest.permission.SEND_SMS) &&
                SmsPolicy.recipient(recipient) != null && body.isNotBlank() && parts in 1..10 && lines.any { it.id == selected },
            onClick = {
                busy = true
                scope.launch {
                    try {
                        val thread = withContext(Dispatchers.IO) {
                            val uri = SmsRepository.send(context, recipient, body, selected)
                            drafts.edit().remove("$draftKey:to").remove("$draftKey:body").apply()
                            try { SmsRepository.threadFor(context, uri) } catch (_: Exception) { null }
                        }
                        onSent(thread)
                    } catch (e: CancellationException) { throw e
                    } catch (e: Exception) {
                        Toast.makeText(context, e.message ?: "No se pudo enviar. El borrador se conserva.", Toast.LENGTH_LONG).show()
                    } finally { busy = false }
                }
            }, modifier = Modifier.fillMaxWidth().padding(Esp.l).heightIn(min = 52.dp)
        ) {
            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null); Spacer(Modifier.width(Esp.s))
            Text(if (busy) "Enviando…" else "Enviar SMS${if (parts > 1) " ($parts partes)" else ""}")
        }
    }
}
