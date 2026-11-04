package com.ejemplo.bloqueador

import android.Manifest
import android.provider.CallLog
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
    var deleteTarget by remember { mutableStateOf<CallGroup?>(null) }
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
            } catch (e: CancellationException) { throw e
            } catch (_: Exception) { loadError = true; llamadas = emptyList() }
        }
    }

    var busqueda by rememberSaveable { mutableStateOf("") }
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

        Buscador(busqueda, { busqueda = it }, "Buscar en llamadas")
        Filtros(listOf("Todas", "Perdidas", "Bloqueadas", "Archivadas"), filtro) { filtro = it }
        if (filtro == 3) Text("Archivo local de Bloqueador. Puedes desarchivar desde el menú ⋮.", Modifier.padding(horizontal = Esp.l, vertical = Esp.s), style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(Esp.s))

        val lista = llamadas
        if (loadError) {
            EstadoVacio(Icons.Filled.History, "No se pudo cargar el registro", "Revisa el permiso de llamadas e inténtalo de nuevo.", accion = "Reintentar", onAccion = { recarga++ })
        } else if (lista == null) {
            Cargando()
        } else {
            val grupos = remember(lista, filtro, busqueda, archive) {
                val bandeja = lista.filter { (it.archiveKey() in archive.calls) == (filtro == 3) }
                val porFiltro = when (filtro) {
                    1 -> bandeja.filter { it.isMissedLike }
                    2 -> bandeja.filter { it.isBlockedLike }
                    else -> bandeja
                }
                PhoneData.groupCalls(porFiltro.filter { coincideBusqueda(it, busqueda) })
            }
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
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = Esp.l)) {
                    items(grupos, key = { it.latest.id }) { g ->
                        FilaLlamada(
                            g,
                            onClick = { seleccion = g.latest },
                            onLlamar = { Acciones.llamar(context, g.latest.number) },
                            onMensaje = { Acciones.mensaje(context, g.latest.number) },
                            archived = filtro == 3, enabled = !busy,
                            onArchive = {
                                val current = archiveStore.read()
                                archive = if (filtro == 3) current.restoreCalls(g.entries.map { it.archiveKey() }) else current.archiveCalls(g.entries.map { it.archiveKey() })
                                archiveStore.save(archive)
                                recarga++
                            },
                            onDelete = { deleteTarget = g }
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
    deleteTarget?.let { group ->
        Confirmar("¿Eliminar ${group.entries.size} llamadas?",
            "Se borrarán definitivamente estos registros del historial del teléfono, también en otras apps. No cambia el registro de bloqueos de Protección. No se puede deshacer.", "Eliminar",
            onConfirmar = {
                deleteTarget = null
                if (PhoneData.has(context, Manifest.permission.WRITE_CALL_LOG)) deleteConfirmed(group.entries)
                else { permissionTarget = group.entries; requestWrite.launch(Manifest.permission.WRITE_CALL_LOG) }
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
private fun FilaLlamada(g: CallGroup, onClick: () -> Unit, onLlamar: () -> Unit, onMensaje: () -> Unit,
    archived: Boolean, enabled: Boolean, onArchive: () -> Unit, onDelete: () -> Unit) {
    val c = g.latest
    val titulo = c.name ?: c.number.ifBlank { "Número oculto" }
    val alerta = tipoVista(c.type).alerta
    val hayNumero = c.number.isNotBlank()
    FilaDeslizable(
        alLlamar = if (hayNumero) onLlamar else null,
        alMensaje = if (hayNumero) onMensaje else null
    ) {
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
            Row(verticalAlignment = Alignment.CenterVertically) {
            if (c.number.isNotBlank()) {
                IconButton(onClick = onLlamar) {
                    Icon(Icons.Filled.Call, contentDescription = "Llamar a $titulo", tint = MaterialTheme.colorScheme.primary)
                }
            }
                HistoryMenu(archived, "$titulo (${g.count} llamadas)", enabled, onArchive, onDelete)
            }
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )
    }
}

/** Hoja inferior con acciones para un número: llamar, mensaje, guardar e historial. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HojaNumero(numero: String, nombre: String?, historial: List<CallEntry>, onCerrar: () -> Unit) {
    val context = LocalContext.current
    val abrirFormulario = LocalAbrirFormulario.current
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
                        BotonRedondo(Icons.Filled.PersonAdd, "Guardar", { onCerrar(); abrirFormulario(PeticionFormulario(numero = numero)) })
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

