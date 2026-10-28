package com.malagel.viewy.ui

import androidx.annotation.StringRes
import com.malagel.viewy.R
import com.malagel.viewy.measurement.*
import com.malagel.viewy.orientation.CaptureStatus
import com.malagel.viewy.measurement.Alignment as AimAlignment

/** Recovery controls handle hardware failures; readiness needs no warning or repeated stage text. */
@StringRes
internal fun contextualGuidance(state: MeasurementUiState): Int? {
    val measurement = state.measurement
    if (measurement.result != null || !measurement.cameraReady || !state.acquisition.running) return null
    if (measurement.latest?.direction?.let { kotlin.math.hypot(it.x, it.y) < MeasurementPolicy.minimumHorizontalNorm } == true) return R.string.guidance_near_vertical
    when (measurement.latest?.quality?.status) {
        CaptureStatus.MOVING -> return R.string.guidance_moving
        CaptureStatus.STALE, CaptureStatus.WAITING, null -> return R.string.guidance_waiting
        CaptureStatus.UNRELIABLE -> return R.string.guidance_unreliable
        CaptureStatus.SETTLING -> return R.string.guidance_settling
        CaptureStatus.READY -> Unit
    }
    when (measurement.alignment) {
        AimAlignment.TURN_LEFT -> return R.string.guidance_left
        AimAlignment.TURN_RIGHT -> return R.string.guidance_right
        AimAlignment.NEAR_VERTICAL -> return R.string.guidance_near_vertical
        else -> Unit
    }
    return measurement.notice?.takeUnless {
        // These describe live conditions; don't retain them after the user corrects their aim.
        it == MeasurementNotice.WAIT_FOR_STABILITY || it == MeasurementNotice.NEAR_VERTICAL || it == MeasurementNotice.ALIGN_WITH_TOP
    }?.let(::noticeResource)
}

@StringRes
internal fun noticeResource(notice: MeasurementNotice): Int = when (notice) {
    MeasurementNotice.CAMERA_INTERRUPTED -> R.string.notice_camera_interrupted
    MeasurementNotice.SESSION_CHANGED -> R.string.notice_session_changed
    MeasurementNotice.CAPTURE_EXPIRED -> R.string.notice_expired
    MeasurementNotice.WAIT_FOR_STABILITY -> R.string.notice_wait_stability
    MeasurementNotice.NEAR_VERTICAL -> R.string.notice_near_vertical
    MeasurementNotice.INCOMPATIBLE_CAPTURES -> R.string.notice_incompatible
    MeasurementNotice.ALIGN_WITH_TOP -> R.string.notice_align
    MeasurementNotice.AIM_BELOW_TOP -> R.string.notice_below_top
    MeasurementNotice.UNUSABLE_ENDPOINTS -> R.string.notice_unusable
    MeasurementNotice.SMALL_SEPARATION -> R.string.notice_small_separation
    MeasurementNotice.SENSOR_RESTARTED -> R.string.notice_sensor_restarted
}
