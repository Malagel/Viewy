package com.malagel.viewy.ui

import com.malagel.viewy.camera.CameraDirectionMapping
import com.malagel.viewy.camera.CameraPreviewState
import com.malagel.viewy.camera.PreviewZoom
import com.malagel.viewy.domain.KnownHeightDistance
import com.malagel.viewy.domain.KnownHeightResult
import com.malagel.viewy.domain.MeasurementMethod
import com.malagel.viewy.domain.ViewingDirection
import com.malagel.viewy.measurement.*
import com.malagel.viewy.orientation.*
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin

class AlignmentGuidanceTest {
    private val mapping = CameraDirectionMapping.forRearCamera("rear", null, null)
    private val quality = OrientationQuality(13, 480_000_000L, 0, 0.0, 0.0, 0.0, 3, CaptureStatus.READY)
    private val first = OrientationObservation(ViewingDirection(1.0, 0.0, 0.5), 1_000_000_000L,
        "frame", OrientationSource.GAME_ROTATION_VECTOR, quality, mapping.id)
    private val ready = MeasurementUiState(
        selection = MeasurementSelection().browse(MeasurementMethod.KNOWN_OBJECT_HEIGHT.mode)
            .activate(MeasurementMethod.KNOWN_OBJECT_HEIGHT),
        resumed = true,
        visibility = MeasurementVisibility(surfaceVisible = true, windowFocused = true, permissionGranted = true),
        measurement = MeasurementState(heightMeters = 2.0, cameraReady = true, stage = MeasurementStage.BOTTOM,
            top = first, latest = first, frameId = first.referenceFrameId),
        camera = CameraPreviewState(ready = true, mapping = mapping, zoom = PreviewZoom(settled = true)),
        acquisition = AcquisitionState(running = true, activeSource = first.source, referenceFrameId = first.referenceFrameId),
    )

    private inner class Harness {
        val guidance = AlignmentGuidance()
        var now = first.timestampNanos
        var state = ready
        fun update(bearing: Double = 0.0, afterMillis: Long = 0): AlignmentCueState {
            now += afterMillis * 1_000_000L
            val angle = Math.toRadians(bearing)
            state = state.copy(measurement = state.measurement.copy(latest = first.copy(
                direction = ViewingDirection(cos(angle), sin(angle), 0.0), timestampNanos = now)))
            return publish()
        }
        fun publish() = guidance.update(state, now)
        fun right(): AlignmentCueState { update(5.0); return update(5.0, 180) }
        fun recover(): AlignmentCueState { update(); return update(afterMillis = 180) }
    }

    @Test fun waitingForTopAndInitialAlignmentNeverShowChevrons() {
        val h = Harness()
        h.state = ready.copy(measurement = ready.measurement.copy(top = null, stage = MeasurementStage.TOP))
        assertEquals(AlignmentCue.HIDDEN, h.update(5.0).cue)
        assertEquals(AlignmentCue.HIDDEN, h.update(5.0, 200).cue)
        h.state = ready
        repeat(5) { assertEquals(AlignmentCue.HIDDEN, h.update(afterMillis = 200).cue) }
    }

    @Test fun directionsUsePhysicalRearCameraCorrectionIncludingWrappedBearings() {
        for ((topDegrees, currentDegrees, expected) in listOf(
            Triple(0.0, 5.0, AlignmentCue.RIGHT), // Positive bearing deviation is a physical left turn.
            Triple(0.0, -5.0, AlignmentCue.LEFT),
            Triple(179.0, -177.0, AlignmentCue.RIGHT),
            Triple(-179.0, 177.0, AlignmentCue.LEFT),
        )) {
            val h = Harness()
            val angle = Math.toRadians(topDegrees)
            h.state = ready.copy(measurement = ready.measurement.copy(top = first.copy(
                direction = ViewingDirection(cos(angle), sin(angle), 0.5))))
            assertEquals(AlignmentCue.HIDDEN, h.update(currentDegrees).cue)
            assertEquals(expected, h.update(currentDegrees, 180).cue)
            assertFalse(h.state.canCapture)
        }
    }

