package com.malagel.viewy.ui

import androidx.annotation.StringRes
import com.malagel.viewy.R
import com.malagel.viewy.measurement.*
import kotlin.math.hypot

/** Presents workflow failures once and the near-vertical restriction while it applies. */
internal class MeasurementGuidance(private val correctionTimeoutMillis: () -> Long = { 5_000L }) {
    private var seenNoticeId = 0L
    private var pending: MeasurementNotice? = null
    private var correction: Int? = null
    private var correctionUntil = 0L
    private var stage: MeasurementStage? = null

    /** Uses existing state publications for expiry; no presentation timer or sensor work. */
    @StringRes
    fun update(state: MeasurementUiState, nowMillis: Long): Int? {
        val measurement = state.measurement
        val newEvent = measurement.noticeId != seenNoticeId
        seenNoticeId = measurement.noticeId
        if (stage != measurement.stage || measurement.notice == null || measurement.completed) {
            pending = null
            correction = null
        }
        stage = measurement.stage
        if (newEvent && !measurement.completed) pending = measurement.notice

        val active = state.acquisitionEligible && measurement.cameraReady && state.acquisition.running && !state.recoveryVisible
        if (!active) {
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
        return if (nearVertical) R.string.guidance_near_vertical else correction
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
