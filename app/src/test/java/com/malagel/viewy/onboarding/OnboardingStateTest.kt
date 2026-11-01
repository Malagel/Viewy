package com.malagel.viewy.onboarding

import com.malagel.viewy.camera.CameraDirectionMapping
import com.malagel.viewy.camera.CameraPreviewState
import com.malagel.viewy.domain.MeasurementMethod
import com.malagel.viewy.measurement.KnownHeightWorkflow
import com.malagel.viewy.measurement.MeasurementSelection
import com.malagel.viewy.settings.AppPreferences
import com.malagel.viewy.ui.MeasurementResources
import com.malagel.viewy.ui.MeasurementUiState
import com.malagel.viewy.ui.MeasurementVisibility
import org.junit.Assert.*
import org.junit.Test

class OnboardingStateTest {
    private val camera = OnboardingPermission.CAMERA
    private fun ready() = OnboardingState()

    @Test fun welcomeViewModelCanAdvanceImmediatelyWithoutDiscovery() {
        val model = OnboardingViewModel()
        assertEquals(OnboardingPage.WELCOME, model.state.value.page)
        assertTrue(model.state.value.canContinue)
        model.next(OnboardingPage.WELCOME)
        assertEquals(OnboardingPage.PERMISSIONS, model.state.value.page)
        assertTrue(model.request(camera))
        assertFalse(model.state.value.canContinue)
        model.result(camera, false)
        assertTrue(model.state.value.canContinue)
        model.next(OnboardingPage.PERMISSIONS)
        assertEquals(OnboardingPage.COMPLETION, model.state.value.page)
    }

    @Test fun freshAndRetainedNavigationNeverCompletesOnboarding() {
        val fresh = OnboardingState()
        assertEquals(OnboardingPage.WELCOME, fresh.page)
        assertTrue(fresh.canContinue)
        assertEquals(OnboardingPage.PERMISSIONS, ready().next().page)
        val retained = ready().next().next()
        // This immutable state is retained by the Activity-owned ViewModel on recreation.
        assertEquals(OnboardingPage.COMPLETION, retained.page)
        assertEquals(OnboardingPage.PERMISSIONS, retained.back().page)
        assertEquals(ready(), retained.back().back())
        assertEquals(fresh, fresh.back())
        assertEquals(retained, retained.next())
        assertFalse(AppPreferences().onboardingCompleted)
        // Process restart constructs fresh temporary state; no page implies persistence.
        assertEquals(OnboardingPage.WELCOME, OnboardingState().page)
    }

    @Test fun staleButtonCallbacksCannotSkipPagesOrOverlapPermissionRequests() {
        val permissions = ready().next(OnboardingPage.WELCOME)
        assertSame(permissions, permissions.next(OnboardingPage.WELCOME))
        val completion = permissions.next(OnboardingPage.PERMISSIONS)
        val returning = completion.back(OnboardingPage.COMPLETION)
        assertSame(returning, returning.back(OnboardingPage.COMPLETION))
        val requesting = permissions.request(camera)
        assertSame(requesting, requesting.request(camera))
        assertSame(requesting, requesting.next())
        assertSame(requesting, requesting.back())
        assertSame(requesting, requesting.refresh(camera, false))
        val welcome = OnboardingState()
        assertSame(welcome, welcome.request(camera))
        assertSame(completion, completion.request(camera))
    }

    @Test fun grantedDeniedSkippedAndSettingsReturnAllAllowContinuation() {
        val permissions = ready().next()
        for (state in listOf(permissions, permissions.refresh(camera, true),
            permissions.request(camera).result(camera, true), permissions.request(camera).result(camera, false))) {
            assertFalse(state.requesting)
            assertEquals(OnboardingPage.COMPLETION, state.next().page)
            assertFalse(AppPreferences().onboardingCompleted)
        }
        val granted = permissions.refresh(camera, true)
        assertSame(granted, granted.request(camera))
        assertEquals(PermissionStatus.NOT_REQUESTED, granted.refresh(camera, false).permissions[camera])
        val denied = permissions.request(camera).result(camera, false)
        assertEquals(PermissionStatus.DENIED, denied.back().next().permissions[camera])
        assertSame(denied, denied.refresh(camera, false))
        assertEquals(PermissionStatus.GRANTED, denied.refresh(camera, true).permissions[camera])
    }

    @Test fun onlyTheCurrentCompletionPageCanRequestPersistence() {
        var writes = 0
        val permissions = ready().next()
        val completion = permissions.next()
        ready().finish { writes++ }
        permissions.finish { writes++ }
        completion.back().finish { writes++ } // Obsolete final-button callback after Back.
        assertEquals(0, writes)
        completion.finish { writes++ }
        assertEquals(1, writes)
    }

    @Test fun onboardingAndHomeDoNotAcquireEvenWithPermissionAndLaterMethodRecoveryStillWorks() {
        val workflow = KnownHeightWorkflow()
        var starts = 0
        val resources = MeasurementResources(workflow, { starts++ }, {}, { _, _ -> error("Unexpected retry") })
        val visibility = MeasurementVisibility(surfaceVisible = true, windowFocused = true)
        for (granted in listOf(false, true)) {
            for (page in OnboardingPage.entries) {
                val onboarding = OnboardingState(page).refresh(camera, granted)
                val measurement = MeasurementUiState(resumed = true, visibility = visibility.copy(
                    permissionGranted = onboarding.permissions[camera] == PermissionStatus.GRANTED))
                resources.reconcile(measurement.acquisitionEligible, measurement.requiresOrientation, false)
                assertNull(resources.startPreview {})
            }
        }
        assertEquals(0, starts)
        val method = MeasurementMethod.KNOWN_OBJECT_HEIGHT
        workflow.setHeight(2.0)
        val denied = MeasurementUiState(selection = MeasurementSelection().browse(method.mode).activate(method),
            measurement = workflow.state, resumed = true, visibility = visibility)
        assertTrue(denied.recoveryVisible)
        assertFalse(denied.acquisitionEligible)
        // The existing measurement launcher/Settings resume supplies the later grant.
        val allowed = denied.copy(visibility = visibility.copy(permissionGranted = true))
        assertTrue(allowed.acquisitionEligible)
        resources.reconcile(allowed.acquisitionEligible, allowed.requiresOrientation, false)
        val token = resources.startPreview {}!!
        val mapping = CameraDirectionMapping.forRearCamera("rear", null, null)
        resources.previewChanged(token, CameraPreviewState(ready = true, open = true, streaming = true, mapping = mapping))
        assertEquals(1, starts)
        resources.clear()
    }
}
