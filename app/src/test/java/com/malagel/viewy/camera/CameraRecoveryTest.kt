package com.malagel.viewy.camera

import org.junit.Assert.*
import org.junit.Test

class CameraRecoveryTest {
    @Test fun deniedAccessAndKnownIncompatibleConfigurationNeverBecomeRetryableThroughWrapping() {
        assertFalse(cameraExceptionRetryable(SecurityException()))
        assertFalse(cameraExceptionRetryable(java.util.concurrent.ExecutionException(SecurityException())))
        assertFalse(cameraExceptionRetryable(IllegalArgumentException(), configuring = true))
        assertFalse(cameraExceptionRetryable(RuntimeException(IllegalArgumentException()), configuring = true))
        assertTrue(cameraExceptionRetryable(IllegalStateException(), configuring = true))
        assertTrue(cameraExceptionRetryable(java.util.concurrent.ExecutionException(RuntimeException())))
    }
    @Test fun retriesAreProgressiveBoundedAndManualResetStartsFreshCycle() {
        val policy = CameraRecovery()
        repeat(2) {
            assertFalse(policy.needsUser)
            for (delay in listOf(500L, 1_000L, 2_000L)) {
                assertEquals(delay, policy.failure(true))
                assertTrue(policy.waiting)
                assertNull(policy.failure(true)) // Duplicate failure cannot spend another attempt.
                policy.retryDue()
            }
            assertNull(policy.failure(true))
            assertTrue(policy.needsUser)
            assertEquals(3, policy.attempts)
            policy.reset() // Manual retry or a new eligible session.
        }
    }

    @Test fun successfulRecoveryClearsFailuresAndPermanentFailureSkipsRetries() {
        val policy = CameraRecovery()
        policy.failure(true)
        policy.retryDue()
        policy.reset() // Streaming success.
        assertEquals(0, policy.attempts)
        assertEquals(500L, policy.failure(true))
        policy.reset()
        assertNull(policy.failure(false))
        assertTrue(policy.needsUser)
        assertFalse(policy.waiting)
        assertEquals(0, policy.attempts)
    }

    @Test fun startupAndInterruptionDeadlinesDoNotSlideAndBriefTransitionsDoNotFail() {
        val watchdog = PreviewWatchdog()
        watchdog.reset(0)
        assertEquals(12_000L, watchdog.readiness(false, 0))
        assertEquals(12_000L, watchdog.readiness(false, 11_999))
        assertFalse(watchdog.expired(11_999))
        assertTrue(watchdog.expired(12_000))
        assertNull(watchdog.readiness(true, 12_000))
        assertFalse(watchdog.expired(99_000))
        assertEquals(102_000L, watchdog.readiness(false, 99_000))
        assertEquals(102_000L, watchdog.readiness(false, 101_000))
        assertNull(watchdog.readiness(true, 101_999))
        assertFalse(watchdog.expired(102_000))
        assertEquals(106_000L, watchdog.readiness(false, 103_000))
        assertTrue(watchdog.expired(106_000))
        watchdog.reset(200_000) // New binding/lifecycle startup gets the full startup allowance.
        assertEquals(212_000L, watchdog.readiness(false, 200_000))
    }
}
