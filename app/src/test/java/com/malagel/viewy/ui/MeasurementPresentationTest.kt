package com.malagel.viewy.ui

import com.malagel.viewy.R
import com.malagel.viewy.domain.MeasurementMethod
import com.malagel.viewy.domain.ViewingDirection
import com.malagel.viewy.measurement.*
import com.malagel.viewy.orientation.*
import com.malagel.viewy.tutorial.TutorialCoordinator
import com.malagel.viewy.tutorial.TutorialPage
import com.malagel.viewy.tutorial.TutorialSeenStore
import org.junit.Assert.*
import org.junit.Test

class MeasurementPresentationTest {
    private val quality = OrientationQuality(13, 480_000_000L, 0, 0.0, 0.0, 0.0, 3, CaptureStatus.READY)
    private val observation = OrientationObservation(ViewingDirection(1.0, 0.0, 0.0), 2_000_000_000L, "frame", OrientationSource.GAME_ROTATION_VECTOR, quality, "camera")
    private val selected = MeasurementSelection().browse(MeasurementMethod.KNOWN_OBJECT_HEIGHT.mode).activate(MeasurementMethod.KNOWN_OBJECT_HEIGHT)
    private val ready = MeasurementUiState(
        selection = selected,
        measurement = MeasurementState(heightMeters = 2.0, cameraReady = true, latest = observation),
        camera = com.malagel.viewy.camera.CameraPreviewState(ready = true),
        acquisition = AcquisitionState(running = true),
    )

    @Test fun heightIsRequiredBeforeCameraUseAndRestartRetainsIt() {
        val workflow = KnownHeightWorkflow()
        assertFalse(MeasurementUiState(selection = selected, measurement = workflow.state).needsCamera)
        workflow.setHeight(2.0)
        assertTrue(MeasurementUiState(selection = selected, measurement = workflow.state).needsCamera)
        workflow.restart()
        assertTrue(MeasurementUiState(selection = selected, measurement = workflow.state).needsCamera)
    }

    @Test fun inactiveSelectionCannotUseCameraOrSensorsEvenWithResidualWorkflowAndCameraState() {
        for (selection in listOf(MeasurementSelection(), MeasurementSelection().dismiss())) {
            val inactive = ready.copy(selection = selection)
            assertFalse(inactive.needsCamera)
            assertFalse(inactive.needsOrientation(resumed = true))
        }
    }

    @Test fun browsingAndReselectionDoNotChangeWorkflowOrResourceEligibility() {
        val method = MeasurementMethod.KNOWN_OBJECT_HEIGHT
        val browsing = selected.openModes().browse(method.mode)
        for (navigation in listOf(selected.openModes(), browsing, browsing.back(), browsing.dismiss(), browsing.activate(method))) {
            val state = ready.copy(selection = navigation)
            assertSame(ready.measurement, state.measurement)
            assertSame(ready.camera, state.camera)
            assertSame(ready.acquisition, state.acquisition)
            assertTrue(state.needsCamera)
            assertTrue(state.needsOrientation(resumed = true))
        }
    }

    @Test fun orientationRequiresHeightResumedActivityAndReadyCamera() {
        assertTrue(ready.needsOrientation(resumed = true))
        assertFalse(ready.needsOrientation(resumed = false))
        assertFalse(ready.copy(camera = ready.camera.copy(ready = false)).needsOrientation(resumed = true))
        assertFalse(ready.copy(measurement = ready.measurement.copy(heightMeters = null)).needsOrientation(resumed = true))
    }

    @Test fun tutorialActionsPreserveMeasurementAndResourceStateThroughBothPagesAndReopening() {
        val method = MeasurementMethod.KNOWN_OBJECT_HEIGHT
        val tutorials = TutorialCoordinator(object : TutorialSeenStore {
            override fun hasSeen(methodId: String) = true
            override fun markSeen(methodId: String) = Unit
        }, methodTutorials.keys)
        val browsing = MeasurementSelection().browse(method.mode)
        tutorials.selectionChanged(browsing)
        tutorials.selectionChanged(selected, method)
        tutorials.selectorHidden(tutorials.state.navigationToken)

        fun assertPreserved() {
            val state = ready.copy(tutorial = tutorials.state)
            assertSame(ready.measurement, state.measurement)
            assertSame(ready.measurement.latest, state.measurement.latest)
            assertSame(ready.camera, state.camera)
            assertSame(ready.acquisition, state.acquisition)
            assertSame(ready.selection, state.selection)
            assertTrue(state.needsCamera)
            assertTrue(state.needsOrientation(resumed = true))
        }
        tutorials.openManually()
        assertPreserved()
        val id = tutorials.state.card!!.id
        tutorials.changePage(id, TutorialPage.DETAILS)
        assertPreserved()
        tutorials.changePage(id, TutorialPage.BASIC)
        assertPreserved()
        tutorials.dismiss(id)
        assertPreserved()
        tutorials.openManually()
        assertPreserved()
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
