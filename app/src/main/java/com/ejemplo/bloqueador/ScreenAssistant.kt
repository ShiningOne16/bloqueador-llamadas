package com.ejemplo.bloqueador

import android.Manifest
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext

/** Premium: el asistente contesta por SMS a los desconocidos y avisa cuando responden. */
@Composable
fun PantallaAsistente(onCerrar: () -> Unit) {
    val context = LocalContext.current
    val store = remember { AsistenteStore(context) }
    var activo by remember { mutableStateOf(store.activo) }
    var nombre by rememberSaveable { mutableStateOf(store.nombre) }
    var tienePermiso by remember { mutableStateOf(PhoneData.has(context, Manifest.permission.SEND_SMS)) }
    val tieneContactos = remember { Contacts.hasPermission(context) }
    val pedir = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        tienePermiso = ok
        if (ok) { store.activo = true; activo = true }
    }
    BackHandler(onBack = onCerrar)

    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
        PremiumHeader("Asistente de llamadas", onCerrar)
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = Esp.l, vertical = Esp.s),
            verticalArrangement = Arrangement.spacedBy(Esp.m)
        ) {
            Tarjeta {
                FilaInterruptor(
                    Icons.Filled.Sms, "Activar asistente",
                    "Cuando llama alguien que no está en tus contactos, la llamada se silencia y se le manda un SMS pidiéndole que se identifique.",
                    activo
                ) { quiere ->
                    if (quiere && !tienePermiso) pedir.launch(Manifest.permission.SEND_SMS)
                    else { store.activo = quiere; activo = quiere }
                }
            }
            if (!tienePermiso) Text(
                "Falta el permiso para enviar SMS. Al activar el asistente Android te lo pedirá.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error
            )
            if (!tieneContactos) Text(
                "Sin permiso de contactos no se sabe quién es desconocido, así que el asistente no atenderá llamadas. Concédelo desde Protección.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error
            )

            OutlinedTextField(
                value = nombre,
                onValueChange = { nombre = it.take(AsistenteRules.MAX_NOMBRE); store.nombre = nombre },
                label = { Text("Tu nombre (opcional)") },
                singleLine = true,
                supportingText = { Text("Aparece en el mensaje: \"soy el asistente de …\"") },
                modifier = Modifier.fillMaxWidth()
            )

            Text("Mensaje que se envía", style = MaterialTheme.typography.titleSmall)
            Tarjeta {
                Text(AsistenteRules.mensaje(nombre), Modifier.padding(Esp.l), style = MaterialTheme.typography.bodyLarge)
            }

            Text("Cómo funciona", style = MaterialTheme.typography.titleSmall)
            Text(
                "• La llamada sigue sonando en silencio mientras quien llama espera; si cuelga, queda en tu registro.\n" +
                    "• Si responde, te llega una notificación con su mensaje y si parece paquetería, cita o publicidad. Desde ahí puedes Responder, Ignorar o Bloquear.\n" +
                    "• Si no responde, probablemente era spam: los números falsos no reciben SMS.\n" +
                    "• A cada número se le escribe una vez al día. Si vuelve a llamar, la llamada suena normal.\n" +
                    "• Máximo 5 mensajes por hora. Los números bloqueados, los contactos ocultos y los permitidos por tus perfiles no se atienden.\n" +
                    "• Cada SMS puede costar según tu plan.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
