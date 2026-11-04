package com.ejemplo.bloqueador

import android.text.format.DateUtils
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import java.text.DateFormat
import java.text.Normalizer
import java.util.Date
import kotlin.math.abs

// -------------------------------------------------------------------------------------------
// Encabezados y contenedores
// -------------------------------------------------------------------------------------------

/** Título grande de cada pestaña, con acciones opcionales a la derecha. */
@Composable
fun Titulo(texto: String, subtitulo: String? = null, acciones: @Composable RowScope.() -> Unit = {}) {
    Row(
        Modifier.fillMaxWidth().padding(start = Esp.l, end = Esp.s, top = Esp.l, bottom = Esp.s),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                texto,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.semantics { heading() }
            )
            if (subtitulo != null) {
                Text(
                    subtitulo,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        acciones()
    }
}

/** Rótulo pequeño que separa secciones (A, B, C… o "Llamadas", "Mensajes"). */
@Composable
fun Rotulo(texto: String, modifier: Modifier = Modifier) {
    Text(
        texto,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier
            .padding(start = Esp.l, end = Esp.l, top = Esp.l, bottom = Esp.xs)
            .semantics { heading() }
    )
}

/** Tarjeta blanca con borde suave para agrupar opciones. */
@Composable
fun Tarjeta(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(content = content)
    }
}

// -------------------------------------------------------------------------------------------
// Filas
// -------------------------------------------------------------------------------------------

/** Ícono dentro de un círculo de color suave; decorativo (el texto de al lado lo explica). */
@Composable
fun IconoCirculo(icono: ImageVector, fondo: Color, color: Color, tamano: Dp = 40.dp) {
    Box(
        Modifier.size(tamano).clip(CircleShape).background(fondo),
        contentAlignment = Alignment.Center
    ) {
        Icon(icono, contentDescription = null, tint = color, modifier = Modifier.size(tamano * 0.55f))
    }
}

