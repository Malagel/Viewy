package com.malagel.viewy.orientation

import com.malagel.viewy.domain.ViewingDirection
import java.util.UUID
import kotlin.math.hypot
import kotlin.math.sqrt

enum class OrientationSource { GAME_ROTATION_VECTOR, ROTATION_VECTOR }

enum class CaptureStatus { WAITING, STALE, UNRELIABLE, SETTLING, MOVING, READY }
enum class Interruption { STARTED, STOPPED, MALFORMED_READING, INVALID_TIMESTAMP, SAMPLE_GAP, ABRUPT_ROTATION, PLATFORM_DISCONTINUITY, ACCURACY_CHANGED }

/** Engineering policy only: these limits do not specify calibrated sensor accuracy. */
data class AcquisitionPolicy(
    val windowNanos: Long = 500_000_000L,
    val minimumSpanNanos: Long = 350_000_000L,
    val minimumSamples: Int = 8,
    val maximumAgeNanos: Long = 250_000_000L,
    val maximumGapNanos: Long = 250_000_000L,
    val maximumVariationRadians: Double = Math.toRadians(0.75),
    val abruptRotationRadians: Double = Math.toRadians(30.0),
    val maximumPairGapNanos: Long = 15_000_000_000L,
) {
    init {
        require(windowNanos > 0 && minimumSpanNanos in 1..windowNanos)
        require(minimumSamples in 2..64 && maximumAgeNanos > 0 && maximumGapNanos > 0)
        require(maximumVariationRadians.isFinite() && maximumVariationRadians > 0)
        require(abruptRotationRadians.isFinite() && abruptRotationRadians > 0)
        require(maximumPairGapNanos > 0)
    }
}

data class OrientationQuality(
    val sampleCount: Int,
    val spanNanos: Long,
    val ageNanos: Long?,
    val rmsVariationRadians: Double?,
    val maximumVariationRadians: Double?,
    val latestStepRadians: Double?,
    val sensorAccuracy: Int?,
    val status: CaptureStatus,
)

data class OrientationObservation(
    val direction: ViewingDirection,
    /** Original sensor event time, same time base as elapsedRealtimeNanos, not wall time. */
    val timestampNanos: Long,
    val referenceFrameId: String,
    val source: OrientationSource,
    val quality: OrientationQuality,
    val cameraMappingId: String = RearCameraAxis.mappingId,
)

sealed interface CaptureResult {
    data class Captured(val observation: OrientationObservation) : CaptureResult
    data class Rejected(val status: CaptureStatus) : CaptureResult
}

enum class PairFailure { DIFFERENT_REFERENCE_FRAME, NON_INCREASING_TIME, EXCESSIVE_TIME_GAP }
sealed interface ObservationComparison {
    data class Comparable(
        val elapsedNanos: Long,
        val angularSeparationRadians: Double,
        val elevationChangeRadians: Double,
        /** Second minus first. Null near vertical; not an independently measured drift estimate. */
        val bearingChangeRadians: Double?,
    ) : ObservationComparison
    data class Rejected(val reason: PairFailure) : ObservationComparison
}

fun compareObservations(
    first: OrientationObservation,
    second: OrientationObservation,
    policy: AcquisitionPolicy = AcquisitionPolicy(),
): ObservationComparison {
    if (first.referenceFrameId != second.referenceFrameId || first.source != second.source ||
        first.cameraMappingId != second.cameraMappingId
    ) return ObservationComparison.Rejected(PairFailure.DIFFERENT_REFERENCE_FRAME)
    if (second.timestampNanos <= first.timestampNanos) {
        return ObservationComparison.Rejected(PairFailure.NON_INCREASING_TIME)
    }
    val elapsed = second.timestampNanos - first.timestampNanos
    if (elapsed > policy.maximumPairGapNanos) return ObservationComparison.Rejected(PairFailure.EXCESSIVE_TIME_GAP)
    val a = first.direction.bearingRadians()
    val b = second.direction.bearingRadians()
    return ObservationComparison.Comparable(
        elapsed, angularSeparation(first.direction, second.direction),
        second.direction.elevationRadians() - first.direction.elevationRadians(),
        if (a == null || b == null) null else wrappedDifference(b, a),
    )
}

/** Pure Kotlin acquisition state. All calls are serialized by the Android adapter on main. */
class OrientationSession(val source: OrientationSource, private val policy: AcquisitionPolicy = AcquisitionPolicy()) {
    private data class Sample(val rotation: OrientationRotation, val direction: ViewingDirection, val time: Long)
    private val samples = ArrayDeque<Sample>()
    var referenceFrameId: String = UUID.randomUUID().toString()
        private set
    var lastInterruption: Interruption = Interruption.STARTED
        private set
    private var accuracy: Int? = null
    private var step: Double? = null
    private var lastReceivedTimestamp: Long? = null

