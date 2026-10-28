package com.malagel.viewy.ui

import com.malagel.viewy.R
import com.malagel.viewy.camera.CameraDirectionMapping
import com.malagel.viewy.camera.CameraPreviewState
import com.malagel.viewy.domain.*
import com.malagel.viewy.measurement.*
import com.malagel.viewy.orientation.*
import com.malagel.viewy.settings.AppPreferences
import com.malagel.viewy.tutorial.*
import org.junit.Assert.*
import org.junit.Test
import java.util.Locale

class DiagnosticsPresentationTest {
    private val method = MeasurementMethod.KNOWN_OBJECT_HEIGHT
    private val selected = MeasurementSelection().browse(method.mode).activate(method)
    private val mapping = CameraDirectionMapping.forRearCamera("rear", null, null)
    private val quality = OrientationQuality(13, 480_000_000, 20_000_000, 0.001, 0.002, null, null, CaptureStatus.READY)
    private val top = OrientationObservation(ViewingDirection(1.0, 0.0, 1.0), 1_000_000_000, "frame", OrientationSource.GAME_ROTATION_VECTOR, quality, mapping.id)
    private val bottom = top.copy(direction = ViewingDirection(1.0, 0.0, 0.0), timestampNanos = 2_000_000_000)
    private val ready = MeasurementUiState(
        selection = selected, resumed = true,
        visibility = MeasurementVisibility(surfaceVisible = true, windowFocused = true, permissionGranted = true),
        measurement = MeasurementState(heightMeters = 2.0, cameraReady = true, latest = top, frameId = "frame"),
        camera = CameraPreviewState(ready = true, messageRes = R.string.camera_ready, mapping = mapping, logicalCameraId = "0", open = true, streaming = true),
        acquisition = AcquisitionState(discoveryCompleted = true, availableSources = setOf(top.source), activeSource = top.source,
            running = true, referenceFrameId = "frame", quality = quality, observation = top),
    )
    private fun present(state: MeasurementUiState = ready, unit: HeightUnit = HeightUnit.METERS, locale: Locale = Locale.US) =
        diagnosticsPresentation(state, unit, locale, cameraPermission = true)
    private fun value(p: DiagnosticsPresentation, label: Int): DiagnosticText = p.sections.flatMap { it.rows }.first { it.label == label }.value

    @Test fun tapAlwaysRoutesToTutorialAndOnlyEnabledHoldRoutesToDiagnostics() {
        for (enabled in listOf(AppPreferences().diagnosticsEnabled, true)) {
            assertEquals(HelpAction.TUTORIAL, helpAction(ready, false, enabled))
            assertEquals(if (enabled) HelpAction.DIAGNOSTICS else HelpAction.NONE, helpAction(ready, true, enabled))
        }
        assertSame(ready, ready.withDiagnostics(true, enabled = false))
    }

    @Test fun modalGatesRejectDuplicateOrStackedHelpAndSettings() {
        val card = TutorialCard(1, method)
        for (state in listOf(ready.copy(selection = selected.openModes()),
            ready.copy(tutorial = TutorialState(card = card)), ready.copy(tutorial = TutorialState(pendingMethod = method)),
            ready.copy(selection = MeasurementSelection().dismiss()), ready.withDiagnostics(true, true))) {
            for (long in listOf(false, true)) assertEquals(HelpAction.NONE, helpAction(state, long, true))
            assertSame(state, state.withDiagnostics(true, true))
        }
        val open = ready.withDiagnostics(true, true)
        assertFalse(open.canOpenSettings)
        assertTrue(open.withDiagnostics(false).canOpenSettings)
    }

