package com.malagel.viewy.ui

import androidx.annotation.StringRes
import com.malagel.viewy.R
import com.malagel.viewy.domain.DistanceUncertainty
import com.malagel.viewy.domain.MeasurementMethod
import com.malagel.viewy.measurement.*
import com.malagel.viewy.orientation.*
import java.util.Locale
import kotlin.math.hypot

internal data class DiagnosticText(@param:StringRes val resource: Int, val arguments: List<String> = emptyList())
internal data class DiagnosticRow(@param:StringRes val label: Int, val value: DiagnosticText)
internal data class DiagnosticSection(@param:StringRes val title: Int, val rows: List<DiagnosticRow>, @param:StringRes val explanation: Int? = null)
internal data class DiagnosticsPresentation(val sections: List<DiagnosticSection>)

/** Formatting only: meters/radians stay in the workflow, including stored result identity. */
internal class DiagnosticFormatting(private val unit: HeightUnit, private val locale: Locale) {
    fun text(@StringRes resource: Int) = DiagnosticText(resource)
    fun raw(value: String?) = value?.takeIf { it.isNotBlank() }?.let { DiagnosticText(R.string.diag_value, listOf(it)) }
        ?: text(R.string.diag_unavailable)
    fun number(value: Double?, @StringRes resource: Int = R.string.diag_value, suffix: String? = null): DiagnosticText {
        if (value == null || !value.isFinite()) return text(R.string.diag_unavailable)
        val arguments = listOf(String.format(locale, "%.6g", value)) + listOfNotNull(suffix)
        return DiagnosticText(resource, arguments)
    }
    fun degrees(radians: Double?) = number(radians?.let(Math::toDegrees), R.string.diag_degrees)
    fun length(meters: Double?) = number(meters?.let(unit::fromMeters), R.string.diag_length, unit.label)
    // dD/d(theta in degrees) = dD/d(theta in radians) * pi/180.
    fun sensitivity(metersPerRadian: Double?) = number(metersPerRadian?.times(Math.PI / 180.0)?.let(unit::fromMeters), R.string.diag_sensitivity, unit.label)
    fun milliseconds(nanos: Long?) = number(nanos?.takeIf { it >= 0 }?.div(1e6), R.string.diag_milliseconds)
    fun seconds(nanos: Long?) = number(nanos?.takeIf { it >= 0 }?.div(1e9), R.string.diag_seconds)
}

@StringRes
internal fun captureStatusResource(status: CaptureStatus?) = when (status) {
    null, CaptureStatus.WAITING -> R.string.diag_waiting_reading
    CaptureStatus.SETTLING -> R.string.guidance_settling
    CaptureStatus.MOVING -> R.string.guidance_moving
    CaptureStatus.STALE -> R.string.diag_stale
    CaptureStatus.UNRELIABLE -> R.string.guidance_unreliable
    CaptureStatus.READY -> R.string.diag_ready
}

@StringRes
private fun sourceResource(source: OrientationSource) = when (source) {
    OrientationSource.GAME_ROTATION_VECTOR -> R.string.diag_game_vector
    OrientationSource.ROTATION_VECTOR -> R.string.diag_rotation_vector
}

@StringRes
private fun interruptionResource(interruption: Interruption) = when (interruption) {
    Interruption.STARTED -> R.string.diag_started
    Interruption.STOPPED -> R.string.diag_stopped
    Interruption.MALFORMED_READING -> R.string.diag_malformed
    Interruption.INVALID_TIMESTAMP -> R.string.diag_invalid_timestamp
    Interruption.SAMPLE_GAP -> R.string.diag_sample_gap
    Interruption.ABRUPT_ROTATION -> R.string.diag_abrupt_rotation
    Interruption.PLATFORM_DISCONTINUITY -> R.string.diag_discontinuity
    Interruption.ACCURACY_CHANGED -> R.string.diag_accuracy_changed
}