/** Opción con interruptor: toda la fila se puede tocar (objetivo grande). */
@Composable
fun FilaInterruptor(
    icono: ImageVector,
    titulo: String,
    detalle: String,
    valor: Boolean,
    habilitado: Boolean = true,
    onCambio: (Boolean) -> Unit
) {
    ListItem(
        modifier = Modifier.toggleable(
            value = valor,
            enabled = habilitado,
            role = Role.Switch,
            onValueChange = onCambio
        ),
        leadingContent = {
            Icon(icono, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        },
        headlineContent = { Text(titulo, style = MaterialTheme.typography.bodyLarge) },
        supportingContent = { Text(detalle) },
        trailingContent = { Switch(checked = valor, onCheckedChange = null, enabled = habilitado) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )
}

/** Fila que abre algo (flecha a la derecha). */
@Composable
fun FilaAccion(
    icono: ImageVector,
    titulo: String,
    detalle: String? = null,
    valor: String? = null,
    onClick: () -> Unit
) {
    ListItem(
        modifier = Modifier.clickable(role = Role.Button, onClick = onClick),
        leadingContent = { Icon(icono, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
        headlineContent = { Text(titulo, style = MaterialTheme.typography.bodyLarge) },
        supportingContent = detalle?.let { { Text(it) } },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (valor != null) {
                    Text(valor, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                }
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )
}

@Composable
fun Separador() {
    HorizontalDivider(Modifier.padding(start = 72.dp), color = MaterialTheme.colorScheme.outlineVariant)
}

// -------------------------------------------------------------------------------------------
// Avatar
// -------------------------------------------------------------------------------------------

/** Fondo suave + letra oscura (claro) o fondo profundo + letra clara (oscuro); contraste ≥ 7:1. */
private class TonoAvatar(val fondoClaro: Long, val letraClara: Long, val fondoOscuro: Long, val letraOscura: Long)

private val TONOS_AVATAR = listOf(
    TonoAvatar(0xFFDCEBFC, 0xFF0B3A73, 0xFF17407A, 0xFFDCEBFC),
    TonoAvatar(0xFFDCFCE7, 0xFF14532D, 0xFF14532D, 0xFFBBF7D0),
    TonoAvatar(0xFFFEE2E2, 0xFF7F1D1D, 0xFF7F1D1D, 0xFFFEE2E2),
    TonoAvatar(0xFFEDE4FA, 0xFF4A1D7A, 0xFF3F2468, 0xFFE9DDFB),
    TonoAvatar(0xFFFFE8D6, 0xFF7C2D12, 0xFF6B2A0E, 0xFFFFE1CC),
    TonoAvatar(0xFFD5F2EE, 0xFF0F4C46, 0xFF0F4C46, 0xFFC8F1EC),
    TonoAvatar(0xFFE0E4FB, 0xFF1E2A78, 0xFF232C6B, 0xFFDDE2FB),
    TonoAvatar(0xFFFCE4EF, 0xFF7A1646, 0xFF6B1640, 0xFFFBDCEA)
)

/** Círculo tonal con la inicial del nombre (decorativo para lectores de pantalla). */
@Composable
fun Avatar(nombre: String, tamano: Dp = 44.dp) {
    val inicial = nombre.trim().firstOrNull()?.takeIf { it.isLetter() }?.uppercaseChar()?.toString() ?: "#"
    val tono = TONOS_AVATAR[abs(nombre.hashCode() % TONOS_AVATAR.size)]
    val oscuro = isSystemInDarkTheme()
    val fondo = Color(if (oscuro) tono.fondoOscuro else tono.fondoClaro)
    val letra = Color(if (oscuro) tono.letraOscura else tono.letraClara)
    Box(
        Modifier.size(tamano).clip(CircleShape).background(fondo).clearAndSetSemantics { },
        contentAlignment = Alignment.Center
    ) {
        Text(
            inicial,
            color = letra,
            fontWeight = FontWeight.SemiBold,
            style = if (tamano >= 56.dp) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleMedium
        )
    }
}

// -------------------------------------------------------------------------------------------
// Estados: vacío, cargando, sin permiso
// -------------------------------------------------------------------------------------------

/** Pantalla vacía que explica qué pasa y qué hacer. */
@Composable
fun EstadoVacio(
    icono: ImageVector,
    titulo: String,
    texto: String,
    accion: String? = null,
    onAccion: (() -> Unit)? = null,
    extra: @Composable ColumnScope.() -> Unit = {}
) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = Esp.xxl, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Esp.m)
    ) {
        IconoCirculo(
            icono,
            fondo = MaterialTheme.colorScheme.primaryContainer,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            tamano = 72.dp
        )
        Text(titulo, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Text(
            texto,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        if (accion != null && onAccion != null) {
            Button(onClick = onAccion, modifier = Modifier.heightIn(min = 48.dp)) { Text(accion) }
        }
        extra()
    }
}

@Composable
fun PermisoFaltante(icono: ImageVector, titulo: String, texto: String, onPedir: () -> Unit) {
    val context = LocalContext.current
    EstadoVacio(icono, titulo, texto, accion = "Dar permiso", onAccion = onPedir) {
        TextButton(onClick = { Acciones.ajustesDeLaApp(context) }) { Text("No aparece el permiso") }
    }
}

@Composable
fun Cargando() {
    Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

// -------------------------------------------------------------------------------------------
// Filtros y diálogos
// -------------------------------------------------------------------------------------------

/** Chips de filtro: el seleccionado muestra palomita (no depende solo del color). */
@Composable
fun Filtros(opciones: List<String>, seleccionado: Int, onSeleccion: (Int) -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = Esp.l),
        horizontalArrangement = Arrangement.spacedBy(Esp.s)
    ) {
        items(opciones.size) { i ->
            val activo = seleccionado == i
            FilterChip(
                selected = activo,
                onClick = { onSeleccion(i) },
                label = { Text(opciones[i]) },
                leadingIcon = if (activo) {
                    { Icon(Icons.Default.Check, contentDescription = null, Modifier.size(FilterChipDefaults.IconSize)) }
                } else null,
                shape = CircleShape,
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = Color.Transparent,
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimaryContainer
                ),
                border = FilterChipDefaults.filterChipBorder(
                    enabled = true,
                    selected = activo,
                    borderColor = MaterialTheme.colorScheme.outlineVariant,
                    selectedBorderColor = Color.Transparent
                ),
                modifier = Modifier.heightIn(min = 40.dp)
            )
        }
    }
}

/**
 * Buscador en píldora, como el de Google Teléfono, Samsung e iOS: relleno suave, sin borde,
 * lupa a la izquierda y botón para borrar a la derecha.
 */
@Composable
fun Buscador(valor: String, onCambio: (String) -> Unit, sugerencia: String) {
    TextField(
        value = valor,
        onValueChange = onCambio,
        modifier = Modifier.fillMaxWidth().padding(horizontal = Esp.l, vertical = Esp.xs).heightIn(min = 52.dp),
        placeholder = { Text(sugerencia) },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        trailingIcon = {
            if (valor.isNotEmpty()) {
                IconButton(onClick = { onCambio("") }) {
                    Icon(Icons.Filled.Clear, contentDescription = "Borrar búsqueda")
                }
            }
        },
        singleLine = true,
        shape = CircleShape,
        colors = TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent,
            errorIndicatorColor = Color.Transparent
        )
    )
}

/**
 * Fila con gestos como la de Samsung: deslizar a la derecha llama y a la izquierda manda mensaje.
 * El fondo muestra ícono y nombre de la acción, crece al acercarse al umbral y vibra suave al
 * cruzarlo. La fila siempre regresa a su lugar; los botones de la fila siguen disponibles sin gestos.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilaDeslizable(
    alLlamar: (() -> Unit)?,
    alMensaje: (() -> Unit)?,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    if (alLlamar == null && alMensaje == null) {
        Box(modifier) { content() }
        return
    }
    val extra = LocalColoresExtra.current
    val haptic = LocalHapticFeedback.current
    val estado = rememberSwipeToDismissBoxState(
        confirmValueChange = { destino ->
            when (destino) {
                SwipeToDismissBoxValue.StartToEnd -> alLlamar?.invoke()
                SwipeToDismissBoxValue.EndToStart -> alMensaje?.invoke()
                SwipeToDismissBoxValue.Settled -> Unit
            }
            false
        }
    )
    // Una vibración breve al cruzar el umbral, para sentir que el gesto "engancha".
    LaunchedEffect(estado.targetValue) {
        if (estado.targetValue != SwipeToDismissBoxValue.Settled) {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }
    SwipeToDismissBox(
        state = estado,
        modifier = modifier,
        enableDismissFromStartToEnd = alLlamar != null,
        enableDismissFromEndToStart = alMensaje != null,
        backgroundContent = {
            val haciaLlamar = estado.dismissDirection == SwipeToDismissBoxValue.StartToEnd
            val haciaMensaje = estado.dismissDirection == SwipeToDismissBoxValue.EndToStart
            val enganchado = estado.targetValue != SwipeToDismissBoxValue.Settled
            val escala by animateFloatAsState(if (enganchado) 1.12f else 0.92f, label = "escalaGesto")
            val fondo = when {
                haciaLlamar -> extra.llamar
                haciaMensaje -> MaterialTheme.colorScheme.primary
                else -> Color.Transparent
            }
            val sobre = if (haciaLlamar) extra.onLlamar else MaterialTheme.colorScheme.onPrimary
            Box(
                Modifier.fillMaxSize().background(fondo).padding(horizontal = Esp.xl),
                contentAlignment = if (haciaLlamar) Alignment.CenterStart else Alignment.CenterEnd
            ) {
                if (haciaLlamar || haciaMensaje) {
                    Row(
                        Modifier.scale(escala),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Esp.s)
                    ) {
                        if (haciaLlamar) {
                            Icon(Icons.Filled.Call, contentDescription = null, tint = sobre)
                            Text("Llamar", color = sobre, style = MaterialTheme.typography.labelLarge)
                        } else {
                            Text("Mensaje", color = sobre, style = MaterialTheme.typography.labelLarge)
                            Icon(Icons.Filled.Sms, contentDescription = null, tint = sobre)
                        }
                    }
                }
            }
        }
    ) {
        Box(Modifier.background(MaterialTheme.colorScheme.background)) { content() }
    }
}

/** Pregunta antes de una acción que no se puede deshacer. */
@Composable
fun Confirmar(
    titulo: String,
    texto: String,
    accion: String,
    onConfirmar: () -> Unit,
    onCancelar: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onCancelar,
        title = { Text(titulo) },
        text = { Text(texto) },
        confirmButton = {
            TextButton(
                onClick = onConfirmar,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) { Text(accion) }
        },
        dismissButton = { TextButton(onClick = onCancelar) { Text("Cancelar") } }
    )
}

