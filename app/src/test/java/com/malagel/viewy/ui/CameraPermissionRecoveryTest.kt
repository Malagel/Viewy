package com.malagel.viewy.ui

import com.malagel.viewy.camera.CameraPreviewState
import com.malagel.viewy.domain.MeasurementMethod
import com.malagel.viewy.measurement.HeightDraft
import com.malagel.viewy.measurement.HeightUnit
import com.malagel.viewy.measurement.MeasurementSelection
import com.malagel.viewy.measurement.MeasurementState
import com.malagel.viewy.tutorial.TutorialState
import org.junit.Assert.*
import org.junit.Test

class CameraPermissionRecoveryTest {
    private val method = MeasurementMethod.KNOWN_OBJECT_HEIGHT
    private val waiting = MeasurementUiState(
        selection = MeasurementSelection().browse(method.mode).activate(method),
        measurement = MeasurementState(heightMeters = 2.0), resumed = true,
        visibility = MeasurementVisibility(surfaceVisible = true, windowFocused = true),
    )

    @Test fun requestingIsNotDenialAndFocusLossKeepsTheExplanationWithoutAcquiring() {
        val pending = CameraPermissionRecovery(granted = false).request()
        assertTrue(pending.requesting)
        assertFalse(pending.needsSettings)
        assertEquals(CameraPermissionAction.NONE, pending.action)
        assertSame(pending, pending.request()) // Repeated taps cannot launch another prompt.
        for (resumed in listOf(false, true)) for (focused in listOf(false, true)) {
            val behindAndroid = waiting.copy(resumed = resumed,
                visibility = waiting.visibility.copy(windowFocused = focused))
            assertTrue(behindAndroid.recoveryVisible)
            assertFalse(behindAndroid.acquisitionEligible)
            assertFalse(behindAndroid.previewVisible)
            assertFalse(behindAndroid.previewHeightEditVisible)
        }
        // Android can resume the Activity before returning its permission result.
        assertTrue(pending.refresh(false).requesting)
        assertEquals(CameraPermissionAction.NONE, pending.refresh(false).action)
    }

    @Test fun denialLeavesRecoveryVisibleAndRetryRequiresAnotherExplicitAction() {
        val denied = CameraPermissionRecovery(granted = false).request().result(false, canRequestAgain = true)
        assertFalse(denied.requesting)
        assertFalse(denied.needsSettings)
        assertEquals(CameraPermissionAction.REQUEST, denied.action)
        assertTrue(waiting.recoveryVisible)
        assertTrue(waiting.copy(resumed = false, visibility = waiting.visibility.copy(windowFocused = false)).recoveryVisible)
        assertTrue(denied.request().requesting)
    }

    @Test fun denialWithoutAnotherPromptRoutesTheSameActionToSettingsAfterTheResult() {
        val pending = CameraPermissionRecovery(granted = false).request()
        assertFalse(pending.needsSettings)
        val denied = pending.result(false, canRequestAgain = false)
        assertFalse(denied.requesting)
        assertTrue(denied.needsSettings)
        assertEquals(CameraPermissionAction.OPEN_SETTINGS, denied.action)
        assertSame(denied, denied.request())
        assertEquals(CameraPermissionAction.OPEN_SETTINGS, denied.refresh(false).action)
        val allowed = denied.refresh(true)
        assertTrue(allowed.granted)
        assertFalse(allowed.needsSettings)
        assertEquals(CameraPermissionAction.NONE, allowed.action)
    }

    @Test fun grantingAndRevocationFollowThePlatformWithoutRelaxingForegroundAcquisition() {
        val granted = CameraPermissionRecovery(granted = false).request().result(true, canRequestAgain = false)
        assertTrue(granted.granted)
        assertFalse(granted.requesting)
        assertFalse(granted.needsSettings)
        assertSame(granted, granted.request())
        val allowed = waiting.copy(visibility = waiting.visibility.copy(permissionGranted = granted.granted))
        assertFalse(allowed.recoveryVisible)
        assertTrue(allowed.acquisitionEligible)
        assertFalse(allowed.previewHeightEditVisible) // Wait for preview readiness alongside zoom.
        val streaming = allowed.copy(camera = CameraPreviewState(ready = true))
        assertTrue(streaming.previewVisible)
        assertTrue(streaming.previewHeightEditVisible)
        assertFalse(allowed.copy(resumed = false).acquisitionEligible)
        assertFalse(allowed.copy(visibility = allowed.visibility.copy(windowFocused = false)).acquisitionEligible)
        val revoked = granted.refresh(false)
        assertEquals(CameraPermissionAction.REQUEST, revoked.action)
        assertFalse(revoked.needsSettings)
    }

    @Test fun keepingTheExplanationDoesNotShowItBehindOtherAppDestinations() {
        val covered = listOf(
            waiting.copy(visibility = waiting.visibility.copy(surfaceVisible = false)),
            waiting.copy(visibility = waiting.visibility.copy(covered = true)),
            waiting.copy(visibility = waiting.visibility.copy(heightEditing = true)),
            waiting.copy(heightEditor = HeightDraft.open(2.0, HeightUnit.METERS)),
            waiting.copy(diagnosticsOpen = true),
            waiting.copy(selection = waiting.selection.openModes()),
            waiting.copy(selection = MeasurementSelection()),
            waiting.copy(measurement = MeasurementState()),
            waiting.copy(tutorial = TutorialState(pendingMethod = method)),
            waiting.copy(tutorial = TutorialState(methodEntering = true)),
        )
        for (state in covered) {
            assertFalse(state.recoveryVisible)
            assertFalse(state.copy(resumed = false, visibility = state.visibility.copy(windowFocused = false)).recoveryVisible)
            assertFalse(state.acquisitionEligible)
        }
    }
}
