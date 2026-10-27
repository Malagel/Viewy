package com.malagel.viewy.camera

import com.malagel.viewy.R

/** Main-thread ownership token: callbacks from disposed/retried previews cannot revive readiness. */
internal class PreviewSession {
    private var generation = 0L
    private var active: Long? = null
    var state = CameraPreviewState()
        private set

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
