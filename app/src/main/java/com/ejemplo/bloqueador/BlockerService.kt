package com.ejemplo.bloqueador

import android.telecom.Call
import android.telecom.CallScreeningService
import android.telecom.TelecomManager
import android.util.Log
import kotlin.concurrent.thread

/**
 * Android llama a este servicio antes de que suene cada llamada entrante.
 * Decide si la llamada pasa o se rechaza en silencio.
 */
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
                if (shouldBlock(details)) block() else allow()
            } catch (e: Exception) {
                Log.e(TAG, "Error al filtrar, se deja pasar la llamada", e)
                allow()
            }
            respondToCall(details, response)
        }
    }

    private fun shouldBlock(details: Call.Details): Boolean {
        val prefs = Prefs(this)
        if (!prefs.enabled) return false

        val number = details.handle?.schemeSpecificPart?.trim().orEmpty()
        val now = System.currentTimeMillis()
        val hidden = number.isEmpty() ||
            details.handlePresentation != TelecomManager.PRESENTATION_ALLOWED

        if (hidden) {
            if (!prefs.blockHidden) return false
            val etiqueta = getString(R.string.hidden_number)
            val last = prefs.lastBlockedAt(etiqueta)
            if (last == null || now - last >= RETRY_WINDOW_MS) prefs.addBlocked(etiqueta, now)
            return true
        }

        // Sin permiso de contactos no sabemos quién es conocido: no bloqueamos (la app avisa en pantalla)
        if (!Contacts.hasPermission(this) || Contacts.isNumberInContacts(this, number)) return false

        val last = prefs.lastBlockedAt(number)

        // Al rechazar una llamada, algunas compañías (o el paso de VoLTE a la red normal) la vuelven
        // a mandar a los pocos segundos. Eso es la MISMA llamada: se bloquea otra vez sin contarla doble.
        if (last != null && now - last < RETRY_WINDOW_MS) {
            Log.i(TAG, "Reintento de la red para $number: se bloquea de nuevo")
            return true
        }

        // "Dejar pasar si insisten": solo cuenta si la persona colgó y volvió a marcar después.
        if (prefs.allowRepeat && last != null && now - last <= REPEAT_WINDOW_MS) {
            Log.i(TAG, "$number volvió a llamar: se deja pasar")
            return false
        }

        prefs.addBlocked(number, now)
        return true
    }

    private fun allow() = CallResponse.Builder().build()

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