    @Test fun retainedDiagnosticsNavigationStopsEligibilityWithoutMutatingSnapshots() {
        val workflow = KnownHeightWorkflow()
        workflow.setHeight(2.0)
        workflow.cameraReady(true)
        workflow.update(top, "frame", true, top.timestampNanos)
        workflow.capture(CaptureResult.Captured(top), top.timestampNanos)
        val before = ready.copy(measurement = workflow.state)
        val open = before.withDiagnostics(true, true)
        // The retained ViewModel holds this immutable snapshot across Activity recreation.
        val retained = open.copy()
        val closed = retained.withDiagnostics(false)
        for (state in listOf(open, retained, closed)) {
            assertSame(before.measurement, state.measurement)
            assertSame(before.camera, state.camera)
            assertSame(before.acquisition, state.acquisition)
            assertSame(before.tutorial, state.tutorial)
            assertSame(workflow.state, state.measurement)
            assertTrue(state.requiresCamera)
            assertEquals(!state.diagnosticsOpen, state.acquisitionEligible)
            present(state)
            assertSame(before.measurement, state.measurement)
        }
        assertEquals(before, closed)
        // Resource reconciliation invalidates the incomplete pair immediately.
        workflow.cameraReady(false)
        workflow.update(null, null, false, top.timestampNanos + 1)
        assertNull(workflow.state.top)
        assertEquals(MeasurementNotice.CAMERA_INTERRUPTED, workflow.state.notice)
    }

    @Test fun diagnosticsDoesNotCompleteOrChangeFirstUseTutorials() {
        var marked = false
        val coordinator = TutorialCoordinator(object : TutorialSeenStore {
            override fun hasSeen(methodId: String) = marked
            override fun markSeen(methodId: String) { marked = true }
        }, setOf(method))
        val browsing = MeasurementSelection().browse(method.mode)
        coordinator.selectionChanged(browsing)
        coordinator.selectionChanged(selected, method)
        val pending = ready.copy(tutorial = coordinator.state)
        assertSame(pending, pending.withDiagnostics(true, true))
        coordinator.selectorHidden(coordinator.state.navigationToken)
        val first = coordinator.state.card!!
        assertEquals(TutorialPage.BASIC, first.page)
        assertFalse(marked)
        coordinator.dismiss(first.id)
        val closed = ready.copy(tutorial = coordinator.state).withDiagnostics(true, true).withDiagnostics(false)
        assertTrue(marked)
        assertEquals(HelpAction.TUTORIAL, helpAction(closed, false, true))
        coordinator.openManually()
        assertEquals(TutorialPage.BASIC, coordinator.state.card!!.page)
    }

    @Test fun inactiveAndUndiscoveredAreDifferentFromGenuineUnavailableHardware() {
        val inactive = ready.copy(measurement = MeasurementState(), acquisition = AcquisitionState())
        val p = present(inactive)
        assertEquals(R.string.diag_inactive_height, value(p, R.string.diag_camera).resource)
        assertEquals(R.string.diag_inactive_height, orientationReadiness(inactive))
        assertEquals(R.string.diag_not_discovered, value(p, R.string.diag_discovery).resource)
        assertEquals(R.string.diag_unavailable, value(p, R.string.diag_logical_id).resource)
        assertEquals(R.string.diag_unavailable, value(p, R.string.diag_reading_age).resource)
        assertEquals(R.string.diag_waiting_camera, orientationReadiness(ready.copy(camera = CameraPreviewState())))
        for ((failure, resource) in listOf(AcquisitionFailure.UNAVAILABLE to R.string.diag_sensor_unavailable,
            AcquisitionFailure.REGISTRATION_FAILED to R.string.diag_registration_failed)) {
            assertEquals(resource, orientationReadiness(ready.copy(acquisition = AcquisitionState(discoveryCompleted = true, failure = failure))))
        }
        assertEquals(R.string.camera_configuration_failed, value(present(ready.copy(camera = CameraPreviewState(failed = true, messageRes = R.string.camera_configuration_failed))), R.string.diag_camera_error).resource)
    }

    @Test fun captureReadinessAndQualityUseWorkflowGatesAndFriendlyReasons() {
        assertEquals(R.string.diag_capture_allowed, captureBlockReason(ready, true))
        assertEquals(R.string.camera_permission_explanation, captureBlockReason(ready, false))
        for (status in CaptureStatus.entries) {
            val observation = top.copy(quality = quality.copy(status = status))
            val state = ready.copy(measurement = ready.measurement.copy(latest = observation),
                acquisition = ready.acquisition.copy(observation = observation, quality = observation.quality))
            assertEquals(status == CaptureStatus.READY, state.measurement.canCapture)
            assertEquals(captureStatusResource(status), orientationReadiness(state))
            if (status != CaptureStatus.READY) assertEquals(captureStatusResource(status), captureBlockReason(state, true))
        }
        assertEquals(R.string.diag_waiting_reading, captureBlockReason(ready.copy(measurement = ready.measurement.copy(latest = null)), true))
        val vertical = ready.copy(measurement = ready.measurement.copy(latest = top.copy(direction = ViewingDirection(0.0, 0.0, 1.0))))
        assertEquals(R.string.diag_near_vertical, captureBlockReason(vertical, true))
        val misaligned = ready.copy(measurement = ready.measurement.copy(top = top, latest = bottom.copy(direction = ViewingDirection(0.0, 1.0, 0.0))))
        assertEquals(R.string.diag_right, captureBlockReason(misaligned, true))
    }

