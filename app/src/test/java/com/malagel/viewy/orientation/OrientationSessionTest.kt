package com.malagel.viewy.orientation

import com.malagel.viewy.domain.ViewingDirection
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.sqrt
import org.junit.Assert.*
import org.junit.Test

class OrientationSessionTest {
    private val tick = 40_000_000L
    private val base = 1_000_000_000L
    private val identity = floatArrayOf(0f,0f,0f,1f)
    private fun session() = OrientationSession(OrientationSource.GAME_ROTATION_VECTOR)
    private fun fill(s: OrientationSession, start: Long = base, values: FloatArray = identity): Long {
        repeat(13) { s.add(values,start + it*tick,start + it*tick) }
        return start + 12*tick
    }
    private fun capture(s: OrientationSession, now: Long) = (s.capture(now) as CaptureResult.Captured).observation
    private fun rotationX(angle: Double) = floatArrayOf(sin(angle/2).toFloat(),0f,0f,cos(angle/2).toFloat())
    @Test fun missingReadingsAreUnavailable() {
        assertEquals(CaptureStatus.WAITING,session().quality(base).status)
        assertTrue(session().capture(base) is CaptureResult.Rejected)
    }
    @Test fun stationaryWindowBecomesReadyWithOriginalTimestampAndMetadata() {
        val s = session()
        val end = fill(s)
        val observation = capture(s,end+1)
        assertEquals(end,observation.timestampNanos)
        assertEquals(1L,observation.quality.ageNanos)
        assertEquals(s.referenceFrameId,observation.referenceFrameId)
        assertEquals(RearCameraAxis.mappingId,observation.cameraMappingId)
        assertEquals(0.0,observation.quality.rmsVariationRadians!!,0.0)
    }
    @Test fun sampleCountAloneDoesNotEstablishTimeCoverage() {
        val s = session()
        repeat(20) { s.add(identity,base+it,base+it) }
        assertEquals(CaptureStatus.SETTLING,s.quality(base+20).status)
    }
    @Test fun freshnessExpiresWithoutFurtherSensorCallbacks() {
        val s = session()
        val end = fill(s)
        assertEquals(CaptureStatus.STALE,s.quality(end+250_000_001L).status)
        assertTrue(s.capture(end+250_000_001L) is CaptureResult.Rejected)
    }
    @Test fun invalidFutureZeroAndRepeatedTimestampsInvalidateFrame() {
        for (timestamp in listOf(0L,base,base-1,base+1)) {
            val s = session()
            s.add(identity,base,base)
            val frame = s.referenceFrameId
            s.add(identity,timestamp,base)
            assertNotEquals(frame,s.referenceFrameId)
            assertEquals(Interruption.INVALID_TIMESTAMP,s.lastInterruption)
            assertNull(s.latest(base))
        }
    }
    @Test fun staleDeliveryRejectedEvenIfQuaternionIsValid() {
        val s = session()
        s.add(identity,base,base+300_000_000L)
        assertEquals(Interruption.SAMPLE_GAP,s.lastInterruption)
        assertEquals(CaptureStatus.WAITING,s.quality(base+300_000_000L).status)
    }
    @Test fun malformedReadingClearsPreviouslyValidCapture() {
        val s = session()
        val end = fill(s)
        val frame = s.referenceFrameId
        s.add(floatArrayOf(Float.NaN,0f,0f),end+tick,end+tick)
        assertNotEquals(frame,s.referenceFrameId)
        assertEquals(Interruption.MALFORMED_READING,s.lastInterruption)
        assertNull(s.latest(end+tick))
    }
    @Test fun sampleGapStartsNewFrameAndRequiresSettling() {
        val s = session()
        val end = fill(s)
        val first = capture(s,end)
        val next = end + 300_000_000L
        s.add(identity,next,next)
        assertNotEquals(first.referenceFrameId,s.referenceFrameId)
        assertEquals(CaptureStatus.SETTLING,s.quality(next).status)
        assertNull(s.quality(next).latestStepRadians)
        val second = capture(s,fill(s,next+tick))
        assertEquals(ObservationComparison.Rejected(PairFailure.DIFFERENT_REFERENCE_FRAME),compareObservations(first,second))
    }
    @Test fun suddenQuaternionStepInvalidatesFrameAndThenRecovers() {
        val s = session()
        val end = fill(s)
        val frame = s.referenceFrameId
        s.add(rotationX(PI/2),end+tick,end+tick)
        assertNotEquals(frame,s.referenceFrameId)
        assertEquals(Interruption.ABRUPT_ROTATION,s.lastInterruption)
        assertEquals(CaptureStatus.SETTLING,s.quality(end+tick).status)
        assertNull(s.quality(end+tick).latestStepRadians)
        assertEquals(CaptureStatus.READY,s.quality(fill(s,end+2*tick,rotationX(PI/2))).status)
        assertEquals(0.0,s.quality(end+14*tick).latestStepRadians!!,1e-7)
    }
    @Test fun rayVariationUsesSphericalStatisticsAndSettlesWhenMotionLeavesWindow() {
        val s = session()
        repeat(13) { i ->
            val time = base+i*tick
            s.add(rotationX(if (i%2==0) 0.03 else -0.03),time,time)
        }
        assertEquals(CaptureStatus.MOVING,s.quality(base+12*tick).status)
        assertEquals(CaptureStatus.READY,s.quality(fill(s,base+13*tick)).status)
    }
    @Test fun rollingAroundPhysicalAxisDoesNotCountAsAimingInstability() {
        val s = session()
        repeat(13) { i ->
            val angle = i*0.05
            val time = base+i*tick
            s.add(floatArrayOf(0f,0f,sin(angle/2).toFloat(),cos(angle/2).toFloat()),time,time)
        }
        assertEquals(CaptureStatus.READY,s.quality(base+12*tick).status)
        assertEquals(0.0,s.quality(base+12*tick).maximumVariationRadians!!,0.0)
    }
    @Test fun quaternionSignFlipIsNotDiscontinuity() {
        val s = session()
        val end = fill(s)
        val frame = s.referenceFrameId
        s.add(floatArrayOf(0f,0f,0f,-1f),end+tick,end+tick)
        assertEquals(frame,s.referenceFrameId)
        assertEquals(CaptureStatus.READY,s.quality(end+tick).status)
    }
    @Test fun unreliableAccuracyBlocksCaptureAndRecoveryResetsHistory() {
        val s = session()
        s.updateAccuracy(0)
        val end = fill(s)
        assertEquals(CaptureStatus.UNRELIABLE,s.quality(end).status)
        val frame = s.referenceFrameId
        s.updateAccuracy(3)
        assertNotEquals(frame,s.referenceFrameId)
        assertEquals(CaptureStatus.WAITING,s.quality(end).status)
        assertEquals(CaptureStatus.READY,s.quality(fill(s,end+tick)).status)
    }
    @Test fun ordinaryAccuracyChangesDoNotResetReferenceFrame() {
        val s = session()
        s.updateAccuracy(1)
        val frame = s.referenceFrameId
        s.updateAccuracy(3)
        assertEquals(frame,s.referenceFrameId)
    }
    @Test fun stopAndNewSessionCannotReuseReferenceFrame() {
        val s = session()
        val end = fill(s)
        val frame = s.referenceFrameId
        s.interrupt(Interruption.STOPPED)
        assertNull(s.latest(end))
        assertNotEquals(frame,s.referenceFrameId)
        assertNotEquals(frame,session().referenceFrameId)
    }
    @Test fun bufferIsBoundedEvenWithFasterThanRequestedDelivery() {
        val s = session()
        repeat(1000) { s.add(identity,base+it*1_000_000L,base+it*1_000_000L) }
        assertEquals(51,s.quality(base+999_000_000L).sampleCount)
        assertEquals(CaptureStatus.READY,s.quality(base+999_000_000L).status)
    }
    @Test fun skippedHighRateEventsStillValidateMonotonicTime() {
        val s = session()
        s.add(identity,base,base)
        s.add(identity,base+2,base+2)
        s.add(identity,base+1,base+3)
        assertEquals(Interruption.INVALID_TIMESTAMP,s.lastInterruption)
        assertNull(s.latest(base+3))
    }
    @Test fun explicitPlatformDiscontinuityRequiresNewWindowAndFrame() {
        val s = session()
        val end = fill(s)
        val frame = s.referenceFrameId
        s.interrupt(Interruption.PLATFORM_DISCONTINUITY)
        assertNotEquals(frame,s.referenceFrameId)
        assertEquals(CaptureStatus.WAITING,s.quality(end).status)
    }
    @Test fun pairTimeSourceAndMappingChecks() {
        val s = session()
        val end = fill(s)
        val a = capture(s,end)
        assertEquals(ObservationComparison.Rejected(PairFailure.NON_INCREASING_TIME),compareObservations(a,a))
        assertEquals(ObservationComparison.Rejected(PairFailure.DIFFERENT_REFERENCE_FRAME),compareObservations(a,a.copy(source=OrientationSource.ROTATION_VECTOR)))
        assertEquals(ObservationComparison.Rejected(PairFailure.DIFFERENT_REFERENCE_FRAME),compareObservations(a,a.copy(cameraMappingId="other")))
        assertEquals(ObservationComparison.Rejected(PairFailure.EXCESSIVE_TIME_GAP),compareObservations(a,a.copy(timestampNanos=end+15_000_000_001L)))
        assertTrue(compareObservations(a,a.copy(timestampNanos=end+15_000_000_000L)) is ObservationComparison.Comparable)
    }
    @Test fun comparisonWrapAndPoleHandling() {
        val s = session()
        val a = capture(s,fill(s))
        fun ray(degrees: Double) = ViewingDirection(cos(Math.toRadians(degrees)),sin(Math.toRadians(degrees)),0.0)
        val b = a.copy(timestampNanos=a.timestampNanos+tick,direction=ray(-179.0))
        val result = compareObservations(a.copy(direction=ray(179.0)),b) as ObservationComparison.Comparable
        assertEquals(2.0,Math.toDegrees(result.bearingChangeRadians!!),1e-12)
        val pole = compareObservations(a,b) as ObservationComparison.Comparable
        assertNull(pole.bearingChangeRadians)
    }
    @Test fun stabilityAcrossBearingWrapUsesShortArcRatherThanEulerAverage() {
        val s = session()
        repeat(13) { i ->
            // Rz(yaw) Rx(90), viewing bearings alternate around +180/-180.
            val yaw = Math.toRadians(if (i%2==0) 89.9 else 90.1)
            val c = kotlin.math.sqrt(0.5)
            val time = base+i*tick
            s.add(floatArrayOf((c*cos(yaw/2)).toFloat(),(c*sin(yaw/2)).toFloat(),(c*sin(yaw/2)).toFloat(),(c*cos(yaw/2)).toFloat()),time,time)
        }
        val quality = s.quality(base+12*tick)
        assertEquals(CaptureStatus.READY,quality.status)
        assertTrue(Math.toDegrees(quality.maximumVariationRadians!!) < 0.11)
    }

