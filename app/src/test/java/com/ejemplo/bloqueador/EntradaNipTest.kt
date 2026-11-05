package com.ejemplo.bloqueador

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EntradaNipTest {
    private val ana = HiddenContact("Ana", listOf("+52 55 1234 5678"), contactId = 7L)
    private val luis = HiddenContact("Luis", listOf("(33) 9876-5432"), contactId = 9L)

    @Test fun noRelockWithoutBackgroundTime() {
        assertFalse(EntradaRules.debeBloquear(0L, 1_000_000L))
    }

    @Test fun relocksOnlyAfterTheWaitHasPassed() {
        val desde = 10_000L
        assertFalse(EntradaRules.debeBloquear(desde, desde + EntradaRules.RELOCK_MS - 1))
        assertTrue(EntradaRules.debeBloquear(desde, desde + EntradaRules.RELOCK_MS))
        assertTrue(EntradaRules.debeBloquear(desde, desde + 10 * 60_000L))
    }

    @Test fun relocksIfTheClockGoesBackwards() {
        assertTrue(EntradaRules.debeBloquear(50_000L, 40_000L))
    }

    @Test fun correctPinAlwaysEntersNormally() {
        assertEquals(ModoEntrada.NORMAL, EntradaRules.modoTrasNip(correcto = true, hayContactosAOcultar = true))
        assertEquals(ModoEntrada.NORMAL, EntradaRules.modoTrasNip(correcto = true, hayContactosAOcultar = false))
    }

    @Test fun wrongPinEntersInFalseModeWhenThereAreContactsToHide() {
        assertEquals(ModoEntrada.FALSO, EntradaRules.modoTrasNip(correcto = false, hayContactosAOcultar = true))
    }

    @Test fun wrongPinIsRejectedWhenThereIsNothingToHide() {
        assertNull(EntradaRules.modoTrasNip(correcto = false, hayContactosAOcultar = false))
    }

    @Test fun keysAddTheListToTheAlreadyHiddenOnes() {
        val keys = EntradaRules.claves(setOf("5500000000"), listOf(ana, luis))
        assertTrue(HiddenRules.isHiddenNumber(keys, "5500000000"))
        assertTrue(HiddenRules.isHiddenNumber(keys, "5512345678"))
        assertTrue(HiddenRules.isHiddenNumber(keys, "+52 33 9876 5432"))
        assertFalse(HiddenRules.isHiddenNumber(keys, "5511111111"))
    }

    @Test fun keysWithAnEmptyListAreJustTheHiddenOnes() {
        assertEquals(setOf("5500000000"), EntradaRules.claves(setOf("5500000000"), emptyList()))
    }

    @Test fun contactsKeepBothListsSoSenderNamesAreMatchedToo() {
        val todos = EntradaRules.contactos(listOf(luis), listOf(ana))
        assertEquals(2, todos.size)
        assertTrue(HiddenRules.matchesSender(todos, "ana"))
        assertTrue(HiddenRules.matchesSender(todos, "(33) 9876 5432"))
        assertFalse(HiddenRules.matchesSender(todos, "Pedro"))
    }

    @Test fun addressBookEntryIsHiddenByIdOrByNumber() {
        val lista = listOf(ana)
        val keys = HiddenRules.keys(lista)
        assertTrue(HiddenRules.isHiddenEntry(lista, keys, 7L, listOf("5500000000")))
        assertTrue(HiddenRules.isHiddenEntry(lista, keys, 99L, listOf("5512345678")))
        assertFalse(HiddenRules.isHiddenEntry(lista, keys, 99L, listOf("5500000000")))
    }
}
