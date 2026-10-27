package com.ejemplo.bloqueador

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class BlockedCall(val number: String, val timestamp: Long)

data class SilencedSms(val sender: String, val text: String, val timestamp: Long)

/** Ajustes y registros (llamadas bloqueadas y SMS silenciados), guardados en SharedPreferences. */
class Prefs(context: Context) {

    private val sp = context.getSharedPreferences("bloqueador", Context.MODE_PRIVATE)

    /** Ya se mostró la pantalla de bienvenida. */
    var bienvenidaVista: Boolean
        get() = sp.getBoolean(KEY_BIENVENIDA, false)
        set(v) = sp.edit().putBoolean(KEY_BIENVENIDA, v).apply()

    // ---------- Llamadas ----------

    var enabled: Boolean
        get() = sp.getBoolean(KEY_ENABLED, true)
        set(v) = sp.edit().putBoolean(KEY_ENABLED, v).apply()

    /** Bloquear llamadas con número oculto / privado / desconocido. */
    var blockHidden: Boolean
        get() = sp.getBoolean(KEY_BLOCK_HIDDEN, true)
        set(v) = sp.edit().putBoolean(KEY_BLOCK_HIDDEN, v).apply()

    /** Si el mismo número insiste dentro de la ventana de tiempo, se le deja pasar (posible urgencia). */
    var allowRepeat: Boolean
        get() = sp.getBoolean(KEY_ALLOW_REPEAT, true)
        set(v) = sp.edit().putBoolean(KEY_ALLOW_REPEAT, v).apply()

    fun blockedCalls(): List<BlockedCall> = readArray(KEY_LOG).map {
        BlockedCall(it.getString("n"), it.getLong("t"))
    }

    @Synchronized
    fun addBlocked(number: String, timestamp: Long) {
        val list = listOf(BlockedCall(number, timestamp)) + blockedCalls()
        writeArray(KEY_LOG, list.take(MAX_LOG).map { JSONObject().put("n", it.number).put("t", it.timestamp) })
    }

    fun clearLog() = sp.edit().remove(KEY_LOG).apply()

    /**
     * Devuelve true si este número ya fue bloqueado hace menos de [windowMs].
     * Se usa para dejar pasar el segundo intento seguido.
     */
    fun recentlyBlocked(number: String, now: Long, windowMs: Long): Boolean {
        val key = normalize(number)
        return blockedCalls().any { normalize(it.number) == key && now - it.timestamp <= windowMs }
    }

    // ---------- Mensajes (SMS) ----------

    /** Silenciar notificaciones de SMS de números que no están en contactos. */
    var smsEnabled: Boolean
        get() = sp.getBoolean(KEY_SMS_ENABLED, true)
        set(v) = sp.edit().putBoolean(KEY_SMS_ENABLED, v).apply()

    /**
     * También silenciar remitentes con nombre de empresa (BANCO, TELCEL…) y códigos cortos (2525…).
     * Apagado por defecto para no esconder códigos de verificación.
     */
    var smsIncludeBusiness: Boolean
        get() = sp.getBoolean(KEY_SMS_BUSINESS, false)
        set(v) = sp.edit().putBoolean(KEY_SMS_BUSINESS, v).apply()

    fun silencedSms(): List<SilencedSms> = readArray(KEY_SMS_LOG).map {
        SilencedSms(it.getString("s"), it.optString("m"), it.getLong("t"))
    }

    @Synchronized
    fun addSilencedSms(sender: String, text: String, timestamp: Long) {
        val current = silencedSms()
        // Las apps de SMS actualizan la misma notificación varias veces: evitamos duplicados
        if (current.any { it.sender == sender && it.text == text && timestamp - it.timestamp < 60_000 }) return
        val list = listOf(SilencedSms(sender, text, timestamp)) + current
        writeArray(KEY_SMS_LOG, list.take(MAX_LOG).map {
            JSONObject().put("s", it.sender).put("m", it.text).put("t", it.timestamp)
        })
    }

    fun clearSmsLog() = sp.edit().remove(KEY_SMS_LOG).apply()

    // ---------- utilidades ----------

    private fun readArray(key: String): List<JSONObject> {
        val arr = try { JSONArray(sp.getString(key, "[]")) } catch (e: Exception) { JSONArray() }
        return (0 until arr.length()).map { arr.getJSONObject(it) }
    }

    private fun writeArray(key: String, items: List<JSONObject>) {
        val arr = JSONArray()
        items.forEach { arr.put(it) }
        sp.edit().putString(key, arr.toString()).apply()
    }

    companion object {
        private const val KEY_BIENVENIDA = "bienvenida_vista"
        private const val KEY_ENABLED = "enabled"
        private const val KEY_BLOCK_HIDDEN = "block_hidden"
        private const val KEY_ALLOW_REPEAT = "allow_repeat"
        private const val KEY_LOG = "log"
        private const val KEY_SMS_ENABLED = "sms_enabled"
        private const val KEY_SMS_BUSINESS = "sms_business"
        private const val KEY_SMS_LOG = "sms_log"
        private const val MAX_LOG = 200

        /** Compara por los últimos 10 dígitos para ignorar prefijos como +52. */
        fun normalize(number: String): String = number.filter { it.isDigit() }.takeLast(10)
    }
}
