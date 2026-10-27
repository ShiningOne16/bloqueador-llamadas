package com.ejemplo.bloqueador

import android.Manifest
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun PantallaContactos(refreshKey: Int, pedirPermisos: () -> Unit) {
    val context = LocalContext.current
    val tienePermiso = remember(refreshKey) { PhoneData.has(context, Manifest.permission.READ_CONTACTS) }

    var contactos by remember { mutableStateOf<List<ContactEntry>?>(null) }
    LaunchedEffect(refreshKey, tienePermiso) {
        if (tienePermiso) contactos = withContext(Dispatchers.IO) { PhoneData.loadContacts(context) }
    }

    var busqueda by rememberSaveable { mutableStateOf("") }
    var seleccion by remember { mutableStateOf<ContactEntry?>(null) }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Titulo("Contactos", subtitulo = contactos?.let { "${it.size} con número" })

            if (!tienePermiso) {
                PermisoFaltante(
                    Icons.Filled.Contacts,
                    "Tu agenda aparecerá aquí",
                    "Permite leer contactos para verlos y para saber quién es conocido.",
                    pedirPermisos
                )
            } else {
                OutlinedTextField(
                    value = busqueda,
                    onValueChange = { busqueda = it },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = Esp.l, vertical = Esp.xs),
                    label = { Text("Buscar nombre o número") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    trailingIcon = {
                        if (busqueda.isNotEmpty()) {
                            IconButton(onClick = { busqueda = "" }) {
                                Icon(Icons.Filled.Clear, contentDescription = "Borrar búsqueda")
                            }
                        }
                    },
                    singleLine = true,
                    shape = MaterialTheme.shapes.large
                )
                val lista = contactos
                if (lista == null) Cargando() else ListaContactos(lista, busqueda) { seleccion = it }
            }
        }

        if (tienePermiso) {
            ExtendedFloatingActionButton(
                onClick = { Acciones.nuevoContacto(context) },
                icon = { Icon(Icons.Filled.PersonAdd, contentDescription = null) },
                text = { Text("Nuevo") },
                modifier = Modifier.align(Alignment.BottomEnd).padding(Esp.l)
            )
        }
    }

    seleccion?.let { c -> HojaContacto(c, onCerrar = { seleccion = null }) }
}

@Composable
private fun ListaContactos(lista: List<ContactEntry>, busqueda: String, onSeleccion: (ContactEntry) -> Unit) {
    val context = LocalContext.current
    val filtrados = remember(lista, busqueda) { filtrarContactos(lista, busqueda) }
    val favoritos = remember(lista, busqueda) {
        if (busqueda.isBlank()) lista.filter { it.starred } else emptyList()
    }

    if (filtrados.isEmpty()) {
        if (busqueda.isBlank()) {
            EstadoVacio(
                Icons.Filled.Contacts, "Aún no tienes contactos",
                "Guarda a las personas que conoces para que sus llamadas sí suenen.",
                accion = "Crear contacto", onAccion = { Acciones.nuevoContacto(context) }
            )
        } else {
            EstadoVacio(Icons.Filled.SearchOff, "Sin resultados", "No hay contactos que coincidan con \"$busqueda\".")
        }
        return
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 96.dp)) {
        if (favoritos.isNotEmpty()) {
            item(key = "fav_header") { Rotulo("Favoritos") }
            items(favoritos, key = { "fav_${it.id}" }) { c -> FilaContacto(c, onClick = { onSeleccion(c) }) }
            item(key = "all_header") { Rotulo("Todos") }
        }

        var letraAnterior: Char? = null
        filtrados.forEachIndexed { i, c ->
            val letra = simplificar(c.name).firstOrNull()?.uppercaseChar()?.takeIf { it.isLetter() } ?: '#'
            if (busqueda.isBlank() && letra != letraAnterior) {
                item(key = "letra_$i") { Rotulo(letra.toString()) }
                letraAnterior = letra
            }
            item(key = c.id) { FilaContacto(c, onClick = { onSeleccion(c) }) }
        }
    }
}

private fun filtrarContactos(lista: List<ContactEntry>, q: String): List<ContactEntry> {
    if (q.isBlank()) return lista
    val texto = simplificar(q.trim())
    val digitos = q.filter { it.isDigit() }
    return lista.filter { c ->
        simplificar(c.name).contains(texto) ||
            (digitos.length >= 2 && c.numbers.any { it.filter(Char::isDigit).contains(digitos) })
    }
}

@Composable
private fun FilaContacto(c: ContactEntry, onClick: () -> Unit) {
    val context = LocalContext.current
    ListItem(
        modifier = Modifier.clickable(onClickLabel = "Ver opciones", onClick = onClick),
        leadingContent = { Avatar(c.name) },
        headlineContent = {
            Text(c.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        supportingContent = {
            c.numbers.firstOrNull()?.let {
                Text(if (c.numbers.size > 1) "$it  ·  +${c.numbers.size - 1} más" else it)
            }
        },
        trailingContent = {
            c.numbers.firstOrNull()?.let { n ->
                IconButton(onClick = { Acciones.llamar(context, n) }) {
                    Icon(Icons.Filled.Call, contentDescription = "Llamar a ${c.name}", tint = MaterialTheme.colorScheme.primary)
                }
            }
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HojaContacto(c: ContactEntry, onCerrar: () -> Unit) {
    val context = LocalContext.current
    ModalBottomSheet(onDismissRequest = onCerrar, containerColor = MaterialTheme.colorScheme.surfaceContainerLowest) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = Esp.l).padding(bottom = Esp.xl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Esp.s)
        ) {
            Avatar(c.name, 64.dp)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Esp.xs)) {
                Text(c.name, style = MaterialTheme.typography.titleLarge)
                if (c.starred) {
                    Icon(Icons.Filled.Star, contentDescription = "Favorito", tint = MaterialTheme.colorScheme.primary)
                }
            }

            c.numbers.firstOrNull()?.let { principal ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = Esp.m),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    BotonRedondo(Icons.Filled.Call, "Llamar", { Acciones.llamar(context, principal); onCerrar() }, destacado = true)
                    BotonRedondo(Icons.Filled.Sms, "Mensaje", { Acciones.mensaje(context, principal); onCerrar() })
                    BotonRedondo(Icons.Filled.Edit, "Editar", { Acciones.editarContacto(context, c); onCerrar() })
                }
            }

            if (c.numbers.size > 1) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Text("Números", style = MaterialTheme.typography.titleSmall, modifier = Modifier.fillMaxWidth().padding(top = Esp.s))
            }
            if (c.numbers.size > 1) {
                c.numbers.forEach { n ->
                    ListItem(
                        headlineContent = { Text(n) },
                        trailingContent = {
                            Row {
                                IconButton(onClick = { Acciones.mensaje(context, n); onCerrar() }) {
                                    Icon(Icons.Filled.Sms, contentDescription = "Mensaje a $n")
                                }
                                IconButton(onClick = { Acciones.llamar(context, n); onCerrar() }) {
                                    Icon(Icons.Filled.Call, contentDescription = "Llamar a $n", tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                    )
                }
            }

            TextButton(onClick = { Acciones.verContacto(context, c); onCerrar() }, modifier = Modifier.heightIn(min = 48.dp)) {
                Icon(Icons.Outlined.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(Esp.s))
                Text("Abrir en la app Contactos")
            }
        }
    }
}
