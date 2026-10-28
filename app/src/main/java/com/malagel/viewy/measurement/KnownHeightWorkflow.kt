package com.malagel.viewy.measurement

import com.malagel.viewy.domain.*
import com.malagel.viewy.orientation.*
import kotlin.math.abs
import kotlin.math.hypot

enum class MeasurementStage { TOP, BOTTOM, CORRECTION, RESULT }
enum class Alignment { ALIGNED, TURN_LEFT, TURN_RIGHT, NEAR_VERTICAL }

/** Presentation resolves these reasons to localized text; workflow logic owns no UI strings. */
enum class MeasurementNotice {
    CAMERA_INTERRUPTED, ACQUISITION_SUSPENDED, SESSION_CHANGED, CAPTURE_EXPIRED, WAIT_FOR_STABILITY,
    NEAR_VERTICAL, INCOMPATIBLE_CAPTURES, ALIGN_WITH_TOP, AIM_BELOW_TOP,
    UNUSABLE_ENDPOINTS, SMALL_SEPARATION, SENSOR_RESTARTED,
}

/** Uncalibrated usability gates; none is an angular error bound. */
object MeasurementPolicy {
    val alignmentToleranceRadians = Math.toRadians(0.45)
    val minimumSeparationRadians = Math.toRadians(0.5)
    val minimumHorizontalNorm = kotlin.math.cos(Math.toRadians(85.0))
    const val duplicateTapNanos = 600_000_000L
}

fun alignment(top: ViewingDirection, current: ViewingDirection): Alignment {
    if (hypot(top.x, top.y) < MeasurementPolicy.minimumHorizontalNorm ||
        hypot(current.x, current.y) < MeasurementPolicy.minimumHorizontalNorm
    ) return Alignment.NEAR_VERTICAL
    val delta = wrappedDifference(current.bearingRadians()!!, top.bearingRadians()!!)
    return when {
        abs(delta) <= MeasurementPolicy.alignmentToleranceRadians -> Alignment.ALIGNED
        delta > 0 -> Alignment.TURN_RIGHT
        else -> Alignment.TURN_LEFT
    }
}

data class MeasurementState(
    val heightMeters: Double? = null,
    val cameraReady: Boolean = false,
    val stage: MeasurementStage = MeasurementStage.TOP,
    val top: OrientationObservation? = null,
    val bottom: OrientationObservation? = null,
    val result: KnownHeightResult.Estimate? = null,
    val latest: OrientationObservation? = null,
    val frameId: String? = null,
    val notice: MeasurementNotice? = null,
    /** Monotonic event identity, including repeated rejections with the same reason. */
    val noticeId: Long = 0,
) {
    val completed: Boolean get() = result != null
    val alignment: Alignment? get() = top?.let { a -> latest?.let { alignment(a.direction, it.direction) } }
    val canCapture: Boolean get() = cameraReady && heightMeters != null && stage != MeasurementStage.RESULT &&
        latest?.quality?.status == CaptureStatus.READY &&
        hypot(latest.direction.x, latest.direction.y) >= MeasurementPolicy.minimumHorizontalNorm &&
        (top == null || alignment == Alignment.ALIGNED)
}

/** Main-thread controller with pure inputs, deterministic transitions and no Android/UI dependencies. */
class KnownHeightWorkflow(private val policy: AcquisitionPolicy = AcquisitionPolicy()) {
    var state = MeasurementState()
        private set
    private var lastTapNanos: Long? = null

    fun setHeight(meters: Double) {
        require(meters.isFinite() && meters > 0.0 && HeightUnit.entries.all { it.fromMeters(meters).isFinite() })
        restart()
        state = state.copy(heightMeters = meters)
    }

    fun cameraReady(ready: Boolean, intentionalSuspension: Boolean = false) {
        if (!ready && state.cameraReady && state.top != null && !state.completed) {
            restart(if (intentionalSuspension) MeasurementNotice.ACQUISITION_SUSPENDED else MeasurementNotice.CAMERA_INTERRUPTED)
        }
        state = if (ready) state.copy(cameraReady = true) else state.copy(cameraReady = false, latest = null, frameId = null)
    }

