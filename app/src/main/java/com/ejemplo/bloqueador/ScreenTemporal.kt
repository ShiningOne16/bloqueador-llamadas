package com.ejemplo.bloqueador

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DoNotDisturbOn
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

/** Premium: modo concentración y permisos temporales. */
@Composable
fun PantallaTemporal(onCerrar: () -> Unit, onChanged: () -> Unit) {
    val context = LocalContext.current
    val store = remember { TemporalStore(context) }
    val ocultos = remember { VistaOculta.claves(context) }
    var version by remember { mutableIntStateOf(0) }
    val minuto = currentProtectionTime() // se renueva cada minuto para que la cuenta regresiva avance
    val ahora = remember(minuto, version) { System.currentTimeMillis() }
    val concentracion = remember(ahora) { store.concentracionHasta }
    val permisos = remember(ahora) {
        store.permisos(ahora).filterNot { !it.paraDesconocidos && HiddenRules.isHiddenNumber(ocultos, it.numero) }
    }
    var nuevoNumero by remember { mutableStateOf(false) }
    BackHandler(onBack = onCerrar)

    fun cambio() { version++; onChanged() }

    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
        PremiumHeader("Concentración y permisos", onCerrar)
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = Esp.l, vertical = Esp.s),
            verticalArrangement = Arrangement.spacedBy(Esp.m)
        ) {
            Text("Modo concentración", style = MaterialTheme.typography.titleMedium)
            Tarjeta {
                if (TemporalRules.activo(concentracion, ahora)) {
                    ListItem(
                        leadingContent = { Icon(Icons.Filled.DoNotDisturbOn, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                        headlineContent = { Text("Activo hasta las ${horaCorta(concentracion)}") },
                        supportingContent = { Text("Faltan ${TemporalRules.restante(concentracion, ahora)}. Se rechazan todas las llamadas, salvo los permisos temporales de un número.") },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                    )
                    Button(
                        onClick = { store.terminarConcentracion(); cambio() },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = Esp.l).padding(bottom = Esp.l).heightIn(min = 48.dp)
                    ) { Text("Terminar ahora") }
                } else {
                    Text(
                        "Rechaza todas las llamadas por un rato, incluso las de tus contactos y aunque Protección esté en pausa. Se apaga solo. Elige cuánto tiempo:",
                        Modifier.padding(Esp.l), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    BotonesDuracion(TemporalRules.DURACIONES_CONCENTRACION) { minutos ->
                        store.iniciarConcentracion(minutos, System.currentTimeMillis()); cambio()
                    }
                }
            }

            Text("Permiso temporal", style = MaterialTheme.typography.titleMedium)
            Tarjeta {
                Text(
                    "Deja pasar llamadas por un rato, por ejemplo mientras esperas al repartidor. Se cierra solo.",
                    Modifier.padding(Esp.l), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text("Todos los desconocidos, por:", Modifier.padding(horizontal = Esp.l), style = MaterialTheme.typography.titleSmall)
                BotonesDuracion(TemporalRules.DURACIONES_PERMISO) { minutos ->
                    store.permitirDesconocidos(minutos, System.currentTimeMillis()); cambio()
                }
                OutlinedButton(
                    onClick = { nuevoNumero = true },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = Esp.l).padding(bottom = Esp.l).heightIn(min = 48.dp)
                ) { Text("Permitir un número") }
            }

            if (permisos.isNotEmpty()) {
                Text("Permisos activos", style = MaterialTheme.typography.titleMedium)
                Tarjeta {
                    permisos.forEachIndexed { i, p ->
                        if (i > 0) HorizontalDivider(Modifier.padding(horizontal = Esp.l), color = MaterialTheme.colorScheme.outlineVariant)
                        ListItem(
                            leadingContent = { Icon(Icons.Filled.Timer, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                            headlineContent = {
                                Text(if (p.paraDesconocidos) "Todos los desconocidos" else p.etiqueta.ifBlank { p.numero })
                            },
                            supportingContent = {
                                Text(
                                    listOfNotNull(p.numero.takeIf { !p.paraDesconocidos && p.etiqueta.isNotBlank() },
                                        "Faltan ${TemporalRules.restante(p.hasta, ahora)}").joinToString(" · ")
                                )
                            },
                            trailingContent = {
                                IconButton(onClick = { store.quitar(p); cambio() }) {
                                    Icon(Icons.Filled.Close, contentDescription = "Quitar permiso")
                                }
                            },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                        )
                    }
                }
            }
        }
    }

    if (nuevoNumero) DialogoPermisoNumero(
        onGuardar = { numero, etiqueta, minutos ->
            store.permitirNumero(numero, etiqueta, minutos, System.currentTimeMillis()); nuevoNumero = false; cambio()
        },
        onCerrar = { nuevoNumero = false }
    )
}

/** Fila de botones con las duraciones disponibles; cada uno aplica la acción de inmediato. */
@Composable
private fun BotonesDuracion(minutos: List<Int>, onElegir: (Int) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = Esp.l, vertical = Esp.s),
        horizontalArrangement = Arrangement.spacedBy(Esp.s),
        verticalAlignment = Alignment.CenterVertically
    ) {
        minutos.forEach { m ->
            OutlinedButton(
                onClick = { onElegir(m) },
                modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                contentPadding = PaddingValues(horizontal = Esp.xs)
            ) { Text(TemporalRules.duracion(m)) }
        }
    }
}

@Composable
private fun DialogoPermisoNumero(onGuardar: (String, String, Int) -> Unit, onCerrar: () -> Unit) {
    var numero by rememberSaveable { mutableStateOf("") }
    var etiqueta by rememberSaveable { mutableStateOf("") }
    var minutos by rememberSaveable { mutableIntStateOf(TemporalRules.DURACIONES_PERMISO[1]) }
    val valido = PremiumRules.validNumber(numero)
    AlertDialog(
        onDismissRequest = onCerrar,
        title = { Text("Permitir un número") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Esp.m)) {
                OutlinedTextField(
                    value = numero, onValueChange = { numero = it.take(20) }, label = { Text("Número") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    isError = numero.isNotEmpty() && !valido, modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = etiqueta, onValueChange = { etiqueta = it.take(30) }, label = { Text("Nombre (opcional)") },
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                Filtros(TemporalRules.DURACIONES_PERMISO.map { TemporalRules.duracion(it) },
                    TemporalRules.DURACIONES_PERMISO.indexOf(minutos).coerceAtLeast(0)) { minutos = TemporalRules.DURACIONES_PERMISO[it] }
            }
        },
        confirmButton = { TextButton(onClick = { onGuardar(numero, etiqueta, minutos) }, enabled = valido) { Text("Permitir") } },
        dismissButton = { TextButton(onClick = onCerrar) { Text("Cancelar") } }
    )
}
