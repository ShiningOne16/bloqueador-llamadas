package com.ejemplo.bloqueador

import android.Manifest
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Report
import androidx.compose.material.icons.filled.Warning
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
fun HojaNumero(
    numero: String, nombre: String?, historial: List<CallEntry>, onCerrar: () -> Unit,
    archivado: Boolean = false, onArchivar: (() -> Unit)? = null, onEliminar: (() -> Unit)? = null,
    onNotas: () -> Unit = {}
) {
    val context = LocalContext.current
    val abrirFormulario = LocalAbrirFormulario.current
    val scope = rememberCoroutineScope()
    val prefs = remember { Prefs(context) }
    var blocked by remember(numero) { mutableStateOf(prefs.manuallyBlocked(numero)) }
    var confirmBlock by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    val noteStore = remember { NoteStore(context) }
    var nota by remember(numero) { mutableStateOf(noteStore.get(numero)) }
    var editandoNota by remember { mutableStateOf(false) }
    val spamStore = remember { SpamStore(context) }
    var reportado by remember(numero) { mutableStateOf(spamStore.reportado(numero)) }
    var permitidoSpam by remember(numero) { mutableStateOf(spamStore.permitido(numero)) }
    val enLista = remember(numero) { SpamLista.categoria(context, numero) }
    var reportando by remember { mutableStateOf(false) }
    var dialogoSpam by remember { mutableStateOf(false) }
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
    val title = NoteRules.title(nombre, nota?.label, numero)
    ModalBottomSheet(onDismissRequest = onCerrar,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = Esp.xl)) {
            Column(Modifier.fillMaxWidth().padding(horizontal = Esp.l), horizontalAlignment = Alignment.CenterHorizontally) {
                Avatar(title, 72.dp)
                Spacer(Modifier.height(Esp.m))
                Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (title != numero && numero.isNotBlank()) {
                    Text(numero, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = Esp.s, vertical = Esp.l)) {
                CallAction(Icons.Filled.Call, "Llamar", valid, { Acciones.llamar(context, numero); onCerrar() }, Modifier.weight(1f), TonoAccion.Principal)
                CallAction(Icons.Filled.Chat, "WhatsApp", valid, { if (Acciones.whatsapp(context, numero)) onCerrar() }, Modifier.weight(1f), TonoAccion.WhatsApp)
                CallAction(Icons.Filled.Edit, "Editar", valid && !editing, {
                    if (Contacts.hasPermission(context)) edit() else readContacts.launch(Manifest.permission.READ_CONTACTS)
                }, Modifier.weight(1f), TonoAccion.Neutro)
                CallAction(if (blocked) Icons.Filled.LockOpen else Icons.Filled.Block,
                    if (blocked) "Desbloquear" else "Bloquear", canBlock, { confirmBlock = true }, Modifier.weight(1f),
                    if (blocked) TonoAccion.Neutro else TonoAccion.Peligro)
            }
            Tarjeta(Modifier.padding(horizontal = Esp.l)) {
                ListItem(
                    leadingContent = { Icon(Icons.Filled.Call, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                    headlineContent = { Text(numero.ifBlank { "Número oculto" }, style = MaterialTheme.typography.titleMedium) },
                    supportingContent = { Text(if (blocked) "Llamadas bloqueadas manualmente" else if (nombre != null) "Contacto guardado" else "No está en tu agenda") },
                    trailingContent = { IconButton(enabled = valid, onClick = { Acciones.mensaje(context, numero); onCerrar() }) {
                        Icon(Icons.Filled.Sms, contentDescription = "Enviar SMS a $title", tint = MaterialTheme.colorScheme.primary)
                    } }, colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )
            }
            if (NoteRules.key(numero).isNotEmpty()) {
                Spacer(Modifier.height(Esp.m))
                Tarjeta(Modifier.padding(horizontal = Esp.l)) {
                    ListItem(
                        modifier = Modifier.clickable(role = Role.Button, onClickLabel = if (nota == null) "Agregar nota" else "Editar nota") { editandoNota = true },
                        leadingContent = { Icon(Icons.Filled.Bookmark, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                        headlineContent = { Text(nota?.label?.ifBlank { "Nota" } ?: "Agregar nota") },
                        supportingContent = nota?.note?.takeIf { it.isNotBlank() }?.let { texto -> { Text(texto, maxLines = 3, overflow = TextOverflow.Ellipsis) } },
                        trailingContent = { Icon(Icons.Filled.Edit, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                    )
                }
            }
            if (nombre == null && SpamRules.reportable(numero)) {
                Spacer(Modifier.height(Esp.m))
                Tarjeta(Modifier.padding(horizontal = Esp.l)) {
                    if (enLista != null) {
                        ListItem(
                            leadingContent = { Icon(Icons.Filled.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                            headlineContent = { Text("En la lista de spam") },
                            supportingContent = { Text(enLista.etiqueta) },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                        )
                        HorizontalDivider(Modifier.padding(horizontal = Esp.l), color = MaterialTheme.colorScheme.outlineVariant)
                        ListItem(
                            modifier = Modifier.clickable(role = Role.Switch) {
                                permitidoSpam = !permitidoSpam; spamStore.permitir(numero, permitidoSpam)
                            },
                            headlineContent = { Text("No es spam") },
                            supportingContent = { Text("Deja de bloquearse por la lista en este teléfono") },
                            trailingContent = { Switch(checked = permitidoSpam, onCheckedChange = null) },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                        )
                        HorizontalDivider(Modifier.padding(horizontal = Esp.l), color = MaterialTheme.colorScheme.outlineVariant)
                    }
                    ListItem(
                        modifier = Modifier.clickable(enabled = !reportado && !reportando, role = Role.Button) { dialogoSpam = true },
                        leadingContent = { Icon(Icons.Filled.Report, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                        headlineContent = { Text(if (reportado) "Ya lo reportaste" else "Reportar como spam") },
                        supportingContent = { Text("Ayuda a que otros no contesten este número") },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                    )
                }
            }
            if (historial.isNotEmpty()) {
                Rotulo("Historial")
                Tarjeta(Modifier.padding(horizontal = Esp.l)) {
                    historial.forEachIndexed { i, call ->
                        if (i > 0) HorizontalDivider(Modifier.padding(horizontal = Esp.l), color = MaterialTheme.colorScheme.outlineVariant)
                        Box(Modifier.fillMaxWidth().padding(horizontal = Esp.l, vertical = Esp.m)) { LineaTipo(call) }
                    }
                }
            }
            if (onArchivar != null || onEliminar != null) {
                Spacer(Modifier.height(Esp.m))
                Tarjeta(Modifier.padding(horizontal = Esp.l)) {
                    if (onArchivar != null) ListItem(
                        modifier = Modifier.clickable(role = Role.Button) { onCerrar(); onArchivar() },
                        leadingContent = { Icon(if (archivado) Icons.Filled.Unarchive else Icons.Filled.Archive, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                        headlineContent = { Text(if (archivado) "Desarchivar" else "Archivar") },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                    )
                    if (onArchivar != null && onEliminar != null) HorizontalDivider(Modifier.padding(horizontal = Esp.l), color = MaterialTheme.colorScheme.outlineVariant)
                    if (onEliminar != null) ListItem(
                        modifier = Modifier.clickable(role = Role.Button) { onCerrar(); onEliminar() },
                        leadingContent = { Icon(Icons.Filled.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                        headlineContent = { Text("Eliminar del registro", color = MaterialTheme.colorScheme.error) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                    )
                }
            }
        }
    }
    if (dialogoSpam) DialogoReporteSpam(
        numero = numero, puedeBloquear = canBlock && !blocked,
        onReportar = { categoria, bloquear ->
            dialogoSpam = false
            if (bloquear) { prefs.setManualBlock(numero, true); blocked = true }
            reportando = true
            scope.launch {
                val error = withContext(Dispatchers.IO) { SpamComunidad.reportar(context, numero, categoria) }
                reportando = false
                if (error == null) reportado = spamStore.reportado(numero)
                Toast.makeText(context,
                    error ?: "Reporte enviado. Gracias.", Toast.LENGTH_LONG).show()
            }
        },
        onCerrar = { dialogoSpam = false }
    )
    if (editandoNota) DialogoNota(
        numeroInicial = numero, editarNumero = false,
        etiquetaInicial = nota?.label.orEmpty(), notaInicial = nota?.note.orEmpty(), puedeBorrar = nota != null,
        onGuardar = { n, etiqueta, texto ->
            noteStore.set(n, etiqueta, texto); nota = noteStore.get(numero); editandoNota = false; onNotas()
        },
        onBorrar = { noteStore.remove(numero); nota = null; editandoNota = false; onNotas() },
        onCerrar = { editandoNota = false }
    )
    if (confirmBlock) Confirmar(if (blocked) "¿Desbloquear este número?" else "¿Bloquear este número?",
        if (blocked) "Se quitará el bloqueo manual. Los horarios y otras reglas de protección seguirán aplicándose."
        else "Sus llamadas se rechazarán en todos los perfiles, incluso si está en tu agenda o tiene una excepción de permitir. El interruptor de llamadas de Protección permite pausar el bloqueo. No cambia sus SMS.",
        if (blocked) "Desbloquear" else "Bloquear", onConfirmar = {
            prefs.setManualBlock(numero, !blocked); blocked = !blocked; confirmBlock = false
        }, onCancelar = { confirmBlock = false })
}

private enum class TonoAccion { Principal, WhatsApp, Neutro, Peligro }

@Composable
private fun CallAction(icon: ImageVector, label: String, enabled: Boolean, onClick: () -> Unit, modifier: Modifier, tono: TonoAccion) {
    val extra = LocalColoresExtra.current
    val oscuro = isSystemInDarkTheme()
    val (fondo, color) = when (tono) {
        TonoAccion.Principal -> extra.marca to extra.onMarca
        TonoAccion.WhatsApp -> if (oscuro) Color(0xFF14532D) to Color(0xFFBBF7D0) else Color(0xFFD3EDDF) to Color(0xFF075E34)
        TonoAccion.Neutro -> MaterialTheme.colorScheme.surfaceContainerHigh to MaterialTheme.colorScheme.onSurface
        TonoAccion.Peligro -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
    }
    Column(modifier.clip(MaterialTheme.shapes.medium).clickable(enabled = enabled, role = Role.Button, onClickLabel = label, onClick = onClick)
        .alpha(if (enabled) 1f else 0.4f).heightIn(min = 88.dp).padding(vertical = Esp.s),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Esp.s)) {
        IconoCirculo(icon, fondo = fondo, color = color, tamano = 56.dp)
        Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
