package com.ejemplo.bloqueador

import org.junit.Assert.*
import org.junit.Test

class HistorySelectionTest {
    @Test fun newCallsAndReusedIdsNeverJoinSelectionDuringRefresh() {
        val old = HistorySelection.key(1, 100)
        val reused = HistorySelection.key(1, 200)
        val newer = HistorySelection.key(2, 300)
        assertEquals(listOf(old), HistorySelection.prune(listOf(old), listOf(old, newer)))
        assertTrue(HistorySelection.prune(listOf(old), listOf(reused, newer)).isEmpty())
    }
    @Test fun selectingPartiallySelectedGroupRequiresExplicitToggle() {
        val selected = listOf("1:100")
        val group = listOf("1:100", "2:200")
        assertEquals(selected, HistorySelection.prune(selected, group))
        assertEquals(group, HistorySelection.toggle(selected, group))
        assertTrue(HistorySelection.toggle(group, group).isEmpty())
    }
    @Test fun deselectingOneGroupKeepsOtherSelections() {
        assertEquals(listOf("other"), HistorySelection.toggle(listOf("one", "two", "other"), listOf("one", "two")))
        assertEquals(listOf("one"), HistorySelection.toggle(listOf("one"), emptyList()))
    }
}
