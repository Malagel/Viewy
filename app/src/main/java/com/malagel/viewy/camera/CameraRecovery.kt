package com.malagel.viewy.camera

/** Provider futures wrap causes; denied access must not become retryable through wrapping. */
internal fun cameraExceptionRetryable(error: Throwable, configuring: Boolean = false): Boolean =
    generateSequence(error) { it.cause }.none {
        it is SecurityException || (configuring && it is IllegalArgumentException)
    }

/** A cycle includes the initial binding and at most three automatic replacement bindings. */
internal class CameraRecovery {
    var attempts = 0
        private set
    var waiting = false
        private set
    var needsUser = false
        private set

    fun reset() {
        attempts = 0
        waiting = false
        needsUser = false
    }

    fun failure(retryable: Boolean): Long? {
        if (waiting || needsUser) return null
        if (!retryable || attempts == 3) {
            needsUser = true
            return null
        }
        waiting = true
        return listOf(500L, 1_000L, 2_000L)[attempts++]
    }

    fun retryDue() { waiting = false }
}

/** Deadlines do not slide on repeated unready notifications. No Android clock is required. */
internal class PreviewWatchdog {
    var hasStreamed = false
        private set
    private var deadline: Long? = null

    fun reset(nowMillis: Long) {
        hasStreamed = false
        deadline = nowMillis + 12_000L
    }

    fun readiness(ready: Boolean, nowMillis: Long): Long? {
        if (ready) {
            hasStreamed = true
            deadline = null
        } else if (deadline == null) {
            deadline = nowMillis + if (hasStreamed) 3_000L else 12_000L
        }
        return deadline
    }

    fun expired(nowMillis: Long): Boolean = deadline?.let { nowMillis >= it } == true
}
