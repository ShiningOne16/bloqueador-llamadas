package com.ejemplo.bloqueador

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class BlockedCall(val number: String, val timestamp: Long)

/** Ajustes y registro de llamadas bloqueadas, guardados en SharedPreferences. */
class Prefs(context: Context) {

    private val sp = context.getSharedPreferences("bloqueador", Context.MODE_PRIVATE)

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

    fun blockedCalls(): List<BlockedCall> {
        val arr = JSONArray(sp.getString(KEY_LOG, "[]"))
        return (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            BlockedCall(o.getString("n"), o.getLong("t"))
        }
    }

    @Synchronized
    fun addBlocked(number: String, timestamp: Long) {
        val list = listOf(BlockedCall(number, timestamp)) + blockedCalls()
        val arr = JSONArray()
        list.take(MAX_LOG).forEach { arr.put(JSONObject().put("n", it.number).put("t", it.timestamp)) }
        sp.edit().putString(KEY_LOG, arr.toString()).apply()
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

    companion object {
        private const val KEY_ENABLED = "enabled"
        private const val KEY_BLOCK_HIDDEN = "block_hidden"
        private const val KEY_ALLOW_REPEAT = "allow_repeat"
        private const val KEY_LOG = "log"
        private const val MAX_LOG = 200

        /** Compara por los últimos 10 dígitos para ignorar prefijos como +52. */
        fun normalize(number: String): String = number.filter { it.isDigit() }.takeLast(10)
    }
}
