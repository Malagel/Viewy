package com.malagel.viewy.ui

import com.malagel.viewy.R
import com.malagel.viewy.camera.CameraPreviewState
import com.malagel.viewy.domain.MeasurementMethod
import com.malagel.viewy.domain.ViewingDirection
import com.malagel.viewy.measurement.*
import com.malagel.viewy.orientation.*
import org.junit.Assert.*
import org.junit.Test

class MeasurementGuidanceTest {
    private val quality = OrientationQuality(13, 480_000_000L, 0, 0.0, 0.0, 0.0, 3, CaptureStatus.READY)
    private val observation = OrientationObservation(ViewingDirection(1.0, 0.0, 0.0), 2_000_000_000L,
        "frame", OrientationSource.GAME_ROTATION_VECTOR, quality, "camera")
    private val ready = MeasurementUiState(
        selection = MeasurementSelection().browse(MeasurementMethod.KNOWN_OBJECT_HEIGHT.mode).activate(MeasurementMethod.KNOWN_OBJECT_HEIGHT),
        resumed = true,
        visibility = MeasurementVisibility(surfaceVisible = true, windowFocused = true, permissionGranted = true),
        measurement = MeasurementState(heightMeters = 2.0, cameraReady = true, latest = observation),
        camera = CameraPreviewState(ready = true), acquisition = AcquisitionState(running = true),
    )
    private fun status(value: CaptureStatus) = ready.copy(measurement = ready.measurement.copy(
        latest = observation.copy(quality = quality.copy(status = value))))
    private val vertical = ready.copy(measurement = ready.measurement.copy(
        latest = observation.copy(direction = ViewingDirection(0.0, 0.0, 1.0))))
    private fun notice(reason: MeasurementNotice, id: Long = 1) = ready.copy(
        measurement = ready.measurement.copy(notice = reason, noticeId = id))

    @Test fun allLiveSensorStatusesRemainSilentRegardlessOfDuration() {
        val hints = MeasurementGuidance()
        for (status in CaptureStatus.entries) {
            val live = status(status)
            for (time in listOf(0L, 800L, 2_000L, 30_000L, 60_000L)) {
                assertNull("$status at $time", hints.update(live, time))
            }
        }
        val missing = ready.copy(measurement = ready.measurement.copy(latest = null))
        assertNull(hints.update(missing, 60_001))
        assertNull(hints.update(missing, 120_000))
    }

    @Test fun nearVerticalMessagePersistsUntilLeavingTheRestrictedRangeAndCaptureRemainsBlocked() {
        val hints = MeasurementGuidance()
        assertTrue(ready.measurement.canCapture)
        assertFalse(vertical.measurement.canCapture)
        assertEquals(R.string.guidance_near_vertical, hints.update(vertical, 0))
        assertEquals(R.string.guidance_near_vertical, hints.update(vertical, 60_000))
        assertNull(hints.update(ready, 60_001))
        // Rejected captures do not replace the persistent angle restriction.
        val rejected = vertical.copy(measurement = vertical.measurement.copy(
            notice = MeasurementNotice.NEAR_VERTICAL, noticeId = 1))
        assertEquals(R.string.guidance_near_vertical, hints.update(rejected, 60_002))
    }

    @Test fun nearVerticalMessageIsHiddenDuringSuspensionAndHardwareRecovery() {
        val hints = MeasurementGuidance()
        assertEquals(R.string.guidance_near_vertical, hints.update(vertical, 0))
        assertNull(hints.update(vertical.copy(resumed = false), 1))
        assertNull(hints.update(vertical.copy(cameraFailureVisible = true), 2))
        assertEquals(R.string.guidance_near_vertical, hints.update(vertical, 3))
        assertNull(hints.update(ready, 4))
    }

    @Test fun restartStageChangesSuspensionAndHardwareRecoveryDiscardOldErrors() {
        for (changed in listOf(
            notice(MeasurementNotice.SMALL_SEPARATION).copy(resumed = false),
            notice(MeasurementNotice.SMALL_SEPARATION).copy(cameraFailureVisible = true),
            notice(MeasurementNotice.SMALL_SEPARATION).copy(measurement = ready.measurement.copy(
                notice = MeasurementNotice.SMALL_SEPARATION, noticeId = 1, stage = MeasurementStage.BOTTOM)),
            ready.copy(measurement = ready.measurement.copy(noticeId = 2)),
        )) {
            val hints = MeasurementGuidance()
            val failed = notice(MeasurementNotice.SMALL_SEPARATION)
            assertEquals(R.string.notice_small_separation, hints.update(failed, 0))
            assertNull(hints.update(changed, 1))
            val returned = failed.copy(measurement = failed.measurement.copy(noticeId = changed.measurement.noticeId))
            assertNull(hints.update(returned, 2))
            assertNull(hints.update(returned, 60_000))
        }
    }

