package com.malagel.viewy.measurement

import com.malagel.viewy.domain.DistanceUncertainty
import com.malagel.viewy.domain.KnownHeightDistance
import com.malagel.viewy.domain.KnownHeightResult
import com.malagel.viewy.domain.ViewingDirection
import com.malagel.viewy.orientation.*
import kotlin.math.*
import org.junit.Assert.*
import org.junit.Test

class KnownHeightWorkflowTest {
    private val base = 2_000_000_000L
    private val quality = OrientationQuality(13, 480_000_000L, 0, 0.0, 0.0, 0.0, 3, CaptureStatus.READY)
    private fun ray(elevation: Double, bearing: Double = 90.0): ViewingDirection {
        val e = Math.toRadians(elevation)
        val b = Math.toRadians(bearing)
        return ViewingDirection(cos(e)*cos(b), cos(e)*sin(b), sin(e))
    }
    private fun observation(e: Double, time: Long = base, bearing: Double = 90.0) =
        OrientationObservation(ray(e, bearing), time, "frame", OrientationSource.GAME_ROTATION_VECTOR, quality, "camera")
    private fun workflow(): KnownHeightWorkflow = KnownHeightWorkflow().also {
        it.setHeight(2.0)
        it.cameraReady(true)
        it.update(observation(30.0), "frame", true, base)
    }
    private fun top(w: KnownHeightWorkflow): OrientationObservation = observation(30.0).also {
        w.capture(CaptureResult.Captured(it), base)
    }
    private fun bottom(w: KnownHeightWorkflow, e: Double = 0.0, time: Long = base+1_000_000_000L, bearing: Double = 90.0) {
        val b = observation(e, time, bearing)
        w.update(b, "frame", true, time)
        w.capture(CaptureResult.Captured(b), time)
    }
    @Test fun validPairUsesDomainAndDoesNotInventUncertainty() {
        val w = workflow()
        assertTrue(w.state.canCapture)
        val a = top(w)
        assertEquals(MeasurementStage.BOTTOM, w.state.stage)
        bottom(w)
        val result = w.state.result!!
        val expected = KnownHeightDistance.estimate(2.0, a.direction, w.state.bottom!!.direction) as KnownHeightResult.Estimate
        assertEquals(expected, result)
        assertEquals(2.0*sqrt(3.0), result.horizontalDistanceMeters, 1e-12)
        assertEquals(DistanceUncertainty.Unavailable, result.uncertainty)
        assertFalse(w.state.canCapture)
    }
    @Test fun actualAcquisitionObservationsDriveTheCompleteWorkflow() {
        val session = OrientationSession(OrientationSource.GAME_ROTATION_VECTOR)
        val w = KnownHeightWorkflow()
        w.setHeight(2.0)
        w.cameraReady(true)
        fun feed(elevation: Double, start: Long): Long {
            val pitch = Math.PI/2 + Math.toRadians(elevation)
            val values = floatArrayOf(sin(pitch/2).toFloat(), 0f, 0f, cos(pitch/2).toFloat())
            repeat(13) { i ->
                val t = start + i*40_000_000L
                session.add(values, t, t)
            }
            val end = start+480_000_000L
            w.update(session.latest(end), session.referenceFrameId, true, end)
            return end
        }
        val topTime = feed(30.0, base)
        w.capture(session.capture(topTime), topTime)
        // Smooth ordinary pitch increments preserve the frame; let the bottom aim settle.
        repeat(30) { i ->
            val pitch = Math.PI/2 + Math.toRadians(29.0-i)
            val t = topTime+(i+1)*40_000_000L
            session.add(floatArrayOf(sin(pitch/2).toFloat(), 0f, 0f, cos(pitch/2).toFloat()), t, t)
        }
        val bottomTime = feed(0.0, topTime+31*40_000_000L)
        w.capture(session.capture(bottomTime), bottomTime)
        assertEquals(MeasurementStage.RESULT, w.state.stage)
        assertEquals(2.0*sqrt(3.0), w.state.result!!.horizontalDistanceMeters, 1e-6)
    }
    @Test fun missingHeightOrCameraPreventsCapture() {
        val w = KnownHeightWorkflow()
        w.update(observation(30.0), "frame", true, base)
        w.capture(CaptureResult.Captured(observation(30.0)), base)
        assertNull(w.state.top)
        w.setHeight(2.0)
        w.capture(CaptureResult.Captured(observation(30.0)), base)
        assertNull(w.state.top)
    }
    @Test fun cameraLossClearsIncompletePairAndCanRecover() {
        val w = workflow()
        top(w)
        w.cameraReady(false)
        assertNull(w.state.result)
        assertNull(w.state.top)
        assertFalse(w.state.canCapture)
        w.cameraReady(true)
        w.update(observation(30.0), "frame", true, base)
        top(w)
        assertNotNull(w.state.top)
    }
    @Test fun completedPairSurvivesCameraAndSensorShutdownAndChangedFramesUntilRestart() {
        val w = workflow()
        top(w)
        bottom(w)
        val completed = w.state
        repeat(3) {
            w.cameraReady(false)
            w.update(null, null, false, base + 100_000_000_000L)
            w.update(observation(10.0).copy(referenceFrameId = "new", cameraMappingId = "new"), "new", true, base)
            assertSame(completed.result, w.state.result)
            assertSame(completed.top, w.state.top)
            assertSame(completed.bottom, w.state.bottom)
            assertEquals(MeasurementStage.RESULT, w.state.stage)
            assertFalse(w.state.canCapture)
        }
        w.restart()
        assertFalse(w.state.completed)
        assertNull(w.state.latest)
        assertNull(w.state.frameId)
        assertEquals(2.0, w.state.heightMeters!!, 0.0)
    }
    @Test fun pausedOrChangedReferenceInvalidatesFirstEndpoint() {
        for ((running, frame) in listOf(false to "frame", true to "new-frame", false to null)) {
            val w = workflow()
            top(w)
            w.update(null, frame, running, base+1)
            assertNull(w.state.top)
            assertEquals(MeasurementStage.TOP, w.state.stage)
        }
    }
    @Test fun changedSourceOrMappingInvalidatesFirstEndpoint() {
        for (changed in listOf(observation(0.0).copy(source = OrientationSource.ROTATION_VECTOR),
            observation(0.0).copy(cameraMappingId = "other-camera"))) {
            val w = workflow()
            top(w)
            w.update(changed, "frame", true, base+1)
            assertNull(w.state.top)
        }
    }
    @Test fun changedFrameAtButtonPressDoesNotBecomeAnAccidentalTopCapture() {
        val w = workflow()
        top(w)
        w.capture(CaptureResult.Captured(observation(0.0, base+1_000_000_000L).copy(referenceFrameId = "new")), base+1_000_000_000L)
        assertNull(w.state.top)
        assertNull(w.state.result)
    }
    @Test fun staleOrFutureOrUnstableCapturesCannotAdvance() {
        for ((o, now) in listOf(
            observation(30.0) to base+250_000_001L,
            observation(30.0, base+1) to base,
            observation(30.0).copy(quality = quality.copy(status = CaptureStatus.MOVING)) to base,
        )) {
            val w = workflow()
            w.capture(CaptureResult.Captured(o), now)
            assertNull(w.state.top)
        }
    }
    @Test fun rejectionDoesNotDiscardValidTop() {
        val w = workflow()
        val a = top(w)
        w.capture(CaptureResult.Rejected(CaptureStatus.STALE), base+1_000_000_000L)
        assertEquals(a, w.state.top)
        assertNull(w.state.result)
    }
    @Test fun pairTimeoutExpiresWithoutAnotherTap() {
        val w = workflow()
        top(w)
        w.update(observation(0.0, base+15_000_000_001L), "frame", true, base+15_000_000_001L)
        assertNull(w.state.top)
        assertEquals(MeasurementNotice.CAPTURE_EXPIRED, w.state.notice)
    }
    @Test fun duplicateTapCannotRecordBothEndpoints() {
        val w = workflow()
        top(w)
        bottom(w, time = base+100_000_000L)
        assertEquals(MeasurementStage.BOTTOM, w.state.stage)
        assertNull(w.state.bottom)
    }
    @Test fun reversedEndpointsCanBeCorrectedWithoutLosingTop() {
        val w = workflow()
        val a = top(w)
        bottom(w, e = 40.0)
        assertEquals(MeasurementStage.CORRECTION, w.state.stage)
        assertEquals(a, w.state.top)
        assertNull(w.state.result)
        bottom(w, time = base+2_000_000_000L)
        assertNotNull(w.state.result)
    }
    @Test fun equalAndTinySeparationRequireCorrection() {
        for (e in listOf(30.0, 29.8)) {
            val w = workflow()
            top(w)
            bottom(w, e)
            assertEquals(MeasurementStage.CORRECTION, w.state.stage)
            assertNull(w.state.result)
        }
    }
    @Test fun recaptureBottomAndRestartHaveExplicitBehavior() {
        val w = workflow()
        val a = top(w)
        bottom(w)
        w.recaptureBottom()
        assertEquals(a, w.state.top)
        assertNull(w.state.bottom)
        assertNull(w.state.result)
        bottom(w, e = 5.0, time = base+2_000_000_000L)
        assertNotNull(w.state.result)
        w.restart()
        assertNull(w.state.top)
        assertEquals(2.0, w.state.heightMeters!!, 0.0)
    }
    @Test fun changingHeightStartsANewPair() {
        val w = workflow()
        top(w)
        bottom(w)
        w.setHeight(3.5)
        assertNull(w.state.result)
        assertNull(w.state.top)
        assertEquals(3.5, w.state.heightMeters!!, 0.0)
    }
    @Test fun alignmentUsesShortArcAndCorrectPhysicalTurnSign() {
        assertEquals(Alignment.ALIGNED, alignment(ray(30.0, 179.5), ray(0.0, -179.5)))
        assertEquals(Alignment.TURN_RIGHT, alignment(ray(30.0, 179.0), ray(0.0, -177.0)))
        assertEquals(Alignment.TURN_LEFT, alignment(ray(30.0, -179.0), ray(0.0, 177.0)))
        assertEquals(Alignment.NEAR_VERTICAL, alignment(ray(89.0), ray(0.0)))
        assertEquals(Alignment.NEAR_VERTICAL, alignment(ray(30.0), ray(-89.0)))
    }
    @Test fun alignmentIsIndependentOfElevationOrderingOrUsableDistanceGeometry() {
        for ((topElevation, bottomElevation) in listOf(30.0 to 0.0, 0.0 to 30.0, 0.0 to 0.0)) {
            assertEquals(Alignment.ALIGNED, alignment(ray(topElevation, 179.5), ray(bottomElevation, -179.5)))
            assertEquals(Alignment.TURN_RIGHT, alignment(ray(topElevation, 179.0), ray(bottomElevation, -177.0)))
            assertEquals(Alignment.TURN_LEFT, alignment(ray(topElevation, -179.0), ray(bottomElevation, 177.0)))
        }
    }
    @Test fun misalignmentBlocksResultAndGuidesCorrection() {
        val w = workflow()
        top(w)
        bottom(w, bearing = 95.0)
        assertFalse(w.state.canCapture)
        assertEquals(Alignment.TURN_RIGHT, w.state.alignment)
        assertNull(w.state.result)
        bottom(w, time = base+2_000_000_000L)
        assertNotNull(w.state.result)
    }
    @Test fun nearVerticalFirstCaptureIsRejected() {
        val w = workflow()
        w.capture(CaptureResult.Captured(observation(89.0)), base)
        assertNull(w.state.top)
    }
    @Test fun aCompletedResultCannotBeOverwrittenByDuplicateCaptures() {
        val w = workflow()
        top(w)
        bottom(w)
        val result = w.state.result
        w.capture(CaptureResult.Captured(observation(10.0, base+2_000_000_000L)), base+2_000_000_000L)
        assertEquals(result, w.state.result)
    }