    fun update(observation: OrientationObservation?, frameId: String?, running: Boolean, nowNanos: Long) {
        val first = state.top
        if (!state.completed && first != null && (!running || frameId != first.referenceFrameId ||
                (observation != null && (observation.source != first.source || observation.cameraMappingId != first.cameraMappingId)))) {
            restart(MeasurementNotice.SESSION_CHANGED)
        } else if (first != null && state.result == null && nowNanos - first.timestampNanos > policy.maximumPairGapNanos) {
            restart(MeasurementNotice.CAPTURE_EXPIRED)
        }
        state = state.copy(latest = observation, frameId = frameId)
    }

    fun restart(message: MeasurementNotice? = null) {
        state = state.copy(stage = MeasurementStage.TOP, top = null, bottom = null, result = null, latest = null, frameId = null, notice = message, noticeId = state.noticeId + 1)
        lastTapNanos = null
    }

    fun recaptureBottom() {
        if (state.top == null) return
        state = state.copy(stage = MeasurementStage.BOTTOM, bottom = null, result = null, notice = null)
        lastTapNanos = null
    }

    fun capture(capture: CaptureResult, nowNanos: Long) {
        if (state.result != null || !state.cameraReady || state.heightMeters == null) return
        if (lastTapNanos?.let { nowNanos - it < MeasurementPolicy.duplicateTapNanos } == true) return
        val observation = (capture as? CaptureResult.Captured)?.observation
        if (observation == null || observation.quality.status != CaptureStatus.READY ||
            nowNanos < observation.timestampNanos || nowNanos - observation.timestampNanos > policy.maximumAgeNanos
        ) {
            state = state.copy(notice = MeasurementNotice.WAIT_FOR_STABILITY, noticeId = state.noticeId + 1)
            return
        }
        if (observation.referenceFrameId != state.frameId) {
            restart(MeasurementNotice.SESSION_CHANGED.takeIf { state.top != null })
            return
        }
        val first = state.top
        if (first == null) {
            if (hypot(observation.direction.x, observation.direction.y) < MeasurementPolicy.minimumHorizontalNorm) {
                state = state.copy(notice = MeasurementNotice.NEAR_VERTICAL, noticeId = state.noticeId + 1)
                return
            }
            state = state.copy(top = observation, stage = MeasurementStage.BOTTOM, notice = null)
            lastTapNanos = nowNanos
            return
        }
        when (val comparison = compareObservations(first, observation, policy)) {
            is ObservationComparison.Rejected -> {
                if (comparison.reason == PairFailure.NON_INCREASING_TIME) return
                restart(MeasurementNotice.INCOMPATIBLE_CAPTURES)
                return
            }
            is ObservationComparison.Comparable -> Unit
        }
        if (alignment(first.direction, observation.direction) != Alignment.ALIGNED) {
            state = state.copy(stage = MeasurementStage.CORRECTION, notice = MeasurementNotice.ALIGN_WITH_TOP, noticeId = state.noticeId + 1)
            return
        }
        when (val result = KnownHeightDistance.estimate(state.heightMeters!!, first.direction, observation.direction)) {
            is KnownHeightResult.Failure -> state = state.copy(
                stage = MeasurementStage.CORRECTION,
                noticeId = state.noticeId + 1,
                notice = when (result.reason) {
                    DistanceFailureReason.REVERSED_ENDPOINTS -> MeasurementNotice.AIM_BELOW_TOP
                    else -> MeasurementNotice.UNUSABLE_ENDPOINTS
                },
            )
            is KnownHeightResult.Estimate -> {
                if (result.diagnostics.elevationSeparationRadians < MeasurementPolicy.minimumSeparationRadians) {
                    state = state.copy(stage = MeasurementStage.CORRECTION, notice = MeasurementNotice.SMALL_SEPARATION, noticeId = state.noticeId + 1)
                } else {
                    state = state.copy(stage = MeasurementStage.RESULT, bottom = observation, result = result, notice = null)
                    lastTapNanos = nowNanos
                }
            }
        }
    }
}
