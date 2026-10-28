package com.malagel.viewy.ui

import com.malagel.viewy.camera.CameraDirectionMapping
import com.malagel.viewy.camera.CameraPreviewState
import com.malagel.viewy.domain.MeasurementMethod
import com.malagel.viewy.domain.ViewingDirection
import com.malagel.viewy.measurement.*
import com.malagel.viewy.orientation.*
import com.malagel.viewy.tutorial.*
import org.junit.Assert.*
import org.junit.Test

class MeasurementResourcesTest {
    private val method = MeasurementMethod.KNOWN_OBJECT_HEIGHT
    private val mapping = CameraDirectionMapping.forRearCamera("rear", null, null)
    private val camera = CameraPreviewState(ready = true, mapping = mapping, open = true, streaming = true)
    private val quality = OrientationQuality(13, 480_000_000L, 0, 0.0, 0.0, 0.0, 3, CaptureStatus.READY)

    private inner class Harness {
        val workflow = KnownHeightWorkflow().also { it.setHeight(2.0) }
        val registration = OrientationRegistration()
        var starts = 0
        var stops = 0
        val releases = mutableListOf<Boolean>()
        var sensorToken = 0L
        var now = 2_000_000_000L
        var ui = MeasurementUiState(
            selection = MeasurementSelection().browse(method.mode).activate(method), resumed = true,
            visibility = MeasurementVisibility(surfaceVisible = true, windowFocused = true, permissionGranted = true),
        )
        val resources = MeasurementResources(workflow, {
            starts++
            sensorToken = registration.begin()
        }, {
            stops++
            registration.invalidate()
            workflow.update(null, null, false, now)
        })

        fun reconcile() {
            ui = ui.copy(measurement = workflow.state)
            resources.reconcile(ui.acquisitionEligible, ui.requiresOrientation,
                ui.resumed && ui.visibility.surfaceVisible && ui.visibility.windowFocused)
        }

        fun bind(): Long {
            reconcile()
            return resources.startPreview { releases += it }!!.also { resources.previewChanged(it, camera) }
        }

        fun sample(z: Double, token: Long = sensorToken): OrientationObservation {
            val observation = OrientationObservation(ViewingDirection(1.0, 0.0, z), now,
                "frame-$token", OrientationSource.GAME_ROTATION_VECTOR, quality, mapping.id)
            if (registration.accepts(token)) workflow.update(observation, observation.referenceFrameId, true, now)
            return observation
        }

        fun capture(z: Double) {
            val observation = sample(z)
            workflow.capture(CaptureResult.Captured(observation), now)
            reconcile()
            now += 1_000_000_000L
        }
    }

    @Test fun repeatedOverlayCyclesInvalidateIncompletePairRetainHeightAndRequireFreshSessions() {
        val h = Harness()
        val base = h.ui
        val overlays = listOf(
            base.copy(visibility = base.visibility.copy(covered = true)), // Settings, diagnostics exit, status
            base.copy(diagnosticsOpen = true),
            base.copy(visibility = base.visibility.copy(heightEditing = true)),
            base.copy(selection = base.selection.openModes()),
            base.copy(tutorial = TutorialState(card = TutorialCard(1, method))),
            base.copy(tutorial = TutorialState(pendingMethod = method)),
            base.copy(tutorial = TutorialState(selectorExiting = true)),
        )
        repeat(3) {
            for (overlay in overlays) {
                val oldPreview = h.bind()
                val oldSensor = h.sensorToken
                h.capture(0.5)
                assertNotNull(h.workflow.state.top)
                h.ui = overlay
                h.reconcile()
                val epoch = h.resources.previewEpoch
                repeat(3) { h.reconcile() }
                assertEquals(epoch, h.resources.previewEpoch)
                assertNull(h.workflow.state.top)
                assertNull(h.workflow.state.latest)
                assertNull(h.workflow.state.frameId)
                assertEquals(2.0, h.workflow.state.heightMeters!!, 0.0)
                assertFalse(h.resources.camera.ready)
                assertFalse(h.resources.previewChanged(oldPreview, camera))
                assertFalse(h.registration.accepts(oldSensor))
                h.sample(0.0, oldSensor)
                assertNull(h.workflow.state.latest)
                h.ui = base
                h.reconcile()
                assertFalse(h.workflow.state.canCapture)
            }
        }
        assertEquals(overlays.size * 3, h.starts)
        assertEquals(h.starts, h.stops)
        assertEquals(h.starts, h.releases.size)
    }

