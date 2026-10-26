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
            prefs.addBlocked(getString(R.string.hidden_number), now)
            return true
        }

        // Sin permiso de contactos no sabemos quién es conocido: no bloqueamos (la app avisa en pantalla)
        if (!Contacts.hasPermission(this) || Contacts.isNumberInContacts(this, number)) return false

        if (prefs.allowRepeat && prefs.recentlyBlocked(number, now, REPEAT_WINDOW_MS)) {
            Log.i(TAG, "Segundo intento de $number: se deja pasar")
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
        const val REPEAT_WINDOW_MS = 3 * 60 * 1000L // 3 minutos
    }
}
