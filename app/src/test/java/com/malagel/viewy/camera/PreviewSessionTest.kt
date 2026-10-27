package com.malagel.viewy.camera

import org.junit.Assert.*
import org.junit.Test

class PreviewSessionTest {
    private val ready = CameraPreviewState(ready = true, mapping = CameraDirectionMapping.forRearCamera("test", null, null))

    @Test fun disposalClearsReadinessAndRejectsLateInitialization() {
        val session = PreviewSession()
        val token = session.start()
        assertTrue(session.update(token, ready))
        assertTrue(session.state.ready)
        assertTrue(session.stop(token))
        assertFalse(session.state.ready)
        assertNull(session.state.mapping)
        assertFalse(session.update(token, ready))
        assertFalse(session.state.ready)
    }

    @Test fun methodChangeRevokesPendingAndReadyPreviewOwnership() {
        for (alreadyReady in listOf(false, true)) {
            val session = PreviewSession()
            val old = session.start()
            if (alreadyReady) session.update(old, ready)
            session.invalidate()
            assertFalse(session.state.ready)
            assertNull(session.state.mapping)
            assertFalse(session.update(old, ready))
            val next = session.start()
            assertTrue(session.update(next, ready))
            assertFalse(session.stop(old))
            assertTrue(session.state.ready)
        }
    }

    @Test fun retryRejectsOldCallbacksAndOldDisposal() {
        val session = PreviewSession()
        val previous = session.start()
        session.update(previous, ready)
        val retry = session.start()
        assertFalse(session.state.ready)
        assertNull(session.state.mapping)
        assertFalse(session.update(previous, ready))
        assertTrue(session.update(retry, ready))
        assertFalse(session.stop(previous))
        assertTrue(session.state.ready)
        assertTrue(session.stop(retry))
    }

    @Test fun closingBeforeProviderInitializationCannotAffectNextPreview() {
        val session = PreviewSession()
        val pending = session.start()
        session.stop(pending)
        val next = session.start()
        assertFalse(session.update(pending, ready))
        assertFalse(session.state.ready)
        assertTrue(session.update(next, ready))
        assertTrue(session.state.ready)
    }
}
