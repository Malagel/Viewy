package com.malagel.viewy.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.malagel.viewy.R
import com.malagel.viewy.settings.*

/** Render in the host window so recreation cannot expose a frame behind a new dialog. */
@Composable
fun SettingsScreen(state: SettingsUiState, model: SettingsViewModel, backdropOnly: Boolean = false, interactive: Boolean = true) {
    // CLOSED starts the host's exit animation. Keep the last page mounted until
    // that animation disposes this screen rather than revealing an empty frame.
    var lastPage by remember { mutableStateOf(state.destination.takeUnless { it == SettingsDestination.CLOSED } ?: SettingsDestination.MAIN) }
    val destination = displayedSettingsPage(state.destination, lastPage)
    SideEffect { if (state.destination != SettingsDestination.CLOSED) lastPage = state.destination }
    val pages = updateTransition(destination, label = "Settings destination")
    val settled = pages.currentState == pages.targetState && !pages.isRunning
    BackHandler(enabled = !backdropOnly && interactive) { if (settled && !state.resetLocked) model.back() }
    val paneLabel = stringResource(R.string.settings)
    // The host already owns system insets. Consume the hit-test layer even on blank
    // areas, so touches cannot reach the still-composed measurement controls.
    // Behind an exceptional workspace dialog, draw the same surface as a visual
    // backdrop without another Back handler, accessibility pane or reset dialog.
    val horizontalOffset = with(LocalDensity.current) { 32.dp.roundToPx() } *
        if (LocalLayoutDirection.current == LayoutDirection.Ltr) 1 else -1
    NavigationInputGuard(!interactive || backdropOnly, Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().semantics { paneTitle = paneLabel }) {
            Surface(Modifier.fillMaxSize().pointerInput(Unit) {}) {
                pages.AnimatedContent(
                    modifier = Modifier.fillMaxSize().clipToBounds(),
                    transitionSpec = {
                        val offset = when (settingsMotion(initialState, targetState)) {
                            SettingsMotion.FORWARD -> horizontalOffset
                            SettingsMotion.BACKWARD -> -horizontalOffset
                            SettingsMotion.NONE -> 0
                        }
                        ((fadeIn(tween(ScreenMotion.fadeMillis)) + slideInHorizontally(
                            tween(ScreenMotion.pageMillis, easing = FastOutSlowInEasing), initialOffsetX = { offset })) togetherWith
                            (fadeOut(tween(ScreenMotion.fadeMillis)) + slideOutHorizontally(
                                tween(ScreenMotion.pageMillis, easing = FastOutSlowInEasing), targetOffsetX = { -offset }))).using(null)
                    },
                ) { page ->
                    NavigationInputGuard(!settled || page != destination, Modifier.fillMaxSize()) {
                        SettingsPage(state, model, page, interactive = interactive && settled && page == destination)
                    }
                }
            }
            if (!backdropOnly && interactive && state.reset == ResetStatus.CONFIRMING) AlertDialog(
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
            if (!backdropOnly && interactive && state.reset == ResetStatus.FAILED) AlertDialog(
                onDismissRequest = model::dismissResetError,
                title = { Text(stringResource(R.string.reset_failed_title)) },
                text = { Text(stringResource(R.string.reset_failed)) },
                confirmButton = { TextButton(onClick = model::dismissResetError) { Text(stringResource(R.string.close)) } },
            )
        }
    }
}

/** Each AnimatedContent entry renders its own page, including its own scroll state. */
@Composable
private fun SettingsPage(state: SettingsUiState, model: SettingsViewModel, page: SettingsDestination, interactive: Boolean) {
    // Observing configuration refreshes both the summary and radio choice in place.
    val configuredTags = LocalConfiguration.current.locales.toLanguageTags()
    val scroll = remember(page) { ScrollState(0) }
    AdaptivePageColumn(Modifier.fillMaxSize(), bodyScroll = scroll, header = {
        ScreenHeader(onBack = { if (interactive) model.back() }, enabled = !state.resetLocked,
            title = stringResource(when (page) {
                SettingsDestination.DEVELOPER -> R.string.developer_options
                SettingsDestination.LANGUAGE -> R.string.language
                else -> R.string.settings
            }))
    }) { scrollModifier ->
        Column(Modifier.widthIn(max = 600.dp).fillMaxWidth()
            .then(scrollModifier)
            .then(if (page == SettingsDestination.LANGUAGE) Modifier.selectableGroup() else Modifier)) {
            when (page) {
                SettingsDestination.MAIN -> {
                    SettingsRow(stringResource(R.string.measurement_units), state.preferences?.let {
                        stringResource(if (it.units == MeasurementUnits.METRIC) R.string.metric else R.string.imperial)
                    } ?: stringResource(R.string.settings_loading), state.canEdit) { if (interactive) model.switchUnits() }
                    SettingsRow(stringResource(R.string.language), languageName(AppLanguages.current(configuredTags)), !state.resetLocked) { if (interactive) model.languages() }
                    SettingsRow(stringResource(R.string.developer_options), enabled = !state.resetLocked) { if (interactive) model.developer() }
                }
                SettingsDestination.LANGUAGE -> {
                    val current = AppLanguages.current(configuredTags)
                    AppLanguage.entries.forEach { language ->
                        ListItem(
                            headlineContent = { Text(languageName(language)) },
                            leadingContent = { RadioButton(selected = current == language, onClick = null) },
                            modifier = Modifier.selectable(selected = current == language, role = Role.RadioButton,
                                enabled = !state.resetLocked, onClick = {
                                    if (interactive) {
                                        model.selectLanguage(language)
                                    }
                                }),
                        )
                    }
                }
                SettingsDestination.DEVELOPER -> {
                    val enabled = state.preferences?.diagnosticsEnabled == true
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.enable_diagnostics)) },
                        trailingContent = { Switch(checked = enabled, onCheckedChange = null, enabled = state.canEdit) },
                        modifier = Modifier.toggleable(enabled = state.canEdit, value = enabled, role = Role.Switch, onValueChange = { if (interactive) model.setDiagnostics(it) }),
                    )
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.erase_app_data), color = MaterialTheme.colorScheme.error) },
                        modifier = Modifier.clickable(enabled = !state.writing && !state.resetLocked, onClick = { if (interactive) model.askReset() }),
                    )
                }
                SettingsDestination.CLOSED -> Unit
            }
            if (state.storageError) {
                Text(stringResource(R.string.settings_storage_error), Modifier.padding(16.dp), color = MaterialTheme.colorScheme.error)
                TextButton(onClick = { if (interactive) model.retryLoading() }, enabled = !state.resetLocked) { Text(stringResource(R.string.retry)) }
            }
            if (state.resetLocked) Text(stringResource(R.string.reset_requested), Modifier.padding(16.dp))
        }
    }
}

@Composable
private fun SettingsRow(title: String, value: String? = null, enabled: Boolean = true, onClick: () -> Unit) {
    ListItem(headlineContent = { Text(title) }, supportingContent = value?.let { { Text(it) } },
        modifier = Modifier.clickable(enabled = enabled, onClick = onClick))
}

@Composable
private fun languageName(language: AppLanguage): String = when (language) {
    AppLanguage.ENGLISH -> stringResource(R.string.language_english)
    AppLanguage.SPANISH -> stringResource(R.string.language_spanish)
}
