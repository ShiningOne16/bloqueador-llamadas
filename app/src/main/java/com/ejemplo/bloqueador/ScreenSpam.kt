package com.ejemplo.bloqueador

import android.text.format.DateUtils
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Report
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** Premium: bloqueo con la lista de spam que reporta la comunidad. */
@Composable
fun PantallaSpam(onCerrar: () -> Unit, onChanged: () -> Unit) {
    val context = LocalContext.current
    val store = remember { SpamStore(context) }
    val configurado = remember { SpamComunidad.configurado(context) }
    var activo by remember { mutableStateOf(store.activo) }
    var version by remember { mutableIntStateOf(0) }
    var total by remember { mutableIntStateOf(0) }
    var tellows by remember { mutableStateOf("Pendiente de activación") }
    var actualizada by remember { mutableLongStateOf(store.ultimaActualizacion) }
    BackHandler(onBack = onCerrar)

    // Lee el tamaño de la lista fuera del hilo principal y vuelve a leerlo mientras llega una descarga.
    LaunchedEffect(version, activo) {
        total = withContext(Dispatchers.IO) { SpamLista.total(context) }
        tellows = withContext(Dispatchers.IO) { SpamLista.estadoTellows(context) }
        actualizada = store.ultimaActualizacion
        if (activo && configurado) {
            repeat(6) {
                delay(2_000)
                total = withContext(Dispatchers.IO) { SpamLista.total(context) }
                tellows = withContext(Dispatchers.IO) { SpamLista.estadoTellows(context) }
                actualizada = store.ultimaActualizacion
            }
        }
    }

    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
        PremiumHeader("Lista de spam", onCerrar)
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = Esp.l, vertical = Esp.s),
            verticalArrangement = Arrangement.spacedBy(Esp.m)
        ) {
            if (!configurado) Tarjeta {
                Text(
                    "Esta versión todavía no está conectada a un servidor de la comunidad, así que no hay lista que descargar ni reportes que enviar. " +
                        "Cuando se conecte, aquí aparecerá la lista.",
                    Modifier.padding(Esp.l), style = MaterialTheme.typography.bodyMedium
                )
            }
            Tarjeta {
                FilaInterruptor(
                    Icons.Filled.Report, "Bloquear números de la lista",
                    "Rechaza las llamadas de desconocidos incluidos en las fuentes de spam disponibles. Tus contactos nunca se bloquean.",
                    activo, habilitado = configurado
                ) { quiere ->
                    store.activo = quiere; activo = quiere
                    SpamProgramacion.programar(context)
                    if (quiere) SpamProgramacion.actualizarYa(context)
                    onChanged()
                }
            }
            Tarjeta {
                ListItem(
                    headlineContent = { Text("Tellows · México") },
                    supportingContent = { Text(tellows) },
                    colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent)
                )
                HorizontalDivider(Modifier.padding(horizontal = Esp.l), color = MaterialTheme.colorScheme.outlineVariant)
                ListItem(
                    headlineContent = { Text("Números en la lista") },
                    trailingContent = { Text("$total", style = MaterialTheme.typography.titleMedium) },
                    supportingContent = {
                        Text(if (actualizada == 0L) "Aún no se ha descargado" else "Actualizada ${DateUtils.getRelativeTimeSpanString(actualizada, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS)}")
                    },
                    colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent)
                )
                HorizontalDivider(Modifier.padding(horizontal = Esp.l), color = MaterialTheme.colorScheme.outlineVariant)
                ListItem(
                    headlineContent = { Text("Tus reportes") },
                    trailingContent = { Text("${store.totalReportados}", style = MaterialTheme.typography.titleMedium) },
                    supportingContent = { Text("Reporta desde el panel de una llamada, tocándola en Recientes.") },
                    colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent)
                )
                OutlinedButton(
                    onClick = { SpamProgramacion.actualizarYa(context); version++; Toast.makeText(context, "Actualizando la lista…", Toast.LENGTH_SHORT).show() },
                    enabled = configurado && activo,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = Esp.l).padding(bottom = Esp.l).heightIn(min = 48.dp)
                ) { Text("Actualizar ahora") }
            }
            Text("Privacidad", style = MaterialTheme.typography.titleSmall)
            Text(
                "• La lista se descarga completa y se consulta en tu teléfono: el servidor nunca sabe quién te llama.\n" +
                    "• Al reportar solo se envía el número reportado, el tipo de spam y un identificador anónimo. No se envían tus contactos, llamadas ni mensajes.\n" +
                    "• La lista comunitaria requiere al menos 3 reportes. Tellows, cuando esté disponible, aporta números de México con puntuación de riesgo de 7 a 9 y al menos 3 comentarios. La lista descargada guarda huellas, no números legibles.\n" +
                    "• Si un número está mal marcado, en su panel puedes tocar \"No es spam\" y deja de bloquearse en tu teléfono.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** Pregunta qué tipo de spam es antes de reportar el número. */
@Composable
internal fun DialogoReporteSpam(
    numero: String,
    puedeBloquear: Boolean,
    onReportar: (CategoriaSpam, Boolean) -> Unit,
    onCerrar: () -> Unit
) {
    var categoria by rememberSaveable { mutableStateOf(CategoriaSpam.COBRANZA.name) }
    var bloquear by rememberSaveable { mutableStateOf(puedeBloquear) }
    AlertDialog(
        onDismissRequest = onCerrar,
        title = { Text("Reportar $numero") },
        text = {
            Column {
                Text("¿Qué tipo de llamada es?", style = MaterialTheme.typography.bodyMedium)
                Column(Modifier.selectableGroup()) {
                    CategoriaSpam.entries.forEach { c ->
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = 48.dp)
                                .selectable(selected = categoria == c.name, role = Role.RadioButton) { categoria = c.name },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = categoria == c.name, onClick = null)
                            Spacer(Modifier.width(Esp.m))
                            Text(c.etiqueta)
                        }
                    }
                }
                if (puedeBloquear) {
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 48.dp)
                            .selectable(selected = bloquear, role = Role.Checkbox) { bloquear = !bloquear },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(checked = bloquear, onCheckedChange = null)
                        Spacer(Modifier.width(Esp.m))
                        Text("Bloquear también este número")
                    }
                }
                Text(
                    "Solo se envía el número y el tipo, de forma anónima.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = { TextButton(onClick = { onReportar(CategoriaSpam.valueOf(categoria), bloquear && puedeBloquear) }) { Text("Reportar") } },
        dismissButton = { TextButton(onClick = onCerrar) { Text("Cancelar") } }
    )
}

