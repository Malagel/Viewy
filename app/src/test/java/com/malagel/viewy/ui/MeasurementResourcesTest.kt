package com.malagel.viewy.ui

import com.malagel.viewy.camera.CameraDirectionMapping
import com.malagel.viewy.camera.CameraPreviewState
import com.malagel.viewy.camera.PreviewRelease
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
        val scheduled = mutableListOf<Retry>()
        var publications = 0
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
        }, schedule = { delay, action ->
            val retry = Retry(delay, action)
            scheduled += retry
            val cancel: () -> Unit = { retry.canceled = true }
            cancel
        }, onChanged = { publications++ })

        fun reconcile() {
            ui = ui.copy(measurement = workflow.state)
            resources.reconcile(ui.acquisitionEligible, ui.requiresOrientation,
                ui.resumed && ui.visibility.surfaceVisible && ui.visibility.windowFocused && !ui.measurement.completed)
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

    private class Retry(val delay: Long, val action: () -> Unit, var canceled: Boolean = false)

    @Test fun visibleReleaseKeepsItsImageThroughDisposalBeforeTheFirstAnimationFrame() {
        for (destination in listOf("editor", "result", "home", "settings", "tutorial", "prompt", "diagnostics")) {
            val h = Harness()
            var foreground = "live camera"
            var copies = 0
            var closes = 0
            val release = PreviewRelease { animate ->
                // Fake native pixels: completion retains an image for the result's own fade.
                foreground = if (animate || h.workflow.state.completed) {
                    copies++
                    "camera image"
                } else "waiting color"
                closes++
            }
            h.reconcile()
            val token = h.resources.startPreview(release = release::release)!!
            assertTrue(h.resources.previewChanged(token, camera))
            when (destination) {
                "editor" -> h.ui = h.ui.copy(visibility = h.ui.visibility.copy(heightEditing = true))
                "result" -> { h.capture(0.5); h.capture(0.0) }
                "home" -> {
                    h.workflow.clear()
                    h.ui = h.ui.copy(selection = h.ui.selection.openModes())
                    h.resources.clear(animateRelease = true)
                }
                "settings" -> h.ui = h.ui.copy(visibility = h.ui.visibility.copy(covered = true))
                "tutorial" -> h.ui = h.ui.copy(tutorial = TutorialState(card = TutorialCard(1, method)))
                "prompt" -> { h.workflow.clear(); h.resources.clear(animateRelease = true) }
                "diagnostics" -> h.ui = h.ui.copy(diagnosticsOpen = true)
            }
            h.reconcile()
            assertTrue(destination, release.released)
            assertEquals(destination, "camera image", foreground)
            // Compose disposes the old effect as eligibility/epoch changes, before drawing its copy.
            release.release(animate = false)
            h.resources.stopPreview(token)
            assertEquals(destination, "camera image", foreground)
            assertEquals(destination, 1, copies)
            assertEquals(destination, 1, closes)
            assertFalse(destination, h.resources.camera.ready)
            assertFalse(destination, h.resources.previewChanged(token, camera))
        }
    }

    @Test fun disposalFirstClosesWithoutCopyingAndLaterSuspensionCannotChangeTheDecision() {
        val h = Harness()
        val actions = mutableListOf<Boolean>()
        val release = PreviewRelease { actions += it }
        h.reconcile()
        val token = h.resources.startPreview(release = release::release)!!
        h.resources.previewChanged(token, camera)
        release.release(animate = false)
        h.resources.stopPreview(token)
        h.ui = h.ui.copy(visibility = h.ui.visibility.copy(covered = true))
        h.reconcile()
        release.release(animate = true)
        assertEquals(listOf(false), actions)
        assertFalse(h.resources.previewChanged(token, camera))
    }

    @Test fun lateDisposalDoesNotRewritePixelsOrStopAReplacementPreview() {
        val h = Harness()
        val actions = mutableListOf<Boolean>()
        val release = PreviewRelease { actions += it }
        h.reconcile()
        val token = h.resources.startPreview(release = release::release)!!
        h.resources.previewChanged(token, camera)
        h.ui = h.ui.copy(visibility = h.ui.visibility.copy(covered = true))
        h.reconcile()
        h.ui = h.ui.copy(visibility = h.ui.visibility.copy(covered = false))
        val replacement = h.bind()
        release.release(animate = false)
        h.resources.stopPreview(token)
        assertEquals(listOf(true), actions)
        assertTrue(h.resources.camera.ready)
        assertTrue(h.resources.previewChanged(replacement, camera))
    }

    @Test fun homeClearsHeightCapturesResultAndCameraBeforeSameMethodCanBeEnteredFresh() {
        for (completed in listOf(false, true)) {
            val h = Harness()
            val token = h.bind()
            val sensor = h.sensorToken
            h.capture(0.5)
            if (completed) h.capture(0.0)
            val editor = HeightEditor(h.workflow)
            editor.open(HeightUnit.METERS)
            editor.key('9')
            editor.cancel()
            h.workflow.clear()
            h.ui = h.ui.copy(selection = h.ui.selection.openModes(), heightEditor = null)
            h.resources.clear()
            h.reconcile()
            assertNull(h.ui.selection.activeMethod)
            assertNull(h.workflow.state.heightMeters)
            assertNull(h.workflow.state.top)
            assertNull(h.workflow.state.bottom)
            assertNull(h.workflow.state.result)
            assertNull(h.workflow.state.resultOrigin)
            assertNull(h.workflow.state.latest)
            assertNull(h.workflow.state.notice)
            assertNull(h.resources.camera.mapping)
            assertFalse(h.resources.camera.zoom.available)
            assertFalse(h.resources.previewChanged(token, camera))
            assertFalse(h.registration.accepts(sensor))
            assertNull(h.resources.startPreview { fail("Home bound a camera") })
            h.ui = h.ui.copy(selection = h.ui.selection.browse(method.mode).activate(method))
            h.reconcile()
            assertFalse(h.ui.heightConfigured)
            assertFalse(h.ui.acquisitionEligible)
            assertNull(h.resources.startPreview { fail("Reentry skipped fresh height entry") })
            editor.open(HeightUnit.METERS)
            assertEquals("", editor.draft!!.text)
            editor.key('3')
            assertTrue(editor.confirm())
            val fresh = h.bind()
            assertNotEquals(token, fresh)
            h.resources.stopPreview(token)
            assertTrue(h.resources.camera.ready)
            assertEquals(3.0, h.workflow.state.heightMeters!!, 0.0)
        }
    }

    @Test fun heightPromptFadeReleasesHardwareSynchronouslyAndRejectsOldReadiness() {
        for (animate in listOf(false, true)) {
            val h = Harness()
            val token = h.bind()
            val sensor = h.sensorToken
            h.capture(0.5)
            h.workflow.clear()
            h.resources.clear(animateRelease = animate)
            // Only a decorative snapshot may outlive clear; no callback or resource owns the fade.
            assertEquals(listOf(animate), h.releases)
            assertNull(h.workflow.state.heightMeters)
            assertNull(h.workflow.state.top)
            assertFalse(h.resources.camera.ready)
            assertFalse(h.resources.previewChanged(token, camera))
            assertFalse(h.registration.accepts(sensor))
            assertNull(h.resources.startPreview { fail("Height prompt acquired a camera") })
            h.reconcile()
            assertEquals(listOf(animate), h.releases)
            h.workflow.setHeight(3.0)
            h.reconcile()
            val fresh = h.resources.startPreview { h.releases += it }!!
            assertNotEquals(token, fresh)
            assertFalse(h.resources.camera.ready)
            assertFalse(h.resources.previewChanged(token, camera))
            assertTrue(h.resources.previewChanged(fresh, camera))
        }
    }

    @Test fun zoomUsesCurrentOwnerLocksAfterTopAndUnlocksAfterRestartOrInvalidation() {
        val h = Harness()
        h.reconcile()
        val requested = mutableListOf<Float>()
        val token = h.resources.startPreview(requestZoom = { requested += it }) { h.releases += it }!!
        val zoomCamera = camera.copy(zoom = com.malagel.viewy.camera.PreviewZoom(1f, 4f, 1f, true, true))
        h.resources.previewChanged(token, zoomCamera)
        h.resources.requestZoom(2f)
        assertEquals(listOf(2f), requested)
        val starts = h.starts
        val epoch = h.resources.previewEpoch
        h.resources.previewChanged(token, zoomCamera.copy(zoom = zoomCamera.zoom.copy(ratio = 2f, settled = false)))
        h.sample(0.5)
        val ui = h.ui.copy(measurement = h.workflow.state, camera = h.resources.camera)
        assertTrue(ui.measurement.canCapture)
        assertFalse(ui.canCapture)
        h.resources.previewChanged(token, zoomCamera.copy(zoom = zoomCamera.zoom.copy(ratio = 2f)))
        assertTrue(ui.copy(camera = h.resources.camera).canCapture)
        assertEquals(starts, h.starts)
        assertEquals(epoch, h.resources.previewEpoch)
        assertTrue(h.releases.isEmpty())
        h.capture(0.5)
        h.resources.requestZoom(3f)
        assertEquals(listOf(2f), requested)
        h.workflow.restart()
        h.reconcile()
        h.resources.requestZoom(3f)
        assertEquals(listOf(2f, 3f), requested)
        h.capture(0.5)
        // Pair expiry is an invalidation that allows zoom again without a rebind.
        h.now += 16_000_000_000L
        h.sample(0.0)
        assertNull(h.workflow.state.top)
        h.resources.requestZoom(4f)
        assertEquals(listOf(2f, 3f, 4f), requested)
        h.capture(0.5)
        h.capture(0.0)
        assertTrue(h.workflow.state.completed)
        h.resources.requestZoom(1f)
        assertEquals(3, requested.size)
        assertFalse(h.resources.previewChanged(token, zoomCamera))
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
            base.copy(tutorial = TutorialState(methodEntering = true)),
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
                assertEquals(MeasurementNotice.ACQUISITION_SUSPENDED, h.workflow.state.notice)
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
        assertNull(completed.notice)
        assertFalse(h.resources.camera.ready)
        assertEquals(listOf(false), h.releases) // Completion needs no decorative camera snapshot.
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
        h.scheduled.single().action()
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

    @Test fun startupAndTransientUnreadinessDoNotScheduleRecoveryButFailuresExhaustThenRetryManually() {
        val h = Harness()
        h.reconcile()
        var token = h.resources.startPreview { h.releases += it }!!
        repeat(5) { h.resources.previewChanged(token, CameraPreviewState()) }
        assertTrue(h.scheduled.isEmpty())
        assertFalse(h.resources.cameraFailureVisible)
        val failure = CameraPreviewState(failed = true)
        for (delay in listOf(500L, 1_000L, 2_000L)) {
            assertTrue(h.resources.previewChanged(token, failure))
            assertFalse(h.resources.cameraFailureVisible)
            assertFalse(h.resources.previewChanged(token, camera))
            assertNull(h.resources.startPreview { fail("Bound before retry was due") })
            val pending = h.scheduled.last()
            assertEquals(delay, pending.delay)
            pending.action()
            val epoch = h.resources.previewEpoch
            pending.action() // A duplicate queued callback cannot request another binding.
            assertEquals(epoch, h.resources.previewEpoch)
            token = h.resources.startPreview { h.releases += it }!!
        }
        h.resources.previewChanged(token, failure)
        assertEquals(3, h.scheduled.size)
        assertTrue(h.resources.cameraFailureVisible)
        assertNull(h.resources.startPreview { fail("Bound after exhaustion") })
        h.resources.retryCamera()
        assertFalse(h.resources.cameraFailureVisible)
        token = h.resources.startPreview { h.releases += it }!!
        h.resources.previewChanged(token, failure)
        assertEquals(500L, h.scheduled.last().delay)
        h.scheduled.last().action()
        h.bind() // Successful streaming clears the cycle.
        h.resources.previewChanged(h.resources.startPreview {}!!, failure)
        assertEquals(500L, h.scheduled.last().delay)
        assertEquals(2.0, h.workflow.state.heightMeters!!, 0.0)
    }

    @Test fun allIneligibilityTransitionsCancelRetriesAndRejectEvenAlreadyQueuedCallbacks() {
        val h = Harness()
        val base = h.ui
        val transitions = listOf(
            base.copy(resumed = false),
            base.copy(visibility = base.visibility.copy(covered = true)),
            base.copy(visibility = base.visibility.copy(heightEditing = true)),
            base.copy(visibility = base.visibility.copy(permissionGranted = false)),
            base.copy(visibility = base.visibility.copy(windowFocused = false)),
            base.copy(visibility = base.visibility.copy(surfaceVisible = false)),
            base.copy(selection = base.selection.openModes()),
            base.copy(diagnosticsOpen = true),
            base.copy(tutorial = TutorialState(card = TutorialCard(1, method))),
        )
        repeat(3) {
            transitions.forEach { hidden ->
                h.ui = base
                val token = h.bind()
                h.capture(0.5)
                h.resources.previewChanged(token, CameraPreviewState(failed = true))
                assertNull(h.workflow.state.top)
                val pending = h.scheduled.last()
                h.ui = hidden
                h.reconcile()
                assertTrue(pending.canceled)
                assertFalse(h.resources.cameraFailureVisible)
                h.ui = base // Rapid return before Compose can dispose the old effect.
                h.reconcile()
                val epoch = h.resources.previewEpoch
                pending.action()
                assertEquals(epoch, h.resources.previewEpoch)
                assertFalse(h.resources.previewChanged(token, camera))
                assertEquals(2.0, h.workflow.state.heightMeters!!, 0.0)
            }
        }
    }

    @Test fun permanentFailuresDoNotScheduleAndCompletionCannotBeRevivedByOldRetry() {
        val h = Harness()
        var token = h.bind()
        h.resources.previewChanged(token, CameraPreviewState(failed = true, retryable = false))
        assertTrue(h.scheduled.isEmpty())
        assertTrue(h.resources.cameraFailureVisible)
        h.resources.retryCamera()
        token = h.bind()
        h.resources.previewChanged(token, CameraPreviewState(failed = true))
        val pending = h.scheduled.last()
        pending.action()
        h.bind()
        h.capture(0.5)
        h.capture(0.0)
        val result = h.workflow.state.result
        assertNotNull(result)
        val epoch = h.resources.previewEpoch
        pending.action()
        h.resources.retryCamera()
        assertEquals(epoch, h.resources.previewEpoch)
        assertNull(h.resources.startPreview { fail("Completed measurement rebound") })
        assertSame(result, h.workflow.state.result)
        assertFalse(h.resources.cameraFailureVisible)
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

    @Test fun overlaysAndBackgroundBeforeTopNeverReportLostProgress() {
        val h = Harness()
        val base = h.ui
        val hidden = listOf(
            base.copy(visibility = base.visibility.copy(covered = true)),
            base.copy(visibility = base.visibility.copy(heightEditing = true)),
            base.copy(selection = base.selection.openModes()),
            base.copy(tutorial = TutorialState(card = TutorialCard(1, method))),
            base.copy(diagnosticsOpen = true), base.copy(resumed = false),
        )
        for (overlay in hidden) {
            h.bind()
            h.sample(0.5)
            h.ui = overlay
            h.reconcile()
            assertNull(h.workflow.state.notice)
            h.ui = base
            h.bind()
            h.sample(0.5)
            assertNull(h.workflow.state.notice)
        }
    }
    @Test fun heightDraftSuspendsImmediatelyRevokesLateCallbacksAndKeepsInterruptionNotice() {
        for (confirm in listOf(false, true)) {
            val h = Harness()
            val token = h.bind()
            h.capture(0.5)
            val editor = HeightEditor(h.workflow)
            editor.open(HeightUnit.METERS)
            // The draft alone gates acquisition, even before the view reports transition coverage.
            h.ui = h.ui.copy(heightEditor = editor.draft)
            h.reconcile()
            assertFalse(h.ui.acquisitionEligible)
            assertFalse(h.ui.canCapture)
            assertTrue(h.ui.canOpenSettings)
            assertTrue(h.ui.canOpenHelp)
            assertNull(h.workflow.state.top)
            assertEquals(MeasurementNotice.ACQUISITION_SUSPENDED, h.workflow.state.notice)
            assertFalse(h.resources.previewChanged(token, camera))
            assertNull(h.resources.startPreview { fail("Editor bound a camera") })
            editor.backspace()
            editor.key('4')
            if (confirm) assertTrue(editor.confirm()) else editor.cancel()
            h.ui = h.ui.copy(heightEditor = null, visibility = h.ui.visibility.copy(heightEditing = true))
            h.reconcile()
            assertNull(h.resources.startPreview { fail("Exiting editor bound a camera") })
            h.ui = h.ui.copy(visibility = h.ui.visibility.copy(heightEditing = false))
            h.bind()
            assertEquals(if (confirm) 4.0 else 2.0, h.workflow.state.heightMeters!!, 0.0)
            assertEquals(MeasurementNotice.ACQUISITION_SUSPENDED, h.workflow.state.notice)
            assertNull(h.workflow.state.top)
        }
    }

    @Test fun correctingOrCancellingCompletedHeightNeverRestartsAcquisition() {
        for (confirm in listOf(false, true)) {
            val h = Harness()
            h.bind()
            h.capture(0.5)
            h.capture(0.0)
            val completed = h.workflow.state
            val starts = h.starts
            val editor = HeightEditor(h.workflow)
            editor.open(HeightUnit.METERS)
            h.ui = h.ui.copy(heightEditor = editor.draft)
            h.reconcile()
            editor.backspace()
            editor.key('4')
            if (confirm) assertTrue(editor.confirm()) else editor.cancel()
            h.ui = h.ui.copy(heightEditor = null)
            h.reconcile()
            assertEquals(starts, h.starts)
            assertNull(h.resources.startPreview { fail("Height correction restarted the camera") })
            assertFalse(h.ui.acquisitionEligible)
            assertSame(completed.top, h.workflow.state.top)
            assertSame(completed.bottom, h.workflow.state.bottom)
            assertSame(completed.resultOrigin, h.workflow.state.resultOrigin)
            if (!confirm) assertSame(completed.result, h.workflow.state.result)
        }
    }
    @Test fun settingsAndHelpPreserveDraftAndSavedEvidenceWithoutRevivingAcquisition() {
        for (completed in listOf(false, true)) for (destination in listOf("settings", "help", "diagnostics")) {
            val h = Harness()
            val token = h.bind()
            h.capture(0.5)
            if (completed) h.capture(0.0)
            val saved = h.workflow.state
            val editor = HeightEditor(h.workflow)
            editor.open(HeightUnit.METERS)
            editor.backspace()
            "1.23456789".forEach(editor::key)
            editor.switchUnit()
            val draft = editor.draft!!
            h.ui = h.ui.copy(heightEditor = draft, visibility = h.ui.visibility.copy(heightEditing = true))
            h.reconcile()
            val editing = h.ui
            repeat(3) {
                h.ui = when (destination) {
                    "settings" -> editing.copy(visibility = editing.visibility.copy(covered = true))
                    "help" -> editing.copy(tutorial = TutorialState(card = TutorialCard(1, method)))
                    else -> editing.withDiagnostics(true, true)
                }
                h.reconcile()
                assertFalse(h.ui.canEditHeight)
                assertSame(draft, editor.draft)
                assertSame(draft, h.ui.heightEditor)
                assertEquals(saved.heightMeters, h.workflow.state.heightMeters)
                assertSame(saved.result, h.workflow.state.result)
                assertFalse(h.ui.acquisitionEligible)
                assertFalse(h.resources.previewChanged(token, camera))
                h.ui = editing
                h.reconcile()
                assertTrue(h.ui.canEditHeight)
                assertSame(draft, editor.draft)
                assertFalse(h.ui.acquisitionEligible)
                assertNull(h.resources.startPreview { fail("Overlay return revived the camera behind the editor") })
            }
            if (!completed) assertNull(h.workflow.state.top) // Existing interruption rule still applies.
            editor.cancel()
            assertEquals(saved.heightMeters, h.workflow.state.heightMeters)
            assertSame(saved.result, h.workflow.state.result)
        }
    }

    @Test fun completeResourceClearRemovesCameraMetadataZoomAndPendingRetryOwnership() {
        val h = Harness()
        val token = h.bind()
        h.resources.previewChanged(token, camera.copy(logicalCameraId = "0", physicalCameraId = "1",
            zoom = com.malagel.viewy.camera.PreviewZoom(1f, 8f, 4f, true, true)))
        h.resources.previewChanged(token, h.resources.camera.copy(ready = false, failed = true, errorDetail = "old failure"))
        val retry = h.scheduled.single()
        h.workflow.clear()
        h.resources.clear()
        val epoch = h.resources.previewEpoch
        retry.action()
        assertEquals(epoch, h.resources.previewEpoch)
        assertTrue(retry.canceled)
        assertFalse(h.resources.camera.failed)
        assertNull(h.resources.camera.errorDetail)
        assertNull(h.resources.camera.logicalCameraId)
        assertNull(h.resources.camera.physicalCameraId)
        assertNull(h.resources.camera.mapping)
        assertEquals(com.malagel.viewy.camera.PreviewZoom(), h.resources.camera.zoom)
        assertFalse(h.resources.previewChanged(token, camera))
        assertNull(h.resources.startPreview { fail("Cleared resources acquired without a new eligible method") })
    }
}