    @Test fun qualityStatisticsExactlyMatchOriginalListCalculationAcrossRollingWindows() {
        val policy = AcquisitionPolicy(abruptRotationRadians = PI + 1.0)
        val sequences = listOf(
            listOf(identity),
            List(90) { i -> rotationX(PI / 2 + sin(i * 0.7) * 0.004) },
            List(90) { i ->
                val pitch = 0.7 + i * 0.03
                val yaw = i * 0.04
                floatArrayOf(
                    (sin(pitch / 2) * cos(yaw / 2)).toFloat(),
                    (sin(pitch / 2) * sin(yaw / 2)).toFloat(),
                    (cos(pitch / 2) * sin(yaw / 2)).toFloat(),
                    (cos(pitch / 2) * cos(yaw / 2)).toFloat(),
                )
            },
            // Exactly opposite rays have no usable spherical mean.
            listOf(identity, floatArrayOf(1f, 0f, 0f, 0f)),
        )
        for (sequence in sequences) {
            val s = OrientationSession(OrientationSource.GAME_ROTATION_VECTOR, policy)
            val retained = ArrayDeque<Pair<Long, ViewingDirection>>()
            assertNull(s.quality(base).rmsVariationRadians)
            assertNull(s.quality(base).maximumVariationRadians)
            for ((index, values) in sequence.withIndex()) {
                val time = base + index * tick
                s.add(values, time, time)
                retained.addLast(time to OrientationRotation.fromRotationVector(values)!!.rotate(RearCameraAxis.assumedDeviceDirection))
                while (retained.size > 64 || time - retained.first().first > policy.windowNanos) retained.removeFirst()
                val rays = retained.map { it.second }
                // Reference the original list-based evaluation, including its summation order.
                val sum = ViewingDirection(rays.sumOf { it.x }, rays.sumOf { it.y }, rays.sumOf { it.z })
                val norm = hypot(hypot(sum.x, sum.y), sum.z)
                val mean = if (norm > 1e-9) ViewingDirection(sum.x / norm, sum.y / norm, sum.z / norm) else null
                val angles = mean?.let { m -> rays.map { angularSeparation(it, m) } }.orEmpty()
                val rms = angles.takeIf { it.isNotEmpty() }?.let { sqrt(it.sumOf { a -> a * a } / it.size) }
                val actual = s.quality(time + 1)
                assertEquals(rays.size, actual.sampleCount)
                assertEquals(time - retained.first().first, actual.spanNanos)
                assertEquals(1L, actual.ageNanos)
                assertEquals(rms?.toRawBits(), actual.rmsVariationRadians?.toRawBits())
                assertEquals(angles.maxOrNull()?.toRawBits(), actual.maximumVariationRadians?.toRawBits())
            }
        }
    }

