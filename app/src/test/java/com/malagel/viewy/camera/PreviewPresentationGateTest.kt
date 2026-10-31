package com.malagel.viewy.camera

import org.junit.Assert.*
import org.junit.Test

class PreviewPresentationGateTest {
    @Test fun streamingBeforeInitialZoomSettlementCannotRevealThePreview() {
        val gate = PreviewPresentationGate()
        assertNull(gate.update(streamReady = false, controlsSettled = true))
        assertNull(gate.update(streamReady = true, controlsSettled = false))
        assertFalse(gate.ready)
        val frame = gate.update(streamReady = true, controlsSettled = true)!!
        assertFalse(gate.ready)
        assertNull(gate.update(streamReady = true, controlsSettled = true))
        assertTrue(gate.presented(frame))
        assertTrue(gate.ready)
        // Ordinary zooming blocks capture separately, without hiding an already displayed image.
        assertNull(gate.update(streamReady = true, controlsSettled = false))
        assertTrue(gate.ready)
    }

    @Test fun readinessLossOrNewStartupControlsRejectAnAlreadyQueuedFrame() {
        for (streamLost in listOf(false, true)) {
            val gate = PreviewPresentationGate()
            val obsolete = gate.update(true, true)!!
            gate.update(streamReady = !streamLost, controlsSettled = streamLost)
            assertFalse(gate.presented(obsolete))
            assertFalse(gate.ready)
            assertFalse(gate.awaitingFrame)
            val next = gate.update(true, true)!!
            assertNotEquals(obsolete, next)
            assertTrue(gate.presented(next))
            assertFalse(gate.presented(next))
        }
    }

    @Test fun closureAndRebindingNeverReuseDisplayedOrPendingReadiness() {
        val gate = PreviewPresentationGate()
        repeat(4) {
            val frame = gate.update(true, true)!!
            if (it % 2 == 0) assertTrue(gate.presented(frame))
            gate.reset()
            assertFalse(gate.ready)
            assertFalse(gate.presented(frame))
            assertNull(gate.update(false, false))
        }
    }
}
