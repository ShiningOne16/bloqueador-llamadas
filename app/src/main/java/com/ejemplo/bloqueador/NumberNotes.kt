package com.ejemplo.bloqueador

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Etiqueta corta y nota libre que el usuario guarda para un número ("Cobranza banco"). */
data class NumberNote(val number: String, val label: String, val note: String, val updated: Long)

/** Sin dependencias de Android: se prueba con valores concretos. */
object NoteRules {
    const val MAX_LABEL = 30
    const val MAX_NOTE = 200

    /** Los números se comparan por sus últimos 10 dígitos, igual que en el resto de la app. */
    fun key(number: String) = PremiumRules.numberKey(number)

    fun cleanLabel(text: String) = text.trim().replace(Regex("\\s+"), " ").take(MAX_LABEL)

    fun cleanNote(text: String) = text.trim().take(MAX_NOTE)

    /** Nombre del contacto, si no la etiqueta, si no el número. */
    fun title(name: String?, label: String?, number: String): String =
        name?.takeIf { it.isNotBlank() } ?: label?.takeIf { it.isNotBlank() } ?: number.ifBlank { "Número oculto" }

    /** Coincide por etiqueta o nota (sin importar acentos ni mayúsculas). */
    fun matches(note: NumberNote?, query: String): Boolean {
        if (note == null || query.isBlank()) return false
        val q = simplificar(query.trim())
        return simplificar(note.label).contains(q) || simplificar(note.note).contains(q)
    }
}

/** Guarda las notas en el teléfono (SharedPreferences privadas de la app). */
class NoteStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("number_notes", Context.MODE_PRIVATE)

    private fun read(): MutableMap<String, NumberNote> {
        val raw = prefs.getString("data", null) ?: return mutableMapOf()
        return try {
            val array = JSONArray(raw)
            val map = LinkedHashMap<String, NumberNote>()
            for (i in 0 until array.length()) {
                val o = array.getJSONObject(i)
                val n = NumberNote(o.optString("number"), o.optString("label"), o.optString("note"), o.optLong("updated"))
                val key = NoteRules.key(n.number)
                if (key.isNotEmpty()) map[key] = n
            }
            map
        } catch (_: Exception) {
            mutableMapOf()
        }
    }

    private fun write(map: Map<String, NumberNote>) {
        val array = JSONArray()
        map.values.forEach {
            array.put(JSONObject().put("number", it.number).put("label", it.label).put("note", it.note).put("updated", it.updated))
        }
        prefs.edit().putString("data", array.toString()).commit()
    }

    /** Notas por clave de número, para consultar muchas filas de una sola vez. */
    fun map(): Map<String, NumberNote> = read()

    fun all(): List<NumberNote> = read().values.sortedByDescending { it.updated }

    fun get(number: String): NumberNote? = read()[NoteRules.key(number)]

    /** Guarda la nota; si etiqueta y nota quedan vacías, la borra. Devuelve false si el número no sirve. */
    fun set(number: String, label: String, note: String): Boolean {
        val key = NoteRules.key(number)
        if (key.isEmpty()) return false
        val l = NoteRules.cleanLabel(label)
        val n = NoteRules.cleanNote(note)
        val map = read()
        if (l.isEmpty() && n.isEmpty()) map.remove(key) else map[key] = NumberNote(number.trim(), l, n, System.currentTimeMillis())
        write(map)
        return true
    }

    fun remove(number: String) {
        val map = read()
        if (map.remove(NoteRules.key(number)) != null) write(map)
    }

    fun removeKeys(keys: Set<String>) {
        val map = read()
        if (keys.any { map.remove(it) != null }) write(map)
    }
}
