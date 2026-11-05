package com.ejemplo.bloqueador

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpamRulesTest {
    @Test fun hashMatchesTheServer() {
        // Mismo valor que cloudflare/test/worker.test.js
        assertEquals("3a1ec8160591", SpamRules.huella("5512345678"))
    }

    @Test fun hashIgnoresFormattingAndCountryCode() {
        val h = SpamRules.huella("5512345678")
        assertEquals(h, SpamRules.huella("+52 55 1234 5678"))
        assertEquals(h, SpamRules.huella("(55) 1234-5678"))
        assertEquals(12, h.length)
    }

    @Test fun differentNumbersHaveDifferentHashes() {
        assertTrue(SpamRules.huella("5512345678") != SpamRules.huella("5512345679"))
    }

    @Test fun emptyOrNonNumericGivesNoHash() {
        assertEquals("", SpamRules.huella(""))
        assertEquals("", SpamRules.huella("Sin número"))
    }

    @Test fun onlyRealPhoneNumbersAreReportable() {
        assertTrue(SpamRules.reportable("+52 55 1234 5678"))
        assertFalse(SpamRules.reportable("123"))
        assertFalse(SpamRules.reportable("BANCO"))
    }

    @Test fun categoryOrderMatchesTheServerIndexes() {
        assertEquals(CategoriaSpam.OTRO, CategoriaSpam.deIndice(0))
        assertEquals(CategoriaSpam.COBRANZA, CategoriaSpam.deIndice(1))
        assertEquals(CategoriaSpam.VENTAS, CategoriaSpam.deIndice(2))
        assertEquals(CategoriaSpam.ESTAFA, CategoriaSpam.deIndice(3))
        assertEquals(CategoriaSpam.ENCUESTA, CategoriaSpam.deIndice(4))
        assertEquals(CategoriaSpam.OTRO, CategoriaSpam.deIndice(99))
    }
}
