package com.ejemplo.bloqueador

import android.Manifest
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HojaNumero(numero: String, nombre: String?, historial: List<CallEntry>, onCerrar: () -> Unit) {
    val context = LocalContext.current
    val abrirFormulario = LocalAbrirFormulario.current
    val scope = rememberCoroutineScope()
    val prefs = remember { Prefs(context) }
    var blocked by remember(numero) { mutableStateOf(prefs.manuallyBlocked(numero)) }
    var confirmBlock by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    val valid = SmsPolicy.recipient(numero) != null
    val canBlock = valid && Prefs.normalize(numero).length >= 7
    fun edit() {
        if (editing) return
        editing = true
        scope.launch {
            try {
                val id = withContext(Dispatchers.IO) { Contacts.findContactId(context, numero) }
                onCerrar()
                abrirFormulario(PeticionFormulario(contactId = id, numero = if (id == null) numero else null))
            } catch (_: Exception) { Toast.makeText(context, "No se pudo abrir el contacto. Revisa sus permisos.", Toast.LENGTH_LONG).show() }
            finally { editing = false }
        }
    }
    val readContacts = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        if (it) edit() else Toast.makeText(context, "Se necesita acceso a la agenda para editar el contacto.", Toast.LENGTH_LONG).show()
    }
    val title = nombre ?: numero.ifBlank { "Número oculto" }
    ModalBottomSheet(onDismissRequest = onCerrar,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = Esp.xl)) {
            Text(title, style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.fillMaxWidth().padding(horizontal = Esp.l, vertical = Esp.s), textAlign = TextAlign.Center)
            Row(Modifier.fillMaxWidth().padding(horizontal = Esp.s, vertical = Esp.m)) {
                CallAction(Icons.Filled.Call, "Llamada", valid, { Acciones.llamar(context, numero); onCerrar() }, Modifier.weight(1f))
                CallAction(Icons.Filled.Chat, "WhatsApp", valid, { if (Acciones.whatsapp(context, numero)) onCerrar() }, Modifier.weight(1f), whatsapp = true)
                CallAction(Icons.Filled.Edit, "Editar", valid && !editing, {
                    if (Contacts.hasPermission(context)) edit() else readContacts.launch(Manifest.permission.READ_CONTACTS)
                }, Modifier.weight(1f))
                CallAction(if (blocked) Icons.Filled.LockOpen else Icons.Filled.Block,
                    if (blocked) "Desbloquear" else "Bloquear", canBlock, { confirmBlock = true }, Modifier.weight(1f))
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            ListItem(
                leadingContent = { Icon(Icons.Filled.Call, contentDescription = null) },
                headlineContent = { Text(numero.ifBlank { "Número oculto" }, style = MaterialTheme.typography.titleMedium) },
                supportingContent = { Text(if (blocked) "Llamadas bloqueadas manualmente" else if (nombre != null) "Contacto guardado" else "No está en tu agenda") },
                trailingContent = { IconButton(enabled = valid, onClick = { Acciones.mensaje(context, numero); onCerrar() }) {
                    Icon(Icons.Filled.Sms, contentDescription = "Enviar SMS a $title")
                } }, colors = ListItemDefaults.colors(containerColor = Color.Transparent)
            )
            if (historial.isNotEmpty()) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Text("Historial", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(Esp.l))
                historial.forEach { call -> Box(Modifier.fillMaxWidth().padding(horizontal = Esp.l, vertical = Esp.s)) { LineaTipo(call) } }
            }
        }
    }
    if (confirmBlock) Confirmar(if (blocked) "¿Desbloquear este número?" else "¿Bloquear este número?",
        if (blocked) "Se quitará el bloqueo manual. Los horarios y otras reglas de protección seguirán aplicándose."
        else "Sus llamadas se rechazarán en todos los perfiles, incluso si está en tu agenda o tiene una excepción de permitir. El interruptor de llamadas de Protección permite pausar el bloqueo. No cambia sus SMS.",
        if (blocked) "Desbloquear" else "Bloquear", onConfirmar = {
            prefs.setManualBlock(numero, !blocked); blocked = !blocked; confirmBlock = false
        }, onCancelar = { confirmBlock = false })
}

@Composable
private fun CallAction(icon: ImageVector, label: String, enabled: Boolean, onClick: () -> Unit, modifier: Modifier, whatsapp: Boolean = false) {
    Column(modifier.clip(MaterialTheme.shapes.medium).clickable(enabled = enabled, role = Role.Button, onClickLabel = label, onClick = onClick)
        .alpha(if (enabled) 1f else 0.4f).heightIn(min = 88.dp).padding(vertical = Esp.s),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Esp.s)) {
        IconoCirculo(icon,
            fondo = if (whatsapp) Color(0xFFD3EDDF) else MaterialTheme.colorScheme.primaryContainer,
            color = if (whatsapp) Color(0xFF075E34) else MaterialTheme.colorScheme.onPrimaryContainer,
            tamano = 54.dp)
        Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
