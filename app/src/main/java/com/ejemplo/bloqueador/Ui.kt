package com.ejemplo.bloqueador

import android.text.format.DateUtils
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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

// Tonos 700/800: todos con contraste ≥ 5:1 frente al texto blanco
private val COLORES_AVATAR = listOf(
    Color(0xFF1565C0), Color(0xFF2E7D32), Color(0xFFC62828), Color(0xFF6A1B9A),
    Color(0xFFBF360C), Color(0xFF00695C), Color(0xFF283593), Color(0xFFAD1457)
)

/** Círculo de color con la inicial del nombre (decorativo para lectores de pantalla). */
@Composable
fun Avatar(nombre: String, tamano: Dp = 44.dp) {
    val inicial = nombre.trim().firstOrNull()?.takeIf { it.isLetter() }?.uppercaseChar()?.toString() ?: "#"
    val color = COLORES_AVATAR[abs(nombre.hashCode() % COLORES_AVATAR.size)]
    Box(
        Modifier.size(tamano).clip(CircleShape).background(color).clearAndSetSemantics { },
        contentAlignment = Alignment.Center
    ) {
        Text(
            inicial,
            color = Color.White,
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
                modifier = Modifier.heightIn(min = 40.dp)
            )
        }
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