    @Test fun completionReleasesOnceAndStaysStoppedAcrossOverlaysBackgroundAndRestart() {
        val h = Harness()
        val old = h.bind()
        val sensor = h.sensorToken
        h.capture(0.5)
        h.capture(0.0)
        val completed = h.workflow.state
        assertNotNull(completed.result)
        assertFalse(h.resources.camera.ready)
        assertEquals(listOf(true), h.releases)
        assertFalse(h.resources.previewChanged(old, camera))
        repeat(3) {
            h.ui = h.ui.copy(diagnosticsOpen = true, resumed = false)
            h.reconcile()
            h.ui = h.ui.copy(diagnosticsOpen = false, resumed = true)
            h.reconcile()
            assertNull(h.resources.startPreview { fail("Completed measurement rebound") })
            h.sample(1.0, sensor)
            assertSame(completed.result, h.workflow.state.result)
            assertSame(completed.top, h.workflow.state.top)
            assertSame(completed.bottom, h.workflow.state.bottom)
        }
        assertEquals(1, h.starts)
        assertEquals(1, h.stops)
        h.workflow.restart()
        h.reconcile()
        val next = h.bind()
        assertNotEquals(old, next)
        assertNotEquals(sensor, h.sensorToken)
        h.resources.stopPreview(old) // Late disposal cannot stop the replacement.
        assertTrue(h.resources.camera.ready)
        assertFalse(h.workflow.state.canCapture)
        h.capture(0.5)
        h.capture(0.0)
        assertNotNull(h.workflow.state.result)
        assertEquals(2.0, h.workflow.state.heightMeters!!, 0.0)
    }

    @Test fun ordinaryPublicationsDoNotRebindAndRapidSuspendResumeRevokesTheOldOwner() {
        val h = Harness()
        val old = h.bind()
        repeat(30) { h.sample(0.5); h.reconcile(); h.resources.previewChanged(old, camera) }
        assertEquals(1, h.starts)
        assertEquals(0, h.stops)
        assertTrue(h.releases.isEmpty())
        val epoch = h.resources.previewEpoch
        h.ui = h.ui.copy(visibility = h.ui.visibility.copy(covered = true))
        h.reconcile()
        // Close before Compose observes the open state: the epoch still forces a new binding.
        h.ui = h.ui.copy(visibility = h.ui.visibility.copy(covered = false))
        h.reconcile()
        assertTrue(h.resources.previewEpoch > epoch)
        assertFalse(h.resources.previewChanged(old, camera))
        h.bind()
        assertEquals(2, h.starts)
    }

    @Test fun backgroundFocusLossPermissionRevocationAndHiddenWorkspaceReleaseImmediately() {
        for (hide in listOf<(MeasurementUiState) -> MeasurementUiState>(
            { it.copy(resumed = false) },
            { it.copy(visibility = it.visibility.copy(windowFocused = false)) },
            { it.copy(visibility = it.visibility.copy(surfaceVisible = false)) },
            { it.copy(visibility = it.visibility.copy(permissionGranted = false)) },
        )) {
            val h = Harness()
            val base = h.ui
            val token = h.bind()
            h.capture(0.5)
            h.ui = hide(base)
            h.reconcile()
            assertEquals(1, h.releases.size)
            if (!h.ui.resumed || !h.ui.visibility.windowFocused || !h.ui.visibility.surfaceVisible) {
                assertFalse(h.releases.single())
            }
            assertNull(h.workflow.state.top)
            assertFalse(h.resources.previewChanged(token, camera))
            assertNull(h.resources.startPreview { fail("Acquisition while hidden") })
            h.ui = base
            h.bind()
            assertFalse(h.workflow.state.canCapture)
            h.sample(0.5)
            assertTrue(h.workflow.state.canCapture)
        }
    }

    @Test fun cameraFailureAndOrientationRetryInvalidatePairsAndRecoverWithFreshOwnership() {
        val h = Harness()
        val token = h.bind()
        h.capture(0.5)
        h.resources.previewChanged(token, camera.copy(ready = false, failed = true))
        assertEquals(1, h.stops)
        assertNull(h.workflow.state.top)
        val next = h.bind()
        assertFalse(h.resources.previewChanged(token, camera))
        h.resources.stopPreview(token)
        assertTrue(h.resources.camera.ready)
        h.capture(0.5)
        val sensor = h.sensorToken
        h.workflow.restart(MeasurementNotice.SENSOR_RESTARTED)
        h.resources.restartOrientation()
        assertFalse(h.registration.accepts(sensor))
        assertNull(h.workflow.state.top)
        assertFalse(h.workflow.state.canCapture)
        assertTrue(h.resources.previewChanged(next, camera))
        h.sample(0.5)
        assertTrue(h.workflow.state.canCapture)
    }

    @Test fun cleanupCallbacksAreAlreadyObsoleteAndFailedSensorRegistrationCannotContaminateFallback() {
        val h = Harness()
        h.reconcile()
        var token = 0L
        token = h.resources.startPreview {
            assertFalse(h.resources.previewChanged(token, camera))
            h.resources.stopPreview(token)
        }!!
        h.resources.previewChanged(token, camera)
        h.ui = h.ui.copy(resumed = false)
        h.reconcile()
        assertFalse(h.resources.camera.ready)

        val failed = h.registration.begin()
        h.registration.invalidate()
        assertFalse(h.registration.accepts(failed))
        val fallback = h.registration.begin()
        assertFalse(h.registration.accepts(failed))
        assertTrue(h.registration.accepts(fallback))
        h.registration.invalidate()
        assertFalse(h.registration.accepts(fallback))
    }
}