    @Test fun stagesAndNoticesRemainVisibleWithoutAResult() {
        for ((stage, expected) in listOf(MeasurementStage.TOP to R.string.diag_waiting_top,
            MeasurementStage.BOTTOM to R.string.diag_waiting_bottom, MeasurementStage.CORRECTION to R.string.diag_correction,
            MeasurementStage.RESULT to R.string.diag_result_ready)) {
            val state = ready.copy(measurement = ready.measurement.copy(stage = stage, notice = MeasurementNotice.CAPTURE_EXPIRED))
            val p = present(state)
            assertEquals(expected, value(p, R.string.diag_stage).resource)
            assertEquals(R.string.notice_expired, value(p, R.string.diag_notice).resource)
            assertEquals(R.string.diag_unavailable, value(p, R.string.diag_horizontal_distance).resource)
        }
    }

    @Test fun missingStaleInvalidAndIncompatibleEndpointsNeverProduceSubstituteResults() {
        val estimate = KnownHeightDistance.estimate(2.0, top.direction, bottom.direction) as KnownHeightResult.Estimate
        val result = ready.copy(measurement = ready.measurement.copy(top = top, bottom = bottom, result = estimate, stage = MeasurementStage.RESULT))
        assertEquals(R.string.diag_pending, value(present(), R.string.diag_top_capture).resource)
        assertEquals(R.string.diag_unavailable, value(present(), R.string.diag_horizontal_distance).resource)
        for (bad in listOf(
            bottom.copy(quality = quality.copy(status = CaptureStatus.STALE)),
            bottom.copy(quality = quality.copy(ageNanos = 300_000_000)),
            bottom.copy(quality = quality.copy(ageNanos = -1)),
            bottom.copy(direction = ViewingDirection(Double.NaN, 0.0, 0.0)),
            bottom.copy(direction = ViewingDirection(0.0, 0.0, 0.0)),
            bottom.copy(referenceFrameId = "old-frame"), bottom.copy(cameraMappingId = "other-lens"),
            bottom.copy(source = OrientationSource.ROTATION_VECTOR), bottom.copy(timestampNanos = top.timestampNanos),
            bottom.copy(timestampNanos = top.timestampNanos + 16_000_000_000),
        )) {
            val p = present(result.copy(measurement = result.measurement.copy(bottom = bad)))
            assertEquals(R.string.diag_invalid_capture, value(p, R.string.diag_bottom_capture).resource)
            assertEquals(R.string.diag_unavailable, value(p, R.string.diag_horizontal_distance).resource)
            assertEquals(R.string.diag_unavailable, value(p, R.string.diag_bottom_elevation).resource)
            assertEquals(R.string.diag_unavailable, value(p, R.string.diag_bottom_sensitivity).resource)
        }
        assertEquals(R.string.diag_unavailable, value(present(result.copy(measurement = result.measurement.copy(top = null))), R.string.diag_horizontal_distance).resource)
    }

    @Test fun knownHeightResultUsesStoredGeometryAndConvertsAllPhysicalQuantities() {
        val estimate = KnownHeightDistance.estimate(2.0, top.direction, bottom.direction) as KnownHeightResult.Estimate
        assertEquals(2.0, estimate.horizontalDistanceMeters, 1e-12)
        val state = ready.copy(measurement = ready.measurement.copy(top = top, bottom = bottom, result = estimate, stage = MeasurementStage.RESULT))
        val metric = present(state)
        val imperial = present(state, HeightUnit.FEET)
        for (label in listOf(R.string.object_height, R.string.diag_horizontal_distance, R.string.diag_top_sensitivity, R.string.diag_bottom_sensitivity)) {
            val a = value(metric, label).arguments
            val b = value(imperial, label).arguments
            assertEquals("m", a[1]); assertEquals("ft", b[1])
            assertEquals(a[0].toDouble() / 0.3048, b[0].toDouble(), 0.0001)
        }
        assertEquals(45.0, value(metric, R.string.diag_top_elevation).arguments[0].toDouble(), 1e-8)
        assertEquals(45.0, value(metric, R.string.diag_separation).arguments[0].toDouble(), 1e-8)
        assertEquals(1.0, value(metric, R.string.diag_capture_interval).arguments[0].toDouble(), 1e-8)
        assertEquals(20.0, value(metric, R.string.diag_top_age).arguments[0].toDouble(), 1e-8)
        assertEquals(R.string.diag_unquantified, value(metric, R.string.diag_uncertainty).resource)
        assertEquals(estimate.diagnostics.topSensitivityMetersPerRadian!! * Math.PI / 180,
            value(metric, R.string.diag_top_sensitivity).arguments[0].toDouble(), 1e-6)
        assertSame(estimate, state.measurement.result)
    }

