package com.malagel.viewy.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.malagel.viewy.R
import com.malagel.viewy.settings.*

/** Keep the camera mounted while covering its controls, focus and accessibility tree. */
@Composable
internal fun SettingsOverlay(visible: Boolean, settings: @Composable () -> Unit, content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Box(Modifier.fillMaxSize().focusProperties { canFocus = !visible }
            .then(if (visible) Modifier.clearAndSetSemantics {} else Modifier),
            contentAlignment = Alignment.TopCenter,
        ) { content() }
        if (visible) settings()
    }
}

/** Render in the host window so recreation cannot expose a frame behind a new dialog. */
@Composable
fun SettingsScreen(state: SettingsUiState, model: SettingsViewModel, backdropOnly: Boolean = false) {
    if (state.destination == SettingsDestination.CLOSED) return
    BackHandler(enabled = !backdropOnly) { if (!state.resetLocked) model.back() }
    val paneLabel = stringResource(R.string.settings)
    // The host already owns system insets. Consume the hit-test layer even on blank
    // areas, so touches cannot reach the still-composed measurement controls.
    // Behind an exceptional workspace dialog, draw the same surface as a visual
    // backdrop without another Back handler, accessibility pane or reset dialog.
    val presentation = if (backdropOnly) Modifier.focusProperties { canFocus = false }.clearAndSetSemantics {}
        else Modifier.semantics { paneTitle = paneLabel }
    Box(Modifier.fillMaxSize().then(presentation)) {
        Surface(Modifier.fillMaxSize().pointerInput(Unit) {}) {
            Column(Modifier.fillMaxSize()) {
                Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = model::back, enabled = !state.resetLocked) { Text(stringResource(R.string.back)) }
                    Text(stringResource(when (state.destination) {
                        SettingsDestination.DEVELOPER -> R.string.developer_options
                        SettingsDestination.LANGUAGE -> R.string.language
                        else -> R.string.settings
                    }), Modifier.weight(1f).padding(horizontal = 8.dp), style = MaterialTheme.typography.headlineSmall)
                }
                val scroll = remember(state.destination) { ScrollState(0) }
                Column(Modifier.widthIn(max = 600.dp).fillMaxWidth().align(Alignment.CenterHorizontally)
                    .weight(1f).verticalScroll(scroll)
                    .then(if (state.destination == SettingsDestination.LANGUAGE) Modifier.selectableGroup() else Modifier)) {
                    when (state.destination) {
                        SettingsDestination.MAIN -> {
                            SettingsRow(stringResource(R.string.measurement_units), state.preferences?.let {
                                stringResource(if (it.units == MeasurementUnits.METRIC) R.string.metric else R.string.imperial)
                            } ?: stringResource(R.string.settings_loading), state.canEdit, model::switchUnits)
                            SettingsRow(stringResource(R.string.language), languageName(AppLanguages.current()), !state.resetLocked, model::languages)
                            SettingsRow(stringResource(R.string.developer_options), onClick = model::developer, enabled = !state.resetLocked)
                        }
                        SettingsDestination.LANGUAGE -> {
                            // A new override can have the same effective language as the device,
                            // so refresh the Android-owned choice even without recreation.
                            var requests by remember { mutableIntStateOf(0) }
                            val current = remember(LocalConfiguration.current, requests) { AppLanguages.current() }
                            AppLanguage.entries.forEach { language ->
                                ListItem(
                                    headlineContent = { Text(languageName(language)) },
                                    leadingContent = { RadioButton(selected = current == language, onClick = null) },
                                    modifier = Modifier.selectable(selected = current == language, role = Role.RadioButton,
                                        enabled = !state.resetLocked, onClick = {
                                            model.selectLanguage(language)
                                            requests++
                                        }),
                                )
                            }
                        }
                        SettingsDestination.DEVELOPER -> {
                            val enabled = state.preferences?.diagnosticsEnabled == true
                            ListItem(
                                headlineContent = { Text(stringResource(R.string.enable_diagnostics)) },
                                trailingContent = { Switch(checked = enabled, onCheckedChange = null, enabled = state.canEdit) },
                                modifier = Modifier.toggleable(enabled = state.canEdit, value = enabled, role = Role.Switch, onValueChange = model::setDiagnostics),
                            )
                            ListItem(
                                headlineContent = { Text(stringResource(R.string.erase_app_data), color = MaterialTheme.colorScheme.error) },
                                modifier = Modifier.clickable(enabled = !state.writing && !state.resetLocked, onClick = model::askReset),
                            )
                        }
                        SettingsDestination.CLOSED -> Unit
                    }
                    if (state.storageError) {
                        Text(stringResource(R.string.settings_storage_error), Modifier.padding(16.dp), color = MaterialTheme.colorScheme.error)
                        TextButton(onClick = model::retryLoading, enabled = !state.resetLocked) { Text(stringResource(R.string.retry)) }
                    }
                    if (state.resetLocked) Text(stringResource(R.string.reset_requested), Modifier.padding(16.dp))
                }
            }
        }
        if (!backdropOnly && state.reset == ResetStatus.CONFIRMING) AlertDialog(
            onDismissRequest = model::cancelReset,
            title = { Text(stringResource(R.string.erase_app_data)) },
            text = { Text(stringResource(R.string.erase_confirmation)) },
            dismissButton = { TextButton(onClick = model::cancelReset) { Text(stringResource(R.string.cancel)) } },
            confirmButton = {
                TextButton(onClick = model::confirmReset, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                    Text(stringResource(R.string.erase_all_data))
                }
            },
        )
        if (!backdropOnly && state.reset == ResetStatus.FAILED) AlertDialog(
            onDismissRequest = model::dismissResetError,
            title = { Text(stringResource(R.string.reset_failed_title)) },
            text = { Text(stringResource(R.string.reset_failed)) },
            confirmButton = { TextButton(onClick = model::dismissResetError) { Text(stringResource(R.string.close)) } },
        )
    }
}

@Composable
private fun SettingsRow(title: String, value: String? = null, enabled: Boolean = true, onClick: () -> Unit) {
    ListItem(headlineContent = { Text(title) }, supportingContent = value?.let { { Text(it) } },
        modifier = Modifier.clickable(enabled = enabled, onClick = onClick))
}

@Composable
private fun languageName(language: AppLanguage): String = when (language) {
    AppLanguage.DEVICE -> stringResource(R.string.language_device)
    AppLanguage.ENGLISH -> stringResource(R.string.language_english)
    AppLanguage.SPANISH -> stringResource(R.string.language_spanish)
}
