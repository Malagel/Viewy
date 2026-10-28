package com.malagel.viewy.camera

import org.junit.Assert.*
import org.junit.Test

class PreviewZoomTest {
    @Test fun zoomHas1xFloorAndRetainsCameraMaximumAbove20x() {
        val zoom = PreviewZoomSession()
        zoom.reset(0.5f, 100f)
        assertEquals(1f, zoom.state.minimum)
        assertEquals(100f, zoom.state.maximum)
        zoom.request(30f)
        assertEquals(30f, zoom.state.ratio)
        zoom.request(150f)
        assertEquals(100f, zoom.state.ratio)
        zoom.request(-10f)
        assertEquals(1f, zoom.state.ratio)
        zoom.reset(1.5f, 2.5f)
        assertEquals(1.5f, zoom.state.ratio)
        assertEquals(2.5f, zoom.state.maximum)
    }

    @Test fun sessionsStartAt1xIndependentlyOfSupportedMinimumAndMaximum() {
        val zoom = PreviewZoomSession()
        for ((minimum, maximum) in listOf(0.6f to 30f, 0.5f to 100f, 1f to 4f, 1f to 1f)) {
            val initial = zoom.reset(minimum, maximum)!!
            assertEquals(maxOf(1f, minimum), zoom.state.minimum)
            assertEquals(maximum, zoom.state.maximum)
            assertEquals(1f, zoom.state.ratio)
            // The same state supplies the displayed number and the CameraX request.
            assertFalse(zoom.state.settled)
            assertTrue(zoom.applied(initial, 0))
            assertTrue(zoom.settle(initial, 350))
            assertTrue(zoom.state.settled)
        }
    }

    @Test fun replacementResetsTo1xAfterMinimumAndHighZoomAndRejectsOldCompletions() {
        val zoom = PreviewZoomSession()
        zoom.reset(0.6f, 30f)
        for (ratio in listOf(1f, 25f)) {
            val previous = zoom.request(ratio)!!
            zoom.applied(previous, 0)
            assertTrue(zoom.settle(previous, 350))
            val initial = zoom.reset(0.6f, 30f)!!
            assertEquals(1f, zoom.state.ratio)
            assertFalse(zoom.state.settled)
            assertFalse(zoom.applied(previous, 500))
            assertFalse(zoom.settle(previous, 1000))
            assertTrue(zoom.applied(initial, 1000))
            assertTrue(zoom.settle(initial, 1350))
        }
    }

    @Test fun invalidOrEntirelySub1xRangesFailClosedAndFixedZoomIsNotEditable() {
        val zoom = PreviewZoomSession()
        for ((low, high) in listOf(0f to 4f, Float.NaN to 4f, 1f to Float.POSITIVE_INFINITY, -1f to 4f, 3f to 2f, 0.5f to 0.9f)) {
            assertNull(zoom.reset(low, high))
            assertFalse(zoom.state.available)
            assertFalse(zoom.state.settled)
        }
        assertNotNull(zoom.reset(1f, 1f))
        assertFalse(zoom.state.editable(true, false, false))
        assertNull(zoom.request(Float.NaN))
    }

    @Test fun cameraMinimumAbove1xRemainsRespected() {
        val zoom = PreviewZoomSession()
        assertNotNull(zoom.reset(5f, 30f))
        assertEquals(5f, zoom.state.ratio)
        val target = zoom.request(zoom.state.ratio * 5f)!!
        assertEquals(25f, zoom.state.ratio)
        assertFalse(zoom.state.settled)
        assertTrue(zoom.applied(target, 0))
        assertTrue(zoom.settle(target, 350))
    }

    @Test fun buttonAndPinchRequestsCannotCross1xFloor() {
        val zoom = PreviewZoomSession()
        zoom.reset(0.6f, 30f)
        zoom.request(1.2f)
        zoom.request(zoom.state.ratio - 0.5f) // Minus button near the lower bound.
        assertEquals(1f, zoom.state.ratio)
        repeat(10) { zoom.request(zoom.state.ratio * 0.6f) } // Repeated zoom-out pinch.
        assertEquals(1f, zoom.state.ratio)
        assertEquals(1f, zoom.state.minimum)
        zoom.request(30f)
        assertEquals(30f, zoom.state.ratio)
        zoom.request(0.6f) // Any caller is bounded, not just the two UI paths.
        assertEquals(1f, zoom.state.ratio)
    }

    @Test fun latestRequestMustBeAcknowledgedThenSettleBeforeCapture() {
        val zoom = PreviewZoomSession()
        val initial = zoom.reset(1f, 8f)!!
        val button = zoom.request(1.5f)!!
        val pinch = zoom.request(zoom.state.ratio * 2f)!!
        assertEquals(3f, zoom.state.ratio)
        assertFalse(zoom.applied(initial, 10))
        assertFalse(zoom.applied(button, 10))
        assertFalse(zoom.settle(pinch, 9999))
        assertTrue(zoom.applied(pinch, 100))
        assertFalse(zoom.settle(pinch, 449))
        assertTrue(zoom.settle(pinch, 450))
        assertTrue(zoom.state.settled)
        val next = zoom.request(4f)!!
        assertFalse(zoom.state.settled)
        assertFalse(zoom.settle(pinch, 1000))
        assertTrue(zoom.owns(next))
    }

    @Test fun closureAndReplacementRevokePendingCallbacks() {
        val zoom = PreviewZoomSession()
        val old = zoom.reset(1f, 4f)!!
        zoom.applied(old, 0)
        zoom.close()
        assertFalse(zoom.owns(old))
        assertFalse(zoom.settle(old, 1000))
        val replacement = zoom.reset(1f, 2f)!!
        assertEquals(1f, zoom.state.ratio)
        assertFalse(zoom.applied(old, 2000))
        assertTrue(zoom.applied(replacement, 2000))
    }

    @Test fun cameraClosureWithoutUnbindingRevokesZoomUntilReopenResetIsApplied() {
        val zoom = PreviewZoomSession()
        zoom.reset(0.6f, 30f)
        val previous = zoom.request(3f)!!
        zoom.applied(previous, 0)
        zoom.settle(previous, 350)
        assertTrue(zoom.state.settled)
        zoom.request(zoom.state.ratio) // OPEN lost; reject any old completion immediately.
        assertFalse(zoom.state.settled)
        assertFalse(zoom.applied(previous, 500))
        val reopened = zoom.reset(0.6f, 30f)!!
        assertEquals(1f, zoom.state.ratio)
        assertFalse(zoom.state.settled)
        zoom.applied(reopened, 1000)
        assertTrue(zoom.settle(reopened, 1350))
    }

    @Test fun topLocksZoomUntilInvalidatedAndCompletionHidesEditing() {
        val zoom = PreviewZoomSession().apply { reset(1f, 4f) }.state
        assertTrue(zoom.editable(true, false, false))
        assertFalse(zoom.editable(true, true, false))
        assertFalse(zoom.editable(true, true, true))
        assertFalse(zoom.editable(false, false, false))
        assertTrue(zoom.editable(true, false, false))
    }
}
