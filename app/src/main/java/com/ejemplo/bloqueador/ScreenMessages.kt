package com.ejemplo.bloqueador

import android.Manifest
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
import kotlinx.coroutines.withContext

@Composable
fun PantallaMensajes(refreshKey: Int, pedirPermisos: () -> Unit) {
    val context = LocalContext.current
    val tienePermiso = remember(refreshKey) { PhoneData.has(context, Manifest.permission.READ_SMS) }

    var hilos by remember { mutableStateOf<List<SmsThread>?>(null) }
    LaunchedEffect(refreshKey, tienePermiso) {
        if (tienePermiso) hilos = withContext(Dispatchers.IO) {
            PhoneData.loadThreads(context, PhoneData.nameIndex(PhoneData.loadContacts(context)))
        }
    }

    var abiertoId by rememberSaveable { mutableStateOf<Long?>(null) }
    val abierto = hilos?.firstOrNull { it.threadId == abiertoId }

    if (abierto != null) {
        BackHandler { abiertoId = null }
        Conversacion(abierto, refreshKey, onAtras = { abiertoId = null })
        return
    }

    var filtro by rememberSaveable { mutableIntStateOf(0) }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            val sinLeer = hilos?.sumOf { it.unread } ?: 0
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

            Filtros(listOf("Todos", "Sin leer", "Desconocidos"), filtro) { filtro = it }
            Spacer(Modifier.height(Esp.s))

            val lista = hilos
            if (lista == null) {
                Cargando()
            } else {
                val visibles = remember(lista, filtro) {
                    when (filtro) {
                        1 -> lista.filter { it.unread > 0 }
                        2 -> lista.filter { it.name == null }
                        else -> lista
                    }
                }
                if (visibles.isEmpty()) {
                    when (filtro) {
                        1 -> EstadoVacio(Icons.Filled.MarkEmailRead, "Todo leído", "No tienes mensajes pendientes.")
                        2 -> EstadoVacio(Icons.Filled.Sms, "Sin desconocidos", "Ningún número desconocido te ha escrito.")
                        else -> EstadoVacio(Icons.Filled.Sms, "Aún no hay mensajes", "Tus conversaciones por SMS aparecerán aquí.")
                    }
                } else {
                    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 96.dp)) {
                        items(visibles, key = { it.threadId }) { h ->
                            FilaHilo(h, onClick = { abiertoId = h.threadId })
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
private fun FilaHilo(h: SmsThread, onClick: () -> Unit) {
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
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )
}

@Composable
private fun Conversacion(h: SmsThread, refreshKey: Int, onAtras: () -> Unit) {
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
