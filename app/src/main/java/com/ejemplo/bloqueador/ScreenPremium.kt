package com.ejemplo.bloqueador

import android.app.TimePickerDialog
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.time.LocalDateTime
import java.util.UUID
import kotlinx.coroutines.delay

/** Se actualiza al cambiar el minuto, también si se deja abierta la pantalla. */
@Composable
fun currentProtectionTime(): LocalDateTime {
    var time by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            time = LocalDateTime.now()
            delay(60_000L - System.currentTimeMillis() % 60_000L)
        }
    }
    return time
}

@Composable
fun PantallaPremium(onCerrar: () -> Unit, onChanged: () -> Unit, refreshKey: Int) {
    val context = LocalContext.current
    val store = remember { PremiumStore(context) }
    var configuration by remember { mutableStateOf(store.read()) }
    var editing by rememberSaveable { mutableStateOf<ProtectionProfile?>(null) }
    var deleting by rememberSaveable { mutableStateOf<ProtectionProfile?>(null) }
    var viendoOcultos by rememberSaveable { mutableStateOf(false) }
    val now = currentProtectionTime()

    fun save(value: PremiumConfiguration) {
        store.save(value)
        configuration = value
        onChanged()
    }

    val editor = editing
    if (editor != null) {
        ProfileEditor(
            initial = editor,
            refreshKey = refreshKey,
            onClose = { editing = null },
            onSave = { profile ->
                val profiles = configuration.profiles.toMutableList()
                val index = profiles.indexOfFirst { it.id == profile.id }
                if (index < 0) profiles.add(profile) else profiles[index] = profile
                save(configuration.copy(profiles = profiles))
                editing = null
            }
        )
        return
    }
    if (viendoOcultos) {
        PantallaOcultos(onCerrar = { viendoOcultos = false }, onChanged = onChanged)
        return
    }
    BackHandler(onBack = onCerrar)

    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
        PremiumHeader("Premium", onCerrar)
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth().selectableGroup(),
            contentPadding = PaddingValues(horizontal = Esp.l, vertical = Esp.s),
            verticalArrangement = Arrangement.spacedBy(Esp.m)
        ) {
            item { PremiumHero() }
            item {
                Tarjeta {
                    FilaAccion(
                        Icons.Filled.VisibilityOff,
                        "Contactos ocultos",
                        "Bloquea llamadas y mensajes de un contacto y escóndelo de la app. Protegido con un PIN de 4 dígitos."
                    ) { viendoOcultos = true }
                }
            }
            item {
                val profile = configuration.activeProfile
                Text(
                    if (profile == null) "Usando protección básica"
                    else "Perfil seleccionado: ${profile.name} · ${if (profile.schedule.contains(now)) "dentro del horario" else "fuera del horario"}",
                    style = MaterialTheme.typography.titleMedium
                )
                Text("Selecciona un perfil. Fuera de su horario las llamadas y los SMS se permiten, salvo las excepciones de llamadas. Los interruptores de Protección permiten pausar el filtrado.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            item {
                // Lista desplegable: se ve solo el perfil seleccionado y al tocarla aparecen los demás.
                var abierto by remember { mutableStateOf(false) }
                val activo = configuration.activeProfile
                Tarjeta {
                    Box {
                        ListItem(
                            modifier = Modifier.clickable(role = Role.DropdownList, onClickLabel = "Cambiar de perfil") { abierto = true },
                            overlineContent = { Text("Perfil") },
                            headlineContent = { Text(activo?.name ?: "Protección básica", style = MaterialTheme.typography.titleMedium) },
                            supportingContent = {
                                Text(
                                    if (activo == null) "Usar los ajustes de la pestaña Protección" else resumenPerfil(activo),
                                    maxLines = 2, overflow = TextOverflow.Ellipsis
                                )
                            },
                            trailingContent = {
                                Icon(if (abierto) Icons.Filled.ArrowDropUp else Icons.Filled.ArrowDropDown, contentDescription = null)
                            },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                        )
                        DropdownMenu(abierto, onDismissRequest = { abierto = false }, modifier = Modifier.widthIn(min = 280.dp)) {
                            PerfilOpcion("Protección básica", "Usar los ajustes de la pestaña Protección", activo == null) {
                                abierto = false; save(configuration.copy(activeProfileId = null))
                            }
                            configuration.profiles.forEach { profile ->
                                PerfilOpcion(profile.name, resumenPerfil(profile), activo?.id == profile.id) {
                                    abierto = false; save(configuration.copy(activeProfileId = profile.id))
                                }
                            }
                        }
                    }
                    if (activo != null) {
                        HorizontalDivider(Modifier.padding(horizontal = Esp.l), color = MaterialTheme.colorScheme.outlineVariant)
                        Row(Modifier.fillMaxWidth().padding(horizontal = Esp.s), horizontalArrangement = Arrangement.End) {
                            TextButton(onClick = { editing = activo }, modifier = Modifier.heightIn(min = 48.dp)) {
                                Icon(Icons.Filled.Edit, contentDescription = null, Modifier.size(18.dp))
                                Spacer(Modifier.width(Esp.s))
                                Text("Editar")
                            }
                            TextButton(
                                onClick = { deleting = activo },
                                modifier = Modifier.heightIn(min = 48.dp),
                                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                            ) {
                                Icon(Icons.Filled.Delete, contentDescription = null, Modifier.size(18.dp))
                                Spacer(Modifier.width(Esp.s))
                                Text("Eliminar")
                            }
                        }
                    }
                }
            }
            item {
                OutlinedButton(
                    onClick = { editing = ProtectionProfile(UUID.randomUUID().toString(), "Nuevo perfil") },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Spacer(Modifier.width(Esp.s))
                    Text("Crear perfil")
                }
            }
        }
    }
    deleting?.let { profile ->
        Confirmar("¿Eliminar ${profile.name}?",
            if (configuration.activeProfileId == profile.id) "Se eliminarán sus horarios y excepciones. Volverás a la protección básica."
            else "Se eliminarán este perfil, sus horarios y sus excepciones.",
            "Eliminar", onConfirmar = {
                save(configuration.copy(
                    profiles = configuration.profiles.filterNot { it.id == profile.id },
                    activeProfileId = configuration.activeProfileId.takeUnless { it == profile.id }
                ))
                deleting = null
            }, onCancelar = { deleting = null })
    }
}

