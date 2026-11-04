package com.ejemplo.bloqueador

import android.app.TimePickerDialog
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
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
fun PantallaPremium(onCerrar: () -> Unit, onChanged: () -> Unit) {
    val context = LocalContext.current
    val store = remember { PremiumStore(context) }
    var configuration by remember { mutableStateOf(store.read()) }
    var editing by rememberSaveable { mutableStateOf<ProtectionProfile?>(null) }
    var deleting by rememberSaveable { mutableStateOf<ProtectionProfile?>(null) }
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
    BackHandler(onBack = onCerrar)

    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
        PremiumHeader("Premium", onCerrar)
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth().selectableGroup(),
            contentPadding = PaddingValues(horizontal = Esp.l, vertical = Esp.s),
            verticalArrangement = Arrangement.spacedBy(Esp.m)
        ) {
            item {
                Tarjeta {
                    Column(Modifier.padding(Esp.l), verticalArrangement = Arrangement.spacedBy(Esp.s)) {
                        Icon(Icons.Filled.WorkspacePremium, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text("Más control, a tu manera", style = MaterialTheme.typography.headlineSmall)
                        Text("Horarios · Excepciones · Perfiles", style = MaterialTheme.typography.titleMedium)
                        Text("Vista previa gratuita. La suscripción mensual todavía no está disponible y no se realizan cobros.",
                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
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
                ProfileChoice("Protección básica", "Usar los ajustes de la pestaña Protección",
                    configuration.activeProfileId == null) { save(configuration.copy(activeProfileId = null)) }
            }
            items(configuration.profiles, key = { it.id }) { profile ->
                Tarjeta {
                    ProfileChoice(profile.name, scheduleDescription(profile.schedule) + "\n${profile.exceptions.size} excepciones de llamadas",
                        configuration.activeProfileId == profile.id) { save(configuration.copy(activeProfileId = profile.id)) }
                    Row(Modifier.fillMaxWidth().padding(horizontal = Esp.s), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { editing = profile }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Editar") }
                        IconButton(onClick = { deleting = profile }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Eliminar perfil ${profile.name}")
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProfileEditor(initial: ProtectionProfile, onClose: () -> Unit, onSave: (ProtectionProfile) -> Unit) {
    val context = LocalContext.current
    var draft by rememberSaveable(initial.id) { mutableStateOf(initial) }
    var discard by rememberSaveable { mutableStateOf(false) }
    var addException by rememberSaveable { mutableStateOf(false) }
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
            Text("Reglas durante el horario", style = MaterialTheme.typography.titleMedium)
            Tarjeta {
                FilaInterruptor(Icons.Filled.PhoneDisabled, "Bloquear desconocidos", "Los contactos siguen entrando salvo una excepción de bloqueo.", draft.blockUnknown) { draft = draft.copy(blockUnknown = it) }
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
