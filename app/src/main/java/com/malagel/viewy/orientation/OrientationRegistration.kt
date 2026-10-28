package com.malagel.viewy.orientation

/** Main-thread registration ownership. Stop/retry revokes even callbacks already queued by Android. */
internal class OrientationRegistration {
    private var generation = 0L
    private var active: Long? = null

    fun begin(): Long = (++generation).also { active = it }
    fun invalidate() { active = null }
    fun accepts(token: Long): Boolean = active == token
}
