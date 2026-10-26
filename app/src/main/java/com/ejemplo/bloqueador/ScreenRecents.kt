package com.ejemplo.bloqueador

import android.Manifest
import android.provider.CallLog
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun PantallaRecientes(refreshKey: Int, pedirPermisos: () -> Unit) {
    val context = LocalContext.current
    val tienePermiso = remember(refreshKey) { PhoneData.has(context, Manifest.permission.READ_CALL_LOG) }

    var llamadas by remember { mutableStateOf<List<CallEntry>?>(null) }
    LaunchedEffect(refreshKey, tienePermiso) {
        if (tienePermiso) llamadas = withContext(Dispatchers.IO) { PhoneData.loadCalls(context) }
    }

    var filtro by rememberSaveable { mutableIntStateOf(0) }
    var seleccion by remember { mutableStateOf<CallEntry?>(null) }

    Column(Modifier.fillMaxSize()) {
        Titulo("Recientes")

        if (!tienePermiso) {
            PermisoFaltante("Permite ver el registro de llamadas para mostrar tus llamadas recientes.", pedirPermisos)
        } else {
            Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Todas", "Perdidas", "Bloqueadas").forEachIndexed { i, t ->
                    FilterChip(selected = filtro == i, onClick = { filtro = i }, label = { Text(t) })
                }
            }

            val lista = llamadas
            when {
                lista == null -> Cargando()
                else -> {
                    val grupos = remember(lista, filtro) {
                        PhoneData.groupCalls(
                            when (filtro) {
                                1 -> lista.filter { it.isMissedLike }
                                2 -> lista.filter { it.isBlockedLike }
                                else -> lista
                            }
                        )
                    }
                    if (grupos.isEmpty()) {
                        TextoVacio("No hay llamadas aquí.")
                    } else {
                        LazyColumn(Modifier.fillMaxSize()) {
                            items(grupos, key = { it.latest.id }) { g ->
                                FilaLlamada(
                                    g,
                                    onClick = { seleccion = g.latest },
                                    onLlamar = { Acciones.llamar(context, g.latest.number) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    seleccion?.let { call ->
        DetalleNumero(
            numero = call.number,
            nombre = call.name,
            historial = llamadas.orEmpty().filter {
                Prefs.normalize(it.number) == Prefs.normalize(call.number)
            }.take(8),
            onCerrar = { seleccion = null }
        )
    }
}

@Composable
private fun FilaLlamada(g: CallGroup, onClick: () -> Unit, onLlamar: () -> Unit) {
    val c = g.latest
    val titulo = c.name ?: c.number.ifBlank { "Número oculto" }
    val rojo = c.isMissedLike
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        leadingContent = { Avatar(titulo) },
        headlineContent = {
            Text(
                if (g.count > 1) "$titulo (${g.count})" else titulo,
                color = if (rojo) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                fontWeight = if (rojo) FontWeight.SemiBold else FontWeight.Normal
            )
        },
        supportingContent = {
            val partes = listOfNotNull(
                tipoLlamada(c.type),
                fechaCorta(c.date),
                duracionTexto(c.duration).ifEmpty { null }
            )
            Text(partes.joinToString(" · "))
        },
        trailingContent = {
            if (c.number.isNotBlank()) {
                IconButton(onClick = onLlamar) { Icon(Icons.Default.Call, contentDescription = "Llamar") }
            }
        }
    )
}

fun tipoLlamada(type: Int): String = when (type) {
    CallLog.Calls.INCOMING_TYPE -> "↙ Entrante"
    CallLog.Calls.OUTGOING_TYPE -> "↗ Saliente"
    CallLog.Calls.MISSED_TYPE -> "↙ Perdida"
    CallLog.Calls.VOICEMAIL_TYPE -> "Buzón de voz"
    CallLog.Calls.REJECTED_TYPE -> "⊘ Rechazada"
    CallLog.Calls.BLOCKED_TYPE -> "⊘ Bloqueada"
    CallLog.Calls.ANSWERED_EXTERNALLY_TYPE -> "Contestada en otro dispositivo"
    else -> "Llamada"
}

/** Ventana con acciones para un número: llamar, mensaje, agregar a contactos e historial. */
@Composable
fun DetalleNumero(numero: String, nombre: String?, historial: List<CallEntry>, onCerrar: () -> Unit) {
    val context = LocalContext.current
    val titulo = nombre ?: numero.ifBlank { "Número oculto" }
    AlertDialog(
        onDismissRequest = onCerrar,
        icon = { Avatar(titulo, 56.dp) },
        title = { Text(titulo) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (nombre != null && numero.isNotBlank()) Text(numero)
                if (numero.isNotBlank()) {
                    Button(onClick = { Acciones.llamar(context, numero); onCerrar() }, Modifier.fillMaxWidth()) {
                        Text("Llamar")
                    }
                    OutlinedButton(onClick = { Acciones.mensaje(context, numero); onCerrar() }, Modifier.fillMaxWidth()) {
                        Text("Enviar mensaje")
                    }
                    if (nombre == null) {
                        OutlinedButton(onClick = { Acciones.nuevoContacto(context, numero); onCerrar() }, Modifier.fillMaxWidth()) {
                            Text("Agregar a contactos")
                        }
                    }
                }
                if (historial.isNotEmpty()) {
                    HorizontalDivider()
                    Text("Historial", style = MaterialTheme.typography.titleSmall)
                    historial.forEach {
                        Text(
                            listOfNotNull(tipoLlamada(it.type), fechaCorta(it.date), duracionTexto(it.duration).ifEmpty { null })
                                .joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onCerrar) { Text("Cerrar") } }
    )
}