/** Uses the same prerequisite distinction as acquisition; no hardware discovery is requested here. */
@StringRes
internal fun orientationReadiness(state: MeasurementUiState): Int = when {
    !state.needsCamera -> R.string.diag_inactive_height
    !state.camera.ready -> R.string.diag_waiting_camera
    state.acquisition.failure == AcquisitionFailure.UNAVAILABLE -> R.string.diag_sensor_unavailable
    state.acquisition.failure == AcquisitionFailure.REGISTRATION_FAILED -> R.string.diag_registration_failed
    !state.acquisition.running -> R.string.diag_waiting_session
    else -> captureStatusResource(state.acquisition.quality?.status)
}

@StringRes
internal fun captureBlockReason(state: MeasurementUiState, cameraPermission: Boolean): Int {
    val m = state.measurement
    return when {
        !state.needsCamera -> R.string.diag_inactive_height
        m.stage == MeasurementStage.RESULT -> R.string.diag_result_ready
        !cameraPermission -> R.string.camera_permission_explanation
        !state.camera.ready -> state.camera.messageRes
        !state.acquisition.running -> orientationReadiness(state)
        m.latest?.quality?.status != CaptureStatus.READY -> captureStatusResource(m.latest?.quality?.status)
        m.latest.direction.let { hypot(it.x, it.y) < MeasurementPolicy.minimumHorizontalNorm } -> R.string.guidance_near_vertical
        m.alignment == Alignment.TURN_LEFT -> R.string.guidance_left
        m.alignment == Alignment.TURN_RIGHT -> R.string.guidance_right
        m.alignment == Alignment.NEAR_VERTICAL -> R.string.guidance_near_vertical
        m.canCapture -> R.string.diag_capture_allowed
        else -> R.string.diag_waiting_reading
    }
}

/** Captured steadiness is retained, but invalid or incompatible residual inputs are never displayed as results. */
internal fun validDiagnosticEndpoint(observation: OrientationObservation?, state: MeasurementUiState): OrientationObservation? = observation?.takeIf {
    val d = it.direction
    d.x.isFinite() && d.y.isFinite() && d.z.isFinite() && hypot(hypot(d.x, d.y), d.z).let { norm -> norm.isFinite() && norm > 0 } &&
        it.timestampNanos > 0 && it.quality.status == CaptureStatus.READY &&
        it.quality.ageNanos?.let { age -> age in 0..AcquisitionPolicy().maximumAgeNanos } == true &&
        it.referenceFrameId == state.measurement.frameId &&
        (state.acquisition.activeSource == null || it.source == state.acquisition.activeSource) &&
        (state.camera.mapping == null || it.cameraMappingId == state.camera.mapping.id)
}

