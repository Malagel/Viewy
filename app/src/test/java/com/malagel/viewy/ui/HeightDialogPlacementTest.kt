package com.malagel.viewy.ui

import org.junit.Assert.*
import org.junit.Test

class HeightDialogPlacementTest {
    @Test fun openingRetainsTheLargestReportedHeightEvenIfTheKeyboardStartsClosing() {
        val placement = listOf(0, 120, 420, 260, 0).fold(HeightDialogPlacement()) { state, inset -> state.observe(inset) }
        assertEquals(420, placement.maximumKeyboardBottomPx)
        assertEquals(420, placement.freeze().frozenKeyboardBottomPx)
    }

    @Test fun revealedCardNeverMovesForHiddenReopenedOrResizedKeyboard() {
        val frozen = HeightDialogPlacement().observe(420).freeze()
        for (inset in listOf(300, 0, 120, 420, 560, 0)) {
            assertSame(frozen, frozen.observe(inset))
            assertEquals(420, frozen.observe(inset).frozenKeyboardBottomPx)
        }
        assertSame(frozen, frozen.freeze())
    }

    @Test fun noKeyboardFallbackIsAlsoFixedForTheRestOfTheOpening() {
        val fallback = HeightDialogPlacement().freeze()
        assertEquals(0, fallback.frozenKeyboardBottomPx)
        assertSame(fallback, fallback.observe(420))
    }

    @Test fun aNewOpeningDoesNotReuseAnotherKeyboardsHeight() {
        val previous = HeightDialogPlacement().observe(420).freeze()
        val next = HeightDialogPlacement().observe(300).freeze()
        assertEquals(420, previous.frozenKeyboardBottomPx)
        assertEquals(300, next.frozenKeyboardBottomPx)
        assertNull(HeightDialogPlacement().frozenKeyboardBottomPx)
        assertEquals(0, HeightDialogPlacement().observe(-1).freeze().frozenKeyboardBottomPx)
    }
}
