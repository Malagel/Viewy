package com.malagel.viewy.ui

import com.malagel.viewy.measurement.Alignment
import com.malagel.viewy.measurement.MeasurementStage
import com.malagel.viewy.orientation.AcquisitionPolicy
import com.malagel.viewy.orientation.CaptureStatus
import com.malagel.viewy.orientation.OrientationObservation

enum class AlignmentCue { HIDDEN, LEFT, RIGHT, RECOVERED }

/** Recovery identity is stable across ordinary sensor publications. */
data class AlignmentCueState(val cue: AlignmentCue = AlignmentCue.HIDDEN, val recoveryId: Long = 0)

/** Presentation-only debounce; never participates in measurement or capture eligibility. */
internal class AlignmentGuidance {
    private val policy = AcquisitionPolicy()
    private var top: OrientationObservation? = null
    private var accepted = Alignment.ALIGNED
    private var pending: Alignment? = null
    private var pendingSince = 0L
    private var recoveryId = 0L
    private var presentation = AlignmentCueState()

    /** Driven by existing sensor/freshness publications, without another timer. */
    fun update(state: MeasurementUiState, nowNanos: Long): AlignmentCueState {
        val first = state.measurement.top
        val current = state.measurement.latest
        val age = current?.let { nowNanos - it.timestampNanos }
        val valid = first != null && current != null && state.acquisitionEligible &&
            state.camera.ready && state.measurement.cameraReady && state.acquisition.running &&
            !state.recoveryVisible && state.measurement.stage in setOf(MeasurementStage.BOTTOM, MeasurementStage.CORRECTION) &&
            first.referenceFrameId == state.measurement.frameId &&
            first.referenceFrameId == state.acquisition.referenceFrameId &&
            first.referenceFrameId == current.referenceFrameId && first.source == current.source &&
            first.source == state.acquisition.activeSource && first.cameraMappingId == current.cameraMappingId &&
            first.cameraMappingId == state.camera.mapping?.id &&
            current.timestampNanos >= first.timestampNanos &&
            nowNanos - first.timestampNanos in 0..policy.maximumPairGapNanos &&
            age != null && age in 0..policy.maximumAgeNanos &&
            current.quality.sensorAccuracy != 0 &&
            current.quality.status in setOf(CaptureStatus.READY, CaptureStatus.MOVING, CaptureStatus.SETTLING)
        val alignment = state.measurement.alignment
        if (!valid || alignment == null || alignment == Alignment.NEAR_VERTICAL) {
            reset()
            return presentation
        }
        if (top != first) {
            reset()
            top = first
        }
        if (alignment == accepted) {
            pending = null
            return presentation
        }
        if (pending != alignment) {
            pending = alignment
            pendingSince = nowNanos
        }
        // Require the same classification for 180 ms, retaining the previous cue meanwhile.
        if (nowNanos - pendingSince < 180_000_000L) return presentation
        accepted = alignment
        pending = null
        presentation = AlignmentCueState(when (alignment) {
            // Workflow directions already describe physical rear-camera corrections.
            Alignment.TURN_LEFT -> AlignmentCue.LEFT
            Alignment.TURN_RIGHT -> AlignmentCue.RIGHT
            Alignment.ALIGNED -> AlignmentCue.RECOVERED
            Alignment.NEAR_VERTICAL -> error("Near-vertical guidance is hidden")
        }, if (alignment == Alignment.ALIGNED) ++recoveryId else recoveryId)
        return presentation
    }

    private fun reset() {
        top = null
        accepted = Alignment.ALIGNED
        pending = null
        presentation = AlignmentCueState(recoveryId = recoveryId)
    }
}
