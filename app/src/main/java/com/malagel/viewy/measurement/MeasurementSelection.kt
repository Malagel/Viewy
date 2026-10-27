package com.malagel.viewy.measurement

import com.malagel.viewy.domain.MeasurementMethod
import com.malagel.viewy.domain.MeasurementMode

/** Navigation only: browsing never changes the active workflow or its resources. */
data class MeasurementSelection(
    val activeMethod: MeasurementMethod = MeasurementMethod.KNOWN_OBJECT_HEIGHT,
    val modeMenuOpen: Boolean = false,
    val browsingMode: MeasurementMode? = null,
) {
    companion object {
        /** Only implemented catalog methods can be restored; workflow data is never restored. */
        fun restore(savedMethodId: String?) = MeasurementSelection(
            activeMethod = MeasurementMethod.entries.firstOrNull { it.id == savedMethodId }
                ?: MeasurementMethod.KNOWN_OBJECT_HEIGHT,
        )
    }

    fun toggleModes() = copy(modeMenuOpen = !modeMenuOpen, browsingMode = null)
    fun dismiss() = copy(modeMenuOpen = false, browsingMode = null)
    fun browse(mode: MeasurementMode) = if (mode.available) {
        copy(modeMenuOpen = false, browsingMode = mode)
    } else this
    fun activate(method: MeasurementMethod) = if (browsingMode == method.mode) {
        copy(activeMethod = method, modeMenuOpen = false, browsingMode = null)
    } else this
}