/** Botón de acción redondo con texto debajo (Llamar, Mensaje, Guardar…). */
@Composable
fun BotonRedondo(icono: ImageVector, texto: String, onClick: () -> Unit, destacado: Boolean = false) {
    Column(
        Modifier.clip(MaterialTheme.shapes.medium).clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = Esp.s, vertical = Esp.xs)
            .widthIn(min = 72.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        val extra = LocalColoresExtra.current
        IconoCirculo(
            icono,
            fondo = if (destacado) extra.llamar else MaterialTheme.colorScheme.primaryContainer,
            color = if (destacado) extra.onLlamar else MaterialTheme.colorScheme.onPrimaryContainer,
            tamano = 52.dp
        )
        Text(
            texto,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(top = Esp.xs),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

// -------------------------------------------------------------------------------------------
// Utilidades de texto
// -------------------------------------------------------------------------------------------

/** Solo la hora ("14:32"), para filas que ya están bajo un encabezado de día. */
fun horaCorta(ts: Long): String = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(ts))

/** Hoy → "14:32"; ayer → "Ayer 14:32"; antes → "24/10/26 14:32". */
fun fechaCorta(ts: Long): String {
    val d = Date(ts)
    val hora = DateFormat.getTimeInstance(DateFormat.SHORT).format(d)
    return when {
        DateUtils.isToday(ts) -> hora
        DateUtils.isToday(ts + DateUtils.DAY_IN_MILLIS) -> "Ayer $hora"
        else -> DateFormat.getDateInstance(DateFormat.SHORT).format(d) + " " + hora
    }
}

fun duracionTexto(segundos: Long): String = when {
    segundos <= 0 -> ""
    segundos < 60 -> "$segundos s"
    else -> "%d:%02d min".format(segundos / 60, segundos % 60)
}

/** Quita acentos y pasa a minúsculas para buscar "Jose" y encontrar "José". */
fun simplificar(s: String): String =
    Normalizer.normalize(s, Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "").lowercase()

fun esNumeroTelefono(s: String): Boolean =
    s.any { it.isDigit() } && s.all { it.isDigit() || it in "+-() ." }
