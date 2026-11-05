package com.ejemplo.bloqueador

import android.Manifest
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.MarkEmailRead
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

@Composable
fun PantallaMensajes(refreshKey: Int, pedirPermisos: () -> Unit, request: SmsOpenRequest?, onRequestConsumed: () -> Unit) {
    val context = LocalContext.current
    val tienePermiso = remember(refreshKey) { PhoneData.has(context, Manifest.permission.READ_SMS) }
    val archiveStore = remember { ArchiveStore(context) }
    var archive by remember(refreshKey) { mutableStateOf(archiveStore.read()) }
    var filtro by rememberSaveable { mutableIntStateOf(0) }
    var busqueda by rememberSaveable { mutableStateOf("") }
    var recarga by remember { mutableIntStateOf(0) }
    var deleteThread by remember { mutableStateOf<SmsThread?>(null) }
    var abiertoId by rememberSaveable { mutableStateOf<Long?>(null) }
    var loadError by remember { mutableStateOf(false) }
    var composeAddress by rememberSaveable { mutableStateOf<String?>(null) }
    var composeBody by rememberSaveable { mutableStateOf("") }
    var multimedia by rememberSaveable { mutableStateOf(false) }
    var selectedThreads by rememberSaveable { mutableStateOf(listOf<SmsDeletionTarget>()) }
    var bulkDelete by remember { mutableStateOf<List<SmsDeletionTarget>?>(null) }
    var busy by remember { mutableStateOf(false) }
    val changes = messageChanges(tienePermiso)
    val scope = rememberCoroutineScope()
    LaunchedEffect(request?.token) {
        request?.let {
            selectedThreads = emptyList()
            multimedia = false
            if (it.compose) { composeAddress = it.address; composeBody = it.body; abiertoId = null }
            else { composeAddress = null; abiertoId = it.threadId }
            onRequestConsumed()
        }
    }

    var hilos by remember { mutableStateOf<List<SmsThread>?>(null) }
    LaunchedEffect(refreshKey, tienePermiso, recarga, filtro == 3, changes) {
        if (tienePermiso) {
            loadError = false
            try {
                val loaded = withContext(Dispatchers.IO) {
                    PhoneData.loadThreads(context, PhoneData.nameIndex(PhoneData.loadContacts(context)),
                        onlyIds = if (filtro == 3) archive.sms.mapNotNull { it.substringBefore(':').toLongOrNull() }.toSet() else null)
                }
                var current = archiveStore.read()
                loaded.forEach { current = current.refreshThread(it.threadId, it.archiveKey()) }
                archive = current
                archiveStore.save(current)
                hilos = loaded
                val available = loaded.filter { (it.archiveKey() in current.sms) == (filtro == 3) }
                    .filter { when (filtro) { 1 -> it.unread > 0; 2 -> it.name == null; else -> true } }.map { it.threadId }.toSet()
                selectedThreads = selectedThreads.filter { it.threadId in available }
            } catch (e: CancellationException) { throw e
            } catch (_: Exception) { loadError = true; hilos = emptyList() }
        }
    }
    val consulta = busqueda.trim()
    var resultados by remember { mutableStateOf<Pair<String, Set<Long>>?>(null) }
    var searchError by remember { mutableStateOf(false) }
    LaunchedEffect(consulta, hilos, tienePermiso, changes, recarga) {
        resultados = null
        searchError = false
        val lista = hilos ?: return@LaunchedEffect
        if (!tienePermiso || consulta.isBlank()) return@LaunchedEffect
        delay(300)
        try {
            val coincidencias = withContext(Dispatchers.IO) {
                PhoneData.searchSmsThreads(context, consulta, lista.map { it.threadId }.toSet())
            }
            resultados = consulta to coincidencias
        } catch (e: CancellationException) { throw e
        } catch (_: Exception) { searchError = true }
    }
    fun toggleThread(thread: SmsThread) {
        selectedThreads = if (selectedThreads.any { it.threadId == thread.threadId }) selectedThreads.filterNot { it.threadId == thread.threadId }
        else selectedThreads + SmsDeletionTarget(thread.threadId, thread.last.id, thread.last.date)
    }

    fun toggleArchive(thread: SmsThread) {
        val current = archiveStore.read()
        archive = if (thread.archiveKey() in current.sms) current.restoreThread(thread.threadId)
        else current.archiveThread(thread.threadId, thread.archiveKey())
        archiveStore.save(archive)
        abiertoId = null
        recarga++
    }
    deleteThread?.let { thread ->
        if (SmsRepository.isDefault(context)) {
            Confirmar("¿Eliminar conversación?", "Se borrarán del teléfono los SMS que aparecen en esta conversación. Los mensajes que lleguen después de esta selección se conservan. No se puede deshacer.", "Eliminar", onConfirmar = {
                deleteThread = null
                scope.launch {
                    try {
                        withContext(Dispatchers.IO) { SmsRepository.deleteThread(context, thread) }
                        archive = archiveStore.read().restoreThread(thread.threadId); archiveStore.save(archive)
                        withContext(Dispatchers.IO) { SmsRepository.clearNotificationIfNoUnread(context, thread.threadId) }; abiertoId = null; recarga++
                    } catch (_: Exception) { Toast.makeText(context, "No se pudo borrar. Comprueba que Teléfono siga siendo la app de SMS predeterminada.", Toast.LENGTH_LONG).show() }
                }
            }, onCancelar = { deleteThread = null })
        } else {
        AlertDialog(onDismissRequest = { deleteThread = null }, title = { Text("Eliminar conversación de SMS") },
            text = { Text("Android solo permite borrar SMS a tu app de mensajes predeterminada. Abre la conversación con ${thread.name ?: thread.address} y usa allí Eliminar. Abrirla no borra mensajes desde Teléfono.") },
            confirmButton = { TextButton(onClick = {
                if (Acciones.manageSms(context, thread.address)) deleteThread = null
                else Toast.makeText(context, "No se pudo abrir la app de SMS. Ábrela desde tu teléfono para eliminar la conversación.", Toast.LENGTH_LONG).show()
            }) { Text("Abrir app SMS") } },
            dismissButton = { TextButton(onClick = { deleteThread = null }) { Text("Cancelar") } })
        }
    }
    bulkDelete?.let { targets ->
        Confirmar("¿Eliminar ${targets.size} conversaciones?", "Se borrarán del teléfono los SMS anteriores a la selección. Los mensajes que lleguen después se conservarán. No se puede deshacer.", "Eliminar",
            onConfirmar = {
                bulkDelete = null; busy = true
                scope.launch {
                    try {
                        val count = withContext(Dispatchers.IO) {
                            val deleted = SmsRepository.deleteThreads(context, targets)
                            targets.forEach { SmsRepository.clearNotificationIfNoUnread(context, it.threadId) }
                            deleted
                        }
                        var current = archiveStore.read()
                        targets.forEach { current = current.restoreThread(it.threadId) }
                        archive = current; archiveStore.save(current); selectedThreads = emptyList()
                        Toast.makeText(context, "$count SMS eliminados", Toast.LENGTH_LONG).show()
                    } catch (e: CancellationException) { throw e
                    } catch (_: Exception) { Toast.makeText(context, "No se pudo completar el borrado. Revisa los mensajes y la app de SMS predeterminada antes de reintentar.", Toast.LENGTH_LONG).show()
                    } finally { busy = false; recarga++ }
                }
            }, onCancelar = { bulkDelete = null })
    }
    if (multimedia) { MmsInboxScreen(refreshKey, onClose = { multimedia = false }); return }
    composeAddress?.let { address ->
        SmsComposer(address, composeBody, refreshKey, onClose = { composeAddress = null }, onSent = { thread ->
            composeAddress = null; composeBody = ""; abiertoId = thread; recarga++
        })
        return
    }
    val abierto = hilos?.firstOrNull { it.threadId == abiertoId }

    if (abierto != null) {
        BackHandler { abiertoId = null }
        Conversacion(abierto, refreshKey, onAtras = { abiertoId = null },
            archived = abierto.archiveKey() in archive.sms,
            onArchive = { toggleArchive(abierto) }, onDelete = { deleteThread = abierto },
            onReply = { text -> composeAddress = abierto.address; composeBody = text })
        return
    }
    val selectionMode = selectedThreads.isNotEmpty()
    BackHandler(enabled = selectionMode && !busy) { selectedThreads = emptyList() }
    val visibleThreads = hilos.orEmpty().filter { (it.archiveKey() in archive.sms) == (filtro == 3) }
        .filter { when (filtro) { 1 -> it.unread > 0; 2 -> it.name == null; else -> true } }
        .filter { consulta.isBlank() || SmsSearch.matchesSender(it.name, it.address, consulta) ||
            (resultados?.takeIf { result -> result.first == consulta }?.second?.contains(it.threadId) == true) }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            val sinLeer = hilos?.filter { it.archiveKey() !in archive.sms }?.sumOf { it.unread } ?: 0
            if (selectionMode) SelectionToolbar(selectedThreads.size, onClose = { selectedThreads = emptyList() },
                onSelectAll = { selectedThreads = visibleThreads.map { SmsDeletionTarget(it.threadId, it.last.id, it.last.date) } },
                onDelete = { bulkDelete = selectedThreads.toList() }, enabled = !busy, canDelete = SmsRepository.isDefault(context))
            else Titulo("Mensajes", subtitulo = if (sinLeer > 0) "$sinLeer sin leer" else null)
            if (selectionMode && !SmsRepository.isDefault(context)) Text("Para borrar varios SMS, elige Teléfono como app de SMS predeterminada.", Modifier.padding(Esp.l), style = MaterialTheme.typography.bodyMedium)
            Column(Modifier.padding(horizontal = Esp.l)) {
                SmsSetupCard(refreshKey + recarga) { recarga++ }
                if (!selectionMode) TextButton(onClick = { multimedia = true }, enabled = tienePermiso) { Text("Multimedia recibida") }
            }

            if (!tienePermiso) {
                PermisoFaltante(
                    Icons.Filled.Sms,
                    "Tus mensajes aparecerán aquí",
                    "Permite leer SMS para ver tus conversaciones.",
                    pedirPermisos
                )
                return@Column
            }

            if (!selectionMode) {
                Buscador(busqueda, { busqueda = it }, "Buscar nombre, número o mensaje")
                Filtros(listOf("Todos", "Sin leer", "Desconocidos", "Archivadas"), filtro) { filtro = it }
            }
            if (filtro == 3) Text("Archivo local. Un SMS nuevo devuelve la conversación a la bandeja.", Modifier.padding(horizontal = Esp.l, vertical = Esp.s), style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(Esp.s))

            val lista = hilos
            if (searchError && consulta.isNotBlank()) {
                EstadoVacio(Icons.Filled.SearchOff, "No se pudo buscar", "Inténtalo de nuevo.", accion = "Reintentar", onAccion = { recarga++ })
            } else if (loadError) {
                EstadoVacio(Icons.Filled.Sms, "No se pudieron cargar los mensajes", "Revisa el permiso de SMS e inténtalo de nuevo.", accion = "Reintentar", onAccion = { recarga++ })
            } else if (lista == null || (consulta.isNotBlank() && resultados?.first != consulta)) {
                Cargando()
            } else {
                val visibles = visibleThreads
                if (visibles.isEmpty()) {
                    if (consulta.isNotBlank()) {
                        EstadoVacio(Icons.Filled.SearchOff, "Sin resultados", "No hay mensajes que coincidan con \"$consulta\" en este filtro.")
                    } else when (filtro) {
                        1 -> EstadoVacio(Icons.Filled.MarkEmailRead, "Todo leído", "No tienes mensajes pendientes.")
                        2 -> EstadoVacio(Icons.Filled.Sms, "Sin desconocidos", "Ningún número desconocido te ha escrito.")
                        3 -> EstadoVacio(Icons.Filled.Sms, "Sin conversaciones archivadas", "Usa el menú ⋮ de una conversación para archivarla.")
                        else -> EstadoVacio(Icons.Filled.Sms, "Aún no hay mensajes", "Tus conversaciones por SMS aparecerán aquí.")
                    }
                } else {
                    LazyColumn(Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(bottom = 96.dp)) {
                        items(visibles, key = { it.threadId }) { h ->
                            FilaHilo(h, onClick = { if (selectionMode) toggleThread(h) else abiertoId = h.threadId }, archived = filtro == 3,
                                onLongClick = { toggleThread(h) }, selectionMode = selectionMode,
                                isSelected = selectedThreads.any { it.threadId == h.threadId }, enabled = !busy,
                                onArchive = { toggleArchive(h) }, onDelete = { deleteThread = h })
                        }
                    }
                }
            }
            // Banner fijo abajo, solo en la lista (no dentro de una conversación).
            if (!selectionMode && consulta.isBlank()) AnuncioBanner()
        }

        if (tienePermiso && !selectionMode) {
            ExtendedFloatingActionButton(
                onClick = { composeAddress = ""; composeBody = "" },
                icon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                text = { Text("Nuevo") },
                containerColor = LocalColoresExtra.current.marca,
                contentColor = LocalColoresExtra.current.onMarca,
                modifier = Modifier.align(Alignment.BottomEnd)
                    .padding(start = Esp.l, end = Esp.l, top = Esp.l, bottom = if (consulta.isBlank() && Anuncios.activos && Anuncios.listo.value) 80.dp else Esp.l)
            )
        }
    }
}

