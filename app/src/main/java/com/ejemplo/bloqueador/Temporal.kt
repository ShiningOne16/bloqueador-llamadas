package com.ejemplo.bloqueador

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Sin dependencias de Android: se prueba con valores concretos. */
object TemporalRules {
    val DURACIONES_CONCENTRACION = listOf(60, 120, 240)
    val DURACIONES_PERMISO = listOf(30, 60, 120, 240)
    private const val MINUTO_MS = 60_000L

    fun activo(hasta: Long, ahora: Long) = hasta > ahora

    fun hastaEn(ahora: Long, minutos: Int) = ahora + minutos * MINUTO_MS

    /** "2 h", "1 h 5 min", "12 min" o "menos de 1 min". */
    fun restante(hasta: Long, ahora: Long): String {
        val minutos = ((hasta - ahora).coerceAtLeast(0L) + MINUTO_MS - 1) / MINUTO_MS
        return when {
            minutos <= 0 -> "menos de 1 min"
            minutos < 60 -> "$minutos min"
            minutos % 60 == 0L -> "${minutos / 60} h"
            else -> "${minutos / 60} h ${minutos % 60} min"
        }
    }

    fun duracion(minutos: Int) = if (minutos % 60 == 0) "${minutos / 60} h" else "$minutos min"
}

/** Permiso temporal: un número concreto, o todos los desconocidos si [numero] está vacío. */
data class PermisoTemporal(val numero: String, val etiqueta: String, val hasta: Long) {
    val paraDesconocidos get() = numero.isEmpty()
}

/**
 * Modo concentración (rechaza todas las llamadas hasta una hora) y permisos temporales (dejan pasar
 * a un número, o a los desconocidos, durante un rato). Todo vence solo por la hora; no hay nada que apagar.
 */
class TemporalStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("temporal_rules", Context.MODE_PRIVATE)

    val concentracionHasta: Long get() = prefs.getLong("focus_until", 0L)

    fun concentracionActiva(ahora: Long) = TemporalRules.activo(concentracionHasta, ahora)

    fun iniciarConcentracion(minutos: Int, ahora: Long) {
        prefs.edit().putLong("focus_until", TemporalRules.hastaEn(ahora, minutos)).commit()
    }

    fun terminarConcentracion() {
        prefs.edit().remove("focus_until").commit()
    }

    private fun leer(): List<PermisoTemporal> {
        val raw = prefs.getString("permits", null) ?: return emptyList()
        return try {
            val a = JSONArray(raw)
            (0 until a.length()).map {
                val o = a.getJSONObject(it)
                PermisoTemporal(o.optString("number"), o.optString("label"), o.optLong("until"))
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun escribir(lista: List<PermisoTemporal>) {
        val a = JSONArray()
        lista.forEach { a.put(JSONObject().put("number", it.numero).put("label", it.etiqueta).put("until", it.hasta)) }
        prefs.edit().putString("permits", a.toString()).commit()
    }

    /** Solo los vigentes, los que vencen antes primero. */
    fun permisos(ahora: Long): List<PermisoTemporal> =
        leer().filter { TemporalRules.activo(it.hasta, ahora) }.sortedBy { it.hasta }

    @Synchronized
    fun permitirNumero(numero: String, etiqueta: String, minutos: Int, ahora: Long) {
        val clave = PremiumRules.numberKey(numero)
        if (clave.isEmpty()) return
        val resto = permisos(ahora).filterNot { !it.paraDesconocidos && PremiumRules.numberKey(it.numero) == clave }
        escribir(resto + PermisoTemporal(numero.trim(), etiqueta.trim().take(30), TemporalRules.hastaEn(ahora, minutos)))
    }

    @Synchronized
    fun permitirDesconocidos(minutos: Int, ahora: Long) {
        val resto = permisos(ahora).filterNot { it.paraDesconocidos }
        escribir(resto + PermisoTemporal("", "", TemporalRules.hastaEn(ahora, minutos)))
    }

    @Synchronized
    fun quitar(permiso: PermisoTemporal) {
        escribir(leer().filterNot { it == permiso })
    }

    fun permiteNumero(numero: String, ahora: Long): Boolean {
        val clave = PremiumRules.numberKey(numero)
        return clave.isNotEmpty() && permisos(ahora).any { !it.paraDesconocidos && PremiumRules.numberKey(it.numero) == clave }
    }

    fun permiteDesconocidos(ahora: Long): Boolean = permisos(ahora).any { it.paraDesconocidos }
}
