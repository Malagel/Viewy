package com.malagel.viewy.measurement

import com.malagel.viewy.domain.MeasurementMethod
import com.malagel.viewy.domain.MeasurementMode

sealed interface SelectionDestination {
    data object Closed : SelectionDestination
    data object Modes : SelectionDestination
    data class Methods(val mode: MeasurementMode) : SelectionDestination
}

/** In-memory navigation only. A new ViewModel starts inactive, regardless of legacy preferences. */
data class MeasurementSelection(
    val activeMethod: MeasurementMethod? = null,
    val destination: SelectionDestination = SelectionDestination.Modes,
) {
    val isOpen: Boolean get() = destination != SelectionDestination.Closed
    val browsingMode: MeasurementMode? get() = (destination as? SelectionDestination.Methods)?.mode

    fun openModes() = copy(destination = SelectionDestination.Modes)
    fun dismiss() = copy(destination = SelectionDestination.Closed)
    fun back() = if (destination is SelectionDestination.Methods) openModes() else dismiss()
    fun browse(mode: MeasurementMode) = if (destination == SelectionDestination.Modes && mode.available) {
        copy(destination = SelectionDestination.Methods(mode))
    } else this
    fun activate(method: MeasurementMethod) = if (browsingMode == method.mode) {
        copy(activeMethod = method, destination = SelectionDestination.Closed)
    } else this
}