/** Pure projection of the already-published snapshot. No clocks, geometry estimates or session calls. */
internal fun diagnosticsPresentation(state: MeasurementUiState, unit: HeightUnit, locale: Locale, cameraPermission: Boolean): DiagnosticsPresentation {
    val f = DiagnosticFormatting(unit, locale)
    fun row(label: Int, value: DiagnosticText) = DiagnosticRow(label, value)
    fun status(label: Int, value: Int) = row(label, f.text(value))
    fun flag(value: Boolean) = f.text(if (value) R.string.diag_yes else R.string.diag_no)
    val m = state.measurement
    val camera = state.camera
    val a = state.acquisition
    val active = state.needsCamera
    val quality = a.quality.takeIf { active && a.running }
    val latest = a.observation.takeIf { active && a.running }
    val measurement = DiagnosticSection(R.string.diag_measurement_status, listOf(
        status(R.string.diag_stage, when (m.stage) {
            MeasurementStage.TOP -> R.string.diag_waiting_top
            MeasurementStage.BOTTOM -> R.string.diag_waiting_bottom
            MeasurementStage.CORRECTION -> R.string.diag_correction
            MeasurementStage.RESULT -> R.string.diag_result_ready
        }),
        row(R.string.diag_valid_height, flag(m.heightMeters?.let { it.isFinite() && it > 0 } == true)),
        status(R.string.diag_camera, when {
            !active -> R.string.diag_inactive_height
            !cameraPermission -> R.string.camera_permission_explanation
            else -> camera.messageRes
        }),
        status(R.string.diag_orientation, orientationReadiness(state)),
        row(R.string.diag_capture_eligible, flag(m.canCapture && active && cameraPermission && camera.ready && a.running)),
        status(R.string.diag_capture_reason, captureBlockReason(state, cameraPermission)),
        row(R.string.diag_notice, m.notice?.let { f.text(noticeResource(it)) } ?: f.text(R.string.diag_none)),
    ))
    val cameraSection = DiagnosticSection(R.string.diag_camera_information, listOf(
        status(R.string.diag_preview, when {
            !active -> R.string.diag_inactive_height
            !cameraPermission -> R.string.camera_permission_explanation
            else -> camera.messageRes
        }),
        row(R.string.diag_camera_open, if (active) flag(camera.open) else f.text(R.string.diag_inactive)),
        row(R.string.diag_streaming, if (active) flag(camera.streaming) else f.text(R.string.diag_inactive)),
        row(R.string.diag_logical_id, f.raw(camera.logicalCameraId.takeIf { active })),
        row(R.string.diag_physical_id, f.raw(camera.physicalCameraId.takeIf { active })),
        row(R.string.diag_mapping_id, f.raw(camera.mapping?.id.takeIf { active })),
        row(R.string.diag_device_direction, camera.mapping?.deviceDirection?.takeIf { active }?.let {
            DiagnosticText(R.string.diag_vector, listOf(it.x, it.y, it.z).map { n -> String.format(locale, "%.6g", n) })
        } ?: f.text(R.string.diag_unavailable)),
        row(R.string.diag_mapping_basis, camera.mapping?.takeIf { active }?.let {
            f.text(if (it.usesLensPose) R.string.diag_lens_pose else R.string.diag_axis_fallback)
        } ?: f.text(R.string.diag_unavailable)),
        row(R.string.diag_camera_error, if (active && camera.failed) f.text(camera.messageRes) else f.text(R.string.diag_none)),
        row(R.string.diag_error_detail, f.raw(camera.errorDetail.takeIf { active && camera.failed })),
    ), R.string.diag_camera_limits)
    val sensorRows = mutableListOf(
        row(R.string.diag_active_source, a.activeSource?.takeIf { active && a.running }?.let { f.text(sourceResource(it)) } ?: f.text(R.string.diag_unavailable)),
        row(R.string.diag_sensor_name, f.raw(a.sensorName.takeIf { active && a.running })),
        status(R.string.diag_discovery, if (a.discoveryCompleted) R.string.diag_discovered else R.string.diag_not_discovered),
    )
    if (a.discoveryCompleted) OrientationSource.entries.forEach { source -> sensorRows += row(sourceResource(source), flag(source in a.availableSources)) }
    sensorRows += listOf(
        row(R.string.diag_running, flag(active && a.running)),
        row(R.string.diag_startup_fallback, if (active && a.running) flag(a.startupFallbackUsed) else f.text(R.string.diag_unavailable)),
        status(R.string.diag_quality, orientationReadiness(state)),
        row(R.string.diag_reading_age, f.milliseconds(quality?.ageNanos)),
        row(R.string.diag_sample_count, f.raw(quality?.sampleCount?.let { String.format(locale, "%d", it) })),
        row(R.string.diag_window, f.milliseconds(quality?.spanNanos)),
        row(R.string.diag_rms, f.degrees(quality?.rmsVariationRadians)),
        row(R.string.diag_maximum, f.degrees(quality?.maximumVariationRadians)),
        row(R.string.diag_step, f.degrees(quality?.latestStepRadians)),
        row(R.string.diag_elevation, f.degrees(latest?.direction?.elevationRadians())),
        row(R.string.diag_bearing, f.degrees(latest?.direction?.bearingRadians())),
        status(R.string.diag_platform_accuracy, when (quality?.sensorAccuracy) {
            0 -> R.string.diag_accuracy_unreliable
            1 -> R.string.diag_accuracy_low
            2 -> R.string.diag_accuracy_medium
            3 -> R.string.diag_accuracy_high
            else -> R.string.diag_accuracy_unknown
        }),
        row(R.string.diag_frame_id, f.raw(a.referenceFrameId.takeIf { active && a.running })),
        row(R.string.diag_mapping_id, f.raw(latest?.cameraMappingId)),
        row(R.string.diag_interruption, a.interruption?.let { f.text(interruptionResource(it)) } ?: f.text(R.string.diag_none)),
        row(R.string.diag_timestamp, latest?.timestampNanos?.let { DiagnosticText(R.string.diag_nanoseconds, listOf(it.toString())) } ?: f.text(R.string.diag_unavailable)),
    )
    val generic = listOf(measurement, cameraSection,
        DiagnosticSection(R.string.diag_sensor_information, sensorRows.toList(), R.string.diag_sensor_limits))
    val methodSections = when (state.selection.activeMethod) {
        MeasurementMethod.KNOWN_OBJECT_HEIGHT -> listOf(knownHeightDiagnostics(state, f))
        null -> emptyList()
    }
    return DiagnosticsPresentation(generic + methodSections)
}

