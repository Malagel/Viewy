package com.malagel.viewy.ui

import org.junit.Assert.*
import org.junit.Test

class SystemBackGestureGuardTest {
    @Test fun reservesBothPhysicalEdgesButLeavesTheInteriorForPaging() {
        for (x in listOf(0f, 12f, 23.99f, 1056f, 1079.99f)) {
            assertTrue("Edge x=$x", startsInSystemBackEdge(x, 1080, 24, 24))
        }
        for (x in listOf(24f, 100f, 540f, 1055.99f)) {
            assertFalse("Interior x=$x", startsInSystemBackEdge(x, 1080, 24, 24))
        }
    }

    @Test fun zeroOrOneSidedInsetsDoNotCreateAnInventedGestureMargin() {
        assertFalse(startsInSystemBackEdge(0f, 800, 0, 0))
        assertFalse(startsInSystemBackEdge(799f, 800, 0, 0))
        assertFalse(startsInSystemBackEdge(0f, 800, 0, 40))
        assertTrue(startsInSystemBackEdge(760f, 800, 0, 40))
        assertTrue(startsInSystemBackEdge(31f, 800, 32, 0))
        assertFalse(startsInSystemBackEdge(799f, 800, 32, 0))
    }

    @Test fun usesCurrentWindowBoundsAndAsymmetricPlatformInsets() {
        assertTrue(startsInSystemBackEdge(19f, 400, 20, 36))
        assertFalse(startsInSystemBackEdge(20f, 400, 20, 36))
        assertTrue(startsInSystemBackEdge(364f, 400, 20, 36))
        assertFalse(startsInSystemBackEdge(363f, 400, 20, 36))
        assertFalse(startsInSystemBackEdge(-1f, 400, 20, 36))
        assertFalse(startsInSystemBackEdge(400f, 400, 20, 36))
        assertFalse(startsInSystemBackEdge(0f, 0, 20, 36))
    }
}
