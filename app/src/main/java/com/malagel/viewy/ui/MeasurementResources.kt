package com.malagel.viewy.ui

import com.malagel.viewy.camera.CameraDirectionMapping
import com.malagel.viewy.camera.CameraPreviewState
import com.malagel.viewy.camera.CameraRecovery
import com.malagel.viewy.camera.PreviewSession
import com.malagel.viewy.measurement.KnownHeightWorkflow

/** The measurement window reports visibility independently of method hardware requirements. */
data class MeasurementVisibility(
    val surfaceVisible: Boolean = false,
    val windowFocused: Boolean = false,
    val covered: Boolean = false,
    val heightEditing: Boolean = false,
    val permissionGranted: Boolean = false,
)

/** Main-thread resource owner with injectable platform effects for lifecycle/ownership tests. */
internal class MeasurementResources(
    private val workflow: KnownHeightWorkflow,
    private val startOrientation: (CameraDirectionMapping) -> Unit,
    private val stopOrientation: () -> Unit,
    private val schedule: (Long, () -> Unit) -> (() -> Unit),
    private val onChanged: () -> Unit = {},
) {
    private val preview = PreviewSession()
    val camera: CameraPreviewState get() = preview.state
    private var eligible = false
    private var orientationRequired = false
    private var activeMapping: String? = null
    private var releasePreview: ((Boolean) -> Unit)? = null
    private var zoomRequest: ((Float) -> Unit)? = null
    private val recovery = CameraRecovery()
    private var cancelRetry: (() -> Unit)? = null
    private var retryGeneration = 0L
    val cameraFailureVisible: Boolean get() = eligible && recovery.needsUser
    var previewEpoch: Long = 0
        private set

    fun reconcile(cameraEligible: Boolean, requiresOrientation: Boolean, animateRelease: Boolean) {
        val wasEligible = eligible
        eligible = cameraEligible
        orientationRequired = requiresOrientation
        if (!eligible) {
            cancelPendingRetry()
            recovery.reset()
            // Revoke tokens before invoking platform cleanup or accepting another owner.
            preview.invalidate(preserveDescription = true)
            val release = releasePreview
            releasePreview = null
            zoomRequest = null
            if (wasEligible) previewEpoch++
            release?.invoke(animateRelease)
        }
        reconcileOrientation()
    }

    /** Complete method exit: revoke ownership/retries and forget previous camera diagnostics/zoom. */
    fun clear() {
        reconcile(false, false, animateRelease = false)
        preview.invalidate()
        previewEpoch++
    }

    fun startPreview(requestZoom: (Float) -> Unit = {}, release: (Boolean) -> Unit): Long? {
        if (!eligible || recovery.waiting || recovery.needsUser) return null
        preview.invalidate()
        releasePreview?.invoke(false)
        releasePreview = release
        zoomRequest = requestZoom
        val token = preview.start()
        reconcileOrientation()
        return token
    }

    fun previewChanged(token: Long, camera: CameraPreviewState): Boolean {
        if (!eligible || !preview.update(token, camera)) return false
        reconcileOrientation()
        if (camera.ready) recovery.reset()
        if (camera.failed) {
            // Revoke callbacks before cleanup; a failed binding cannot publish readiness again.
            preview.invalidate(preserveDescription = true)
            val release = releasePreview
            releasePreview = null
            zoomRequest = null
            release?.invoke(false)
            val delay = recovery.failure(camera.retryable)
            if (delay != null) {
                val generation = ++retryGeneration
                cancelRetry = schedule(delay) {
                    if (eligible && generation == retryGeneration && recovery.waiting) {
                        cancelRetry = null
                        recovery.retryDue()
                        previewEpoch++
                        onChanged()
                    }
                }
            }
        }
        return true
    }

    fun requestZoom(ratio: Float) {
        if (eligible && camera.zoom.editable(camera.ready, workflow.state.top != null, workflow.state.completed)) {
            zoomRequest?.invoke(ratio)
        }
    }

    fun retryCamera() {
        if (!eligible || !recovery.needsUser) return
        cancelPendingRetry()
        recovery.reset()
        preview.invalidate()
        previewEpoch++
    }

    private fun cancelPendingRetry() {
        retryGeneration++
        cancelRetry?.invoke()
        cancelRetry = null
    }

    fun stopPreview(token: Long) {
        if (!preview.stop(token)) return
        releasePreview = null
        zoomRequest = null
        reconcileOrientation()
    }

    fun restartOrientation() {
        activeMapping = null
        stopOrientation()
        reconcileOrientation()
    }

    private fun reconcileOrientation() {
        val desired = camera.mapping.takeIf { eligible && orientationRequired && camera.ready }
        workflow.cameraReady(desired != null, intentionalSuspension = !eligible)
        if (desired?.id != activeMapping) {
            activeMapping = desired?.id
            if (desired == null) stopOrientation() else startOrientation(desired)
        }
    }
}
