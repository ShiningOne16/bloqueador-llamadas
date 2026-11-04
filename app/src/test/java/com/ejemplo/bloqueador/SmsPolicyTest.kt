package com.ejemplo.bloqueador

import org.junit.Assert.*
import org.junit.Test

class SmsPolicyTest {
    @Test fun acceptsInternationalLocalAndShortCodeNumbers() {
        assertEquals("+525512345678", SmsPolicy.recipient("+52 (55) 1234-5678"))
        assertEquals("12345", SmsPolicy.recipient("12345"))
        assertEquals("5512345678", SmsPolicy.recipient("55 1234 5678"))
    }
    @Test fun rejectsServiceCodesMultipleRecipientsAndNonPhoneSenders() {
        listOf("", "+", "12+34", "++12345", "BANCO", "*123#", "12345;67890", "12345,67890", "tel:12345", "1", "123456789012345678901").forEach {
            assertNull(it, SmsPolicy.recipient(it))
        }
    }
    @Test fun allMultipartSegmentsMustSucceed() {
        assertEquals("pending", SmsPolicy.sendState(3, mapOf(2 to -1, 0 to -1), -1))
        assertEquals("sent", SmsPolicy.sendState(3, mapOf(2 to -1, 0 to -1, 1 to -1), -1))
        assertEquals("pending", SmsPolicy.sendState(0, emptyMap(), -1))
    }
    @Test fun oneFailedSegmentKeepsWholeMessageUnconfirmed() {
        assertEquals("failed", SmsPolicy.sendState(3, mapOf(0 to -1, 1 to 4), -1))
        assertEquals("failed", SmsPolicy.sendState(3, mapOf(0 to -1, 1 to 4, 2 to -1), -1))
    }
}
