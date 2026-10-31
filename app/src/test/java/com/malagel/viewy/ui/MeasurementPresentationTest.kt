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

    @Test fun systemBackRestartsOnlyAnActiveCompletedMeasurement() {
        val result = com.malagel.viewy.domain.KnownHeightDistance.estimate(2.0,
            ViewingDirection(1.0, 0.0, 0.5), observation.direction) as com.malagel.viewy.domain.KnownHeightResult.Estimate
        val completed = ready.copy(measurement = ready.measurement.copy(
            stage = MeasurementStage.RESULT, result = result, resultOrigin = result))
        assertTrue(completed.backRestartsMeasurement)
        for (stage in listOf(MeasurementStage.TOP, MeasurementStage.BOTTOM, MeasurementStage.CORRECTION)) {
            assertFalse(ready.copy(measurement = ready.measurement.copy(stage = stage)).backRestartsMeasurement)
        }
        assertFalse(completed.copy(selection = selected.back()).backRestartsMeasurement)
        assertFalse(completed.copy(selection = selected.openModes()).backRestartsMeasurement)
        // Once Restart clears completion, Back returns to height entry instead.
        val workflow = KnownHeightWorkflow().apply { setHeight(2.0); restart() }
        val restarted = completed.copy(measurement = workflow.state)
        assertFalse(restarted.backRestartsMeasurement)
        assertTrue(restarted.backOpensHeightEditor)
        assertTrue(restarted.acquisitionEligible)
        assertEquals(selected, restarted.selection)
        assertEquals(2.0, restarted.measurement.heightMeters!!, 0.0)
    }

    @Test fun systemBackReturnsConfiguredCameraStagesToHeightEntry() {
        for (stage in listOf(MeasurementStage.TOP, MeasurementStage.BOTTOM, MeasurementStage.CORRECTION)) {
            val camera = ready.copy(measurement = ready.measurement.copy(stage = stage))
            assertTrue(camera.backOpensHeightEditor)
            assertFalse(camera.copy(selection = selected.back()).backOpensHeightEditor)
            assertFalse(camera.copy(selection = selected.openModes()).backOpensHeightEditor)
        }
        assertFalse(ready.copy(measurement = MeasurementState()).backOpensHeightEditor)
        val result = com.malagel.viewy.domain.KnownHeightDistance.estimate(2.0,
            ViewingDirection(1.0, 0.0, 0.5), observation.direction) as com.malagel.viewy.domain.KnownHeightResult.Estimate
        assertFalse(ready.copy(measurement = ready.measurement.copy(
            stage = MeasurementStage.RESULT, result = result)).backOpensHeightEditor)
    }

    @Test fun inactiveOrCoveredMeasurementCannotAcquireEvenWithResidualReadyHardware() {
        val hidden = listOf(
            ready.copy(selection = MeasurementSelection()), ready.copy(selection = MeasurementSelection().openModes()),
            ready.copy(selection = selected.openModes()),
            ready.copy(tutorial = com.malagel.viewy.tutorial.TutorialState(methodEntering = true)),
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

    @Test fun homeAndMethodsSuspendAcquisitionUntilMeasurementEntry() {
        val method = MeasurementMethod.KNOWN_OBJECT_HEIGHT
        val browsing = selected.openModes().browse(method.mode)
        for (navigation in listOf(selected.openModes(), browsing, browsing.back())) {
            assertFalse(ready.copy(selection = navigation).acquisitionEligible)
        }
        assertFalse(ready.copy(selection = browsing.openModes()).acquisitionEligible)
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
        tutorials.methodEntered(tutorials.state.navigationToken)
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
        assertFalse(ready.copy(acquisition = AcquisitionState(running = false)).recoveryVisible)
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

    @Test fun editorAllowsNavigationWhileItsAcquisitionAndInputGatesRemainIndependent() {
        val draft = HeightDraft.open(2.0, HeightUnit.METERS)
        val editing = ready.copy(heightEditor = draft, visibility = ready.visibility.copy(heightEditing = true))
        assertTrue(editing.canOpenSettings)
        assertTrue(editing.canOpenHelp)
        assertTrue(editing.canEditHeight)
        assertEquals(HelpAction.TUTORIAL, helpAction(editing, false, false))
        assertFalse(editing.canCapture)
        assertFalse(editing.acquisitionEligible)
        val covered = listOf(
            editing.copy(visibility = editing.visibility.copy(covered = true)),
            editing.copy(resumed = false),
            editing.copy(tutorial = com.malagel.viewy.tutorial.TutorialState(
                card = com.malagel.viewy.tutorial.TutorialCard(1, selected.activeMethod!!))),
            editing.withDiagnostics(true, true),
        )
        covered.forEach {
            assertSame(draft, it.heightEditor)
            assertFalse(it.canEditHeight)
            assertFalse(it.acquisitionEligible)
        }
        val home = editing.copy(selection = selected.openModes(), heightEditor = null, measurement = MeasurementState())
        assertFalse(home.acquisitionEligible)
        assertFalse(home.heightConfigured)
        assertNull(home.heightEditor)
    }

    @Test fun instructionsDisappearDuringEditingAndReturnAccordingToCurrentCaptureStage() {
        assertEquals(R.string.measurement_stage_top, stageInstruction(ready.measurement, false))
        val bottom = ready.measurement.copy(stage = MeasurementStage.BOTTOM, top = observation)
        assertEquals(R.string.measurement_stage_bottom, stageInstruction(bottom, false))
        assertNull(stageInstruction(ready.measurement, true))
        assertNull(stageInstruction(bottom, true))
        assertNull(stageInstruction(MeasurementState(), false))
        val result = com.malagel.viewy.domain.KnownHeightDistance.estimate(2.0,
            ViewingDirection(1.0, 0.0, 0.5), observation.direction) as com.malagel.viewy.domain.KnownHeightResult.Estimate
        assertNull(stageInstruction(ready.measurement.copy(result = result, stage = MeasurementStage.RESULT), false))
    }
    @Test fun workspaceIdentityChangesOnlyWhenEnteringANewMethodActivation() {
        val tutorial = com.malagel.viewy.tutorial.TutorialState()
        val fresh = MeasurementState()
        val home = MeasurementUiState()
        val methods = home.navigateTo(home.selection.browse(selected.activeMethod!!.mode), tutorial, fresh)
        assertEquals(home.methodActivationId, methods.methodActivationId)
        val entered = methods.navigateTo(selected, tutorial, fresh)
        assertTrue(entered.methodActivationId > methods.methodActivationId)
        assertEquals(entered.methodActivationId, entered.navigateTo(selected, tutorial, fresh).methodActivationId)
        val edited = entered.copy(measurement = ready.measurement, heightEditor = HeightDraft.open(2.0, HeightUnit.METERS))
        val settings = edited.copy(visibility = edited.visibility.copy(covered = true))
        val help = edited.copy(tutorial = tutorial.copy(card = com.malagel.viewy.tutorial.TutorialCard(1, selected.activeMethod!!)))
        for (covered in listOf(settings, help, edited.copy())) {
            assertEquals(entered.methodActivationId, covered.methodActivationId)
            assertSame(edited.heightEditor, covered.heightEditor)
        }
        val exiting = edited.navigateTo(selected.openModes(), tutorial, fresh)
        assertEquals(entered.methodActivationId, exiting.methodActivationId) // Outgoing content can finish its fade.
        val browsed = exiting.navigateTo(exiting.selection.browse(selected.activeMethod!!.mode), tutorial, fresh)
        val reentered = browsed.navigateTo(selected, tutorial, fresh)
        assertTrue(reentered.methodActivationId > entered.methodActivationId)
        assertNull(reentered.measurement.heightMeters)
        assertNull(reentered.measurement.result)
        assertNull(reentered.heightEditor)
    }

    @Test fun homeAndBackClearLiveHeightWhileOutgoingCameraOrResultSnapshotsStayConsistent() {
        val tutorial = com.malagel.viewy.tutorial.TutorialState()
        val result = com.malagel.viewy.domain.KnownHeightDistance.estimate(2.0,
            ViewingDirection(1.0, 0.0, 0.5), observation.direction) as com.malagel.viewy.domain.KnownHeightResult.Estimate
        for (completed in listOf(false, true)) for (goHome in listOf(false, true)) {
            val methods = MeasurementUiState().navigateTo(selected.openModes().browse(selected.activeMethod!!.mode), tutorial, MeasurementState())
            val active = methods.navigateTo(selected, tutorial, ready.measurement.copy(
                stage = if (completed) MeasurementStage.RESULT else MeasurementStage.TOP,
                result = result.takeIf { completed }, resultOrigin = result.takeIf { completed }))
            val outgoing = active.measurement
            val destination = if (goHome) active.selection.openModes() else active.selection.back()
            val exited = active.navigateTo(destination, tutorial, MeasurementState())
            assertNull(exited.measurement.heightMeters)
            assertFalse(exited.acquisitionEligible)
            assertEquals(2.0, outgoing.heightMeters!!, 0.0) // Displayed camera labels read this snapshot's height.
            if (completed) assertSame(result, outgoing.result)
            val methodsAgain = if (goHome) exited.navigateTo(exited.selection.browse(selected.activeMethod!!.mode), tutorial, MeasurementState()) else exited
            val incoming = methodsAgain.navigateTo(selected, tutorial, MeasurementState())
            assertNotEquals(active.methodActivationId, incoming.methodActivationId)
            assertNull(incoming.measurement.result)
            assertFalse(incoming.heightConfigured)
        }
    }
}
