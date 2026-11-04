package com.ejemplo.bloqueador

import android.Manifest
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Se eligen números individuales. La selección se aplica al borrador al pulsar Usar selección. */
@Composable
fun ScheduledContactPicker(
    selected: List<ScheduledContact>,
    refreshKey: Int,
    onClose: () -> Unit,
    onSave: (List<ScheduledContact>) -> Unit
) {
    val context = LocalContext.current
    var chosen by rememberSaveable { mutableStateOf(selected) }
    var query by rememberSaveable { mutableStateOf("") }
    var selectedOnly by rememberSaveable { mutableStateOf(false) }
    var revision by remember { mutableIntStateOf(0) }
    var permitted by remember { mutableStateOf(Contacts.hasPermission(context)) }
    var contacts by remember { mutableStateOf<List<ScheduledContact>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val requestPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        permitted = it
        revision++
    }

    LaunchedEffect(refreshKey, revision) {
        permitted = Contacts.hasPermission(context)
        if (!permitted) return@LaunchedEffect
        contacts = null
        error = null
        try {
            contacts = withContext(Dispatchers.IO) {
                PhoneData.loadContacts(context).flatMap { contact ->
                    contact.numbers.filter(PremiumRules::validNumber).map { ScheduledContact(it, contact.name) }
                }.distinctBy { PremiumRules.numberKey(it.number) }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: SecurityException) {
            permitted = false
        } catch (_: Exception) {
            error = "No se pudo cargar tu agenda. Inténtalo de nuevo."
        }
    }
    BackHandler(onBack = onClose)

    val chosenKeys = remember(chosen) { chosen.map { PremiumRules.numberKey(it.number) }.toSet() }
    // Conserva también los números seleccionados antes, aunque ya no estén en la agenda.
    val available = remember(contacts, chosen) {
        (contacts.orEmpty() + chosen).distinctBy { PremiumRules.numberKey(it.number) }
    }
    val filtered = remember(available, query, chosenKeys, selectedOnly) {
        val text = simplificar(query.trim())
        val digits = query.filter { it.isDigit() }
        available.filter { contact ->
            (!selectedOnly || PremiumRules.numberKey(contact.number) in chosenKeys) &&
                (text.isBlank() || simplificar(contact.name).contains(text) ||
                    (digits.isNotEmpty() && contact.number.filter { it.isDigit() }.contains(digits)))
        }
    }

    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).imePadding()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose, modifier = Modifier.size(48.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Cancelar selección")
            }
            Text("Elegir contactos", style = MaterialTheme.typography.titleLarge)
        }
        Text("Marca los números que se bloquearán durante el horario. Si una persona tiene varios, elige los que quieras.",
            Modifier.padding(horizontal = Esp.l, vertical = Esp.s), style = MaterialTheme.typography.bodyMedium)

        if (!permitted) {
            Box(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                PermisoFaltante(Icons.Filled.Contacts, "Elige de tu agenda",
                    "Se necesita permiso para leer los contactos. Tu agenda no se modifica.",
                    onPedir = { requestPermission.launch(Manifest.permission.READ_CONTACTS) })
            }
        } else {
            OutlinedTextField(query, { query = it }, label = { Text("Buscar nombre o número") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) }, singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = Esp.l))
            FilterChip(selectedOnly, { selectedOnly = !selectedOnly }, label = { Text("Solo seleccionados (${chosen.size})") },
                modifier = Modifier.padding(horizontal = Esp.l).heightIn(min = 48.dp))
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when {
                    error != null -> EstadoVacio(Icons.Filled.Contacts, "No se pudo cargar", error.orEmpty(),
                        accion = "Reintentar", onAccion = { revision++ })
                    contacts == null -> Cargando()
                    filtered.isEmpty() -> EstadoVacio(Icons.Filled.Contacts, "Sin contactos aquí",
                        if (selectedOnly) "No hay números seleccionados que coincidan con la búsqueda."
                        else "Busca otro nombre o guarda un contacto con un número de 7 a 15 dígitos.")
                    else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = Esp.s)) {
                        items(filtered, key = { PremiumRules.numberKey(it.number) }) { contact ->
                            val key = PremiumRules.numberKey(contact.number)
                            val checked = key in chosenKeys
                            ListItem(
                                modifier = Modifier.toggleable(checked, role = Role.Checkbox, onValueChange = { check ->
                                    chosen = if (check) chosen + contact else chosen.filterNot { PremiumRules.numberKey(it.number) == key }
                                }),
                                leadingContent = { Checkbox(checked = checked, onCheckedChange = null) },
                                headlineContent = { Text(contact.name) },
                                supportingContent = { Text(contact.number) },
                                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background)
                            )
                        }
                    }
                }
            }
        }
        Button(onClick = { onSave(chosen) }, modifier = Modifier.fillMaxWidth().padding(Esp.l).heightIn(min = 48.dp)) {
            Text("Usar selección (${chosen.size})")
        }
    }
}
