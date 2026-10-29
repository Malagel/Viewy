package com.malagel.viewy.ui

import com.malagel.viewy.domain.KnownHeightResult

/** Equal geometry from a later capture is still a distinct presentation entry. */
internal class ResultIdentity(private val estimate: KnownHeightResult.Estimate) {
    override fun equals(other: Any?): Boolean = other is ResultIdentity && estimate === other.estimate
    override fun hashCode(): Int = System.identityHashCode(estimate)
}

/** Host-local entry memory. Recreated hosts show their already-completed result directly. */
internal class ResultEntryMemory(initialResult: KnownHeightResult.Estimate?) {
    private var entered = initialResult

    fun enter(result: KnownHeightResult.Estimate): Boolean {
        if (entered === result) return false
        entered = result
        return true
    }
}

/** One frame-driven timeline: counter, height, separation, disclaimer, without overlapping reveals. */
internal object ResultReveal {
    const val entranceMillis = 215
    const val countMillis = 750
    const val fadeMillis = 180
    const val totalMillis = countMillis + 3 * fadeMillis

    fun countFraction(elapsed: Float): Float = (elapsed / countMillis).coerceIn(0f, 1f)
    fun supportingAlpha(elapsed: Float, index: Int): Float =
        ((elapsed - countMillis - index * fadeMillis) / fadeMillis).coerceIn(0f, 1f)
}
