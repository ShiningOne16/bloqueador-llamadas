package com.ejemplo.bloqueador

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

class CallLogFormatTest {
    private val zona = TimeZone.getTimeZone("UTC")
    private val es = Locale("es", "MX")

    private fun ts(y: Int, m: Int, d: Int, h: Int = 12): Long =
        Calendar.getInstance(zona).apply { clear(); set(y, m - 1, d, h, 0, 0) }.timeInMillis

    private val ahora = ts(2024, 5, 15) // miércoles

    private fun etiqueta(t: Long) = DiaRegistro.etiqueta(t, ahora, es, zona)

    @Test fun todayAndYesterday() {
        assertEquals("Hoy", etiqueta(ts(2024, 5, 15, 0)))
        assertEquals("Hoy", etiqueta(ts(2024, 5, 15, 23)))
        assertEquals("Ayer", etiqueta(ts(2024, 5, 14, 23)))
    }

    @Test fun lastWeekUsesCapitalizedWeekday() {
        assertEquals("Domingo", etiqueta(ts(2024, 5, 12)))
        assertEquals("Jueves", etiqueta(ts(2024, 5, 9)))
    }

    @Test fun olderThanAWeekUsesDate() {
        val e = etiqueta(ts(2024, 5, 8))
        assertNotEquals("Hoy", e)
        assertTrue(e.any { it.isDigit() })
    }

    @Test fun futureDatesCountAsToday() {
        assertEquals("Hoy", etiqueta(ts(2024, 5, 16)))
    }

    @Test fun headerIsInsertedOnlyWhenTheDayChanges() {
        fun g(id: Long, t: Long) = CallGroup(CallEntry(id, "5512345678", null, 1, t, 0L), 1)
        val filas = DiaRegistro.agrupar(
            listOf(g(3, ts(2024, 5, 15, 10)), g(2, ts(2024, 5, 15, 8)), g(1, ts(2024, 5, 14, 20))),
            ahora, es, zona
        )
        assertEquals(5, filas.size)
        assertEquals("Hoy", (filas[0] as FilaRegistro.Cabecera).etiqueta)
        assertEquals("Ayer", (filas[3] as FilaRegistro.Cabecera).etiqueta)
        assertTrue(filas[1] is FilaRegistro.Llamadas && filas[2] is FilaRegistro.Llamadas)
    }

    @Test fun emptyListHasNoHeaders() {
        assertTrue(DiaRegistro.agrupar(emptyList(), ahora, es, zona).isEmpty())
    }
}