    @Test fun latestAndCapturePreserveEveryQualityStatusAndRejectionReason() {
        fun check(s: OrientationSession, now: Long, status: CaptureStatus) {
            val quality = s.quality(now)
            assertEquals(status, quality.status)
            val latest = s.latest(now)
            if (latest != null) assertEquals(quality, latest.quality)
            if (status == CaptureStatus.READY) {
                assertEquals(CaptureResult.Captured(latest!!), s.capture(now))
            } else {
                assertEquals(CaptureResult.Rejected(status), s.capture(now))
            }
        }
        val s = session()
        check(s, base, CaptureStatus.WAITING)
        s.add(identity, base, base)
        check(s, base, CaptureStatus.SETTLING)
        val end = fill(s, base + tick)
        check(s, end + 250_000_000L, CaptureStatus.READY)
        check(s, end + 250_000_001L, CaptureStatus.STALE)
        check(s, end - 1, CaptureStatus.STALE)
        val moving = session()
        repeat(13) { i ->
            val time = base + i * tick
            moving.add(rotationX(if (i % 2 == 0) 0.03 else -0.03), time, time)
        }
        check(moving, base + 12 * tick, CaptureStatus.MOVING)
        val unreliable = session()
        unreliable.updateAccuracy(0)
        val unreliableEnd = fill(unreliable)
        check(unreliable, unreliableEnd, CaptureStatus.UNRELIABLE)
        check(unreliable, unreliableEnd + 250_000_001L, CaptureStatus.STALE)
    }

