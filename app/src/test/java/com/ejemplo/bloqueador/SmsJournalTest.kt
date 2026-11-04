package com.ejemplo.bloqueador

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class SmsJournalTest {
    private val context get() = RuntimeEnvironment.getApplication()

    @Test fun partResultsSurviveClosingAndReopeningDatabase() {
        SmsJournal(context).use {
            it.create("multipart", "content://sms/1", 2, 123456L)
            assertEquals("pending", it.record("multipart", 1, Activity.RESULT_OK)?.state)
        }
        SmsJournal(context).use {
            assertEquals("sent", it.record("multipart", 0, Activity.RESULT_OK)?.state)
            assertEquals(123456L, it.record("multipart", 1, Activity.RESULT_OK)?.date)
        }
    }

    @Test fun failedSegmentCannotBecomeSentAfterLaterSuccess() {
        SmsJournal(context).use {
            it.create("failed", "content://sms/2", 2)
            assertEquals("failed", it.record("failed", 0, 4)?.state)
            assertEquals("failed", it.record("failed", 1, Activity.RESULT_OK)?.state)
        }
    }

    @Test fun malformedOrUnknownCallbacksDoNotCreateSendingRecords() {
        SmsJournal(context).use {
            assertNull(it.record("missing", 0, Activity.RESULT_OK))
            it.create("known", "content://sms/3", 1)
            assertNull(it.record("known", 5, Activity.RESULT_OK))
        }
    }

    @Test fun incomingReceiptPersistsEvenIfOriginalMessageWasDeleted() {
        SmsJournal(context).use { it.recordIncoming("receipt", "content://sms/5") }
        SmsJournal(context).use { assertTrue(it.received("receipt")); assertFalse(it.received("new-receipt")) }
    }

    @Test fun externalSmsLinkOnlyProducesAnEditorRequest() {
        val request = SmsNavigation.parse(Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:%2B525512345678?body=Hola%20Ana")))!!
        assertEquals("+525512345678", request.address)
        assertEquals("Hola Ana", request.body)
        assertTrue(request.compose)
        assertNull(request.threadId)
        assertNull(SmsNavigation.parse(Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com"))))
    }

    @Test fun defaultSmsComponentsAreProtectedBySystemPermissions() {
        val pm = context.packageManager
        val sms = pm.getReceiverInfo(ComponentName(context, SmsDeliveryReceiver::class.java), 0)
        assertEquals("android.permission.BROADCAST_SMS", sms.permission)
        assertTrue(sms.exported)
        val mms = pm.getReceiverInfo(ComponentName(context, MmsDeliveryReceiver::class.java), 0)
        assertEquals("android.permission.BROADCAST_WAP_PUSH", mms.permission)
        val sent = pm.getReceiverInfo(ComponentName(context, SmsSentReceiver::class.java), 0)
        assertFalse(sent.exported)
        val service = pm.getServiceInfo(ComponentName(context, SmsRespondService::class.java), 0)
        assertEquals("android.permission.SEND_RESPOND_VIA_MESSAGE", service.permission)
    }
}
