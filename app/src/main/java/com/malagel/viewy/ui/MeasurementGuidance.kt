package com.malagel.viewy.ui

import androidx.annotation.StringRes
import com.malagel.viewy.R
import com.malagel.viewy.measurement.*
import com.malagel.viewy.orientation.CaptureStatus
import kotlin.math.hypot

/** Presents workflow failures, persistent angle restrictions and sustained-movement assistance. */
internal class MeasurementGuidance(private val correctionTimeoutMillis: () -> Long = { 5_000L }) {
    private var seenNoticeId = 0L
    private var pending: MeasurementNotice? = null
    private var correction: Int? = null
    private var correctionUntil = 0L
    private var stage: MeasurementStage? = null
    private var movingSince: Long? = null
    private var movingFrame: String? = null
    private var steadyShown = false
    private var steadyUntil = 0L

    /** Uses existing state publications for expiry; no presentation timer or sensor work. */
    @StringRes
    fun update(state: MeasurementUiState, nowMillis: Long): Int? {
        val measurement = state.measurement
        val newEvent = measurement.noticeId != seenNoticeId
        seenNoticeId = measurement.noticeId
        if (stage != measurement.stage || (newEvent && measurement.notice == null)) resetMovement()
        if (stage != measurement.stage || measurement.notice == null || measurement.completed) {
            pending = null
            correction = null
        }
        stage = measurement.stage
        if (newEvent && !measurement.completed) pending = measurement.notice

        val active = state.acquisitionEligible && measurement.cameraReady && state.acquisition.running && !state.recoveryVisible
        if (!active) {
            resetMovement()
            correction = null
            // A newly lost pending capture may be explained on return, but never replayed.
            if (pending !in resetNotices || measurement.completed) pending = null
            return null
        }
        pending?.let {
            correction = noticeResource(it)
            correctionUntil = nowMillis + correctionTimeoutMillis().coerceAtLeast(5_000L)
            pending = null
        }
        if (nowMillis >= correctionUntil) correction = null
        val nearVertical = measurement.latest?.direction?.let {
            hypot(it.x, it.y) < MeasurementPolicy.minimumHorizontalNorm
        } == true || measurement.alignment == Alignment.NEAR_VERTICAL
        val moving = measurement.latest?.quality?.status == CaptureStatus.MOVING && state.camera.ready && state.camera.zoom.settled && !nearVertical
        if (!moving) resetMovement()
        else {
            val frame = measurement.latest.referenceFrameId
            if (movingFrame != frame) resetMovement()
            movingFrame = frame
            val since = movingSince ?: nowMillis.also { movingSince = it }
            if (!steadyShown && nowMillis - since >= 6_000L && correction == null) {
                steadyShown = true
                steadyUntil = nowMillis + correctionTimeoutMillis().coerceAtLeast(5_000L)
            }
        }
        return when {
            nearVertical -> R.string.guidance_near_vertical
            correction != null -> correction
            steadyShown && nowMillis < steadyUntil -> R.string.guidance_keep_steady
            else -> null
        }
    }

    private fun resetMovement() {
        movingSince = null
        movingFrame = null
        steadyShown = false
        steadyUntil = 0L
    }

    private companion object {
        val resetNotices = setOf(
            MeasurementNotice.CAMERA_INTERRUPTED, MeasurementNotice.ACQUISITION_SUSPENDED,
            MeasurementNotice.SESSION_CHANGED, MeasurementNotice.CAPTURE_EXPIRED,
            MeasurementNotice.INCOMPATIBLE_CAPTURES, MeasurementNotice.SENSOR_RESTARTED,
        )
    }
}

@StringRes
internal fun noticeResource(notice: MeasurementNotice): Int = when (notice) {
    MeasurementNotice.ACQUISITION_SUSPENDED -> R.string.notice_acquisition_suspended
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

/** The reserved caption area stays put while editing suppresses its live instruction. */
@StringRes
internal fun stageInstruction(state: MeasurementState, editingHeight: Boolean): Int? = when {
    editingHeight || state.heightMeters == null || state.completed -> null
    state.stage == MeasurementStage.TOP -> R.string.measurement_stage_top
    else -> R.string.measurement_stage_bottom
}