@Composable
private fun PremiumHeader(title: String, onClose: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(end = Esp.s), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onClose, modifier = Modifier.size(48.dp)) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
        }
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
    }
}

/** Una sola línea de horario y, solo si hay, cuántos contactos y excepciones tiene. */
private fun resumenPerfil(p: ProtectionProfile): String {
    val extras = listOfNotNull(
        p.scheduledContacts.size.takeIf { it > 0 }?.let { if (it == 1) "1 contacto" else "$it contactos" },
        p.exceptions.size.takeIf { it > 0 }?.let { if (it == 1) "1 excepción" else "$it excepciones" }
    )
    return (listOf(scheduleDescription(p.schedule)) + extras).joinToString(" · ")
}

@Composable
private fun ProfileChoice(title: String, description: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().selectable(selected, role = Role.RadioButton, onClick = onClick).padding(Esp.m),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Esp.m)
    ) {
        RadioButton(selected = selected, onClick = null)
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun PerfilOpcion(titulo: String, descripcion: String, seleccionado: Boolean, onClick: () -> Unit) {
    DropdownMenuItem(
        text = {
            Column {
                Text(titulo, style = MaterialTheme.typography.titleSmall)
                Text(descripcion, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        },
        trailingIcon = { if (seleccionado) Icon(Icons.Filled.Check, contentDescription = "Seleccionado", tint = MaterialTheme.colorScheme.primary) },
        onClick = onClick
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProfileEditor(initial: ProtectionProfile, refreshKey: Int, onClose: () -> Unit, onSave: (ProtectionProfile) -> Unit) {
    val context = LocalContext.current
    var draft by rememberSaveable(initial.id) { mutableStateOf(initial) }
    var discard by rememberSaveable { mutableStateOf(false) }
    var addException by rememberSaveable { mutableStateOf(false) }
    var selectContacts by rememberSaveable { mutableStateOf(false) }
    var removing by rememberSaveable { mutableStateOf<NumberException?>(null) }
    val valid = draft.name.isNotBlank() && (!draft.schedule.enabled || draft.schedule.days.isNotEmpty())
    fun close() { if (draft != initial) discard = true else onClose() }
    fun pickTime(start: Boolean) {
        val minute = if (start) draft.schedule.startMinute else draft.schedule.endMinute
        TimePickerDialog(context, { _, hour, min ->
            draft = draft.copy(schedule = if (start) draft.schedule.copy(startMinute = hour * 60 + min)
            else draft.schedule.copy(endMinute = hour * 60 + min))
        }, minute / 60, minute % 60, true).show()
    }
    if (selectContacts) {
        ScheduledContactPicker(
            selected = draft.scheduledContacts, refreshKey = refreshKey,
            onClose = { selectContacts = false },
            onSave = { draft = draft.copy(scheduledContacts = it); selectContacts = false }
        )
        return
    }
    BackHandler { close() }

    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).imePadding()) {
        PremiumHeader("Editar perfil", ::close)
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Esp.l),
            verticalArrangement = Arrangement.spacedBy(Esp.m)
        ) {
            OutlinedTextField(
                draft.name, { draft = draft.copy(name = it.take(40)) },
                label = { Text("Nombre del perfil") }, singleLine = true,
                isError = draft.name.isBlank(), modifier = Modifier.fillMaxWidth()
            )
            Text("Horario de llamadas y SMS", style = MaterialTheme.typography.titleMedium)
            Tarjeta {
                FilaInterruptor(Icons.Filled.Schedule, "Usar horario", "Sin horario, este perfil se aplica todo el día.", draft.schedule.enabled) {
                    draft = draft.copy(schedule = draft.schedule.copy(enabled = it))
                }
                if (draft.schedule.enabled) {
                    Column(Modifier.padding(Esp.m), verticalArrangement = Arrangement.spacedBy(Esp.s)) {
                        Text("Días en que comienza el horario", style = MaterialTheme.typography.bodyMedium)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(Esp.s)) {
                            listOf("Lun", "Mar", "Mié", "Jue", "Vie", "Sáb", "Dom").forEachIndexed { index, day ->
                                val value = index + 1
                                FilterChip(
                                    selected = value in draft.schedule.days,
                                    onClick = {
                                        val days = draft.schedule.days
                                        draft = draft.copy(schedule = draft.schedule.copy(days = if (value in days) days - value else days + value))
                                    }, label = { Text(day) }, modifier = Modifier.heightIn(min = 48.dp)
                                )
                            }
                        }
                        if (draft.schedule.days.isEmpty()) Text("Selecciona al menos un día.", color = MaterialTheme.colorScheme.error)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(Esp.m)) {
                            OutlinedButton(onClick = { pickTime(true) }, modifier = Modifier.heightIn(min = 48.dp)) {
                                Text("Desde ${formatMinute(draft.schedule.startMinute)}")
                            }
                            OutlinedButton(onClick = { pickTime(false) }, modifier = Modifier.heightIn(min = 48.dp)) {
                                Text("Hasta ${formatMinute(draft.schedule.endMinute)}")
                            }
                        }
                        Text(scheduleDescription(draft.schedule), style = MaterialTheme.typography.bodyMedium)
                        Text("Se usa la hora local del teléfono. La misma hora de inicio y fin equivale a 24 horas.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Text("Contactos a bloquear en este horario", style = MaterialTheme.typography.titleMedium)
            Text("Elige los números de tu agenda cuyas llamadas quieres rechazar durante los días y horas de este perfil. Fuera de ese horario podrán llamar. Si desactivas Usar horario, el bloqueo se aplica todo el día. Para bloquear solo estos contactos, desactiva Bloquear desconocidos.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Tarjeta {
                if (draft.scheduledContacts.isEmpty()) Text("Ningún contacto seleccionado", Modifier.padding(Esp.l))
                draft.scheduledContacts.forEach { contact ->
                    val exception = draft.exceptions.firstOrNull { PremiumRules.numberKey(it.number) == PremiumRules.numberKey(contact.number) }
                    ListItem(
                        headlineContent = { Text(contact.name.ifBlank { contact.number }) },
                        supportingContent = { Text(contact.number + when (exception?.action) {
                            NumberAction.ALLOW -> " · Tiene Permitir siempre: no se bloqueará."
                            NumberAction.BLOCK -> " · Tiene Bloquear siempre: también se bloquea fuera del horario."
                            null -> " · Solo en el horario del perfil"
                        }) },
                        trailingContent = { IconButton(onClick = { draft = draft.copy(scheduledContacts = draft.scheduledContacts - contact) }) {
                            Icon(Icons.Filled.Close, contentDescription = "Quitar ${contact.name} del horario")
                        } },
                        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest)
                    )
                }
                TextButton(onClick = { selectContacts = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Icon(Icons.Filled.Contacts, contentDescription = null)
                    Spacer(Modifier.width(Esp.s))
                    Text("Elegir de mi agenda")
                }
            }
            Text("Reglas durante el horario", style = MaterialTheme.typography.titleMedium)
            Tarjeta {
                FilaInterruptor(Icons.Filled.PhoneDisabled, "Bloquear desconocidos", "También puedes bloquear contactos elegidos arriba durante este horario.", draft.blockUnknown) { draft = draft.copy(blockUnknown = it) }
                Separador()
                FilaInterruptor(Icons.Filled.VisibilityOff, "Bloquear números ocultos", "Llamadas privadas o sin número.", draft.blockHidden) { draft = draft.copy(blockHidden = it) }
                Separador()
                FilaInterruptor(Icons.Filled.Repeat, "Dejar pasar si insisten", "Permite otro intento entre 45 segundos y 3 minutos. No anula excepciones de bloqueo.", draft.allowRepeat) { draft = draft.copy(allowRepeat = it) }
                Separador()
                FilaInterruptor(Icons.Filled.NotificationsOff, "Silenciar SMS desconocidos", "Cancela sus notificaciones; los mensajes se conservan.", draft.silenceUnknownSms) { draft = draft.copy(silenceUnknownSms = it) }
                Separador()
                FilaInterruptor(Icons.Filled.Business, "Incluir empresas y códigos cortos", "También puede ocultar avisos de códigos de verificación.", draft.includeBusinessSms, habilitado = draft.silenceUnknownSms) { draft = draft.copy(includeBusinessSms = it) }
            }
            Text("Excepciones de llamadas", style = MaterialTheme.typography.titleMedium)
            Text("Se aplican siempre mientras este perfil esté seleccionado, incluso fuera de su horario. No se aplican a SMS. Pausar las llamadas en Protección desactiva también estas excepciones.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Tarjeta {
                if (draft.exceptions.isEmpty()) Text("Sin excepciones", Modifier.padding(Esp.l))
                draft.exceptions.forEach { exception ->
                    ListItem(
                        headlineContent = { Text(exception.label.ifBlank { exception.number }) },
                        supportingContent = { Text((if (exception.action == NumberAction.ALLOW) "Permitir siempre" else "Bloquear siempre") + " · " + exception.number) },
                        trailingContent = { IconButton(onClick = { removing = exception }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Eliminar excepción ${exception.number}")
                        } },
                        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest)
                    )
                }
                TextButton(onClick = { addException = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Spacer(Modifier.width(Esp.s))
                    Text("Agregar excepción")
                }
            }
            Spacer(Modifier.height(Esp.s))
        }
        Button(
            onClick = { onSave(draft.copy(name = draft.name.trim())) }, enabled = valid,
            modifier = Modifier.fillMaxWidth().padding(Esp.l).heightIn(min = 48.dp)
        ) { Text("Guardar perfil") }
    }
    if (discard) Confirmar("¿Descartar cambios?", "Los cambios de este perfil todavía no se han guardado.", "Descartar",
        onConfirmar = onClose, onCancelar = { discard = false })
    removing?.let { exception ->
        Confirmar("¿Eliminar excepción?", "Se quitará la regla para ${exception.number} al guardar el perfil.", "Eliminar",
            onConfirmar = { draft = draft.copy(exceptions = draft.exceptions - exception); removing = null }, onCancelar = { removing = null })
    }
    if (addException) ExceptionDialog(draft.exceptions, onClose = { addException = false }) {
        draft = draft.copy(exceptions = draft.exceptions + it)
        addException = false
    }
}

@Composable
private fun ExceptionDialog(existing: List<NumberException>, onClose: () -> Unit, onAdd: (NumberException) -> Unit) {
    var number by rememberSaveable { mutableStateOf("") }
    var label by rememberSaveable { mutableStateOf("") }
    var action by rememberSaveable { mutableStateOf(NumberAction.ALLOW) }
    val validNumber = PremiumRules.validNumber(number)
    val duplicate = validNumber && existing.any { PremiumRules.numberKey(it.number) == PremiumRules.numberKey(number) }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Excepción de llamadas") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(Esp.s)) {
                OutlinedTextField(number, { number = it.take(32) }, label = { Text("Número de teléfono") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), singleLine = true,
                    isError = number.isNotBlank() && (!validNumber || duplicate),
                    supportingText = { Text(if (duplicate) "Este número ya tiene una regla; elimínala para cambiarla."
                    else "De 7 a 15 dígitos. Se compara por los últimos 10, como en la agenda.") })
                OutlinedTextField(label, { label = it.take(60) }, label = { Text("Nombre (opcional)") }, singleLine = true)
                Column(Modifier.selectableGroup()) {
                    ProfileChoice("Permitir siempre", "Incluso si no está en tus contactos", action == NumberAction.ALLOW) { action = NumberAction.ALLOW }
                    ProfileChoice("Bloquear siempre", "Incluso si está guardado en contactos", action == NumberAction.BLOCK) { action = NumberAction.BLOCK }
                }
            }
        },
        confirmButton = { TextButton(enabled = validNumber && !duplicate, onClick = { onAdd(NumberException(number.trim(), label.trim(), action)) }) { Text("Agregar") } },
        dismissButton = { TextButton(onClick = onClose) { Text("Cancelar") } }
    )
}

