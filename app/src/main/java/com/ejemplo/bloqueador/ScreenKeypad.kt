package com.ejemplo.bloqueador

import android.Manifest
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Backspace
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val TECLAS = listOf(
    "1" to "", "2" to "ABC", "3" to "DEF",
    "4" to "GHI", "5" to "JKL", "6" to "MNO",
    "7" to "PQRS", "8" to "TUV", "9" to "WXYZ",
    "*" to "", "0" to "+", "#" to ""
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PantallaTeclado(refreshKey: Int) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val extra = LocalColoresExtra.current
    val abrirFormulario = LocalAbrirFormulario.current
    var numero by rememberSaveable { mutableStateOf("") }

    // Contactos para sugerir mientras marcas
    var contactos by remember { mutableStateOf<List<ContactEntry>>(emptyList()) }
    LaunchedEffect(refreshKey) {
        if (PhoneData.has(context, Manifest.permission.READ_CONTACTS)) {
            contactos = withContext(Dispatchers.IO) { PhoneData.loadContacts(context) }
        }
    }
    val sugerencias = remember(numero, contactos) {
        val digitos = numero.filter { it.isDigit() }
        if (digitos.length < 3) emptyList()
        else contactos.mapNotNull { c ->
            c.numbers.firstOrNull { it.filter(Char::isDigit).contains(digitos) }?.let { c to it }
        }.take(3)
    }
    val nombreExacto = remember(numero, contactos) {
        val n = Prefs.normalize(numero)
        if (n.length < 7) null else contactos.firstOrNull { c -> c.numbers.any { Prefs.normalize(it) == n } }?.name
    }

    fun teclear(t: String) {
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        if (numero.length < 32) numero += t
    }

    // El teclado es un gráfico fijo: sus dígitos no crecen con el tamaño de letra del sistema
    // para que nunca se corten; el resto de la pantalla sí respeta ese ajuste.
    val digitoSp = with(LocalDensity.current) { 30.dp.toSp() }
    val letrasSp = with(LocalDensity.current) { 11.dp.toSp() }
    val numeroSp = with(LocalDensity.current) { (if (numero.length > 13) 28.dp else 36.dp).toSp() }

    // Tamaño de tecla según el alto disponible, para que todo quepa en teléfonos pequeños
    BoxWithConstraints(Modifier.fillMaxSize()) {
    val tecla = when {
        maxHeight < 520.dp -> 60.dp
        maxHeight < 620.dp -> 68.dp
        else -> 76.dp
    }
    val hueco = if (tecla < 70.dp) Esp.s else Esp.m

    Column(Modifier.fillMaxSize().padding(top = Esp.s), horizontalAlignment = Alignment.CenterHorizontally) {

        // Sugerencias de contactos (ocupan el espacio libre de arriba)
        Column(
            Modifier.weight(1f).fillMaxWidth().padding(horizontal = Esp.s),
            verticalArrangement = Arrangement.Bottom
        ) {
            if (numero.isEmpty()) {
                Text(
                    "Escribe un número o parte de él para buscar en tus contactos.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = Esp.xl, vertical = Esp.l)
                )
            }
            sugerencias.forEach { (c, n) ->
                ListItem(
                    modifier = Modifier.clip(MaterialTheme.shapes.medium)
                        .clickable(onClickLabel = "Usar este número") { numero = n },
                    leadingContent = { Avatar(c.name, 40.dp) },
                    headlineContent = { Text(c.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    supportingContent = { Text(n) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )
            }
        }

        // Número marcado
        Text(
            numero.ifEmpty { " " },
            fontSize = numeroSp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth().padding(horizontal = Esp.l)
                .semantics {
                    liveRegion = LiveRegionMode.Polite
                    contentDescription = if (numero.isEmpty()) "Sin número" else "Número: ${numero.toCharArray().joinToString(" ")}"
                }
        )
        Text(
            nombreExacto ?: " ",
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = Esp.m)
        )

        // Teclado 4 × 3
        Column(
            Modifier.widthIn(max = 360.dp).fillMaxWidth().padding(horizontal = Esp.xl),
            verticalArrangement = Arrangement.spacedBy(hueco)
        ) {
            TECLAS.chunked(3).forEach { fila ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    fila.forEach { (digito, letras) ->
                        Box(
                            Modifier.size(tecla).clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                .combinedClickable(
                                    onClick = { teclear(digito) },
                                    onLongClick = if (digito == "0") ({ teclear("+") }) else null,
                                    onLongClickLabel = if (digito == "0") "Escribir +" else null
                                )
                                .semantics { role = Role.Button; contentDescription = digito },
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.clearAndSetSemantics { }
                            ) {
                                Text(digito, fontSize = digitoSp, fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface)
                                if (letras.isNotEmpty()) {
                                    Text(letras, fontSize = letrasSp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Guardar · Llamar · Borrar
        Row(
            Modifier.widthIn(max = 360.dp).fillMaxWidth().padding(horizontal = Esp.xl, vertical = hueco),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(tecla), contentAlignment = Alignment.Center) {
                if (numero.isNotEmpty() && nombreExacto == null) {
                    IconButton(onClick = { abrirFormulario(PeticionFormulario(numero = numero)) }, modifier = Modifier.size(56.dp)) {
                        Icon(Icons.Filled.PersonAdd, contentDescription = "Guardar como contacto",
                            tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            Box(
                Modifier.size(tecla).clip(CircleShape)
                    .background(if (numero.isBlank()) extra.llamar.copy(alpha = 0.45f) else extra.llamar)
                    .clickable(enabled = numero.isNotBlank(), role = Role.Button, onClickLabel = "Llamar") {
                        Acciones.llamar(context, numero)
                    }
                    .semantics { contentDescription = if (nombreExacto != null) "Llamar a $nombreExacto" else "Llamar" },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Call, contentDescription = null, tint = extra.onLlamar, modifier = Modifier.size(34.dp))
            }
            Box(
                Modifier.size(tecla).clip(CircleShape)
                    .combinedClickable(
                        enabled = numero.isNotEmpty(),
                        onClick = { numero = numero.dropLast(1) },
                        onLongClick = { numero = "" },
                        onLongClickLabel = "Borrar todo"
                    )
                    .semantics { role = Role.Button; contentDescription = "Borrar" },
                contentAlignment = Alignment.Center
            ) {
                if (numero.isNotEmpty()) {
                    Icon(Icons.AutoMirrored.Outlined.Backspace, contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(28.dp))
                }
            }
        }
    }
}
}
