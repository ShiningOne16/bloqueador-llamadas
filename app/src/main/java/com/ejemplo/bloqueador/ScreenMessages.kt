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
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Create
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
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

    Column(Modifier.fillMaxSize()) {
        Titulo("Mensajes") {
            IconButton(onClick = { Acciones.nuevoMensaje(context) }) {
                Icon(Icons.Default.Create, contentDescription = "Nuevo mensaje")
            }
        }

        if (!tienePermiso) {
            PermisoFaltante("Permite leer SMS para ver tus mensajes aquí.", pedirPermisos)
        } else {
            Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Todos", "No leídos", "Desconocidos").forEachIndexed { i, t ->
                    FilterChip(selected = filtro == i, onClick = { filtro = i }, label = { Text(t) })
                }
            }
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
                    TextoVacio("No hay mensajes aquí.")
                } else {
                    LazyColumn(Modifier.fillMaxSize()) {
                        items(visibles, key = { it.threadId }) { h ->
                            FilaHilo(h, onClick = { abiertoId = h.threadId })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FilaHilo(h: SmsThread, onClick: () -> Unit) {
    val titulo = h.name ?: h.address
    val noLeido = h.unread > 0
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        leadingContent = { Avatar(titulo) },
        headlineContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    titulo,
                    fontWeight = if (noLeido) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (h.name == null) {
                    Text(
                        "  Desconocido",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        supportingContent = {
            Text(
                (if (h.last.incoming) "" else "Tú: ") + h.last.body.replace('\n', ' '),
                maxLines = 2, overflow = TextOverflow.Ellipsis,
                fontWeight = if (noLeido) FontWeight.SemiBold else FontWeight.Normal
            )
        },
        trailingContent = {
            Column(horizontalAlignment = Alignment.End) {
                Text(fechaCorta(h.last.date), style = MaterialTheme.typography.labelSmall)
                if (noLeido) {
                    Box(
                        Modifier.padding(top = 4.dp).clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
                            .padding(horizontal = 7.dp, vertical = 2.dp)
                    ) {
                        Text(
                            h.unread.toString(),
                            color = MaterialTheme.colorScheme.onPrimary,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }
        }
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

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onAtras) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Atrás") }
            Avatar(titulo, 36.dp)
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(titulo, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (h.name != null) Text(h.address, style = MaterialTheme.typography.bodySmall)
            }
            if (esNumeroTelefono(h.address)) {
                IconButton(onClick = { Acciones.llamar(context, h.address) }) {
                    Icon(Icons.Default.Call, contentDescription = "Llamar")
                }
            }
        }
        HorizontalDivider()

        val lista = mensajes
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (lista == null) {
                Cargando()
            } else {
                LazyColumn(
                    Modifier.fillMaxSize(),
                    reverseLayout = true, // empieza abajo, con el más reciente
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(lista, key = { it.id }) { m -> Burbuja(m) }
                }
            }
        }

        Button(
            onClick = { Acciones.mensaje(context, h.address) },
            enabled = esNumeroTelefono(h.address),
            modifier = Modifier.fillMaxWidth().padding(12.dp)
        ) { Text("Responder") }
    }
}

@Composable
private fun Burbuja(m: SmsMsg) {
    val propio = !m.incoming
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (propio) Arrangement.End else Arrangement.Start) {
        Column(
            Modifier.widthIn(max = 300.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(
                    if (propio) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceVariant
                )
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Text(m.body)
            Text(
                fechaCorta(m.date),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.End)
            )
        }
    }
}
