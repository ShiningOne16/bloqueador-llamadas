package com.ejemplo.bloqueador

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** Premium: lista de todas las notas de números, con alta, edición y borrado. */
@Composable
fun PantallaNotas(onCerrar: () -> Unit) {
    val context = LocalContext.current
    val store = remember { NoteStore(context) }
    // Los números de contactos ocultos nunca se muestran.
    val ocultos = remember { VistaOculta.claves(context) }
    var version by remember { mutableIntStateOf(0) }
    val notas = remember(version) { store.all().filterNot { HiddenRules.isHiddenNumber(ocultos, it.number) } }
    var editando by remember { mutableStateOf<NumberNote?>(null) }
    var nueva by remember { mutableStateOf(false) }
    BackHandler(onBack = onCerrar)

    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
        PremiumHeader("Notas de números", onCerrar)
        if (notas.isEmpty()) {
            EstadoVacio(
                Icons.Filled.Bookmark,
                "Aún no tienes notas",
                "Ponle una etiqueta a un número (\"Cobranza banco\", \"Repartidor\") para reconocerlo cuando vuelva a llamar. También puedes hacerlo desde el panel de cualquier llamada.",
                accion = "Agregar nota",
                onAccion = { nueva = true }
            )
        } else {
            LazyColumn(Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(bottom = Esp.l)) {
                items(notas, key = { NoteRules.key(it.number) }) { n ->
                    val titulo = n.label.ifBlank { n.number }
                    ListItem(
                        modifier = Modifier.clickable(role = Role.Button, onClickLabel = "Editar nota") { editando = n },
                        leadingContent = { Avatar(titulo) },
                        headlineContent = { Text(titulo, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        supportingContent = {
                            Text(
                                listOfNotNull(n.number.takeIf { n.label.isNotBlank() }, n.note.ifBlank { null }).joinToString(" · "),
                                maxLines = 2, overflow = TextOverflow.Ellipsis
                            )
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                    )
                }
                item {
                    OutlinedButton(
                        onClick = { nueva = true },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = Esp.l, vertical = Esp.m).heightIn(min = 48.dp)
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null)
                        Spacer(Modifier.width(Esp.s))
                        Text("Agregar nota")
                    }
                }
            }
        }
    }

    if (nueva) DialogoNota(
        numeroInicial = "", editarNumero = true, etiquetaInicial = "", notaInicial = "", puedeBorrar = false,
        onGuardar = { numero, etiqueta, nota -> store.set(numero, etiqueta, nota); nueva = false; version++ },
        onBorrar = {}, onCerrar = { nueva = false }
    )
    editando?.let { n ->
        DialogoNota(
            numeroInicial = n.number, editarNumero = false, etiquetaInicial = n.label, notaInicial = n.note, puedeBorrar = true,
            onGuardar = { numero, etiqueta, nota -> store.set(numero, etiqueta, nota); editando = null; version++ },
            onBorrar = { store.remove(n.number); editando = null; version++ },
            onCerrar = { editando = null }
        )
    }
}

/** Ventana para escribir la etiqueta y la nota de un número. */
@Composable
internal fun DialogoNota(
    numeroInicial: String,
    editarNumero: Boolean,
    etiquetaInicial: String,
    notaInicial: String,
    puedeBorrar: Boolean,
    onGuardar: (numero: String, etiqueta: String, nota: String) -> Unit,
    onBorrar: () -> Unit,
    onCerrar: () -> Unit
) {
    var numero by rememberSaveable { mutableStateOf(numeroInicial) }
    var etiqueta by rememberSaveable { mutableStateOf(etiquetaInicial) }
    var nota by rememberSaveable { mutableStateOf(notaInicial) }
    val numeroValido = PremiumRules.validNumber(numero)
    val hayTexto = etiqueta.isNotBlank() || nota.isNotBlank()
    AlertDialog(
        onDismissRequest = onCerrar,
        title = { Text(if (puedeBorrar) "Editar nota" else "Nota de número") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Esp.m)) {
                if (editarNumero) {
                    OutlinedTextField(
                        value = numero, onValueChange = { numero = it.take(20) },
                        label = { Text("Número") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        isError = numero.isNotEmpty() && !numeroValido,
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    Text(numero, style = MaterialTheme.typography.titleMedium)
                }
                OutlinedTextField(
                    value = etiqueta, onValueChange = { etiqueta = it.take(NoteRules.MAX_LABEL) },
                    label = { Text("Etiqueta") }, placeholder = { Text("Ej. Cobranza banco") }, singleLine = true,
                    supportingText = { Text("${etiqueta.length}/${NoteRules.MAX_LABEL}") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = nota, onValueChange = { nota = it.take(NoteRules.MAX_NOTE) },
                    label = { Text("Nota (opcional)") }, minLines = 2, maxLines = 4,
                    supportingText = { Text("${nota.length}/${NoteRules.MAX_NOTE}") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (puedeBorrar) {
                    TextButton(
                        onClick = onBorrar,
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) { Text("Eliminar") }
                }
                TextButton(onClick = { onGuardar(numero, etiqueta, nota) }, enabled = numeroValido && hayTexto) { Text("Guardar") }
            }
        },
        dismissButton = { TextButton(onClick = onCerrar) { Text("Cancelar") } }
    )
}