    @Test fun localizedFormattingUnknownAccuracyPolesAndMissingDerivativesAreHonest() {
        val spanish = DiagnosticFormatting(HeightUnit.FEET, Locale.forLanguageTag("es-CL"))
        assertTrue(spanish.length(2.0).arguments[0].contains(','))
        assertEquals(R.string.diag_unavailable, spanish.length(Double.POSITIVE_INFINITY).resource)
        assertEquals(R.string.diag_unavailable, spanish.degrees(null).resource)
        assertEquals(R.string.diag_unavailable, spanish.sensitivity(null).resource)
        val pole = top.copy(direction = ViewingDirection(0.0, 0.0, 1.0))
        val p = present(ready.copy(acquisition = ready.acquisition.copy(observation = pole)))
        assertEquals(R.string.diag_unavailable, value(p, R.string.diag_bearing).resource)
        assertEquals(R.string.diag_accuracy_unknown, value(p, R.string.diag_platform_accuracy).resource)
        for ((accuracy, expected) in listOf(0 to R.string.diag_accuracy_unreliable, 1 to R.string.diag_accuracy_low,
            2 to R.string.diag_accuracy_medium, 3 to R.string.diag_accuracy_high, 99 to R.string.diag_accuracy_unknown)) {
            assertEquals(expected, value(present(ready.copy(acquisition = ready.acquisition.copy(quality = quality.copy(sensorAccuracy = accuracy)))), R.string.diag_platform_accuracy).resource)
        }
        assertEquals(R.string.diag_axis_fallback, value(present(), R.string.diag_mapping_basis).resource)
        val pose = CameraDirectionMapping.forRearCamera("rear", 1, floatArrayOf(0f, 1f, 0f, 0f))
        assertEquals(R.string.diag_lens_pose, value(present(ready.copy(camera = ready.camera.copy(mapping = pose))), R.string.diag_mapping_basis).resource)
    }

    @Test fun stoppedCompletedResultUsesCapturedPairWithoutALiveReferenceFrame() {
        val workflow = KnownHeightWorkflow()
        workflow.setHeight(2.0)
        workflow.cameraReady(true)
        workflow.update(top, "frame", true, top.timestampNanos)
        workflow.capture(CaptureResult.Captured(top), top.timestampNanos)
        workflow.update(bottom, "frame", true, bottom.timestampNanos)
        workflow.capture(CaptureResult.Captured(bottom), bottom.timestampNanos)
        val estimate = workflow.state.result!!
        workflow.cameraReady(false)
        workflow.update(null, null, false, bottom.timestampNanos + 100_000_000_000)
        val stopped = ready.copy(measurement = workflow.state, camera = ready.camera.copy(ready = false),
            acquisition = AcquisitionState(), diagnosticsOpen = true)
        assertNull(stopped.measurement.frameId)
        val p = present(stopped)
        assertEquals(R.string.diag_recorded, value(p, R.string.diag_top_capture).resource)
        assertEquals(R.string.diag_recorded, value(p, R.string.diag_bottom_capture).resource)
        assertEquals(estimate.horizontalDistanceMeters,
            value(p, R.string.diag_horizontal_distance).arguments[0].toDouble(), 1e-6)
        assertEquals(R.string.diag_result_ready, captureBlockReason(stopped, true))
        assertEquals(R.string.diag_stopped, orientationReadiness(stopped))
        assertFalse(stopped.acquisitionEligible)
    }
}
