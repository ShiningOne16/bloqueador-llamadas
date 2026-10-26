package com.ejemplo.bloqueador

import android.Manifest
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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

    Column(Modifier.fillMaxSize()) {
        Titulo("Contactos") {
            IconButton(onClick = { Acciones.nuevoContacto(context) }) {
                Icon(Icons.Default.Add, contentDescription = "Nuevo contacto")
            }
        }

        if (!tienePermiso) {
            PermisoFaltante("Permite leer contactos para ver tu agenda.", pedirPermisos)
        } else {
            OutlinedTextField(
                value = busqueda,
                onValueChange = { busqueda = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                placeholder = { Text("Buscar nombre o número") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (busqueda.isNotEmpty()) {
                        IconButton(onClick = { busqueda = "" }) { Icon(Icons.Default.Clear, contentDescription = "Borrar") }
                    }
                },
                singleLine = true
            )
            val lista = contactos
            if (lista == null) Cargando() else ListaContactos(lista, busqueda) { seleccion = it }
        }
    }

    seleccion?.let { c -> DetalleContacto(c, onCerrar = { seleccion = null }) }
}

@Composable
private fun ListaContactos(lista: List<ContactEntry>, busqueda: String, onSeleccion: (ContactEntry) -> Unit) {
    val filtrados = remember(lista, busqueda) { filtrarContactos(lista, busqueda) }
    val favoritos = remember(lista, busqueda) {
        if (busqueda.isBlank()) lista.filter { it.starred } else emptyList()
    }

    if (filtrados.isEmpty()) {
        TextoVacio(if (busqueda.isBlank()) "No tienes contactos con número." else "Sin resultados.")
        return
    }

    LazyColumn(Modifier.fillMaxSize()) {
        if (favoritos.isNotEmpty()) {
            item(key = "fav_header") { EncabezadoSeccion("★ Favoritos") }
            items(favoritos, key = { "fav_${it.id}" }) { c -> FilaContacto(c, onClick = { onSeleccion(c) }) }
            item(key = "all_header") { EncabezadoSeccion("Todos (${filtrados.size})") }
        }

        var letraAnterior: Char? = null
        filtrados.forEachIndexed { i, c ->
            val letra = simplificar(c.name).firstOrNull()?.uppercaseChar()?.takeIf { it.isLetter() } ?: '#'
            if (busqueda.isBlank() && letra != letraAnterior) {
                item(key = "letra_${i}") { EncabezadoSeccion(letra.toString()) }
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
private fun EncabezadoSeccion(texto: String) {
    Text(
        texto,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp)
    )
}

@Composable
private fun FilaContacto(c: ContactEntry, onClick: () -> Unit) {
    val context = LocalContext.current
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        leadingContent = { Avatar(c.name) },
        headlineContent = { Text(c.name) },
        supportingContent = {
            c.numbers.firstOrNull()?.let {
                Text(if (c.numbers.size > 1) "$it  (+${c.numbers.size - 1})" else it)
            }
        },
        trailingContent = {
            c.numbers.firstOrNull()?.let { n ->
                IconButton(onClick = { Acciones.llamar(context, n) }) {
                    Icon(Icons.Default.Call, contentDescription = "Llamar a ${c.name}")
                }
            }
        }
    )
}

@Composable
private fun DetalleContacto(c: ContactEntry, onCerrar: () -> Unit) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onCerrar,
        icon = { Avatar(c.name, 56.dp) },
        title = {
            Row {
                Text(c.name, modifier = Modifier.weight(1f, fill = false))
                if (c.starred) Icon(Icons.Default.Star, contentDescription = "Favorito", Modifier.padding(start = 6.dp))
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                c.numbers.forEach { n ->
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Text(n, modifier = Modifier.weight(1f))
                        TextButton(onClick = { Acciones.mensaje(context, n); onCerrar() }) { Text("SMS") }
                        FilledIconButton(onClick = { Acciones.llamar(context, n); onCerrar() }) {
                            Icon(Icons.Default.Call, contentDescription = "Llamar")
                        }
                    }
                }
                HorizontalDivider()
                OutlinedButton(onClick = { Acciones.editarContacto(context, c); onCerrar() }, Modifier.fillMaxWidth()) {
                    Text("Editar contacto")
                }
                OutlinedButton(onClick = { Acciones.verContacto(context, c); onCerrar() }, Modifier.fillMaxWidth()) {
                    Text("Ver en Contactos")
                }
            }
        },
        confirmButton = { TextButton(onClick = onCerrar) { Text("Cerrar") } }
    )
}
