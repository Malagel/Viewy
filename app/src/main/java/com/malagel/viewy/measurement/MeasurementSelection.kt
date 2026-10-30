package com.malagel.viewy.measurement

import com.malagel.viewy.domain.MeasurementMethod
import com.malagel.viewy.domain.MeasurementMode

sealed interface SelectionDestination {
    data object Home : SelectionDestination
    data class Methods(val mode: MeasurementMode) : SelectionDestination
    data class Measurement(val method: MeasurementMethod) : SelectionDestination
}

/** In-memory destinations; workflow ownership survives returning Home separately. */
data class MeasurementSelection(val destination: SelectionDestination = SelectionDestination.Home) {
    val activeMethod: MeasurementMethod? get() = (destination as? SelectionDestination.Measurement)?.method
    val isOpen: Boolean get() = activeMethod == null
    val browsingMode: MeasurementMode? get() = (destination as? SelectionDestination.Methods)?.mode

    fun openModes() = copy(destination = SelectionDestination.Home)
    fun back() = when (val current = destination) {
        is SelectionDestination.Measurement -> copy(destination = SelectionDestination.Methods(current.method.mode))
        else -> openModes()
    }
    fun browse(mode: MeasurementMode) = if (destination == SelectionDestination.Home && mode.available) {
        copy(destination = SelectionDestination.Methods(mode))
    } else this
    fun activate(method: MeasurementMethod) = if (browsingMode == method.mode && method in method.mode.methods) {
        copy(destination = SelectionDestination.Measurement(method))
    } else this
}
