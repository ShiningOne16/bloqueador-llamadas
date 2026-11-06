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
                    "• Tú eliges cada reporte. Solo se envían el número seleccionado de México, la categoría y un identificador aleatorio de instalación. No se suben tu agenda, historial, mensajes ni números ocultos.\n" +
                    "• El servidor recibe la IP y conserva huellas de la red y la instalación para limitar abusos. Las huellas no garantizan anonimato.\n" +
                    "• Reportar bloquea el número en tu teléfono. Un reporte explícito basta para entrar en la lista comunitaria: no significa que otros usuarios lo hayan confirmado. El servidor actualizado admite ${SpamRules.MAX_REPORTES_POR_DIA} intentos diarios por red e instalación. Los reportes vencen a los 180 días y la copia comunitaria caduca tras 7 días sin actualizar.\n" +
                    "• Tellows sigue pendiente. Si se activa, sus datos vencen en un máximo de 72 horas.\n" +
                    "• \"No es spam\" quita tu bloqueo manual, evita el bloqueo por la lista y permite retirar tu reporte. Otras reglas personales pueden seguir aplicándose. Tus contactos solo son excepciones locales.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** Pregunta qué tipo de spam es antes de reportar el número. */
@Composable
internal fun DialogoReporteSpam(
    numero: String,
    onReportar: (CategoriaSpam) -> Unit,
    onCerrar: () -> Unit
) {
    var categoria by rememberSaveable { mutableStateOf(CategoriaSpam.OTRO.name) }
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
                Text(
                    "Se bloqueará en tu teléfono. Al confirmar envías este número, su categoría y un identificador aleatorio de instalación. El servidor recibe tu IP para limitar abusos. No se envían tu agenda ni historial. Puedes corregirlo con \"No es spam\".",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = { TextButton(onClick = { onReportar(CategoriaSpam.valueOf(categoria)) }) { Text("Reportar y bloquear") } },
        dismissButton = { TextButton(onClick = onCerrar) { Text("Cancelar") } }
    )
}

