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
        selection = selected, resumed = true,
        visibility = MeasurementVisibility(surfaceVisible = true, windowFocused = true, permissionGranted = true),
        measurement = MeasurementState(heightMeters = 2.0, cameraReady = true, latest = observation),
        camera = com.malagel.viewy.camera.CameraPreviewState(ready = true),
        acquisition = AcquisitionState(running = true),
    )

    @Test fun previewAndHeightControlsShareVisibilityThroughStartupAndSuspension() {
        assertTrue(ready.previewVisible)
        assertTrue(ready.previewHeightEditVisible)
        val starting = ready.copy(camera = ready.camera.copy(ready = false), acquisition = AcquisitionState())
        assertFalse(starting.previewVisible)
        assertFalse(starting.previewHeightEditVisible)
        val suspended = listOf(
            ready.copy(resumed = false),
            ready.copy(visibility = ready.visibility.copy(windowFocused = false)),
            ready.copy(visibility = ready.visibility.copy(surfaceVisible = false)),
            ready.copy(visibility = ready.visibility.copy(covered = true)),
            ready.copy(visibility = ready.visibility.copy(heightEditing = true)),
            ready.copy(selection = selected.openModes()),
            ready.copy(diagnosticsOpen = true),
            ready.copy(tutorial = com.malagel.viewy.tutorial.TutorialState(pendingMethod = selected.activeMethod)),
        )
        suspended.forEach {
            assertFalse(it.previewVisible)
            assertFalse(it.previewHeightEditVisible)
        }
        assertTrue(starting.copy(camera = ready.camera, acquisition = ready.acquisition).previewHeightEditVisible)
    }

    @Test fun previewHeightEditorIsForAcquisitionAndRecoveryOnly() {
        val permission = ready.copy(visibility = ready.visibility.copy(permissionGranted = false))
        val failure = ready.copy(camera = ready.camera.copy(ready = false), cameraFailureVisible = true)
        val result = com.malagel.viewy.domain.KnownHeightDistance.estimate(2.0,
            ViewingDirection(1.0, 0.0, 0.5), observation.direction) as com.malagel.viewy.domain.KnownHeightResult.Estimate
        val completed = ready.copy(measurement = ready.measurement.copy(result = result))
        assertFalse(completed.previewHeightEditVisible) // Completed height editing belongs to the result panel.
        for (state in listOf(permission, failure)) {
            assertFalse(state.previewVisible)
            assertTrue(state.previewHeightEditVisible)
            assertFalse(state.copy(visibility = state.visibility.copy(covered = true)).previewHeightEditVisible)
            assertFalse(state.copy(visibility = state.visibility.copy(windowFocused = false)).previewHeightEditVisible)
        }
        assertFalse(ready.copy(selection = MeasurementSelection()).previewHeightEditVisible)
    }

    @Test fun capabilityIsIndependentOfHeightForegroundAndCompletion() {
        val workflow = KnownHeightWorkflow()
        assertTrue(MeasurementUiState(selection = selected).requiresCamera)
        assertFalse(ready.copy(measurement = workflow.state).acquisitionEligible)
        workflow.setHeight(2.0)
        assertTrue(ready.copy(measurement = workflow.state).acquisitionEligible)
        workflow.restart()
        assertTrue(ready.copy(measurement = workflow.state).acquisitionEligible)
        assertEquals(2.0, workflow.state.heightMeters!!, 0.0)
    }

    @Test fun inactiveOrCoveredMeasurementCannotAcquireEvenWithResidualReadyHardware() {
        val hidden = listOf(
            ready.copy(selection = MeasurementSelection()), ready.copy(selection = MeasurementSelection().dismiss()),
            ready.copy(selection = selected.openModes()),
            ready.copy(tutorial = com.malagel.viewy.tutorial.TutorialState(selectorExiting = true)),
            ready.copy(tutorial = com.malagel.viewy.tutorial.TutorialState(pendingMethod = selected.activeMethod)),
            ready.copy(diagnosticsOpen = true), ready.copy(resumed = false),
            ready.copy(visibility = ready.visibility.copy(surfaceVisible = false)),
            ready.copy(visibility = ready.visibility.copy(windowFocused = false)),
            ready.copy(visibility = ready.visibility.copy(permissionGranted = false)),
            ready.copy(visibility = ready.visibility.copy(covered = true)),
            ready.copy(visibility = ready.visibility.copy(heightEditing = true)),
        )
        assertTrue(ready.acquisitionEligible)
        hidden.forEach { assertFalse(it.toString(), it.acquisitionEligible) }
        // Camera readiness gates orientation in the resource owner, not preview startup eligibility.
        assertTrue(ready.copy(camera = ready.camera.copy(ready = false)).acquisitionEligible)
    }

    @Test fun browsingAndReselectionSuspendUntilSheetRemoval() {
        val method = MeasurementMethod.KNOWN_OBJECT_HEIGHT
        val browsing = selected.openModes().browse(method.mode)
        for (navigation in listOf(selected.openModes(), browsing, browsing.back())) {
            assertFalse(ready.copy(selection = navigation).acquisitionEligible)
        }
        assertTrue(ready.copy(selection = browsing.dismiss()).acquisitionEligible)
        assertTrue(ready.copy(selection = browsing.activate(method)).acquisitionEligible)
    }

    @Test fun tutorialPagesAndReopeningSuspendUntilDismissal() {
        val method = MeasurementMethod.KNOWN_OBJECT_HEIGHT
        val tutorials = TutorialCoordinator(object : TutorialSeenStore {
            override fun hasSeen(methodId: String) = true
            override fun markSeen(methodId: String) = Unit
        }, methodTutorials.keys)
        tutorials.selectionChanged(MeasurementSelection().browse(method.mode))
        tutorials.selectionChanged(selected, method)
        assertFalse(ready.copy(tutorial = tutorials.state).acquisitionEligible)
        tutorials.selectorHidden(tutorials.state.navigationToken)
        repeat(3) {
            assertTrue(ready.copy(tutorial = tutorials.state).acquisitionEligible)
            tutorials.openManually()
            val id = tutorials.state.card!!.id
            for (page in TutorialPage.entries) {
                tutorials.changePage(id, page)
                assertFalse(ready.copy(tutorial = tutorials.state).acquisitionEligible)
            }
            tutorials.dismiss(id)
        }
        assertTrue(ready.copy(tutorial = tutorials.state).acquisitionEligible)
    }

    @Test fun startupWaitingAndAutomaticRecoveryRemainQuietUntilUserActionIsRequired() {
        val starting = ready.copy(camera = com.malagel.viewy.camera.CameraPreviewState(),
            acquisition = AcquisitionState(running = false))
        assertTrue(starting.acquisitionEligible)
        assertFalse(starting.recoveryVisible)
        assertFalse(starting.copy(camera = starting.camera.copy(messageRes = R.string.camera_waiting)).recoveryVisible)
        val failed = starting.copy(camera = starting.camera.copy(failed = true))
        assertFalse(failed.recoveryVisible) // Automatic recovery is silent.
        assertTrue(failed.copy(cameraFailureVisible = true).recoveryVisible)
        assertTrue(starting.copy(visibility = starting.visibility.copy(permissionGranted = false)).recoveryVisible)
        assertFalse(starting.copy(selection = MeasurementSelection(),
            visibility = starting.visibility.copy(permissionGranted = false)).recoveryVisible)
        assertFalse(starting.copy(visibility = starting.visibility.copy(permissionGranted = false, windowFocused = false)).recoveryVisible)
        assertTrue(ready.copy(acquisition = AcquisitionState(running = false)).recoveryVisible)
    }

    @Test fun suspendedFailureSnapshotsNeverShowRecoveryControlsOnTheMeasurementSurface() {
        val failed = ready.copy(cameraFailureVisible = true)
        assertTrue(failed.recoveryVisible)
        listOf(
            failed.copy(resumed = false),
            failed.copy(visibility = failed.visibility.copy(covered = true)),
            failed.copy(visibility = failed.visibility.copy(heightEditing = true)),
            failed.copy(selection = selected.openModes()),
            failed.copy(tutorial = com.malagel.viewy.tutorial.TutorialState(
                card = com.malagel.viewy.tutorial.TutorialCard(1, selected.activeMethod!!))),
            failed.copy(diagnosticsOpen = true),
        ).forEach { assertFalse(it.recoveryVisible) }
    }

}
