package com.malagel.viewy.camera

/** Startup controls must settle before requesting a displayed frame. Later zooms keep the image. */
internal class PreviewPresentationGate {
    private var generation = 0L
    private var pending: Long? = null
    val awaitingFrame: Boolean get() = pending != null
    var ready = false
        private set

    fun update(streamReady: Boolean, controlsSettled: Boolean): Long? {
        if (!streamReady || (!ready && !controlsSettled)) {
            reset()
            return null
        }
        if (ready || pending != null) return null
        return (++generation).also { pending = it }
    }

    fun presented(token: Long): Boolean {
        if (pending != token) return false
        pending = null
        ready = true
        return true
    }

    fun reset() {
        generation++
        pending = null
        ready = false
    }
}