/** Etiqueta pequeña "Desconocido" con ícono (no depende solo del color). */
@Composable
private fun EtiquetaDesconocido() {
    Row(
        Modifier.clip(CircleShape)
            .background(MaterialTheme.colorScheme.errorContainer)
            .padding(horizontal = Esp.s, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Icon(
            Icons.Filled.HelpOutline, contentDescription = null,
            tint = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.size(14.dp)
        )
        Text("Desconocido", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onErrorContainer)
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun FilaHilo(h: SmsThread, onClick: () -> Unit, archived: Boolean, onArchive: () -> Unit, onDelete: () -> Unit,
    onLongClick: () -> Unit, selectionMode: Boolean, isSelected: Boolean, enabled: Boolean) {
    val titulo = h.name ?: h.address
    val noLeido = h.unread > 0
    ListItem(
        modifier = Modifier.combinedClickable(enabled = enabled, onClickLabel = if (selectionMode) "Cambiar selección" else "Abrir conversación", onClick = onClick,
            onLongClick = onLongClick, onLongClickLabel = "Seleccionar conversaciones").semantics { selected = isSelected },
        leadingContent = { if (selectionMode) Checkbox(isSelected, onCheckedChange = null) else Avatar(titulo) },
        headlineContent = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Esp.s)) {
                Text(
                    titulo,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (noLeido) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (h.name == null) EtiquetaDesconocido()
            }
        },
        supportingContent = {
            Text(
                (if (h.last.incoming) "" else "Tú: ") + h.last.body.replace('\n', ' '),
                maxLines = 2, overflow = TextOverflow.Ellipsis,
                fontWeight = if (noLeido) FontWeight.SemiBold else FontWeight.Normal,
                color = if (noLeido) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(Esp.xs)) {
                Text(
                    fechaCorta(h.last.date),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (noLeido) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (noLeido) {
                    Badge(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.semantics { contentDescription = "${h.unread} sin leer" }
                    ) { Text(h.unread.toString()) }
                }
            }
                if (!selectionMode) HistoryMenu(archived, titulo, enabled = enabled, onArchive = onArchive, onDelete = onDelete)
            }
        },
        colors = ListItemDefaults.colors(containerColor = if (isSelected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
    )
}

@Composable
private fun Conversacion(h: SmsThread, refreshKey: Int, onAtras: () -> Unit, archived: Boolean, onArchive: () -> Unit, onDelete: () -> Unit, onReply: (String) -> Unit) {
    val context = LocalContext.current
    var mensajes by remember { mutableStateOf<List<SmsMsg>?>(null) }
    val changes = messageChanges(PhoneData.has(context, Manifest.permission.READ_SMS))
    var deleting by remember { mutableStateOf<List<SmsMsg>?>(null) }
    var selectedMessages by rememberSaveable(h.threadId) { mutableStateOf(listOf<String>()) }
    var deletingBusy by remember { mutableStateOf(false) }
    var revision by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(h.threadId, refreshKey, changes, revision) {
        try {
            mensajes = withContext(Dispatchers.IO) { PhoneData.loadConversation(context, h.threadId) }
            selectedMessages = HistorySelection.prune(selectedMessages, mensajes.orEmpty().map { HistorySelection.key(it.id, it.date) })
        }
        catch (_: Exception) { Toast.makeText(context, "No se pudieron cargar los SMS.", Toast.LENGTH_LONG).show() }
    }
    LaunchedEffect(h.threadId, mensajes?.firstOrNull()?.id, SmsRepository.isDefault(context)) {
        val loaded = mensajes ?: return@LaunchedEffect
        withContext(Dispatchers.IO) { try { SmsRepository.markRead(context, loaded) } catch (_: SecurityException) { } }
        SmsNotifications.clear(context, h.threadId)
    }
    val titulo = h.name ?: h.address
    val esNumero = esNumeroTelefono(h.address)
    val selectionMode = selectedMessages.isNotEmpty()
    BackHandler(enabled = selectionMode && !deletingBusy) { selectedMessages = emptyList() }
    fun toggleMessage(message: SmsMsg) { selectedMessages = HistorySelection.toggle(selectedMessages, listOf(HistorySelection.key(message.id, message.date))) }

    Column(Modifier.fillMaxSize()) {
        if (selectionMode) {
            SelectionToolbar(selectedMessages.size, onClose = { selectedMessages = emptyList() },
                onSelectAll = { selectedMessages = mensajes.orEmpty().map { HistorySelection.key(it.id, it.date) } },
                onDelete = { deleting = mensajes.orEmpty().filter { HistorySelection.key(it.id, it.date) in selectedMessages } },
                enabled = !deletingBusy, canDelete = SmsRepository.isDefault(context))
            if (!SmsRepository.isDefault(context)) Text("Elige Teléfono como app de SMS predeterminada para borrar varios mensajes.", Modifier.padding(Esp.l), style = MaterialTheme.typography.bodyMedium)
        } else {
        Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = Esp.xs, vertical = Esp.s),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onAtras) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver a mensajes")
                }
                Avatar(titulo, 40.dp)
                Column(Modifier.weight(1f).padding(start = Esp.m)) {
                    Text(titulo, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        if (h.name != null) h.address else "No está en tus contactos",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (esNumero) {
                    IconButton(onClick = { Acciones.llamar(context, h.address) }) {
                        Icon(Icons.Filled.Call, contentDescription = "Llamar a $titulo", tint = MaterialTheme.colorScheme.primary)
                    }
                }
                HistoryMenu(archived, titulo, onArchive = onArchive, onDelete = onDelete)
            }
        }
        }

        val lista = mensajes
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (lista == null) {
                Cargando()
            } else {
                LazyColumn(
                    Modifier.fillMaxSize(),
                    reverseLayout = true, // empieza abajo, con el más reciente
                    contentPadding = PaddingValues(horizontal = Esp.m, vertical = Esp.l),
                    verticalArrangement = Arrangement.spacedBy(Esp.s)
                ) {
                    items(lista, key = { it.id }) { m -> Burbuja(m,
                        selectionMode = selectionMode, isSelected = HistorySelection.key(m.id, m.date) in selectedMessages,
                        onClick = { if (selectionMode && !deletingBusy) toggleMessage(m) },
                        onLongClick = { if (!deletingBusy) toggleMessage(m) },
                        onRetry = { onReply(m.body) }) }
                }
            }
        }

        if (!selectionMode) Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
            Button(
                onClick = { onReply("") },
                enabled = SmsPolicy.recipient(h.address) != null,
                modifier = Modifier.fillMaxWidth().padding(Esp.m).heightIn(min = 52.dp)
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(Esp.s))
                Text(if (esNumero) "Responder" else "No se puede responder a este remitente")
            }
        }
    }
    deleting?.let { messages -> Confirmar("¿Eliminar ${messages.size} SMS?", "Se borrarán únicamente los mensajes seleccionados. No se puede deshacer.", "Eliminar", onConfirmar = {
        deleting = null; deletingBusy = true
        scope.launch {
            try { withContext(Dispatchers.IO) { SmsRepository.deleteMessages(context, messages) }; selectedMessages = emptyList() }
            catch (e: CancellationException) { throw e
            } catch (_: Exception) { Toast.makeText(context, "No se pudo completar el borrado. Revisa los mensajes antes de reintentar.", Toast.LENGTH_LONG).show() }
            finally { deletingBusy = false; revision++ }
        }
    }, onCancelar = { deleting = null }) }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Burbuja(m: SmsMsg, selectionMode: Boolean, isSelected: Boolean, onClick: () -> Unit, onLongClick: () -> Unit, onRetry: () -> Unit) {
    val propio = !m.incoming
    val cs = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth()
        .background(if (isSelected) cs.secondaryContainer else Color.Transparent)
        .combinedClickable(onClick = onClick, onLongClick = onLongClick, onLongClickLabel = "Seleccionar mensajes")
        .semantics { selected = isSelected },
        horizontalArrangement = if (propio) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically) {
        if (selectionMode) Checkbox(isSelected, onCheckedChange = null)
        Column(
            Modifier.weight(1f, fill = false).widthIn(max = 300.dp)
                .clip(
                    RoundedCornerShape(
                        topStart = 18.dp, topEnd = 18.dp,
                        bottomStart = if (propio) 18.dp else 4.dp,
                        bottomEnd = if (propio) 4.dp else 18.dp
                    )
                )
                .background(if (propio) cs.primary else cs.surfaceContainerHigh)
                .padding(horizontal = Esp.m, vertical = Esp.s)
                .semantics(mergeDescendants = true) { }
        ) {
            Text(
                m.body,
                style = MaterialTheme.typography.bodyLarge,
                color = if (propio) cs.onPrimary else cs.onSurface
            )
            Text(
                (if (!propio) "" else when (m.type) {
                    android.provider.Telephony.Sms.MESSAGE_TYPE_SENT -> "Enviado · "
                    android.provider.Telephony.Sms.MESSAGE_TYPE_FAILED -> "Envío no confirmado · "
                    android.provider.Telephony.Sms.MESSAGE_TYPE_DRAFT -> "Borrador · "
                    else -> "Enviando · "
                }) + fechaCorta(m.date),
                style = MaterialTheme.typography.labelMedium,
                color = if (propio) cs.onPrimary.copy(alpha = 0.85f) else cs.onSurfaceVariant,
                modifier = Modifier.align(Alignment.End).padding(top = 2.dp)
            )
            if (!selectionMode && m.type == android.provider.Telephony.Sms.MESSAGE_TYPE_FAILED) TextButton(onClick = onRetry,
                colors = ButtonDefaults.textButtonColors(contentColor = if (propio) cs.onPrimary else cs.primary)) { Text("Revisar y reenviar") }
        }
    }
}

