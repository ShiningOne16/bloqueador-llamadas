package com.ejemplo.bloqueador

import org.junit.Assert.*
import org.junit.Test

class HiddenWipeTest {
    private class Operations : HiddenWipeOperations {
        var data = WipeCounts(1, 2, 3, 1)
        var pin = true
        var hiddenList = true
        var reads = 0
        var deletes = 0
        var failReadAt = 0
        var failDelete = false
        var leaveData = false
        var newMessage = false
        override fun snapshot(): WipeCounts {
            reads++
            if (reads == failReadAt) throw SecurityException("Falta permiso")
            return data
        }
        override fun deleteSnapshot() {
            deletes++
            data = data.copy(contacts = 0)
            if (failDelete) throw SecurityException("No se pudieron borrar los SMS")
            if (!leaveData) data = WipeCounts(0, 0, 0, 0)
            if (newMessage) data = data.copy(sms = 1, threads = 1)
        }
        override fun finish() {
            check(data.empty)
            pin = false
            hiddenList = false
        }
    }

    private fun assertProtected(op: Operations, report: WipeReport) {
        assertFalse(report.complete)
        assertNotNull(report.failure)
        assertTrue(op.pin)
        assertTrue(op.hiddenList)
    }

    @Test fun missingReadPermissionCannotBeMistakenForAnEmptyHistory() {
        val op = Operations().apply { failReadAt = 1 }
        assertProtected(op, HiddenWipe.perform(op))
        assertEquals(0, op.deletes)
    }

    @Test fun partialDeletionPreservesProtectionAndCanBeRetried() {
        val op = Operations().apply { failDelete = true }
        assertProtected(op, HiddenWipe.perform(op))
        assertEquals(0, op.data.contacts)
        op.failDelete = false
        val retry = HiddenWipe.perform(op)
        assertTrue(retry.complete)
        assertEquals(0, retry.contactsTotal) // Ya se borró en el primer intento.
        assertEquals(2, retry.callsDeleted)
        assertEquals(3, retry.smsMessagesDeleted)
        assertFalse(op.pin)
        assertFalse(op.hiddenList)
    }

    @Test fun providerReturningWithoutDeletingAllRowsCannotResetThePin() {
        val op = Operations().apply { leaveData = true }
        val report = HiddenWipe.perform(op)
        assertProtected(op, report)
        assertEquals(1, report.contactsDeleted)
        assertTrue(report.smsSkipped)
    }

    @Test fun verificationFailureAfterDeletingStillKeepsProtection() {
        val op = Operations().apply { failReadAt = 2 }
        assertProtected(op, HiddenWipe.perform(op))
        assertTrue(op.data.empty)
    }

    @Test fun newMessageDuringDeletionKeepsTheContactHidden() {
        val op = Operations().apply { newMessage = true }
        assertProtected(op, HiddenWipe.perform(op))
    }

    @Test fun successRequiresAVerifiedEmptySnapshot() {
        val op = Operations()
        val report = HiddenWipe.perform(op)
        assertTrue(report.complete)
        assertNull(report.failure)
        assertEquals(2, op.reads)
        assertEquals(1, report.contactsDeleted)
        assertEquals(2, report.callsDeleted)
        assertEquals(3, report.smsMessagesDeleted)
        assertFalse(op.pin)
        assertFalse(op.hiddenList)
    }

    @Test fun zeroCountsAloneDoNotMeanTheResetSucceeded() {
        assertFalse(WipeReport().complete)
    }
}