    @Test fun captureUsesCurrentFreshnessAndLatestActualRayWithCustomMetadata() {
        val deviceDirection = ViewingDirection(0.0, 1.0, 0.0)
        val s = OrientationSession(
            OrientationSource.ROTATION_VECTOR, deviceDirection = deviceDirection, cameraMappingId = "selected-camera",
        )
        s.updateAccuracy(3)
        repeat(13) { i ->
            val time = base + i * tick
            s.add(rotationX(i * 0.0001), time, time)
        }
        val end = base + 12 * tick
        val frame = s.referenceFrameId
        val published = s.latest(end)!!
        val now = end + 123_456_789L
        val observation = capture(s, now)
        assertEquals(end, observation.timestampNanos)
        assertEquals(OrientationRotation.fromRotationVector(rotationX(0.0012))!!.rotate(deviceDirection), observation.direction)
        assertEquals(frame, observation.referenceFrameId)
        assertEquals(OrientationSource.ROTATION_VECTOR, observation.source)
        assertEquals("selected-camera", observation.cameraMappingId)
        assertEquals(s.quality(now), observation.quality)
        assertEquals(published.quality.copy(ageNanos = now - end), observation.quality)
        assertEquals(CaptureResult.Rejected(CaptureStatus.STALE), s.capture(end + 250_000_001L))
        assertEquals(frame, s.referenceFrameId)
        assertEquals(Interruption.STARTED, s.lastInterruption)
    }
}
