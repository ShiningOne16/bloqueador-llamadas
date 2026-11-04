package com.ejemplo.bloqueador

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TemporalRulesTest {
    private val minuto = 60_000L

    @Test fun ruleIsActiveUntilTheExactEndTime() {
        assertTrue(TemporalRules.activo(1_000, 999))
        assertFalse(TemporalRules.activo(1_000, 1_000))
        assertFalse(TemporalRules.activo(0, 5))
    }

    @Test fun endTimeAddsTheMinutes() {
        assertEquals(10_000L + 120 * minuto, TemporalRules.hastaEn(10_000L, 120))
    }

    @Test fun remainingTimeIsRoundedUpAndReadable() {
        assertEquals("2 h", TemporalRules.restante(120 * minuto, 0))
        assertEquals("1 h 5 min", TemporalRules.restante(65 * minuto, 0))
        assertEquals("12 min", TemporalRules.restante(12 * minuto, 0))
        assertEquals("1 min", TemporalRules.restante(1, 0))
        assertEquals("menos de 1 min", TemporalRules.restante(0, 5_000))
    }

    @Test fun durationLabels() {
        assertEquals("30 min", TemporalRules.duracion(30))
        assertEquals("1 h", TemporalRules.duracion(60))
        assertEquals("4 h", TemporalRules.duracion(240))
    }

    @Test fun offeredDurationsAreSortedAndPositive() {
        assertEquals(TemporalRules.DURACIONES_CONCENTRACION.sorted(), TemporalRules.DURACIONES_CONCENTRACION)
        assertEquals(TemporalRules.DURACIONES_PERMISO.sorted(), TemporalRules.DURACIONES_PERMISO)
        assertTrue((TemporalRules.DURACIONES_CONCENTRACION + TemporalRules.DURACIONES_PERMISO).all { it > 0 })
    }

    @Test fun permitForAllUnknownHasNoNumber() {
        assertTrue(PermisoTemporal("", "", 1L).paraDesconocidos)
        assertFalse(PermisoTemporal("5512345678", "Repartidor", 1L).paraDesconocidos)
    }
}
