package com.ejemplo.bloqueador

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HiddenContactsTest {
    // Pocas iteraciones: la prueba comprueba la lógica, no la dureza del hash.
    private val iterations = 1_000

    @Test fun pinMustBeExactlyFourDigits() {
        assertTrue(HiddenRules.validPin("0123"))
        assertFalse(HiddenRules.validPin("123"))
        assertFalse(HiddenRules.validPin("12345"))
        assertFalse(HiddenRules.validPin("12a4"))
        assertFalse(HiddenRules.validPin(""))
    }

    @Test fun correctPinVerifiesAndWrongPinDoesNot() {
        val salt = HiddenRules.newSalt()
        val hash = HiddenRules.hashPin("4821", salt, iterations)
        assertTrue(HiddenRules.verifyPin("4821", salt, hash, iterations))
        assertFalse(HiddenRules.verifyPin("4822", salt, hash, iterations))
        assertFalse(HiddenRules.verifyPin("482", salt, hash, iterations))
    }

    @Test fun samePinWithDifferentSaltGivesDifferentHash() {
        val a = HiddenRules.hashPin("1111", HiddenRules.newSalt(), iterations)
        val b = HiddenRules.hashPin("1111", HiddenRules.newSalt(), iterations)
        assertFalse(a.contentEquals(b))
    }

    @Test fun firstFailuresDoNotLockButFifthDoes() {
        var lock = PinLock()
        repeat(4) { lock = HiddenRules.afterFailure(lock, 1_000L) }
        assertEquals(4, lock.failures)
        assertEquals(0L, HiddenRules.lockedSeconds(lock, 1_000L))
        assertEquals(1, HiddenRules.attemptsLeft(lock))

        lock = HiddenRules.afterFailure(lock, 1_000L)
        assertEquals(30L, HiddenRules.lockedSeconds(lock, 1_000L))
        assertEquals(0, HiddenRules.attemptsLeft(lock))
    }

    @Test fun lockGrowsAndIsCapped() {
        var lock = PinLock()
        repeat(5) { lock = HiddenRules.afterFailure(lock, 0L) }
        lock = HiddenRules.afterFailure(lock, 0L)
        assertEquals(60L, HiddenRules.lockedSeconds(lock, 0L))
        repeat(20) { lock = HiddenRules.afterFailure(lock, 0L) }
        assertEquals(15 * 60L, HiddenRules.lockedSeconds(lock, 0L))
    }

    @Test fun lockExpires() {
        var lock = PinLock()
        repeat(5) { lock = HiddenRules.afterFailure(lock, 0L) }
        assertEquals(1L, HiddenRules.lockedSeconds(lock, 29_001L))
        assertEquals(0L, HiddenRules.lockedSeconds(lock, 30_000L))
    }

    @Test fun numbersMatchByLastTenDigits() {
        val keys = HiddenRules.keys(listOf(HiddenContact("Ana", listOf("+52 55 1234 5678"))))
        assertTrue(HiddenRules.isHiddenNumber(keys, "5512345678"))
        assertTrue(HiddenRules.isHiddenNumber(keys, "(55) 1234-5678"))
        assertFalse(HiddenRules.isHiddenNumber(keys, "5512345679"))
        assertFalse(HiddenRules.isHiddenNumber(keys, ""))
    }

    @Test fun hiddenCallerWithoutNumberIsNeverMatched() {
        val keys = HiddenRules.keys(listOf(HiddenContact("Sin número", emptyList())))
        assertTrue(keys.isEmpty())
        assertFalse(HiddenRules.isHiddenNumber(keys, ""))
    }

    @Test fun smsSenderMatchesByNumberOrContactName() {
        val hidden = listOf(HiddenContact("Ana López", listOf("5512345678")))
        assertTrue(HiddenRules.matchesSender(hidden, "+52 55 1234 5678"))
        assertTrue(HiddenRules.matchesSender(hidden, "ana lópez"))
        assertFalse(HiddenRules.matchesSender(hidden, "Ana"))
        assertFalse(HiddenRules.matchesSender(hidden, "5598765432"))
        assertFalse(HiddenRules.matchesSender(hidden, "  "))
    }

    @Test fun agendaEntryIsHiddenByIdOrByNumber() {
        val hidden = listOf(HiddenContact("Ana", listOf("5512345678"), contactId = 7L, lookupKey = "k"))
        val keys = HiddenRules.keys(hidden)
        assertTrue(HiddenRules.isHiddenEntry(hidden, keys, 7L, listOf("5500000000")))
        assertTrue(HiddenRules.isHiddenEntry(hidden, keys, 99L, listOf("+52 55 1234 5678")))
        assertFalse(HiddenRules.isHiddenEntry(hidden, keys, 99L, listOf("5500000000")))
    }

    @Test fun unknownContactIdDoesNotHideEverything() {
        val hidden = listOf(HiddenContact("Ana", listOf("5512345678"), contactId = -1L))
        val keys = HiddenRules.keys(hidden)
        assertFalse(HiddenRules.isHiddenEntry(hidden, keys, -1L, listOf("5500000000")))
    }
}
