package com.malagel.viewy.orientation

import com.malagel.viewy.domain.ViewingDirection
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
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
}
