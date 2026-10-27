package com.ejemplo.bloqueador

import android.Manifest
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.SimCard
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Filtros por origen, en el orden en que se muestran. */
private val ORIGENES = listOf(null, TipoCuenta.GOOGLE, TipoCuenta.SIM, TipoCuenta.TELEFONO)

private fun nombreOrigen(t: TipoCuenta?) = when (t) {
    null -> "Todos"
    TipoCuenta.GOOGLE -> "Google"
    TipoCuenta.SIM -> "SIM"
    TipoCuenta.TELEFONO -> "Teléfono"
    TipoCuenta.OTRA -> "Otras"
}

@Composable
fun PantallaContactos(refreshKey: Int, pedirPermisos: () -> Unit) {
    val context = LocalContext.current
    val abrirFormulario = LocalAbrirFormulario.current
    val tienePermiso = remember(refreshKey) { PhoneData.has(context, Manifest.permission.READ_CONTACTS) }

    var recarga by remember { mutableIntStateOf(0) }
    var contactos by remember { mutableStateOf<List<ContactEntry>?>(null) }
    LaunchedEffect(refreshKey, tienePermiso, recarga) {
        if (tienePermiso) contactos = withContext(Dispatchers.IO) { PhoneData.loadContacts(context) }
    }

    var busqueda by rememberSaveable { mutableStateOf("") }
    var origen by rememberSaveable { mutableIntStateOf(0) }
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
                // Solo se muestran los filtros de origen que tienen contactos
                val origenesPresentes = remember(lista) {
                    val presentes = lista.orEmpty().flatMap { it.cuentas }.toSet()
                    ORIGENES.filter { it == null || it in presentes }
                }
                if (origenesPresentes.size > 2) {
                    Spacer(Modifier.height(Esp.xs))
                    Filtros(origenesPresentes.map { nombreOrigen(it) }, origen.coerceAtMost(origenesPresentes.lastIndex)) {
                        origen = it
                    }
                }
                val filtroOrigen = origenesPresentes.getOrNull(origen)

                if (lista == null) {
                    Cargando()
                } else {
                    ListaContactos(
                        lista.filter { filtroOrigen == null || filtroOrigen in it.cuentas },
                        busqueda,
                        onNuevo = { abrirFormulario(PeticionFormulario()) }
                    ) { seleccion = it }
                }
            }
        }

        if (tienePermiso) {
            ExtendedFloatingActionButton(
                onClick = { abrirFormulario(PeticionFormulario()) },
                icon = { Icon(Icons.Filled.PersonAdd, contentDescription = null) },
                text = { Text("Nuevo") },
                modifier = Modifier.align(Alignment.BottomEnd).padding(Esp.l)
            )
        }
    }

    seleccion?.let { c ->
        HojaContacto(
            c,
            onCerrar = { seleccion = null },
            onEliminado = { seleccion = null; recarga++ }
        )
    }
}

@Composable
private fun ListaContactos(
    lista: List<ContactEntry>,
    busqueda: String,
    onNuevo: () -> Unit,
    onSeleccion: (ContactEntry) -> Unit
) {
    val filtrados = remember(lista, busqueda) { filtrarContactos(lista, busqueda) }
    val favoritos = remember(lista, busqueda) {
        if (busqueda.isBlank()) lista.filter { it.starred } else emptyList()
    }

    if (filtrados.isEmpty()) {
        if (busqueda.isBlank()) {
            EstadoVacio(
                Icons.Filled.Contacts, "No hay contactos aquí",
                "Guarda a las personas que conoces para que sus llamadas sí suenen.",
                accion = "Crear contacto", onAccion = onNuevo
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
    val soloSim = c.cuentas == setOf(TipoCuenta.SIM)
    ListItem(
        modifier = Modifier.clickable(onClickLabel = "Ver opciones", onClick = onClick),
        leadingContent = { Avatar(c.name) },
        headlineContent = {
            Text(c.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        supportingContent = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Esp.xs)) {
                if (soloSim) {
                    Icon(Icons.Filled.SimCard, contentDescription = "En la SIM", modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                c.numbers.firstOrNull()?.let {
                    Text(if (c.numbers.size > 1) "$it  ·  +${c.numbers.size - 1} más" else it)
                }
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
private fun HojaContacto(c: ContactEntry, onCerrar: () -> Unit, onEliminado: () -> Unit) {
    val context = LocalContext.current
    val abrirFormulario = LocalAbrirFormulario.current
    val scope = rememberCoroutineScope()
    var confirmarBorrar by remember { mutableStateOf(false) }

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
            if (c.cuentas.isNotEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Esp.xs)) {
                    Icon(iconoCuenta(c.cuentas.first()), contentDescription = null, modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        "Guardado en: " + c.cuentas.sortedBy { it.ordinal }.joinToString(" y ") { nombreOrigen(it) },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Row(
                Modifier.fillMaxWidth().padding(vertical = Esp.m),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                c.numbers.firstOrNull()?.let { principal ->
                    BotonRedondo(Icons.Filled.Call, "Llamar", { Acciones.llamar(context, principal); onCerrar() }, destacado = true)
                    BotonRedondo(Icons.Filled.Sms, "Mensaje", { Acciones.mensaje(context, principal); onCerrar() })
                }
                BotonRedondo(Icons.Filled.Edit, "Editar", { onCerrar(); abrirFormulario(PeticionFormulario(contactId = c.id)) })
            }

            if (c.numbers.size > 1) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Text("Números", style = MaterialTheme.typography.titleSmall, modifier = Modifier.fillMaxWidth().padding(top = Esp.s))
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

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = { Acciones.verContacto(context, c); onCerrar() }, modifier = Modifier.heightIn(min = 48.dp)) {
                    Icon(Icons.Outlined.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(Esp.s))
                    Text("Abrir en Contactos")
                }
                TextButton(
                    onClick = { confirmarBorrar = true },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(Esp.s))
                    Text("Eliminar")
                }
            }
        }
    }

    if (confirmarBorrar) {
        val enGoogle = TipoCuenta.GOOGLE in c.cuentas
        Confirmar(
            titulo = "¿Eliminar a ${c.name}?",
            texto = if (enGoogle) "Se borrará de este teléfono y de tu cuenta de Google en todos tus dispositivos."
            else "Se borrará de este teléfono. No se puede deshacer.",
            accion = "Eliminar",
            onConfirmar = {
                confirmarBorrar = false
                scope.launch {
                    val r = withContext(Dispatchers.IO) { ContactStore.eliminar(context, c) }
                    when (r) {
                        is ContactStore.Resultado.Ok -> {
                            Toast.makeText(context, "Contacto eliminado", Toast.LENGTH_SHORT).show()
                            onEliminado()
                        }
                        is ContactStore.Resultado.Error -> Toast.makeText(context, r.mensaje, Toast.LENGTH_LONG).show()
                    }
                }
            },
            onCancelar = { confirmarBorrar = false }
        )
    }
}
