package com.ejemplo.bloqueador

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDateTime
import java.io.*

class PremiumRulesTest {
    private fun at(value: String) = LocalDateTime.parse(value)
    private val phone = "5512345678"

    @Test fun daytimeIncludesStartAndExcludesEnd() {
        val schedule = ProtectionSchedule(true, setOf(1), 9 * 60, 18 * 60)
        assertFalse(schedule.contains(at("2026-11-02T08:59:59")))
        assertTrue(schedule.contains(at("2026-11-02T09:00:00")))
        assertTrue(schedule.contains(at("2026-11-02T17:59:59")))
        assertFalse(schedule.contains(at("2026-11-02T18:00:00")))
        assertFalse(schedule.contains(at("2026-11-03T10:00:00")))
    }

    @Test fun overnightUsesDayOnWhichShiftStarts() {
        val schedule = ProtectionSchedule(true, setOf(1), 22 * 60, 7 * 60)
        assertFalse(schedule.contains(at("2026-11-02T06:00:00")))
        assertTrue(schedule.contains(at("2026-11-02T22:00:00")))
        assertTrue(schedule.contains(at("2026-11-03T00:00:00")))
        assertTrue(schedule.contains(at("2026-11-03T06:59:59")))
        assertFalse(schedule.contains(at("2026-11-03T07:00:00")))
        assertFalse(schedule.contains(at("2026-11-03T22:00:00")))
    }

    @Test fun sundayNightContinuesIntoMonday() {
        val schedule = ProtectionSchedule(true, setOf(7), 22 * 60, 7 * 60)
        assertTrue(schedule.contains(at("2026-11-02T06:00:00")))
        assertFalse(schedule.contains(at("2026-11-02T22:00:00")))
    }

    @Test fun equalTimesMeanCalendarDayAndDisabledScheduleAlwaysApplies() {
        val schedule = ProtectionSchedule(true, setOf(1), 0, 0)
        assertTrue(schedule.contains(at("2026-11-02T00:00:00")))
        assertTrue(schedule.contains(at("2026-11-02T23:59:59")))
        assertFalse(schedule.contains(at("2026-11-03T00:00:00")))
        assertTrue(schedule.copy(enabled = false, days = emptySet()).contains(at("2026-11-03T12:00:00")))
        assertFalse(schedule.copy(days = emptySet()).contains(at("2026-11-02T12:00:00")))
    }

    @Test fun noSelectedProfileKeepsBasicProtection() {
        assertEquals(CallDecision.DEFAULT, PremiumRules.callDecision(null, phone, false, at("2026-11-02T12:00:00")))
        assertNull(PremiumConfiguration().activeProfile)
        assertNull(PremiumConfiguration(activeProfileId = "deleted").activeProfile)
    }

    @Test fun outsideScheduleAllowsCallsIncludingHiddenNumbers() {
        val profile = defaultProfiles().first()
        assertEquals(CallDecision.ALLOW, PremiumRules.callDecision(profile, phone, false, at("2026-11-02T20:00:00")))
        assertEquals(CallDecision.ALLOW, PremiumRules.callDecision(profile, "", true, at("2026-11-02T20:00:00")))
        assertEquals(CallDecision.DEFAULT, PremiumRules.callDecision(profile, phone, false, at("2026-11-02T12:00:00")))
    }

    @Test fun allowExceptionOverridesUnknownAndHiddenRulesDoNotMatchNumbers() {
        val profile = ProtectionProfile("p", "Test", exceptions = listOf(NumberException("+52 55 1234 5678", action = NumberAction.ALLOW)))
        assertEquals(CallDecision.ALLOW, PremiumRules.callDecision(profile, phone, false, at("2026-11-02T12:00:00")))
        assertEquals(CallDecision.BLOCK, PremiumRules.callDecision(profile, phone, true, at("2026-11-02T12:00:00")))
    }

    @Test fun explicitBlockAppliesOutsideScheduleAndWithUnknownBlockingOff() {
        val profile = defaultProfiles().first().copy(blockUnknown = false, allowRepeat = true,
            exceptions = listOf(NumberException(phone, action = NumberAction.BLOCK)))
        assertEquals(CallDecision.BLOCK, PremiumRules.callDecision(profile, phone, false, at("2026-11-02T20:00:00")))
        assertEquals(CallDecision.ALLOW, PremiumRules.callDecision(profile, "5587654321", false, at("2026-11-02T12:00:00")))
    }

    @Test fun hiddenCallsRespectProfileOption() {
        val profile = ProtectionProfile("p", "Test", blockHidden = false)
        assertEquals(CallDecision.ALLOW, PremiumRules.callDecision(profile, "", true, at("2026-11-02T12:00:00")))
        assertEquals(CallDecision.BLOCK, PremiumRules.callDecision(profile.copy(blockHidden = true), "", true, at("2026-11-02T12:00:00")))
    }

    @Test fun numberValidationRejectsNamesShortCodesAndServiceCommands() {
        assertTrue(PremiumRules.validNumber("+52 (55) 1234-5678"))
        listOf("", "BANCO", "12345", "*5551234567#", "1234567890123456").forEach { assertFalse(it, PremiumRules.validNumber(it)) }
        assertEquals(PremiumRules.numberKey(phone), PremiumRules.numberKey("+52 55 1234 5678"))
    }

    @Test fun editorStateCanBeSavedAcrossActivityRecreation() {
        val profile = defaultProfiles().first().copy(exceptions = listOf(NumberException(phone, "Prueba", NumberAction.ALLOW)))
        val bytes = ByteArrayOutputStream()
        ObjectOutputStream(bytes).use { it.writeObject(profile) }
        val restored = ObjectInputStream(ByteArrayInputStream(bytes.toByteArray())).use { it.readObject() }
        assertEquals(profile, restored)
    }
}
