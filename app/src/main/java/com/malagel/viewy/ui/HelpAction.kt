package com.malagel.viewy.ui

/** Gesture routing has no hardware/workflow access. The persisted Settings snapshot gates access. */
internal enum class HelpAction { NONE, TUTORIAL, DIAGNOSTICS }

internal fun helpAction(state: MeasurementUiState, longPress: Boolean, diagnosticsEnabled: Boolean): HelpAction = when {
    !state.canOpenHelp || state.diagnosticsOpen -> HelpAction.NONE
    !longPress -> HelpAction.TUTORIAL
    diagnosticsEnabled -> HelpAction.DIAGNOSTICS
    else -> HelpAction.NONE
}

/** Retained in the measurement ViewModel, never persisted or applied to the workflow. */
internal fun MeasurementUiState.withDiagnostics(open: Boolean, enabled: Boolean = false): MeasurementUiState = when {
    !open -> if (diagnosticsOpen) copy(diagnosticsOpen = false) else this
    helpAction(this, longPress = true, diagnosticsEnabled = enabled) == HelpAction.DIAGNOSTICS -> copy(diagnosticsOpen = true)
    else -> this
}
