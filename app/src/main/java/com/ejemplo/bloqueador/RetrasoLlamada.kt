package com.ejemplo.bloqueador

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import kotlin.math.ceil

/** Espera corta antes de marcar, para poder cancelar una llamada hecha por error. */
object RetrasoLlamada : DefaultLifecycleObserver {
    const val SEGUNDOS_MIN = 1
    const val SEGUNDOS_MAX = 10
    const val SEGUNDOS_DEFECTO = 2

    /** La duración se fija al pedir la llamada y no cambia mientras está pendiente. */
    var esperaMs = SEGUNDOS_DEFECTO * 1000
        private set

    /** Número que se va a marcar cuando termine la espera; null si no hay ninguna pendiente. */
    var pendiente by mutableStateOf<String?>(null)
        private set

    fun pedir(numero: String, segundos: Int = SEGUNDOS_DEFECTO) {
        if (pendiente == null) {
            esperaMs = segundos.coerceIn(SEGUNDOS_MIN, SEGUNDOS_MAX) * 1000
            pendiente = numero
        }
    }

    fun cancelar() {
        pendiente = null
    }

    // Una llamada requiere mantener la app visible; nunca se recupera al volver a entrar.
    override fun onStop(owner: LifecycleOwner) = cancelar()
    override fun onDestroy(owner: LifecycleOwner) = cancelar()
}

/** Cuenta regresiva configurable con "Cancelar". Va una sola vez en la raíz de la app. */
@Composable
fun AvisoLlamada() {
    val numero = RetrasoLlamada.pendiente ?: return
    val esperaMs = RetrasoLlamada.esperaMs
    val context = LocalContext.current
    val progreso = remember(numero) { Animatable(0f) }

    LaunchedEffect(numero) {
        progreso.animateTo(1f, tween(esperaMs, easing = LinearEasing))
        // El ciclo de vida puede cancelar antes de que Compose retire este efecto.
        if (RetrasoLlamada.pendiente == numero) {
            RetrasoLlamada.cancelar()
            Acciones.llamarYa(context, numero)
        }
    }

    val segundos = ceil((1f - progreso.value) * esperaMs / 1000f).toInt().coerceAtLeast(1)
    AlertDialog(
        onDismissRequest = { RetrasoLlamada.cancelar() },
        title = { Text("Llamando a $numero") },
        text = {
            Column {
                Text("Se marcará en $segundos s. Toca Cancelar si fue un error.")
                Spacer(Modifier.height(16.dp))
                LinearProgressIndicator(progress = { progreso.value }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = { Button(onClick = { RetrasoLlamada.cancelar() }) { Text("Cancelar") } },
        dismissButton = {
            TextButton(onClick = {
                RetrasoLlamada.cancelar()
                Acciones.llamarYa(context, numero)
            }) { Text("Llamar ya") }
        }
    )
}
