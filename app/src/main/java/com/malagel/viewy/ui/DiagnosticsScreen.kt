package com.malagel.viewy.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import com.malagel.viewy.R
import com.malagel.viewy.domain.MeasurementMethod
import com.malagel.viewy.measurement.HeightUnit

/** Host-window overlay, like Settings: the measurement and its preview keep their bounds/owner. */
@Composable
internal fun DiagnosticsScreen(
    state: MeasurementUiState, unit: HeightUnit, cameraPermission: Boolean,
    onBack: () -> Unit, backdropOnly: Boolean = false, interactive: Boolean = true,
) {
    BackHandler(enabled = !backdropOnly && interactive, onBack = onBack)
    val title = stringResource(R.string.diag_title)
    val locale = LocalLocale.current.platformLocale
    val presentation = remember(state.selection.activeMethod, state.measurement, state.camera, state.acquisition, unit, locale, cameraPermission) {
        diagnosticsPresentation(state, unit, locale, cameraPermission)
    }
    val semantics = if (backdropOnly) Modifier.focusProperties { canFocus = false }.clearAndSetSemantics {}
        else Modifier.semantics { paneTitle = title }
    Surface(Modifier.fillMaxSize().then(semantics).pointerInput(Unit) {}) {
        // The host already applies system-bar/cutout insets. Everything, including
        // the Back action, scrolls so even the smallest viewfinder remains usable.
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally) {
            ScreenHeader(onBack = onBack, title = title)
            Column(Modifier.widthIn(max = 600.dp).fillMaxWidth().padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)) {
                state.selection.activeMethod?.let { method ->
                    Text(stringResource(when (method) {
                        MeasurementMethod.KNOWN_OBJECT_HEIGHT -> R.string.known_height_method
                    }), style = MaterialTheme.typography.titleMedium)
                }
                presentation.sections.forEach { section ->
                    Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = MaterialTheme.shapes.large) {
                        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(stringResource(section.title), Modifier.semantics { heading() }, style = MaterialTheme.typography.titleMedium)
                            section.rows.forEach { row ->
                                // Stacked label/value rows wrap identifiers and large text without clipping.
                                Column(Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}) {
                                    Text(stringResource(row.label), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(stringResource(row.value.resource, *row.value.arguments.toTypedArray()), style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                            section.explanation?.let { Text(stringResource(it), style = MaterialTheme.typography.bodySmall) }
                        }
                    }
                }
            }
        }
    }
}
