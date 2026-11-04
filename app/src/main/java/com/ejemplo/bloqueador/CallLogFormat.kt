package com.ejemplo.bloqueador

import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/** Una línea del registro: encabezado de día o grupo de llamadas. */
sealed interface FilaRegistro {
    data class Cabecera(val etiqueta: String, val clave: Long) : FilaRegistro
    data class Llamadas(val grupo: CallGroup) : FilaRegistro
    /** Lugar para un anuncio nativo; [lugar] numera los anuncios de la lista. */
    data class Anuncio(val lugar: Int) : FilaRegistro
}

/** Sin dependencias de Android: se prueba con fechas concretas. */
object DiaRegistro {
    private const val DIA_MS = 86_400_000.0

    private fun inicioDeDia(ts: Long, zona: TimeZone): Long {
        val c = Calendar.getInstance(zona)
        c.timeInMillis = ts
        c.set(Calendar.HOUR_OF_DAY, 0)
        c.set(Calendar.MINUTE, 0)
        c.set(Calendar.SECOND, 0)
        c.set(Calendar.MILLISECOND, 0)
        return c.timeInMillis
    }

    /** "Hoy", "Ayer", el día de la semana (hasta 6 días atrás) o la fecha. */
    fun etiqueta(ts: Long, ahora: Long, locale: Locale = Locale.getDefault(), zona: TimeZone = TimeZone.getDefault()): String {
        val dias = Math.round((inicioDeDia(ahora, zona) - inicioDeDia(ts, zona)) / DIA_MS).toInt()
        return when {
            dias <= 0 -> "Hoy"
            dias == 1 -> "Ayer"
            dias < 7 -> {
                val f = SimpleDateFormat("EEEE", locale)
                f.timeZone = zona
                f.format(ts).replaceFirstChar { it.titlecase(locale) }
            }
            else -> {
                val f = DateFormat.getDateInstance(DateFormat.MEDIUM, locale)
                f.timeZone = zona
                f.format(ts)
            }
        }
    }

    /**
     * Intercala un anuncio después de la llamada número [primero] y luego cada [cada] llamadas.
     * Nunca queda un anuncio al final de la lista ni en listas cortas.
     */
    fun conAnuncios(filas: List<FilaRegistro>, primero: Int = 6, cada: Int = 14): List<FilaRegistro> {
        val total = filas.count { it is FilaRegistro.Llamadas }
        if (total <= primero) return filas
        val resultado = ArrayList<FilaRegistro>(filas.size + total / cada + 1)
        var vistas = 0
        var proximo = primero
        var lugar = 0
        for (f in filas) {
            resultado += f
            if (f is FilaRegistro.Llamadas) {
                vistas++
                if (vistas == proximo && vistas < total) {
                    resultado += FilaRegistro.Anuncio(lugar++)
                    proximo += cada
                }
            }
        }
        return resultado
    }

    /** Inserta un encabezado cada vez que cambia el día. Espera los grupos ya ordenados por fecha. */
    fun agrupar(
        grupos: List<CallGroup>, ahora: Long,
        locale: Locale = Locale.getDefault(), zona: TimeZone = TimeZone.getDefault()
    ): List<FilaRegistro> {
        val filas = ArrayList<FilaRegistro>(grupos.size + 8)
        var ultimoDia = Long.MIN_VALUE
        for (g in grupos) {
            val dia = inicioDeDia(g.latest.date, zona)
            if (dia != ultimoDia) {
                filas += FilaRegistro.Cabecera(etiqueta(g.latest.date, ahora, locale, zona), g.latest.id)
                ultimoDia = dia
            }
            filas += FilaRegistro.Llamadas(g)
        }
        return filas
    }
}
