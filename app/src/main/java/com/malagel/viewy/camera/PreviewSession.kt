package com.malagel.viewy.camera

import com.malagel.viewy.R

/** Main-thread ownership token: callbacks from disposed/retried previews cannot revive readiness. */
internal class PreviewSession {
    private var generation = 0L
    private var active: Long? = null
    var state = CameraPreviewState()
        private set

    /** Revoke ownership before suspension or a different method, including queued callbacks. */
    fun invalidate(preserveDescription: Boolean = false) {
        active = null
        state = if (preserveDescription) state.copy(
            ready = false, open = false, streaming = false,
            messageRes = if (state.failed) state.messageRes else R.string.camera_stopped,
        ) else CameraPreviewState(messageRes = R.string.camera_stopped)
    }

    fun start(): Long {
        state = CameraPreviewState()
        return (++generation).also { active = it }
    }

    fun update(token: Long, value: CameraPreviewState): Boolean {
        if (active != token) return false
        state = value
        return true
    }

    fun stop(token: Long): Boolean {
        if (active != token) return false
        active = null
        state = CameraPreviewState(messageRes = R.string.camera_stopped)
        return true
    }
}
