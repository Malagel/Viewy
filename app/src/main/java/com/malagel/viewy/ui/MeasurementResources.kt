package com.malagel.viewy.ui

import com.malagel.viewy.camera.CameraDirectionMapping
import com.malagel.viewy.camera.CameraPreviewState
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
) {
    private val preview = PreviewSession()
    val camera: CameraPreviewState get() = preview.state
    private var eligible = false
    private var orientationRequired = false
    private var activeMapping: String? = null
    private var releasePreview: ((Boolean) -> Unit)? = null
    var previewEpoch: Long = 0
        private set

    fun reconcile(cameraEligible: Boolean, requiresOrientation: Boolean, animateRelease: Boolean) {
        eligible = cameraEligible
        orientationRequired = requiresOrientation
        if (!eligible) {
            // Revoke tokens before invoking platform cleanup or accepting another owner.
            preview.invalidate(preserveDescription = true)
            val release = releasePreview
            releasePreview = null
            if (release != null) previewEpoch++
            release?.invoke(animateRelease)
        }
        reconcileOrientation()
    }

    fun startPreview(release: (Boolean) -> Unit): Long? {
        if (!eligible) return null
        preview.invalidate()
        releasePreview?.invoke(false)
        releasePreview = release
        val token = preview.start()
        reconcileOrientation()
        return token
    }

    fun previewChanged(token: Long, camera: CameraPreviewState): Boolean {
        if (!eligible || !preview.update(token, camera)) return false
        reconcileOrientation()
        return true
    }

    fun stopPreview(token: Long) {
        if (!preview.stop(token)) return
        releasePreview = null
        reconcileOrientation()
    }

    fun restartOrientation() {
        activeMapping = null
        stopOrientation()
        reconcileOrientation()
    }

    private fun reconcileOrientation() {
        val desired = camera.mapping.takeIf { eligible && orientationRequired && camera.ready }
        workflow.cameraReady(desired != null)
        if (desired?.id != activeMapping) {
            activeMapping = desired?.id
            if (desired == null) stopOrientation() else startOrientation(desired)
        }
    }
}