/** Method content stays independent of generic acquisition/camera sections; never reruns geometry. */
private fun knownHeightDiagnostics(state: MeasurementUiState, f: DiagnosticFormatting): DiagnosticSection {
    val m = state.measurement
    val top = validDiagnosticEndpoint(m.top, state)
    val bottom = validDiagnosticEndpoint(m.bottom, state)
    val pair = if (top != null && bottom != null) compareObservations(top, bottom) as? ObservationComparison.Comparable else null
    val result = m.result.takeIf { pair != null }
    val d = result?.diagnostics
    fun row(label: Int, value: DiagnosticText) = DiagnosticRow(label, value)
    fun endpoint(observation: OrientationObservation?, valid: OrientationObservation?) = f.text(when {
        observation == null -> R.string.diag_pending
        valid == null -> R.string.diag_invalid_capture
        else -> R.string.diag_recorded
    })
    return DiagnosticSection(R.string.known_height_method, listOf(
        row(R.string.object_height, f.length(m.heightMeters)),
        row(R.string.diag_top_capture, endpoint(m.top, top)),
        row(R.string.diag_bottom_capture, endpoint(m.bottom, bottom.takeIf { pair != null })),
        row(R.string.diag_top_elevation, f.degrees(d?.topElevationRadians ?: top?.direction?.elevationRadians())),
        row(R.string.diag_bottom_elevation, f.degrees(d?.bottomElevationRadians ?: bottom?.takeIf { pair != null }?.direction?.elevationRadians())),
        row(R.string.diag_separation, f.degrees(d?.elevationSeparationRadians)),
        row(R.string.diag_bearing_difference, f.degrees(d?.signedBearingDifferenceRadians)),
        row(R.string.diag_alignment_error, f.degrees(d?.alignmentErrorRadians)),
        row(R.string.diag_capture_interval, f.seconds(pair?.elapsedNanos)),
        row(R.string.diag_top_age, f.milliseconds(top?.quality?.ageNanos)),
        row(R.string.diag_top_rms, f.degrees(top?.quality?.rmsVariationRadians)),
        row(R.string.diag_top_maximum, f.degrees(top?.quality?.maximumVariationRadians)),
        row(R.string.diag_bottom_age, f.milliseconds(bottom?.takeIf { pair != null }?.quality?.ageNanos)),
        row(R.string.diag_bottom_rms, f.degrees(bottom?.takeIf { pair != null }?.quality?.rmsVariationRadians)),
        row(R.string.diag_bottom_maximum, f.degrees(bottom?.takeIf { pair != null }?.quality?.maximumVariationRadians)),
        row(R.string.diag_horizontal_distance, f.length(result?.horizontalDistanceMeters)),
        row(R.string.diag_top_sensitivity, f.sensitivity(d?.topSensitivityMetersPerRadian)),
        row(R.string.diag_bottom_sensitivity, f.sensitivity(d?.bottomSensitivityMetersPerRadian)),
        row(R.string.diag_uncertainty, f.text(when (result?.uncertainty) {
            is DistanceUncertainty.Bounded -> R.string.diag_conditional_bounds
            is DistanceUncertainty.Unbounded -> R.string.diag_unbounded
            DistanceUncertainty.NumericallyIndeterminate -> R.string.diag_indeterminate
            else -> R.string.diag_unquantified
        })),
    ), R.string.diag_method_limits)
}
