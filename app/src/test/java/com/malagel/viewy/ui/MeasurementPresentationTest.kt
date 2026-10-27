package com.malagel.viewy.ui

import com.malagel.viewy.R
import com.malagel.viewy.domain.ViewingDirection
import com.malagel.viewy.measurement.*
import com.malagel.viewy.orientation.*
import org.junit.Assert.*
import org.junit.Test

class MeasurementPresentationTest {
    private val quality = OrientationQuality(13, 480_000_000L, 0, 0.0, 0.0, 0.0, 3, CaptureStatus.READY)
    private val observation = OrientationObservation(ViewingDirection(1.0, 0.0, 0.0), 2_000_000_000L, "frame", OrientationSource.GAME_ROTATION_VECTOR, quality, "camera")
    private val ready = MeasurementUiState(
        measurement = MeasurementState(heightMeters = 2.0, cameraReady = true, latest = observation),
        acquisition = AcquisitionState(running = true),
    )

    @Test fun heightIsRequiredBeforeCameraUseAndRestartRetainsIt() {
        val workflow = KnownHeightWorkflow()
        assertFalse(MeasurementUiState(measurement = workflow.state).needsCamera)
        workflow.setHeight(2.0, HeightUnit.FEET)
        assertTrue(MeasurementUiState(measurement = workflow.state).needsCamera)
        workflow.restart()
        assertTrue(MeasurementUiState(measurement = workflow.state).needsCamera)
    }

    @Test fun normalReadinessHasNoWarningAndResolvedLiveNoticesDisappear() {
        assertNull(contextualGuidance(ready))
        listOf(MeasurementNotice.WAIT_FOR_STABILITY, MeasurementNotice.ALIGN_WITH_TOP, MeasurementNotice.NEAR_VERTICAL).forEach {
            assertNull(contextualGuidance(ready.copy(measurement = ready.measurement.copy(notice = it))))
        }
    }

    @Test fun hintsTrackMotionAndSettlingWithoutHidingCaptureFailures() {
        assertEquals(R.string.guidance_moving, contextualGuidance(ready.copy(measurement = ready.measurement.copy(latest = observation.copy(quality = quality.copy(status = CaptureStatus.MOVING))))))
        assertEquals(R.string.guidance_settling, contextualGuidance(ready.copy(measurement = ready.measurement.copy(latest = observation.copy(quality = quality.copy(status = CaptureStatus.SETTLING))))))
        assertEquals(R.string.notice_below_top, contextualGuidance(ready.copy(measurement = ready.measurement.copy(notice = MeasurementNotice.AIM_BELOW_TOP))))
        assertEquals(R.string.notice_expired, contextualGuidance(ready.copy(measurement = ready.measurement.copy(notice = MeasurementNotice.CAPTURE_EXPIRED))))
    }

    @Test fun alignmentAndPoleGuidanceUseExistingPhysicalDirections() {
        val top = observation.copy(direction = ViewingDirection(0.0, 1.0, 0.2))
        assertEquals(R.string.guidance_left, contextualGuidance(ready.copy(measurement = ready.measurement.copy(top = top))))
        assertEquals(R.string.guidance_near_vertical, contextualGuidance(ready.copy(measurement = ready.measurement.copy(latest = observation.copy(direction = ViewingDirection(0.0, 0.0, 1.0))))))
    }

    @Test fun hardwareFailuresAreLeftToPersistentRecoveryControls() {
        assertNull(contextualGuidance(ready.copy(measurement = ready.measurement.copy(cameraReady = false))))
        assertNull(contextualGuidance(ready.copy(acquisition = AcquisitionState(running = false))))
    }
}