    @Test fun cameraShutdownWithoutTopIsSilentForIntentionalAndUnexpectedLoss() {
        for (intentional in listOf(false, true)) {
            val w = workflow()
            repeat(3) {
                w.cameraReady(false, intentional)
                w.update(null, null, false, base)
                assertNull(w.state.notice)
                assertEquals(MeasurementStage.TOP, w.state.stage)
                assertNull(w.state.top)
                w.cameraReady(true)
            }
        }
    }

    @Test fun shutdownReportsLostProgressOnceAndDistinguishesIntentionalSuspension() {
        for (intentional in listOf(false, true)) {
            val w = workflow()
            top(w)
            w.cameraReady(false, intentional)
            val interrupted = w.state
            assertEquals(if (intentional) MeasurementNotice.ACQUISITION_SUSPENDED else MeasurementNotice.CAMERA_INTERRUPTED, interrupted.notice)
            assertNull(interrupted.top)
            w.cameraReady(false, intentional)
            w.update(null, null, false, base)
            assertEquals(interrupted.noticeId, w.state.noticeId)
            w.cameraReady(true)
            assertEquals(interrupted.noticeId, w.state.noticeId)
        }
    }

    @Test fun repeatedRejectionsHaveNewIdentitiesButSensorUpdatesDoNotReissueThem() {
        val w = workflow()
        top(w)
        bottom(w, e = 40.0)
        val first = w.state.noticeId
        w.update(observation(40.0, base + 2_000_000_000L), "frame", true, base + 2_000_000_000L)
        assertEquals(first, w.state.noticeId)
        bottom(w, e = 40.0, time = base + 2_000_000_000L)
        assertTrue(w.state.noticeId > first)
        assertEquals(MeasurementNotice.AIM_BELOW_TOP, w.state.notice)
        w.restart()
        assertNull(w.state.notice)
    }

    @Test fun exactFifteenSecondBoundaryStillAcceptsValidGeometry() {
        assertEquals(15_000_000_000L, AcquisitionPolicy().maximumPairGapNanos)
        val w = workflow()
        top(w)
        bottom(w, time = base + 15_000_000_000L)
        assertNotNull(w.state.result)
        w.cameraReady(false)
        assertNull(w.state.notice)
    }

    @Test fun changedFrameAtFirstTapDoesNotClaimLostProgress() {
        val w = workflow()
        w.capture(CaptureResult.Captured(observation(30.0).copy(referenceFrameId = "new")), base)
        assertNull(w.state.top)
        assertNull(w.state.notice)
    }
}