/** Tarjeta de bienvenida: degradado azul noche con la marca como acento. Texto claro (≥ 5.7:1). */
@Composable
private fun PremiumHero() {
    val extra = LocalColoresExtra.current
    val degradado = Brush.linearGradient(listOf(Color(0xFF0F1319), Color(0xFF17407A)))
    val texto = Color(0xFFE7E9EA)
    val suave = Color(0xFFB6C2CF)
    Box(Modifier.fillMaxWidth().clip(MaterialTheme.shapes.extraLarge).background(degradado).padding(Esp.xl)) {
        Column(verticalArrangement = Arrangement.spacedBy(Esp.m)) {
            IconoCirculo(Icons.Filled.WorkspacePremium, fondo = extra.marca, color = extra.onMarca, tamano = 48.dp)
            Text("Más control, a tu manera", style = MaterialTheme.typography.headlineSmall, color = texto)
            Column(verticalArrangement = Arrangement.spacedBy(Esp.s)) {
                listOf("Horarios", "Excepciones", "Perfiles", "Contactos ocultos").forEach { beneficio ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Esp.s)) {
                        Icon(Icons.Filled.Check, contentDescription = null, tint = extra.marca, modifier = Modifier.size(18.dp))
                        Text(beneficio, style = MaterialTheme.typography.bodyLarge, color = texto)
                    }
                }
            }
            Text(
                "Vista previa gratuita. La suscripción mensual todavía no está disponible y no se realizan cobros.",
                style = MaterialTheme.typography.bodyMedium, color = suave
            )
        }
    }
}