    @Test fun recoveryTriggersOnlyOnceAndOrdinaryPublicationsDoNotRestartIt() {
        val h = Harness()
        assertEquals(AlignmentCue.RIGHT, h.right().cue)
        assertEquals(AlignmentCue.RIGHT, h.update().cue)
        val success = h.update(afterMillis = 180)
        assertEquals(AlignmentCue.RECOVERED, success.cue)
        assertTrue(success.recoveryId > 0)
        repeat(6) { assertEquals(success, h.update(afterMillis = 200)) }
        h.right()
        val next = h.recover()
        assertEquals(AlignmentCue.RECOVERED, next.cue)
        assertTrue(next.recoveryId > success.recoveryId)
    }

    @Test fun directionSwitchIsFilteredAndDoesNotTriggerSuccess() {
        val h = Harness()
        val right = h.right()
        assertEquals(right, h.update(-5.0))
        assertEquals(right, h.update(-5.0, 179))
        val left = h.update(-5.0, 1)
        assertEquals(AlignmentCue.LEFT, left.cue)
        assertEquals(right.recoveryId, left.recoveryId)
    }

    @Test fun thresholdChatterCannotFlashSuccessAndFilteringNeverChangesCaptureEligibility() {
        val h = Harness()
        assertEquals(AlignmentCue.HIDDEN, h.update(0.5).cue)
        repeat(8) {
            assertEquals(AlignmentCue.HIDDEN, h.update(0.4, 60).cue)
            assertTrue(h.state.canCapture)
            assertEquals(AlignmentCue.HIDDEN, h.update(0.5, 60).cue)
            assertFalse(h.state.canCapture)
        }
        val right = h.update(0.5, 180)
        assertEquals(AlignmentCue.RIGHT, right.cue)
        repeat(8) {
            assertEquals(right, h.update(0.4, 60))
            assertTrue(h.state.canCapture)
            assertEquals(right, h.update(0.5, 60))
        }
        assertEquals(AlignmentCue.RECOVERED, h.recover().cue)
    }

    @Test fun freshMovingAndSettlingReadingsCanGuideWithoutEnablingCapture() {
        for (status in listOf(CaptureStatus.MOVING, CaptureStatus.SETTLING)) {
            val h = Harness()
            h.update(5.0)
            h.now += 180_000_000L
            h.state = h.state.copy(measurement = h.state.measurement.copy(latest = h.state.measurement.latest!!.copy(
                timestampNanos = h.now, quality = quality.copy(status = status))))
            assertEquals(AlignmentCue.RIGHT, h.publish().cue)
            assertFalse(h.state.canCapture)
        }
    }

    @Test fun staleMissingUnreliableFutureAndNearVerticalReadingsCancelRecovery() {
        val current = first.copy(timestampNanos = first.timestampNanos + 500_000_000L)
        for (observation in listOf(null,
            current.copy(timestampNanos = first.timestampNanos),
            current.copy(timestampNanos = first.timestampNanos + 501_000_000L),
            current.copy(quality = quality.copy(status = CaptureStatus.STALE)),
            current.copy(quality = quality.copy(status = CaptureStatus.WAITING)),
            current.copy(quality = quality.copy(status = CaptureStatus.UNRELIABLE)),
            current.copy(quality = quality.copy(sensorAccuracy = 0)),
            current.copy(direction = ViewingDirection(0.0, 0.0, 1.0)),
            current.copy(direction = ViewingDirection(0.01, 0.0, 1.0)),
        )) {
            val h = Harness()
            h.right()
            assertEquals(AlignmentCue.RECOVERED, h.recover().cue)
            h.now = first.timestampNanos + 500_000_000L
            h.state = h.state.copy(measurement = h.state.measurement.copy(latest = observation))
            assertEquals(AlignmentCue.HIDDEN, h.publish().cue)
            assertEquals(AlignmentCue.HIDDEN, h.update(afterMillis = 200).cue)
        }
    }

