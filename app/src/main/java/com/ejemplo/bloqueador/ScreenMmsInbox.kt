package com.ejemplo.bloqueador

import android.content.ContentUris
import android.provider.Telephony
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun MmsInboxScreen(refreshKey: Int, onClose: () -> Unit) {
    val context = LocalContext.current
    val changes = messageChanges(PhoneData.has(context, android.Manifest.permission.READ_SMS))
    var revision by remember { mutableIntStateOf(0) }
    var messages by remember { mutableStateOf<List<MmsItem>?>(null) }
    var deleting by remember { mutableStateOf<MmsItem?>(null) }
    var error by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(refreshKey, revision, changes) {
        try { messages = withContext(Dispatchers.IO) { MmsInbox.list(context) }; error = false }
        catch (_: Exception) { error = true }
    }
    BackHandler(onBack = onClose)
    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver a SMS") }
            Text("Multimedia recibida", style = MaterialTheme.typography.titleLarge)
        }
        Text("Los MMS usan datos móviles. En esta versión no se envían fotos ni se ofrece RCS.", Modifier.padding(Esp.l), style = MaterialTheme.typography.bodyMedium)
        OutlinedButton(enabled = SmsRepository.isDefault(context), onClick = {
            scope.launch {
                try { withContext(Dispatchers.IO) { MmsInbox.retry(context) }; revision++ }
                catch (_: Exception) { Toast.makeText(context, "No se pudo descargar. Revisa los datos móviles y la SIM.", Toast.LENGTH_LONG).show() }
            }
        }, modifier = Modifier.padding(horizontal = Esp.l)) { Text("Reintentar descargas pendientes") }
        if (error) Text("No se pudieron leer los MMS. Revisa el permiso de SMS.", Modifier.padding(Esp.l), color = MaterialTheme.colorScheme.error)
        else if (messages == null) Cargando()
        else LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(Esp.l), verticalArrangement = Arrangement.spacedBy(Esp.m)) {
            if (messages!!.isEmpty()) item { Text("No hay MMS recibidos.") }
            items(messages.orEmpty(), key = { it.id }) { item ->
                Tarjeta {
                    Column(Modifier.padding(Esp.l), verticalArrangement = Arrangement.spacedBy(Esp.s)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(item.sender, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                            if (SmsRepository.isDefault(context)) IconButton(onClick = { deleting = item }) { Icon(Icons.Filled.Delete, contentDescription = "Eliminar MMS") }
                        }
                        Text(fechaCorta(item.date), style = MaterialTheme.typography.bodySmall)
                        Text(if (item.pending) "Pendiente de descarga" else item.text.ifBlank { "Mensaje multimedia" })
                        item.parts.forEachIndexed { index, part ->
                            TextButton(onClick = {
                                scope.launch {
                                    try { val intent = withContext(Dispatchers.IO) { MmsInbox.attachmentIntent(context, part) }; context.startActivity(intent) }
                                    catch (_: Exception) { Toast.makeText(context, "No se encontró una app para abrir este adjunto.", Toast.LENGTH_LONG).show() }
                                }
                            }) { Text("Abrir adjunto ${index + 1} (${part.mime})") }
                        }
                    }
                }
            }
        }
    }
    deleting?.let { item -> Confirmar("¿Eliminar MMS?", "Se borrará el mensaje y sus adjuntos del teléfono. No se puede deshacer.", "Eliminar", onConfirmar = {
        deleting = null
        scope.launch {
            try {
                check(SmsRepository.isDefault(context))
                withContext(Dispatchers.IO) { MmsInbox.delete(context, item) }
                revision++
            } catch (_: Exception) { Toast.makeText(context, "No se pudo borrar el MMS.", Toast.LENGTH_LONG).show() }
        }
    }, onCancelar = { deleting = null }) }
}
