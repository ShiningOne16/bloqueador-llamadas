package com.ejemplo.bloqueador

import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.DateFormat
import java.text.Normalizer
import java.util.Date
import kotlin.math.abs

@Composable
fun Titulo(texto: String, acciones: @Composable RowScope.() -> Unit = {}) {
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            texto,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f)
        )
        acciones()
    }
}

private val COLORES_AVATAR = listOf(
    Color(0xFF1E88E5), Color(0xFF43A047), Color(0xFFE53935), Color(0xFF8E24AA),
    Color(0xFFFB8C00), Color(0xFF00897B), Color(0xFF3949AB), Color(0xFFD81B60)
)

/** Círculo de color con la inicial del nombre. */
@Composable
fun Avatar(nombre: String, tamano: Dp = 40.dp) {
    val inicial = nombre.trim().firstOrNull()?.takeIf { it.isLetter() }?.uppercaseChar()?.toString() ?: "#"
    val color = COLORES_AVATAR[abs(nombre.hashCode() % COLORES_AVATAR.size)]
    Box(
        Modifier.size(tamano).clip(CircleShape).background(color),
        contentAlignment = Alignment.Center
    ) {
        Text(inicial, color = Color.White, fontWeight = FontWeight.Bold, fontSize = (tamano.value * 0.42f).sp)
    }
}

@Composable
fun PermisoFaltante(texto: String, onPedir: () -> Unit) {
    val context = LocalContext.current
    Column(
        Modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(texto, textAlign = TextAlign.Center)
        Button(onClick = onPedir) { Text("Dar permiso") }
        TextButton(onClick = { Acciones.ajustesDeLaApp(context) }) { Text("Abrir ajustes de la app") }
        Text(
            "Si el permiso no aparece, en ajustes de la app toca ⋮ → \"Permitir configuración restringida\".",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun Cargando() {
    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
fun TextoVacio(texto: String) {
    Text(
        texto,
        modifier = Modifier.fillMaxWidth().padding(32.dp),
        textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

/** Hoy → "14:32"; antes → "24/10/26 14:32". */
fun fechaCorta(ts: Long): String {
    val d = Date(ts)
    val hora = DateFormat.getTimeInstance(DateFormat.SHORT).format(d)
    return when {
        DateUtils.isToday(ts) -> hora
        DateUtils.isToday(ts + DateUtils.DAY_IN_MILLIS) -> "Ayer $hora"
        else -> DateFormat.getDateInstance(DateFormat.SHORT).format(d) + " " + hora
    }
}

fun duracionTexto(segundos: Long): String =
    if (segundos <= 0) "" else "%d:%02d".format(segundos / 60, segundos % 60)

/** Quita acentos y pasa a minúsculas para buscar "Jose" y encontrar "José". */
fun simplificar(s: String): String =
    Normalizer.normalize(s, Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "").lowercase()

fun esNumeroTelefono(s: String): Boolean =
    s.any { it.isDigit() } && s.all { it.isDigit() || it in "+-() ." }
