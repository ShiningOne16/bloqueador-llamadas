package com.ejemplo.bloqueador

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class BlockedCall(val number: String, val timestamp: Long)

data class SilencedSms(val sender: String, val text: String, val timestamp: Long)

/** Ajustes y registros (llamadas bloqueadas y SMS silenciados), guardados en SharedPreferences. */
class Prefs(context: Context) {

    private val appContext = context.applicationContext
    private val sp = context.getSharedPreferences("bloqueador", Context.MODE_PRIVATE)

    /** Ya se mostró la pantalla de bienvenida. */
    var bienvenidaVista: Boolean
        get() = sp.getBoolean(KEY_BIENVENIDA, false)
        set(v) = sp.edit().putBoolean(KEY_BIENVENIDA, v).apply()

    /** Última cuenta elegida al guardar un contacto (Google, SIM, teléfono). */
    var ultimaCuenta: String?
        get() = sp.getString(KEY_ULTIMA_CUENTA, null)
        set(v) = sp.edit().putString(KEY_ULTIMA_CUENTA, v).apply()

    // ---------- Llamadas ----------

    var enabled: Boolean
        get() = sp.getBoolean(KEY_ENABLED, true)
        set(v) = sp.edit().putBoolean(KEY_ENABLED, v).apply()

    fun manuallyBlocked(number: String): Boolean = normalize(number).let {
        it.isNotEmpty() && it in sp.getStringSet("manual_blocks", emptySet()).orEmpty()
    }
    fun manualBlockCount(): Int = sp.getStringSet("manual_blocks", emptySet()).orEmpty().size
    fun manualBlockedNumbers(): List<String> = sp.getStringSet("manual_blocks", emptySet()).orEmpty().sorted()

    fun setManualBlock(number: String, blocked: Boolean) {
        val key = normalize(number)
        require(key.length >= 7) { "No se puede bloquear un número oculto o incompleto" }
        val current = sp.getStringSet("manual_blocks", emptySet()).orEmpty().toSet()
        sp.edit().putStringSet("manual_blocks", if (blocked) current + key else current - key).apply()
    }

    /** Bloquear llamadas con número oculto / privado / desconocido. */
    var blockHidden: Boolean
        get() = sp.getBoolean(KEY_BLOCK_HIDDEN, true)
        set(v) = sp.edit().putBoolean(KEY_BLOCK_HIDDEN, v).apply()

    /**
     * Si el mismo número vuelve a llamar dentro de la ventana de tiempo, se le deja pasar (posible urgencia).
     * Apagado por defecto: se bloquea siempre.
     */
    var allowRepeat: Boolean
        get() = sp.getBoolean(KEY_ALLOW_REPEAT, false)
        set(v) = sp.edit().putBoolean(KEY_ALLOW_REPEAT, v).apply()

    /** Función Premium: espera 2 segundos (con "Cancelar") antes de marcar. Apagado por defecto. */
    var delayCall: Boolean
        get() = sp.getBoolean(KEY_DELAY_CALL, false)
        set(v) = sp.edit().putBoolean(KEY_DELAY_CALL, v).apply()

    /** Los contactos ocultos no dejan rastro: sus registros anteriores tampoco se guardan ni se muestran. */
    private fun storedCalls(): List<BlockedCall> {
        val all = readArray(KEY_LOG).map { BlockedCall(it.getString("n"), it.getLong("t")) }
        val hidden = HiddenStore(appContext).hiddenKeys()
        return if (hidden.isEmpty()) all else all.filterNot { HiddenRules.isHiddenNumber(hidden, it.number) }
    }

    /** Lo que se muestra: además esconde los contactos de una sesión con NIP de entrada incorrecto. */
    fun blockedCalls(): List<BlockedCall> {
        val all = storedCalls()
        val extra = SesionEntrada.aOcultar()
        if (extra.isEmpty()) return all
        val keys = HiddenRules.keys(extra)
        return all.filterNot { HiddenRules.isHiddenNumber(keys, it.number) }
    }

    @Synchronized
    fun addBlocked(number: String, timestamp: Long) {
        val list = listOf(BlockedCall(number, timestamp)) + storedCalls()
        writeArray(KEY_LOG, list.take(MAX_LOG).map { JSONObject().put("n", it.number).put("t", it.timestamp) })
    }

    fun clearLog() = sp.edit().remove(KEY_LOG).apply()

    /** Cuándo se bloqueó por última vez este número (null si nunca). */
    fun lastBlockedAt(number: String): Long? {
        val key = normalize(number)
        return storedCalls().filter { normalize(it.number) == key || it.number == number }
            .maxOfOrNull { it.timestamp }
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

    private fun storedSms(): List<SilencedSms> {
        val all = readArray(KEY_SMS_LOG).map { SilencedSms(it.getString("s"), it.optString("m"), it.getLong("t")) }
        val hidden = HiddenStore(appContext).contacts()
        return if (hidden.isEmpty()) all else all.filterNot { HiddenRules.matchesSender(hidden, it.sender) }
    }

    /** Lo que se muestra: además esconde los contactos de una sesión con NIP de entrada incorrecto. */
    fun silencedSms(): List<SilencedSms> {
        val all = storedSms()
        val extra = SesionEntrada.aOcultar()
        return if (extra.isEmpty()) all else all.filterNot { HiddenRules.matchesSender(extra, it.sender) }
    }

    @Synchronized
    fun addSilencedSms(sender: String, text: String, timestamp: Long) {
        val current = storedSms()
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
        private const val KEY_ULTIMA_CUENTA = "ultima_cuenta"
        private const val KEY_ENABLED = "enabled"
        private const val KEY_BLOCK_HIDDEN = "block_hidden"
        private const val KEY_ALLOW_REPEAT = "allow_repeat"
        private const val KEY_DELAY_CALL = "delay_call"
        private const val KEY_LOG = "log"
        private const val KEY_SMS_ENABLED = "sms_enabled"
        private const val KEY_SMS_BUSINESS = "sms_business"
        private const val KEY_SMS_LOG = "sms_log"
        private const val MAX_LOG = 200

        /** Compara por los últimos 10 dígitos para ignorar prefijos como +52. */
        fun normalize(number: String): String = number.filter { it.isDigit() }.takeLast(10)
    }
}

