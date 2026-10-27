package com.ejemplo.bloqueador

import android.provider.ContactsContract.CommonDataKinds.Phone
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material.icons.filled.SimCard
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Qué abrir en el formulario: contacto nuevo (con número opcional) o uno existente. */
data class PeticionFormulario(val contactId: Long? = null, val numero: String? = null)

private val TIPOS_TELEFONO = listOf(
    Phone.TYPE_MOBILE to "Móvil",
    Phone.TYPE_HOME to "Casa",
    Phone.TYPE_WORK to "Trabajo",
    Phone.TYPE_OTHER to "Otro"
)

private fun nombreTipo(tipo: Int) = TIPOS_TELEFONO.firstOrNull { it.first == tipo }?.second ?: "Otro"

fun iconoCuenta(tipo: TipoCuenta): ImageVector = when (tipo) {
    TipoCuenta.GOOGLE -> Icons.Filled.Cloud
    TipoCuenta.SIM -> Icons.Filled.SimCard
    TipoCuenta.TELEFONO -> Icons.Filled.PhoneAndroid
    TipoCuenta.OTRA -> Icons.Filled.AccountCircle
}

@Composable
fun FormularioContacto(peticion: PeticionFormulario, onCerrar: () -> Unit, onGuardado: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { Prefs(context) }
    val scope = rememberCoroutineScope()
    val editando = peticion.contactId != null

    var cargando by remember { mutableStateOf(true) }
    var existente by remember { mutableStateOf<ContactoEditable?>(null) }
    var cuentas by remember { mutableStateOf<List<CuentaContacto>>(emptyList()) }
    var cuenta by remember { mutableStateOf<CuentaContacto?>(null) }
    var original by remember { mutableStateOf(ContactoForm()) }
    var form by remember { mutableStateOf(ContactoForm()) }

    LaunchedEffect(peticion) {
        // Se lee en segundo plano y se asigna en el hilo principal
        if (peticion.contactId != null) {
            val e = withContext(Dispatchers.IO) { ContactStore.cargar(context, peticion.contactId) }
            existente = e
            cuenta = e?.cuenta
            original = e?.datos ?: ContactoForm()
        } else {
            val lista = withContext(Dispatchers.IO) { ContactStore.cuentasDisponibles(context) }
            cuentas = lista
            cuenta = lista.firstOrNull { it.id == prefs.ultimaCuenta } ?: lista.firstOrNull()
            original = ContactoForm(telefonos = listOf(TelefonoForm(peticion.numero.orEmpty())))
        }
        form = original
        cargando = false
    }

    val esSim = cuenta?.tipo == TipoCuenta.SIM
    var intentoGuardar by remember { mutableStateOf(false) }
    var guardando by remember { mutableStateOf(false) }
    var confirmarSalir by remember { mutableStateOf(false) }

    val errorNombre = intentoGuardar && form.nombre.isBlank() && form.apellidos.isBlank()
    val errorNumero = intentoGuardar && esSim && form.telefonos.none { it.numero.isNotBlank() }
    val hayCambios = form != original

    fun salir() { if (hayCambios) confirmarSalir = true else onCerrar() }
    BackHandler { salir() }

    fun guardar() {
        intentoGuardar = true
        val c = cuenta ?: return
        if (form.nombre.isBlank() && form.apellidos.isBlank()) return
        if (esSim && form.telefonos.none { it.numero.isNotBlank() }) return
        guardando = true
        scope.launch {
            val r = withContext(Dispatchers.IO) {
                val e = existente
                if (e != null) ContactStore.actualizar(context, e, form) else ContactStore.crear(context, c, form)
            }
            guardando = false
            when (r) {
                is ContactStore.Resultado.Ok -> {
                    if (!editando) prefs.ultimaCuenta = c.id
                    val donde = if (c.tipo == TipoCuenta.GOOGLE) "en Google" else if (c.tipo == TipoCuenta.SIM) "en la SIM" else "en el teléfono"
                    Toast.makeText(context, if (editando) "Cambios guardados" else "Contacto guardado $donde", Toast.LENGTH_SHORT).show()
                    onGuardado()
                    onCerrar()
                }
                is ContactStore.Resultado.Error -> Toast.makeText(context, r.mensaje, Toast.LENGTH_LONG).show()
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        // Barra superior: cerrar · título · guardar
        Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = Esp.xs, vertical = Esp.s),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { salir() }) { Icon(Icons.Filled.Close, contentDescription = "Cerrar") }
                Text(
                    if (editando) "Editar contacto" else "Nuevo contacto",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f).padding(start = Esp.xs)
                )
                Button(
                    onClick = { guardar() },
                    enabled = !cargando && !guardando && cuenta != null,
                    modifier = Modifier.padding(end = Esp.s).heightIn(min = 48.dp)
                ) { Text(if (guardando) "Guardando…" else "Guardar") }
            }
        }

        if (cargando) {
            Cargando()
            return@Column
        }
        if (editando && existente == null) {
            EstadoVacio(Icons.Filled.Person, "No se encontró el contacto", "Puede que se haya borrado.", "Volver", onCerrar)
            return@Column
        }

        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding()
                .padding(horizontal = Esp.l, vertical = Esp.l),
            verticalArrangement = Arrangement.spacedBy(Esp.m)
        ) {
            // ---- Dónde se guarda
            if (editando) {
                cuenta?.let { c ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Esp.s)) {
                        Icon(iconoCuenta(c.tipo), contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text(
                            "Guardado en: ${c.titulo}" + if (c.tipo == TipoCuenta.GOOGLE && c.accountName != null) " (${c.accountName})" else "",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                Text("Guardar en", style = MaterialTheme.typography.titleMedium)
                Tarjeta(Modifier.selectableGroup()) {
                    cuentas.forEachIndexed { i, c ->
                        if (i > 0) Separador()
                        OpcionCuenta(c, seleccionada = c.id == cuenta?.id) { cuenta = c }
                    }
                }
            }

            if (esSim) {
                Aviso("La SIM solo guarda nombre y un número. El nombre puede recortarse según tu tarjeta.")
            }

            // ---- Nombre
            Text("Nombre", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = Esp.s))
            OutlinedTextField(
                value = form.nombre,
                onValueChange = { form = form.copy(nombre = it) },
                label = { Text("Nombre") },
                leadingIcon = { Icon(Icons.Filled.Person, contentDescription = null) },
                isError = errorNombre,
                supportingText = if (errorNombre) ({ Text("Escribe un nombre o apellido.") }) else null,
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = form.apellidos,
                onValueChange = { form = form.copy(apellidos = it) },
                label = { Text("Apellidos (opcional)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth()
            )

            // ---- Teléfonos
            Text("Teléfono", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = Esp.s))
            val visibles = if (esSim) form.telefonos.take(1) else form.telefonos
            visibles.forEachIndexed { i, t ->
                FilaTelefono(
                    t,
                    error = errorNumero && i == 0,
                    mostrarTipo = !esSim,
                    puedeQuitar = visibles.size > 1,
                    onCambio = { nuevo -> form = form.copy(telefonos = form.telefonos.toMutableList().also { it[i] = nuevo }) },
                    onQuitar = { form = form.copy(telefonos = form.telefonos.toMutableList().also { it.removeAt(i) }) }
                )
            }
            if (!esSim) {
                TextButton(
                    onClick = { form = form.copy(telefonos = form.telefonos + TelefonoForm("")) },
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(Esp.s))
                    Text("Agregar otro número")
                }

                // ---- Correo y favorito
                OutlinedTextField(
                    value = form.correo,
                    onValueChange = { form = form.copy(correo = it) },
                    label = { Text("Correo (opcional)") },
                    leadingIcon = { Icon(Icons.Filled.Email, contentDescription = null) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Done),
                    modifier = Modifier.fillMaxWidth()
                )
                Tarjeta {
                    FilaInterruptor(
                        Icons.Filled.Star, "Favorito",
                        "Aparece arriba en tu lista de contactos.", form.favorito
                    ) { form = form.copy(favorito = it) }
                }
            }
            Spacer(Modifier.height(Esp.xl))
        }
    }

    if (confirmarSalir) {
        Confirmar(
            titulo = "¿Descartar cambios?",
            texto = "Lo que escribiste no se guardará.",
            accion = "Descartar",
            onConfirmar = { confirmarSalir = false; onCerrar() },
            onCancelar = { confirmarSalir = false }
        )
    }
}

