package com.ejemplo.bloqueador

import android.Manifest
import android.provider.CallLog
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallMade
import androidx.compose.material.icons.automirrored.filled.CallMissed
import androidx.compose.material.icons.automirrored.filled.CallReceived
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PhoneForwarded
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.Voicemail
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun PantallaRecientes(refreshKey: Int, pedirPermisos: () -> Unit, irAProteccion: () -> Unit) {
    val context = LocalContext.current
    val tienePermiso = remember(refreshKey) { PhoneData.has(context, Manifest.permission.READ_CALL_LOG) }
    val pasosFaltantes = remember(refreshKey) { estadoConfig(context).faltan }

    var llamadas by remember { mutableStateOf<List<CallEntry>?>(null) }
    LaunchedEffect(refreshKey, tienePermiso) {
        if (tienePermiso) llamadas = withContext(Dispatchers.IO) { PhoneData.loadCalls(context) }
    }

    var filtro by rememberSaveable { mutableIntStateOf(0) }
    var seleccion by remember { mutableStateOf<CallEntry?>(null) }

    Column(Modifier.fillMaxSize()) {
        Titulo("Recientes")

        if (pasosFaltantes > 0) AvisoConfigurar(irAProteccion)

        if (!tienePermiso) {
            PermisoFaltante(
                Icons.Filled.History,
                "Tus llamadas aparecerán aquí",
                "Permite ver el registro de llamadas para mostrar tus llamadas recientes.",
                pedirPermisos
            )
            return@Column
        }

        Filtros(listOf("Todas", "Perdidas", "Bloqueadas"), filtro) { filtro = it }
        Spacer(Modifier.height(Esp.s))

        val lista = llamadas
        if (lista == null) {
            Cargando()
        } else {
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
                EstadoVacio(
                    Icons.Filled.History,
                    when (filtro) {
                        1 -> "Sin llamadas perdidas"
                        2 -> "Sin llamadas bloqueadas"
                        else -> "Aún no hay llamadas"
                    },
                    "Cuando haya llamadas, las verás aquí."
                )
            } else {
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = Esp.l)) {
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

    seleccion?.let { call ->
        HojaNumero(
            numero = call.number,
            nombre = call.name,
            historial = llamadas.orEmpty().filter {
                Prefs.normalize(it.number) == Prefs.normalize(call.number)
            }.take(10),
            onCerrar = { seleccion = null }
        )
    }
}

/** Banda ámbar que lleva a terminar la configuración. */
@Composable
private fun AvisoConfigurar(onClick: () -> Unit) {
    val extra = LocalColoresExtra.current
    Surface(
        onClick = onClick,
        color = extra.aviso,
        contentColor = extra.onAviso,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth().padding(horizontal = Esp.l, vertical = Esp.xs)
    ) {
        Row(
            Modifier.padding(horizontal = Esp.l, vertical = Esp.m),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Esp.m)
        ) {
            Icon(Icons.Filled.Warning, contentDescription = null)
            Text("La protección no está completa.", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            Text("Configurar", style = MaterialTheme.typography.labelLarge)
        }
    }
}

private data class TipoVista(val icono: ImageVector, val texto: String, val alerta: Boolean)

private fun tipoVista(type: Int): TipoVista = when (type) {
    CallLog.Calls.INCOMING_TYPE -> TipoVista(Icons.AutoMirrored.Filled.CallReceived, "Entrante", false)
    CallLog.Calls.OUTGOING_TYPE -> TipoVista(Icons.AutoMirrored.Filled.CallMade, "Saliente", false)
    CallLog.Calls.MISSED_TYPE -> TipoVista(Icons.AutoMirrored.Filled.CallMissed, "Perdida", true)
    CallLog.Calls.VOICEMAIL_TYPE -> TipoVista(Icons.Filled.Voicemail, "Buzón de voz", false)
    CallLog.Calls.REJECTED_TYPE, CallLog.Calls.BLOCKED_TYPE -> TipoVista(Icons.Filled.Block, "Bloqueada", true)
    CallLog.Calls.ANSWERED_EXTERNALLY_TYPE -> TipoVista(Icons.Filled.PhoneForwarded, "En otro dispositivo", false)
    else -> TipoVista(Icons.Filled.Call, "Llamada", false)
}

/** "Perdida · Hoy 10:32 · 2:15 min" con ícono del tipo (no depende solo del color). */
@Composable
private fun LineaTipo(c: CallEntry, extraTexto: String? = null) {
    val t = tipoVista(c.type)
    val color = if (t.alerta) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Esp.xs)) {
        Icon(t.icono, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
        Text(
            listOfNotNull(t.texto, extraTexto, fechaCorta(c.date), duracionTexto(c.duration).ifEmpty { null })
                .joinToString(" · "),
            color = color,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun FilaLlamada(g: CallGroup, onClick: () -> Unit, onLlamar: () -> Unit) {
    val c = g.latest
    val titulo = c.name ?: c.number.ifBlank { "Número oculto" }
    val alerta = tipoVista(c.type).alerta
    ListItem(
        modifier = Modifier.clickable(onClickLabel = "Ver opciones", onClick = onClick),
        leadingContent = { Avatar(titulo) },
        headlineContent = {
            Text(
                titulo,
                fontWeight = if (alerta) FontWeight.SemiBold else FontWeight.Normal,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        supportingContent = { LineaTipo(c, if (g.count > 1) "${g.count} veces" else null) },
        trailingContent = {
            if (c.number.isNotBlank()) {
                IconButton(onClick = onLlamar) {
                    Icon(Icons.Filled.Call, contentDescription = "Llamar a $titulo", tint = MaterialTheme.colorScheme.primary)
                }
            }
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )
}

/** Hoja inferior con acciones para un número: llamar, mensaje, guardar e historial. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HojaNumero(numero: String, nombre: String?, historial: List<CallEntry>, onCerrar: () -> Unit) {
    val context = LocalContext.current
    val titulo = nombre ?: numero.ifBlank { "Número oculto" }
    ModalBottomSheet(onDismissRequest = onCerrar, containerColor = MaterialTheme.colorScheme.surfaceContainerLowest) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = Esp.l).padding(bottom = Esp.xl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Esp.s)
        ) {
            Avatar(titulo, 64.dp)
            Text(titulo, style = MaterialTheme.typography.titleLarge)
            if (nombre != null && numero.isNotBlank()) {
                Text(numero, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (nombre == null && numero.isNotBlank()) {
                Text("No está en tus contactos", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            if (numero.isNotBlank()) {
                Row(
                    Modifier.fillMaxWidth().padding(vertical = Esp.m),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    BotonRedondo(Icons.Filled.Call, "Llamar", { Acciones.llamar(context, numero); onCerrar() }, destacado = true)
                    BotonRedondo(Icons.Filled.Sms, "Mensaje", { Acciones.mensaje(context, numero); onCerrar() })
                    if (nombre == null) {
                        BotonRedondo(Icons.Filled.PersonAdd, "Guardar", { Acciones.nuevoContacto(context, numero); onCerrar() })
                    }
                }
            }

            if (historial.isNotEmpty()) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Text(
                    "Historial",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.fillMaxWidth().padding(top = Esp.s)
                )
                historial.forEach { c ->
                    Box(Modifier.fillMaxWidth().padding(vertical = Esp.xs)) { LineaTipo(c) }
                }
            }
        }
    }
}