    fun interrupt(reason: Interruption) {
        samples.clear()
        step = null
        lastReceivedTimestamp = null
        referenceFrameId = UUID.randomUUID().toString()
        lastInterruption = reason
    }

    fun updateAccuracy(value: Int) {
        // A loss/recovery of reliability invalidates comparisons, not every accuracy-level change.
        if ((accuracy == 0) != (value == 0)) interrupt(Interruption.ACCURACY_CHANGED)
        accuracy = value
    }

    fun add(values: FloatArray, timestampNanos: Long, nowNanos: Long) {
        val rotation = OrientationRotation.fromRotationVector(values)
        if (rotation == null) {
            interrupt(Interruption.MALFORMED_READING)
            return
        }
        val previous = samples.lastOrNull()
        if (timestampNanos <= 0 || timestampNanos > nowNanos ||
            (lastReceivedTimestamp != null && timestampNanos <= lastReceivedTimestamp!!)
        ) {
            interrupt(Interruption.INVALID_TIMESTAMP)
            return
        }
        if (nowNanos - timestampNanos > policy.maximumAgeNanos) {
            interrupt(Interruption.SAMPLE_GAP)
            return
        }
        lastReceivedTimestamp = timestampNanos
        // Sampling is a platform hint. Keep time coverage when a device delivers much faster:
        // validate every event, but retain/transform at most 100 samples per sensor second.
        if (previous != null && timestampNanos - previous.time < 10_000_000L) return
        val change = previous?.rotation?.separation(rotation)
        val previousFrameId = referenceFrameId
        when {
            previous != null && timestampNanos - previous.time > policy.maximumGapNanos -> interrupt(Interruption.SAMPLE_GAP)
            change != null && change > policy.abruptRotationRadians -> interrupt(Interruption.ABRUPT_ROTATION)
        }
        lastReceivedTimestamp = timestampNanos
        // An interruption’s first reading has no previous sample in its new frame.
        step = change.takeIf { referenceFrameId == previousFrameId }
        samples.addLast(Sample(rotation, rotation.rotate(RearCameraAxis.assumedDeviceDirection), timestampNanos))
        while (samples.size > 64 || timestampNanos - samples.first().time > policy.windowNanos) samples.removeFirst()
    }

    fun quality(nowNanos: Long): OrientationQuality {
        val latest = samples.lastOrNull()
        val age = latest?.let { nowNanos - it.time }
        val span = if (latest == null) 0 else latest.time - samples.first().time
        val sum = ViewingDirection(samples.sumOf { it.direction.x }, samples.sumOf { it.direction.y }, samples.sumOf { it.direction.z })
        val norm = hypot(hypot(sum.x, sum.y), sum.z)
        val mean = if (norm > 1e-9) ViewingDirection(sum.x/norm, sum.y/norm, sum.z/norm) else null
        val angles = mean?.let { m -> samples.map { angularSeparation(it.direction, m) } }.orEmpty()
        val rms = angles.takeIf { it.isNotEmpty() }?.let { sqrt(it.sumOf { a -> a*a } / it.size) }
        val maximum = angles.maxOrNull()
        val status = when {
            latest == null -> CaptureStatus.WAITING
            age == null || age < 0 || age > policy.maximumAgeNanos -> CaptureStatus.STALE
            accuracy == 0 -> CaptureStatus.UNRELIABLE
            samples.size < policy.minimumSamples || span < policy.minimumSpanNanos -> CaptureStatus.SETTLING
            maximum == null || maximum > policy.maximumVariationRadians -> CaptureStatus.MOVING
            else -> CaptureStatus.READY
        }
        return OrientationQuality(samples.size, span, age, rms, maximum, step, accuracy, status)
    }

    fun latest(nowNanos: Long): OrientationObservation? = samples.lastOrNull()?.let {
        OrientationObservation(it.direction, it.time, referenceFrameId, source, quality(nowNanos))
    }

    /** Capture the latest actual ray, not an averaged synthetic orientation or a button timestamp. */
    fun capture(nowNanos: Long): CaptureResult {
        val quality = quality(nowNanos)
        return if (quality.status == CaptureStatus.READY) CaptureResult.Captured(latest(nowNanos)!!)
        else CaptureResult.Rejected(quality.status)
    }
}
