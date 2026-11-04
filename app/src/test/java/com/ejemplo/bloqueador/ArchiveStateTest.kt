package com.ejemplo.bloqueador

import org.junit.Assert.*
import org.junit.Test

class ArchiveStateTest {
    @Test fun archiveAndRestoreCallsDoNotHideNewRecordsWithReusedIds() {
        val old = ArchiveState.callKey(1, 100)
        val current = ArchiveState.callKey(1, 200)
        val other = ArchiveState.callKey(2, 100)
        val archive = ArchiveState().archiveCalls(listOf(old, other))
        assertTrue(old in archive.calls)
        assertFalse(current in archive.calls)
        val restored = archive.restoreCalls(listOf(old))
        assertFalse(old in restored.calls)
        assertTrue(other in restored.calls)
    }

    @Test fun archivingCallGroupTracksExactlyItsMembers() {
        val first = CallEntry(3, "5512345678", "Ana", 1, 300, 10)
        val second = first.copy(id = 2, number = "+52 55 1234 5678", date = 200)
        val other = first.copy(id = 1, number = "5587654321", date = 100)
        val groups = PhoneData.groupCalls(listOf(first, second, other))
        assertEquals(2, groups.size)
        assertEquals(2, groups.first().count)
        assertEquals(listOf(first, second), groups.first().entries)
        val archive = ArchiveState().archiveCalls(groups.first().entries.map { it.archiveKey() })
        assertTrue(first.archiveKey() in archive.calls)
        assertTrue(second.archiveKey() in archive.calls)
        assertFalse(other.archiveKey() in archive.calls)
    }

    @Test fun archivedConversationStaysArchivedWhenLatestMessageIsUnchanged() {
        val key = ArchiveState.threadKey(1, 50, 100, "5512345678")
        val archive = ArchiveState().archiveThread(1, key)
        assertEquals(archive, archive.refreshThread(1, key))
        assertTrue(key in archive.sms)
    }

    @Test fun newSmsRestoresConversationAndDoesNotRearchiveIfNewSmsIsLaterDeleted() {
        val old = ArchiveState.threadKey(1, 50, 100, "5512345678")
        val latest = ArchiveState.threadKey(1, 51, 200, "5512345678")
        val archived = ArchiveState().archiveThread(1, old)
        val updated = archived.refreshThread(1, latest)
        assertTrue(updated.sms.isEmpty())
        assertTrue(updated.refreshThread(1, old).sms.isEmpty())
    }

    @Test fun differentMessageWithSameDateAlsoRestoresConversation() {
        val old = ArchiveState.threadKey(1, 50, 100, "5512345678")
        val latest = ArchiveState.threadKey(1, 51, 100, "5512345678")
        assertTrue(ArchiveState().archiveThread(1, old).refreshThread(1, latest).sms.isEmpty())
    }

    @Test fun reusingThreadIdForAnotherSenderDoesNotHideNewConversation() {
        val old = ArchiveState.threadKey(1, 50, 100, "5512345678")
        val reused = ArchiveState.threadKey(1, 50, 100, "5587654321")
        assertTrue(ArchiveState().archiveThread(1, old).refreshThread(1, reused).sms.isEmpty())
    }

    @Test fun restoreOnlyTouchesTheSelectedThread() {
        val one = ArchiveState.threadKey(1, 50, 100, "Uno")
        val ten = ArchiveState.threadKey(10, 60, 100, "Diez")
        val archive = ArchiveState().archiveThread(1, one).archiveThread(10, ten).restoreThread(1)
        assertFalse(one in archive.sms)
        assertTrue(ten in archive.sms)
    }

    @Test fun rearchivingReplacesSnapshotAndPreservesOtherThreads() {
        val one = ArchiveState.threadKey(1, 50, 100, "Uno")
        val next = ArchiveState.threadKey(1, 51, 200, "Uno")
        val ten = ArchiveState.threadKey(10, 60, 100, "Diez")
        val archive = ArchiveState().archiveThread(1, one).archiveThread(10, ten).archiveThread(1, next)
        assertEquals(setOf(next, ten), archive.sms)
    }
}
