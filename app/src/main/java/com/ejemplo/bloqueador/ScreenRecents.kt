package com.ejemplo.bloqueador

import android.Manifest
import android.provider.CallLog
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Shield
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.material.icons.filled.SearchOff
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
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun PantallaRecientes(refreshKey: Int, pedirPermisos: () -> Unit, irAProteccion: () -> Unit) {
    val context = LocalContext.current
    val tienePermiso = remember(refreshKey) { PhoneData.has(context, Manifest.permission.READ_CALL_LOG) }
    val pasosFaltantes = remember(refreshKey) { estadoConfig(context).faltan }
    val archiveStore = remember { ArchiveStore(context) }
    var archive by remember(refreshKey) { mutableStateOf(archiveStore.read()) }
    var filtro by rememberSaveable { mutableIntStateOf(0) }
    var recarga by remember { mutableIntStateOf(0) }
    var deleteTarget by remember { mutableStateOf<List<CallEntry>?>(null) }
    var selectedKeys by rememberSaveable { mutableStateOf(listOf<String>()) }
    var permissionTarget by remember { mutableStateOf<List<CallEntry>>(emptyList()) }
    var denied by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var loadError by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun deleteConfirmed(calls: List<CallEntry>) {
        if (calls.isEmpty() || busy) return
        scope.launch {
            busy = true
            try {
                val count = withContext(Dispatchers.IO) { PhoneData.deleteCalls(context, calls) }
                archive = archiveStore.read().restoreCalls(calls.map { it.archiveKey() })
                archiveStore.save(archive)
                selectedKeys = emptyList()
                Toast.makeText(context, if (count == 0) "Estas llamadas ya no están en el registro" else "$count llamadas eliminadas", Toast.LENGTH_LONG).show()
            } catch (e: CancellationException) { throw e
            } catch (_: SecurityException) { denied = true
            } catch (_: Exception) {
                Toast.makeText(context, "No se pudo completar el borrado. Revisa el registro antes de intentarlo de nuevo.", Toast.LENGTH_LONG).show()
            } finally { busy = false; recarga++ }
        }
    }
    val requestWrite = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val calls = permissionTarget
        permissionTarget = emptyList()
        if (granted) deleteConfirmed(calls) else denied = true
    }

    var llamadas by remember { mutableStateOf<List<CallEntry>?>(null) }
    LaunchedEffect(refreshKey, tienePermiso, recarga, filtro == 3) {
        if (tienePermiso) {
            loadError = false
            llamadas = null
            try {
                llamadas = withContext(Dispatchers.IO) {
                    PhoneData.loadCalls(context, onlyIds = if (filtro == 3) archive.calls.mapNotNull { it.substringBefore(':').toLongOrNull() }.toSet() else null)
                }
                selectedKeys = HistorySelection.prune(selectedKeys, llamadas.orEmpty().map { it.archiveKey() })
            } catch (e: CancellationException) { throw e
            } catch (_: Exception) { loadError = true; llamadas = emptyList() }
        }
    }

    var busqueda by rememberSaveable { mutableStateOf("") }
    var seleccion by remember { mutableStateOf<CallGroup?>(null) }
    val selectionMode = selectedKeys.isNotEmpty()
    BackHandler(enabled = selectionMode && !busy) { selectedKeys = emptyList() }
    val visibleGroups = remember(llamadas, filtro, busqueda, archive) {
        val bandeja = llamadas.orEmpty().filter { (it.archiveKey() in archive.calls) == (filtro == 3) }
        val porFiltro = when (filtro) {
            1 -> bandeja.filter { it.isMissedLike }
            2 -> bandeja.filter { it.isBlockedLike }
            else -> bandeja
        }
        PhoneData.groupCalls(porFiltro.filter { coincideBusqueda(it, busqueda) })
    }
    val filas = remember(visibleGroups) { DiaRegistro.agrupar(visibleGroups, System.currentTimeMillis()) }
    val haptic = LocalHapticFeedback.current

    Column(Modifier.fillMaxSize()) {
        if (selectionMode) SelectionToolbar(selectedKeys.size,
            onClose = { selectedKeys = emptyList() },
            onSelectAll = { selectedKeys = visibleGroups.flatMap { it.entries }.map { it.archiveKey() }.distinct() },
            onDelete = { deleteTarget = llamadas.orEmpty().filter { it.archiveKey() in selectedKeys } }, enabled = !busy)
        else Titulo("Recientes")

        if (pasosFaltantes > 0) AvisoConfigurar(pasosFaltantes, irAProteccion)

        if (!tienePermiso) {
            PermisoFaltante(
                Icons.Filled.History,
                "Tus llamadas aparecerán aquí",
                "Permite ver el registro de llamadas para mostrar tus llamadas recientes.",
                pedirPermisos
            )
            return@Column
        }

        if (!selectionMode) {
            Buscador(busqueda, { busqueda = it }, "Buscar en llamadas")
            Filtros(listOf("Todas", "Perdidas", "Bloqueadas", "Archivadas"), filtro) { filtro = it }
        }
        if (filtro == 3) Text("Archivo local de Bloqueador. Toca una llamada para desarchivarla.", Modifier.padding(horizontal = Esp.l, vertical = Esp.s),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(Esp.s))

        val lista = llamadas
        if (loadError) {
            EstadoVacio(Icons.Filled.History, "No se pudo cargar el registro", "Revisa el permiso de llamadas e inténtalo de nuevo.", accion = "Reintentar", onAccion = { recarga++ })
        } else if (lista == null) {
            Cargando()
        } else {
            val grupos = visibleGroups
            if (grupos.isEmpty()) {
                if (busqueda.isNotBlank()) {
                    EstadoVacio(
                        Icons.Filled.SearchOff,
                        "Sin resultados",
                        "No hay llamadas que coincidan con \"$busqueda\"."
                    )
                } else {
                    EstadoVacio(
                        Icons.Filled.History,
                        when (filtro) {
                            1 -> "Sin llamadas perdidas"
                            2 -> "Sin llamadas bloqueadas"
                            3 -> "Sin llamadas archivadas"
                            else -> "Aún no hay llamadas"
                        },
                        "Cuando haya llamadas, las verás aquí."
                    )
                }
            } else {
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = Esp.xl)) {
                    items(
                        filas,
                        key = { f -> when (f) { is FilaRegistro.Cabecera -> "h${f.clave}"; is FilaRegistro.Llamadas -> f.grupo.latest.id } },
                        contentType = { f -> if (f is FilaRegistro.Cabecera) 0 else 1 }
                    ) { f ->
                        when (f) {
                            is FilaRegistro.Cabecera -> CabeceraDia(f.etiqueta, Modifier.animateItem())
                            is FilaRegistro.Llamadas -> {
                                val g = f.grupo
                                FilaLlamada(
                                    g,
                                    modifier = Modifier.animateItem(),
                                    onClick = {
                                        if (selectionMode) selectedKeys = HistorySelection.toggle(selectedKeys, g.entries.map { it.archiveKey() })
                                        else seleccion = g
                                    },
                                    onLongClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        selectedKeys = HistorySelection.toggle(selectedKeys, g.entries.map { it.archiveKey() })
                                    },
                                    selectionMode = selectionMode,
                                    selectionState = when {
                                        g.entries.all { it.archiveKey() in selectedKeys } -> ToggleableState.On
                                        g.entries.any { it.archiveKey() in selectedKeys } -> ToggleableState.Indeterminate
                                        else -> ToggleableState.Off
                                    },
                                    onLlamar = { Acciones.llamar(context, g.latest.number) },
                                    onMensaje = { Acciones.mensaje(context, g.latest.number) },
                                    enabled = !busy
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    seleccion?.let { g ->
        val call = g.latest
        HojaNumero(
            numero = call.number,
            nombre = call.name,
            historial = llamadas.orEmpty().filter {
                Prefs.normalize(it.number) == Prefs.normalize(call.number)
            }.take(10),
            onCerrar = { seleccion = null },
            archivado = filtro == 3,
            onArchivar = {
                val current = archiveStore.read()
                val keys = g.entries.map { it.archiveKey() }
                archive = if (filtro == 3) current.restoreCalls(keys) else current.archiveCalls(keys)
                archiveStore.save(archive)
                recarga++
            },
            onEliminar = { deleteTarget = g.entries }
        )
    }
    deleteTarget?.let { calls ->
        Confirmar("¿Eliminar ${calls.size} llamadas?",
            "Se borrarán definitivamente estos registros del historial del teléfono, también en otras apps. No cambia el registro de bloqueos de Protección. No se puede deshacer.", "Eliminar",
            onConfirmar = {
                deleteTarget = null
                if (PhoneData.has(context, Manifest.permission.WRITE_CALL_LOG)) deleteConfirmed(calls)
                else { permissionTarget = calls; requestWrite.launch(Manifest.permission.WRITE_CALL_LOG) }
            }, onCancelar = { deleteTarget = null })
    }
    if (denied) AlertDialog(onDismissRequest = { denied = false }, title = { Text("No se autorizó el borrado") },
        text = { Text("Android no permite a Bloqueador borrar llamadas sin el permiso de escritura del registro. Puedes revisar sus permisos o borrar desde tu app de teléfono.") },
        confirmButton = { TextButton(onClick = {
            denied = false
            if (!Acciones.manageCallLog(context)) Toast.makeText(context, "Abre tu app de teléfono para borrar el registro.", Toast.LENGTH_LONG).show()
        }) { Text("Abrir Teléfono") } },
        dismissButton = { TextButton(onClick = { denied = false; Acciones.ajustesDeLaApp(context) }) { Text("Revisar permisos") } })
}

/** Tarjeta discreta que lleva a terminar la configuración; solo el ícono lleva el tono de aviso. */
@Composable
private fun AvisoConfigurar(pasos: Int, onClick: () -> Unit) {
    val extra = LocalColoresExtra.current
    Surface(
        onClick = onClick,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth().padding(horizontal = Esp.l, vertical = Esp.xs)
    ) {
        Row(
            Modifier.padding(horizontal = Esp.l, vertical = Esp.m),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Esp.m)
        ) {
            IconoCirculo(Icons.Filled.Shield, fondo = extra.aviso, color = extra.onAviso, tamano = 40.dp)
            Column(Modifier.weight(1f)) {
                Text("Completa tu protección", style = MaterialTheme.typography.titleSmall)
                Text(
                    if (pasos == 1) "Falta 1 paso" else "Faltan $pasos pasos",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Encabezado de día ("Hoy", "Ayer", "Lunes"…) sobre las llamadas de ese día. */
@Composable
private fun CabeceraDia(etiqueta: String, modifier: Modifier = Modifier) {
    Text(
        etiqueta,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.fillMaxWidth().padding(start = Esp.l, end = Esp.l, top = Esp.l, bottom = Esp.xs)
            .semantics { heading() }
    )
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
internal fun LineaTipo(c: CallEntry, extraTexto: String? = null, conFecha: Boolean = true) {
    val t = tipoVista(c.type)
    val color = if (t.alerta) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Esp.xs)) {
        Icon(t.icono, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
        Text(
            listOfNotNull(t.texto, extraTexto, if (conFecha) fechaCorta(c.date) else null, duracionTexto(c.duration).ifEmpty { null })
                .joinToString(" · "),
            color = color,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** Coincide por nombre (sin importar acentos) o por dígitos del número, como en Contactos. */
private fun coincideBusqueda(c: CallEntry, q: String): Boolean {
    if (q.isBlank()) return true
    val texto = simplificar(q.trim())
    val digitos = q.filter { it.isDigit() }
    val porNombre = c.name?.let { simplificar(it).contains(texto) } ?: false
    val porNumero = digitos.length >= 2 && c.number.filter { it.isDigit() }.contains(digitos)
    return porNombre || porNumero
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun FilaLlamada(g: CallGroup, onClick: () -> Unit, onLlamar: () -> Unit, onMensaje: () -> Unit,
    enabled: Boolean, onLongClick: () -> Unit, selectionMode: Boolean, selectionState: ToggleableState,
    modifier: Modifier = Modifier) {
    val c = g.latest
    val titulo = c.name ?: c.number.ifBlank { "Número oculto" }
    val alerta = tipoVista(c.type).alerta
    val hayNumero = c.number.isNotBlank()
    FilaDeslizable(
        alLlamar = if (hayNumero && !selectionMode) onLlamar else null,
        alMensaje = if (hayNumero && !selectionMode) onMensaje else null,
        modifier = modifier
    ) {
    ListItem(
        modifier = Modifier.combinedClickable(enabled = enabled, onClickLabel = if (selectionMode) "Cambiar selección" else "Ver opciones", onClick = onClick,
            onLongClick = onLongClick, onLongClickLabel = "Seleccionar llamadas")
            .semantics { selected = selectionState == ToggleableState.On },
        leadingContent = { if (selectionMode) TriStateCheckbox(selectionState, onClick = null) else Avatar(titulo) },
        headlineContent = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Esp.s)) {
                Text(
                    titulo,
                    modifier = Modifier.weight(1f, fill = false),
                    fontWeight = if (alerta) FontWeight.SemiBold else FontWeight.Medium,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (g.count > 1) {
                    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                        Text(
                            "${g.count}",
                            modifier = Modifier.padding(horizontal = Esp.s, vertical = 1.dp)
                                .semantics { contentDescription = "${g.count} llamadas" },
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        },
        supportingContent = { LineaTipo(c, conFecha = false) },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Esp.xs)) {
                Text(
                    horaCorta(c.date),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (alerta) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (hayNumero && !selectionMode) {
                    FilledTonalIconButton(onClick = onLlamar, enabled = enabled, modifier = Modifier.size(40.dp)) {
                        Icon(Icons.Filled.Call, contentDescription = "Llamar a $titulo", modifier = Modifier.size(20.dp))
                    }
                }
            }
        },
        colors = ListItemDefaults.colors(containerColor = if (selectionState != ToggleableState.Off) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
    )
    }
}
