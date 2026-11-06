package com.ejemplo.bloqueador

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ArchivedSpamDialog(calls: List<CallEntry>, onClose: () -> Unit, onChanged: () -> Unit,
    source: SpamCallSource = SpamCallSource.ARCHIVED) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var plan by remember { mutableStateOf<ArchivedSpamPlan?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var result by remember { mutableStateOf<ArchivedSpamResult?>(null) }
    var category by remember { mutableStateOf(CategoriaSpam.OTRO) }
    var menu by remember { mutableStateOf(false) }
    var sending by remember { mutableStateOf(false) }
    var stopping by remember { mutableStateOf(false) }
    var job by remember { mutableStateOf<Job?>(null) }

    LaunchedEffect(Unit) {
        try {
            plan = withContext(Dispatchers.IO) { ArchivedSpam.prepare(context, calls, ArchiveStore(context).read().calls, source) }
        } catch (e: CancellationException) { throw e
        } catch (_: Exception) { error = "No se pudo comprobar la privacidad de los números. No se enviará ninguno." }
    }
    fun send() {
        val selected = plan ?: return
        sending = true; stopping = false; error = null; result = null
        job = scope.launch {
            try { result = ArchivedSpam.report(context, selected, category)
            } catch (e: CancellationException) {
                error = "Proceso detenido. El envío en curso puede haberse completado. Puedes reintentar: los reportes confirmados no se repiten."
                throw e
            } catch (_: Exception) {
                error = "No se pudo completar el proceso. Los bloqueos y reportes ya realizados se conservan; puedes reintentar."
            } finally { sending = false; stopping = false; onChanged() }
        }
    }
    AlertDialog(
        onDismissRequest = { if (!sending) onClose() },
        title = { Text("Enviar ${source.label} a spam") },
        text = {
            Column(Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                val current = plan
                val done = result
                when {
                    sending -> {
                        CircularProgressIndicator()
                        Text(if (stopping) "Deteniendo después del envío en curso…" else "Bloqueando y enviando los reportes…")
                    }
                    done != null -> {
                        Text("${done.blocked} números bloqueados en tu teléfono. ${done.sent} reportes enviados; ${done.alreadyReported} ya estaban enviados.")
                        Text("${done.pending} pendientes de envío. ${done.omitted} registros omitidos.")
                        done.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                        Text("Los registros de llamadas y su archivo se conservan. La lista comunitaria se publica en la siguiente actualización del servidor, hasta 6 horas después.")
                    }
                    else -> {
                        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                        if (current == null && error == null) CircularProgressIndicator()
                        if (current != null) {
                            Text("Revisa estos ${current.numbers.size} números únicos de todas tus llamadas ${source.label} de este filtro, aunque no aparezcan en la búsqueda actual.")
                            Text(current.numbers.joinToString("\n").ifEmpty { "No hay números compatibles para reportar." },
                                Modifier.fillMaxWidth().heightIn(max = 160.dp).verticalScroll(rememberScrollState()))
                            Text("${current.omitted} registros omitidos. Se respetan No es spam, los contactos ocultos y el NIP alternativo; solo se admiten números de México.")
                            Text("Categoría para estos números")
                            Box {
                                OutlinedButton(onClick = { menu = true }) { Text(category.etiqueta) }
                                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                    CategoriaSpam.entries.forEach { item ->
                                        DropdownMenuItem(text = { Text(item.etiqueta) }, onClick = { category = item; menu = false })
                                    }
                                }
                            }
                            Text("Al confirmar se bloquearán con el filtro de llamadas activo y se enviará cada número, su categoría y un identificador de instalación. El servidor recibe tu IP. No se envían nombres, fechas ni registros de llamadas. Un reporte basta para la lista comunitaria.")
                            Text("Máximo ${SpamRules.MAX_REPORTES_POR_DIA} intentos diarios por red e instalación con el servidor actualizado. Los números pendientes quedan bloqueados localmente y puedes reintentar. No se borran ni desarchivan llamadas.")
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (!sending && plan?.numbers?.isNotEmpty() == true && (result == null || result!!.pending > 0)) {
                TextButton(onClick = { send() }) { Text(if (result == null && error == null) "Reportar y bloquear" else "Reintentar pendientes") }
            } else if (!sending) TextButton(onClick = onClose) { Text("Cerrar") }
        },
        dismissButton = {
            if (sending) TextButton(enabled = !stopping, onClick = { stopping = true; job?.cancel() }) { Text("Detener") }
            else if (plan?.numbers?.isNotEmpty() == true && (result == null || result!!.pending > 0))
                TextButton(onClick = onClose) { Text(if (result == null) "Cancelar" else "Cerrar") }
        }
    )
}
