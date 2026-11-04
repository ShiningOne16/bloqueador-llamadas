package com.ejemplo.bloqueador

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NoteRulesTest {
    @Test fun keyUsesLastTenDigits() {
        assertEquals(NoteRules.key("+52 55 1234 5678"), NoteRules.key("(55) 1234-5678"))
        assertEquals("", NoteRules.key("sin número"))
    }

    @Test fun labelIsTrimmedCollapsedAndLimited() {
        assertEquals("Cobranza banco", NoteRules.cleanLabel("  Cobranza \n  banco  "))
        assertEquals(NoteRules.MAX_LABEL, NoteRules.cleanLabel("x".repeat(100)).length)
        assertEquals(NoteRules.MAX_NOTE, NoteRules.cleanNote(" " + "y".repeat(500)).length)
    }

    @Test fun titlePrefersNameThenLabelThenNumber() {
        assertEquals("Ana", NoteRules.title("Ana", "Cobranza", "5512345678"))
        assertEquals("Cobranza", NoteRules.title(null, "Cobranza", "5512345678"))
        assertEquals("Cobranza", NoteRules.title("  ", "Cobranza", "5512345678"))
        assertEquals("5512345678", NoteRules.title(null, "", "5512345678"))
        assertEquals("Número oculto", NoteRules.title(null, null, ""))
    }

    @Test fun searchMatchesLabelOrNoteIgnoringAccents() {
        val n = NumberNote("5512345678", "Cobranza Banco", "Llama por la tarjeta de crédito", 0L)
        assertTrue(NoteRules.matches(n, "banco"))
        assertTrue(NoteRules.matches(n, "CREDITO"))
        assertFalse(NoteRules.matches(n, "repartidor"))
        assertFalse(NoteRules.matches(n, ""))
        assertFalse(NoteRules.matches(null, "banco"))
    }
}
