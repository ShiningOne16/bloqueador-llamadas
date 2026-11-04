package com.ejemplo.bloqueador

import android.telecom.Call
import android.telecom.CallScreeningService
import android.telecom.TelecomManager
import android.util.Log
import kotlin.concurrent.thread
import java.time.LocalDateTime

/**
 * Android llama a este servicio antes de que suene cada llamada entrante.
 * Decide si la llamada pasa o se rechaza en silencio.
 */
/** Qué hacer con una llamada entrante. */
private enum class Veredicto { PASAR, BLOQUEAR, ASISTIR }

class BlockerService : CallScreeningService() {

    override fun onScreenCall(details: Call.Details) {
        // Solo filtramos llamadas entrantes
        if (details.callDirection != Call.Details.DIRECTION_INCOMING) {
            respondToCall(details, allow())
            return
        }

        // La consulta de contactos se hace fuera del hilo principal.
        // Android da ~5 s para responder; esto tarda milisegundos.
        thread {
            val response = try {
                if (isHiddenCaller(details)) blockSilently()
                else when (decidir(details)) {
                    Veredicto.BLOQUEAR -> block()
                    Veredicto.ASISTIR -> silence()
                    Veredicto.PASAR -> allow()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error al filtrar, se deja pasar la llamada", e)
                allow()
            }
            respondToCall(details, response)
        }
    }

    private fun decidir(details: Call.Details): Veredicto {
        val prefs = Prefs(this)
        if (!prefs.enabled) return Veredicto.PASAR

        val number = details.handle?.schemeSpecificPart?.trim().orEmpty()
        val now = System.currentTimeMillis()
        val hidden = number.isEmpty() ||
            details.handlePresentation != TelecomManager.PRESENTATION_ALLOWED

        if (!hidden && prefs.manuallyBlocked(number)) {
            val last = prefs.lastBlockedAt(number)
            if (last == null || now - last >= RETRY_WINDOW_MS) prefs.addBlocked(number, now)
            return Veredicto.BLOQUEAR
        }

        val profile = PremiumStore(this).read().activeProfile
        when (PremiumRules.callDecision(profile, number, hidden, LocalDateTime.now())) {
            CallDecision.ALLOW -> return Veredicto.PASAR
            CallDecision.BLOCK -> {
                val label = if (hidden) getString(R.string.hidden_number) else number
                val last = prefs.lastBlockedAt(label)
                if (last == null || now - last >= RETRY_WINDOW_MS) prefs.addBlocked(label, now)
                return Veredicto.BLOQUEAR
            }
            CallDecision.DEFAULT -> Unit
        }

        if (hidden) {
            if (!prefs.blockHidden) return Veredicto.PASAR
            val etiqueta = getString(R.string.hidden_number)
            val last = prefs.lastBlockedAt(etiqueta)
            if (last == null || now - last >= RETRY_WINDOW_MS) prefs.addBlocked(etiqueta, now)
            return Veredicto.BLOQUEAR
        }

        // Sin permiso de contactos no sabemos quién es conocido: no bloqueamos (la app avisa en pantalla)
        if (!Contacts.hasPermission(this) || Contacts.isNumberInContacts(this, number)) return Veredicto.PASAR

        // Asistente (Premium): a un desconocido que ya recibió el SMS se le deja sonar si insiste;
        // a uno nuevo se le silencia y se le pide que se identifique, en lugar de rechazarlo.
        if (Asistente.yaAtendido(this, number)) return Veredicto.PASAR
        if (profile?.schedule?.contains(LocalDateTime.now()) != false && Asistente.atender(this, number)) return Veredicto.ASISTIR

        val last = prefs.lastBlockedAt(number)

        // Al rechazar una llamada, algunas compañías (o el paso de VoLTE a la red normal) la vuelven
        // a mandar a los pocos segundos. Eso es la MISMA llamada: se bloquea otra vez sin contarla doble.
        if (last != null && now - last < RETRY_WINDOW_MS) {
            Log.i(TAG, "Reintento de la red para $number: se bloquea de nuevo")
            return Veredicto.BLOQUEAR
        }

        // "Dejar pasar si insisten": solo cuenta si la persona colgó y volvió a marcar después.
        if ((profile?.allowRepeat ?: prefs.allowRepeat) && last != null && now - last <= REPEAT_WINDOW_MS) {
            Log.i(TAG, "$number volvió a llamar: se deja pasar")
            return Veredicto.PASAR
        }

        prefs.addBlocked(number, now)
        return Veredicto.BLOQUEAR
    }

    /**
     * Contactos ocultos (Premium): siempre se rechazan, incluso con el filtro pausado, y no dejan
     * registro ni en Bloqueador ni en el historial del teléfono ni en notificaciones.
     */
    private fun isHiddenCaller(details: Call.Details): Boolean {
        val number = details.handle?.schemeSpecificPart?.trim().orEmpty()
        return number.isNotEmpty() && HiddenStore(this).isHidden(number)
    }

    private fun blockSilently() = CallResponse.Builder()
        .setDisallowCall(true)
        .setRejectCall(true)
        .setSkipCallLog(true)
        .setSkipNotification(true)
        .build()

    private fun allow() = CallResponse.Builder().build()

    /** La llamada sigue activa pero sin sonar ni vibrar, mientras el asistente espera la respuesta. */
    private fun silence() = CallResponse.Builder()
        .setSilenceCall(true)
        .setSkipNotification(false)
        .build()

    private fun block() = CallResponse.Builder()
        .setDisallowCall(true)   // no se conecta
        .setRejectCall(true)     // se cuelga como si rechazaras
        .setSkipCallLog(false)   // sí aparece en el historial del teléfono
        .setSkipNotification(true) // sin notificación de llamada perdida
        .build()

    companion object {
        private const val TAG = "BlockerService"
        /** Reintentos automáticos de la red: llegan en segundos. */
        const val RETRY_WINDOW_MS = 45 * 1000L
        /** Ventana de "dejar pasar si insisten". */
        const val REPEAT_WINDOW_MS = 3 * 60 * 1000L // 3 minutos
    }
}

