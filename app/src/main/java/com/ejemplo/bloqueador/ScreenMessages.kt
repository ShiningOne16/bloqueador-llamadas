package com.ejemplo.bloqueador

import android.Manifest
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext

@Composable
fun PantallaMensajes(refreshKey: Int, pedirPermisos: () -> Unit) {
    val context = LocalContext.current
    val tienePermiso = remember(refreshKey) { PhoneData.has(context, Manifest.permission.READ_SMS) }
    val archiveStore = remember { ArchiveStore(context) }
    var archive by remember(refreshKey) { mutableStateOf(archiveStore.read()) }
    var filtro by rememberSaveable { mutableIntStateOf(0) }
    var recarga by remember { mutableIntStateOf(0) }
    var deleteThread by remember { mutableStateOf<SmsThread?>(null) }
    var abiertoId by rememberSaveable { mutableStateOf<Long?>(null) }
    var loadError by remember { mutableStateOf(false) }

    var hilos by remember { mutableStateOf<List<SmsThread>?>(null) }
    LaunchedEffect(refreshKey, tienePermiso, recarga, filtro == 3) {
        if (tienePermiso) {
            loadError = false
            hilos = null
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
            } catch (e: CancellationException) { throw e
            } catch (_: Exception) { loadError = true; hilos = emptyList() }
        }
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
        AlertDialog(onDismissRequest = { deleteThread = null }, title = { Text("Eliminar conversación de SMS") },
            text = { Text("Android solo permite borrar SMS a tu app de mensajes predeterminada. Abre la conversación con ${thread.name ?: thread.address} y usa allí Eliminar. Abrirla no borra mensajes desde Bloqueador.") },
            confirmButton = { TextButton(onClick = {
                if (Acciones.manageSms(context, thread.address)) deleteThread = null
                else Toast.makeText(context, "No se pudo abrir la app de SMS. Ábrela desde tu teléfono para eliminar la conversación.", Toast.LENGTH_LONG).show()
            }) { Text("Abrir app SMS") } },
            dismissButton = { TextButton(onClick = { deleteThread = null }) { Text("Cancelar") } })
    }
    val abierto = hilos?.firstOrNull { it.threadId == abiertoId }

    if (abierto != null) {
        BackHandler { abiertoId = null }
        Conversacion(abierto, refreshKey, onAtras = { abiertoId = null },
            archived = abierto.archiveKey() in archive.sms,
            onArchive = { toggleArchive(abierto) }, onDelete = { deleteThread = abierto })
        return
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            val sinLeer = hilos?.filter { it.archiveKey() !in archive.sms }?.sumOf { it.unread } ?: 0
            Titulo("Mensajes", subtitulo = if (sinLeer > 0) "$sinLeer sin leer" else null)

            if (!tienePermiso) {
                PermisoFaltante(
                    Icons.Filled.Sms,
                    "Tus mensajes aparecerán aquí",
                    "Permite leer SMS para ver tus conversaciones.",
                    pedirPermisos
                )
                return@Column
            }

            Filtros(listOf("Todos", "Sin leer", "Desconocidos", "Archivadas"), filtro) { filtro = it }
            if (filtro == 3) Text("Archivo local. Un SMS nuevo devuelve la conversación a la bandeja.", Modifier.padding(horizontal = Esp.l, vertical = Esp.s), style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(Esp.s))

            val lista = hilos
            if (loadError) {
                EstadoVacio(Icons.Filled.Sms, "No se pudieron cargar los mensajes", "Revisa el permiso de SMS e inténtalo de nuevo.", accion = "Reintentar", onAccion = { recarga++ })
            } else if (lista == null) {
                Cargando()
            } else {
                val visibles = remember(lista, filtro, archive) {
                    val bandeja = lista.filter { (it.archiveKey() in archive.sms) == (filtro == 3) }
                    when (filtro) {
                        1 -> bandeja.filter { it.unread > 0 }
                        2 -> bandeja.filter { it.name == null }
                        else -> bandeja
                    }
                }
                if (visibles.isEmpty()) {
                    when (filtro) {
                        1 -> EstadoVacio(Icons.Filled.MarkEmailRead, "Todo leído", "No tienes mensajes pendientes.")
                        2 -> EstadoVacio(Icons.Filled.Sms, "Sin desconocidos", "Ningún número desconocido te ha escrito.")
                        3 -> EstadoVacio(Icons.Filled.Sms, "Sin conversaciones archivadas", "Usa el menú ⋮ de una conversación para archivarla.")
                        else -> EstadoVacio(Icons.Filled.Sms, "Aún no hay mensajes", "Tus conversaciones por SMS aparecerán aquí.")
                    }
                } else {
                    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 96.dp)) {
                        items(visibles, key = { it.threadId }) { h ->
                            FilaHilo(h, onClick = { abiertoId = h.threadId }, archived = filtro == 3,
                                onArchive = { toggleArchive(h) }, onDelete = { deleteThread = h })
                        }
                    }
                }
            }
        }

        if (tienePermiso) {
            ExtendedFloatingActionButton(
                onClick = { Acciones.nuevoMensaje(context) },
                icon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                text = { Text("Nuevo") },
                modifier = Modifier.align(Alignment.BottomEnd).padding(Esp.l)
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
private fun FilaHilo(h: SmsThread, onClick: () -> Unit, archived: Boolean, onArchive: () -> Unit, onDelete: () -> Unit) {
    val titulo = h.name ?: h.address
    val noLeido = h.unread > 0
    ListItem(
        modifier = Modifier.clickable(onClickLabel = "Abrir conversación", onClick = onClick),
        leadingContent = { Avatar(titulo) },
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
                HistoryMenu(archived, titulo, onArchive = onArchive, onDelete = onDelete)
            }
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )
}

@Composable
private fun Conversacion(h: SmsThread, refreshKey: Int, onAtras: () -> Unit, archived: Boolean, onArchive: () -> Unit, onDelete: () -> Unit) {
    val context = LocalContext.current
    var mensajes by remember { mutableStateOf<List<SmsMsg>?>(null) }
    LaunchedEffect(h.threadId, refreshKey) {
        mensajes = withContext(Dispatchers.IO) { PhoneData.loadConversation(context, h.threadId) }
    }
    val titulo = h.name ?: h.address
    val esNumero = esNumeroTelefono(h.address)

    Column(Modifier.fillMaxSize()) {
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
                    items(lista, key = { it.id }) { m -> Burbuja(m) }
                }
            }
        }

        Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
            Button(
                onClick = { Acciones.mensaje(context, h.address) },
                enabled = esNumero,
                modifier = Modifier.fillMaxWidth().padding(Esp.m).heightIn(min = 52.dp)
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(Esp.s))
                Text(if (esNumero) "Responder" else "No se puede responder a este remitente")
            }
        }
    }
}

@Composable
private fun Burbuja(m: SmsMsg) {
    val propio = !m.incoming
    val cs = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (propio) Arrangement.End else Arrangement.Start) {
        Column(
            Modifier.widthIn(max = 300.dp)
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
                (if (propio) "Enviado · " else "") + fechaCorta(m.date),
                style = MaterialTheme.typography.labelMedium,
                color = if (propio) cs.onPrimary.copy(alpha = 0.85f) else cs.onSurfaceVariant,
                modifier = Modifier.align(Alignment.End).padding(top = 2.dp)
            )
        }
    }
}

