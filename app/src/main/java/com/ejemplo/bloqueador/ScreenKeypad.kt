package com.ejemplo.bloqueador

import android.Manifest
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

    Column(
        Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Sugerencias de contactos
        Column(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.Bottom) {
            sugerencias.forEach { (c, n) ->
                ListItem(
                    modifier = Modifier.clip(MaterialTheme.shapes.medium)
                        .combinedClickable(onClick = { numero = n }),
                    leadingContent = { Avatar(c.name, 36.dp) },
                    headlineContent = { Text(c.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    supportingContent = { Text(n) }
                )
            }
        }

        // Número marcado
        Text(
            numero.ifEmpty { " " },
            style = MaterialTheme.typography.headlineLarge,
            fontSize = if (numero.length > 14) 26.sp else 34.sp,
            textAlign = TextAlign.Center,
            maxLines = 1,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
        )
        Text(
            nombreExacto ?: " ",
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        // Teclado 4 x 3
        TECLAS.chunked(3).forEach { fila ->
            Row(
                Modifier.fillMaxWidth().padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                fila.forEach { (digito, letras) ->
                    Tecla(
                        digito, letras,
                        onClick = { teclear(digito) },
                        onLongClick = if (digito == "0") ({ teclear("+") }) else null
                    )
                }
            }
        }

        // Acciones: agregar contacto, llamar, borrar
        Row(
            Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(72.dp), contentAlignment = Alignment.Center) {
                if (numero.isNotEmpty() && nombreExacto == null) {
                    TextButton(onClick = { Acciones.nuevoContacto(context, numero) }) { Text("Guardar") }
                }
            }
            Box(
                Modifier.size(72.dp).clip(CircleShape).background(Color(0xFF2E7D32))
                    .combinedClickable(onClick = {
                        if (numero.isNotBlank()) Acciones.llamar(context, numero)
                    }),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Call, contentDescription = "Llamar", tint = Color.White, modifier = Modifier.size(32.dp))
            }
            Box(
                Modifier.size(72.dp).clip(CircleShape)
                    .combinedClickable(
                        onClick = { if (numero.isNotEmpty()) numero = numero.dropLast(1) },
                        onLongClick = { numero = "" }
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (numero.isNotEmpty()) Text("⌫", fontSize = 28.sp)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Tecla(digito: String, letras: String, onClick: () -> Unit, onLongClick: (() -> Unit)?) {
    Column(
        Modifier.size(76.dp).clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(digito, fontSize = 30.sp, fontWeight = FontWeight.Medium)
        if (letras.isNotEmpty()) {
            Text(letras, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
