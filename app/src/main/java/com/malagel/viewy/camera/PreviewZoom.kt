package com.malagel.viewy.camera

/** CameraX-reported limits with a 1× user-zoom floor. No geometry dependency. */
data class PreviewZoom(
    val minimum: Float = 1f,
    val maximum: Float = 1f,
    val ratio: Float = 1f,
    val available: Boolean = false,
    val settled: Boolean = false,
) {
    fun editable(cameraReady: Boolean, hasTop: Boolean, completed: Boolean): Boolean =
        available && cameraReady && !hasTop && !completed && maximum > minimum
}

/** Latest-request ownership plus a conservative preview drain interval after CameraX acknowledgement.
 * This interval is a usability guard, not an exposure timestamp or calibrated latency bound.
 */
internal class PreviewZoomSession {
    var state = PreviewZoom()
        private set
    private var generation = 0L
    private var settleAt: Long? = null

    fun reset(minimum: Float, maximum: Float): Long? {
        close()
        if (!minimum.isFinite() || !maximum.isFinite() || minimum <= 0f || maximum < minimum) return null
        // Both input methods and button bounds use this effective range. A logical
        // camera's sub-1× range may require a different physical output.
        val userMinimum = maxOf(1f, minimum)
        if (userMinimum > maximum) return null
        state = PreviewZoom(userMinimum, maximum, userMinimum, available = true)
        return request(userMinimum)
    }

    fun request(ratio: Float): Long? {
        if (!state.available || !ratio.isFinite()) return null
        state = state.copy(ratio = ratio.coerceIn(state.minimum, state.maximum), settled = false)
        settleAt = null
        return ++generation
    }

    fun applied(token: Long, nowMillis: Long): Boolean {
        if (!state.available || token != generation) return false
        settleAt = nowMillis + SETTLE_MILLIS
        return true
    }

    /** A reported lens transition needs fresh settling, not another zoom request. */
    fun restartSettling() {
        if (!state.available) return
        state = state.copy(settled = false)
        settleAt = null
    }

    fun settle(token: Long, nowMillis: Long): Boolean {
        if (token != generation || settleAt?.let { nowMillis >= it } != true) return false
        state = state.copy(settled = true)
        return true
    }

    fun owns(token: Long): Boolean = state.available && token == generation

    fun close() {
        generation++
        settleAt = null
        state = PreviewZoom()
    }

    companion object { const val SETTLE_MILLIS = 350L }
}