    @Test fun invalidatedOrReplacedTopAndExpiredPairsResetVisualHistory() {
        for (top in listOf(null, first.copy(timestampNanos = first.timestampNanos + 1))) {
            val h = Harness()
            h.right()
            h.state = h.state.copy(measurement = h.state.measurement.copy(top = top))
            assertEquals(AlignmentCue.HIDDEN, h.update().cue)
            h.state = h.state.copy(measurement = h.state.measurement.copy(top = first))
            assertEquals(AlignmentCue.HIDDEN, h.update(afterMillis = 200).cue)
        }
        val h = Harness()
        h.right()
        assertEquals(AlignmentCue.HIDDEN, h.update(5.0, 15_001).cue)
    }

    @Test fun sessionSourceAndMappingMismatchesHideImmediately() {
        val current = first.copy(timestampNanos = first.timestampNanos + 200_000_000L)
        val variants = listOf(
            ready.copy(measurement = ready.measurement.copy(frameId = "other")),
            ready.copy(acquisition = ready.acquisition.copy(referenceFrameId = "other")),
            ready.copy(acquisition = ready.acquisition.copy(activeSource = OrientationSource.ROTATION_VECTOR)),
            ready.copy(camera = ready.camera.copy(mapping = CameraDirectionMapping.forRearCamera("other", null, null))),
            ready.copy(measurement = ready.measurement.copy(latest = current.copy(referenceFrameId = "other"))),
            ready.copy(measurement = ready.measurement.copy(latest = current.copy(cameraMappingId = "other"))),
            ready.copy(measurement = ready.measurement.copy(latest = current.copy(source = OrientationSource.ROTATION_VECTOR))),
        )
        for (invalid in variants) {
            val h = Harness()
            h.right()
            h.state = invalid
            assertEquals(AlignmentCue.HIDDEN, h.publish().cue)
        }
    }

    @Test fun suspensionCameraLossAndResumptionCannotReplaySuccess() {
        val variants = listOf(
            ready.copy(resumed = false),
            ready.copy(visibility = ready.visibility.copy(covered = true)),
            ready.copy(visibility = ready.visibility.copy(surfaceVisible = false)),
            ready.copy(visibility = ready.visibility.copy(windowFocused = false)),
            ready.copy(visibility = ready.visibility.copy(heightEditing = true)),
            ready.copy(visibility = ready.visibility.copy(permissionGranted = false)),
            ready.copy(selection = ready.selection.openModes()),
            ready.copy(diagnosticsOpen = true),
            ready.copy(camera = ready.camera.copy(ready = false)),
            ready.copy(cameraFailureVisible = true),
            ready.copy(measurement = ready.measurement.copy(cameraReady = false)),
            ready.copy(acquisition = ready.acquisition.copy(running = false)),
        )
        for (suspended in variants) {
            val h = Harness()
            h.right()
            h.recover()
            h.state = suspended
            assertEquals(AlignmentCue.HIDDEN, h.publish().cue)
            h.state = ready
            assertEquals(AlignmentCue.HIDDEN, h.update(afterMillis = 200).cue)
            assertEquals(AlignmentCue.RIGHT, h.right().cue)
        }
    }

    @Test fun completionAndRestartDiscardDirectionalAndSuccessStates() {
        val result = KnownHeightDistance.estimate(2.0, first.direction, ViewingDirection(1.0, 0.0, 0.0)) as KnownHeightResult.Estimate
        for (recover in listOf(false, true)) {
            val h = Harness()
            h.right()
            if (recover) h.recover()
            h.state = h.state.copy(measurement = h.state.measurement.copy(stage = MeasurementStage.RESULT, result = result))
            assertEquals(AlignmentCue.HIDDEN, h.publish().cue)
            h.state = ready.copy(measurement = ready.measurement.copy(top = null, stage = MeasurementStage.TOP))
            assertEquals(AlignmentCue.HIDDEN, h.update().cue)
            h.state = ready
            assertEquals(AlignmentCue.HIDDEN, h.update(afterMillis = 200).cue)
        }
    }
}