    @Test fun eachWorkflowFailureHasAReadableLocalizedMessage() {
        for (reason in MeasurementNotice.entries) {
            assertEquals(noticeResource(reason), MeasurementGuidance().update(notice(reason), 0))
        }
    }

    @Test fun correctionExpiresOnceAndRepeatedReasonNeedsANewEvent() {
        val hints = MeasurementGuidance()
        val failed = notice(MeasurementNotice.AIM_BELOW_TOP).copy(acquisition = AcquisitionState(
            running = true, quality = quality.copy(status = CaptureStatus.MOVING)))
        assertEquals(R.string.notice_below_top, hints.update(failed, 0))
        assertEquals(R.string.notice_below_top, hints.update(failed, 800))
        assertEquals(R.string.notice_below_top, hints.update(failed, 4_999))
        assertNull(hints.update(failed, 5_000))
        assertNull(hints.update(failed, 10_000))
        assertEquals(R.string.notice_below_top, hints.update(failed.copy(
            measurement = failed.measurement.copy(noticeId = 2)), 10_001))
        assertEquals(R.string.notice_expired, hints.update(notice(MeasurementNotice.CAPTURE_EXPIRED, 3), 10_002))
    }

    @Test fun onlyNewLostProgressNoticeWaitsForResumptionAndCannotResurfaceAgain() {
        val hints = MeasurementGuidance()
        val reset = notice(MeasurementNotice.ACQUISITION_SUSPENDED)
        assertNull(hints.update(reset.copy(resumed = false), 0))
        assertNull(hints.update(reset.copy(resumed = false), 10_000))
        assertEquals(R.string.notice_acquisition_suspended, hints.update(reset, 10_001))
        assertNull(hints.update(reset, 15_001))
        assertNull(hints.update(reset.copy(resumed = false), 16_000))
        assertNull(hints.update(reset, 17_000))
    }

    @Test fun alignmentIsSilentWhileExistingCaptureGateStillBlocksIt() {
        val hints = MeasurementGuidance()
        for (bearing in listOf(-1.0, 1.0)) {
            val misaligned = ready.copy(measurement = ready.measurement.copy(top = observation.copy(
                direction = ViewingDirection(0.0, bearing, 0.2)), stage = MeasurementStage.BOTTOM))
            assertFalse(misaligned.measurement.canCapture)
            assertNull(hints.update(misaligned, 0))
            assertNull(hints.update(misaligned, 10_000))
            assertEquals(R.string.notice_align, hints.update(misaligned.copy(measurement = misaligned.measurement.copy(
                notice = MeasurementNotice.ALIGN_WITH_TOP, noticeId = if (bearing < 0) 1 else 2)), 10_001))
            assertNull(hints.update(ready, 10_002))
        }
    }

    @Test fun accessibilityCanExtendCorrectionsAndCompletedResultsSuppressAllGuidance() {
        val hints = MeasurementGuidance { 10_000L }
        val correction = notice(MeasurementNotice.SMALL_SEPARATION)
        assertEquals(R.string.notice_small_separation, hints.update(correction, 0))
        assertEquals(R.string.notice_small_separation, hints.update(correction, 9_999))
        assertNull(hints.update(correction, 10_000))
        val result = com.malagel.viewy.domain.KnownHeightDistance.estimate(2.0,
            ViewingDirection(1.0, 0.0, 0.5), ViewingDirection(1.0, 0.0, 0.0)) as com.malagel.viewy.domain.KnownHeightResult.Estimate
        val completed = vertical.copy(measurement = vertical.measurement.copy(
            stage = MeasurementStage.RESULT, result = result,
            notice = MeasurementNotice.CAMERA_INTERRUPTED, noticeId = 2))
        assertNull(hints.update(completed, 10_001))
        assertNull(hints.update(completed, 20_000))
    }
}