@Composable
private fun OpcionCuenta(c: CuentaContacto, seleccionada: Boolean, onClick: () -> Unit) {
    ListItem(
        modifier = Modifier.selectable(selected = seleccionada, role = Role.RadioButton, onClick = onClick),
        leadingContent = { Icon(iconoCuenta(c.tipo), contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
        headlineContent = { Text(c.titulo, style = MaterialTheme.typography.bodyLarge) },
        supportingContent = { Text(c.detalle) },
        trailingContent = { RadioButton(selected = seleccionada, onClick = null) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )
}

@Composable
private fun FilaTelefono(
    t: TelefonoForm,
    error: Boolean,
    mostrarTipo: Boolean,
    puedeQuitar: Boolean,
    onCambio: (TelefonoForm) -> Unit,
    onQuitar: () -> Unit
) {
    var menu by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Esp.s)) {
        OutlinedTextField(
            value = t.numero,
            onValueChange = { v -> onCambio(t.copy(numero = v.filter { it.isDigit() || it in "+-() #*" })) },
            label = { Text("Número") },
            leadingIcon = { Icon(Icons.Filled.Phone, contentDescription = null) },
            isError = error,
            supportingText = if (error) ({ Text("La SIM necesita un número.") }) else null,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Next),
            modifier = Modifier.weight(1f)
        )
        if (mostrarTipo) {
            Box {
                OutlinedButton(onClick = { menu = true }, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(nombreTipo(t.tipo))
                    Icon(Icons.Filled.ArrowDropDown, contentDescription = "Cambiar tipo de número")
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    TIPOS_TELEFONO.forEach { (tipo, texto) ->
                        DropdownMenuItem(text = { Text(texto) }, onClick = { onCambio(t.copy(tipo = tipo)); menu = false })
                    }
                }
            }
        }
        if (puedeQuitar) {
            IconButton(onClick = onQuitar) {
                Icon(Icons.Filled.RemoveCircleOutline, contentDescription = "Quitar número", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun Aviso(texto: String) {
    Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = MaterialTheme.shapes.medium) {
        Row(
            Modifier.fillMaxWidth().padding(Esp.m),
            horizontalArrangement = Arrangement.spacedBy(Esp.s),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Outlined.Info, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
            Text(texto, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
        }
    }
}
