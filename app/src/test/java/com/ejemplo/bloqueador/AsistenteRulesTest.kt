package com.ejemplo.bloqueador

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AsistenteRulesTest {
    @Test fun messageWithoutNameFitsOneGsmSms() {
        val m = AsistenteRules.mensaje("")
        assertTrue(m.length <= 160)
        assertTrue(m.all { it.code < 128 })
        assertTrue(m.contains("de esta linea"))
    }

    @Test fun messageUsesTheNameAndCleansIt() {
        assertTrue(AsistenteRules.mensaje("  Ana   López ").contains("asistente de llamadas de Ana López."))
        assertEquals(AsistenteRules.MAX_NOMBRE, AsistenteRules.limpiarNombre("x".repeat(100)).length)
        assertTrue(AsistenteRules.mensaje("x".repeat(100)).length < 200)
    }

    @Test fun rateLimitIsFivePerHour() {
        assertTrue(AsistenteRules.puedeEnviar(4))
        assertFalse(AsistenteRules.puedeEnviar(5))
    }

    @Test fun windowsStartAtTheSendTimeAndNeverMatchTheFuture() {
        val ahora = 1_000_000_000L
        assertTrue(AsistenteRules.enFuerza(ahora - 1_000, ahora, AsistenteRules.ENFRIAMIENTO_MS))
        assertFalse(AsistenteRules.enFuerza(ahora - AsistenteRules.ENFRIAMIENTO_MS, ahora, AsistenteRules.ENFRIAMIENTO_MS))
        assertFalse(AsistenteRules.enFuerza(0L, ahora, AsistenteRules.ENFRIAMIENTO_MS))
        assertFalse(AsistenteRules.enFuerza(ahora + 5_000, ahora, AsistenteRules.ENFRIAMIENTO_MS))
    }

    @Test fun deliveryAndAppointmentsLookLegitimate() {
        assertEquals(ClaseRespuesta.LEGITIMA, AsistenteRules.clasificar("Soy el repartidor, tengo un paquete para usted"))
        assertEquals(ClaseRespuesta.LEGITIMA, AsistenteRules.clasificar("Consultorio del Dr. Pérez, su cita es mañana"))
        assertEquals(ClaseRespuesta.LEGITIMA, AsistenteRules.clasificar("ENTREGA de Mercado Libre"))
    }

    @Test fun promotionsAndLoansLookLikeSpam() {
        assertEquals(ClaseRespuesta.SPAM, AsistenteRules.clasificar("Felicidades, ganaste un premio. Entra a http://x.co"))
        assertEquals(ClaseRespuesta.SPAM, AsistenteRules.clasificar("Tenemos un préstamo pre-aprobado para ti"))
    }

    @Test fun mixedOrUnknownTextIsUncertain() {
        assertEquals(ClaseRespuesta.INCIERTA, AsistenteRules.clasificar("Hola, soy Luis"))
        assertEquals(ClaseRespuesta.INCIERTA, AsistenteRules.clasificar("Tu paquete ganó un descuento"))
        assertEquals(ClaseRespuesta.INCIERTA, AsistenteRules.clasificar(""))
    }
}
