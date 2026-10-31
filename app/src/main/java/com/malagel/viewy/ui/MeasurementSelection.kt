package com.malagel.viewy.ui

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.malagel.viewy.R
import com.malagel.viewy.domain.MeasurementMethod
import com.malagel.viewy.domain.MeasurementMode
import com.malagel.viewy.measurement.SelectionDestination

@StringRes
private fun MeasurementMode.title(): Int = when (this) {
    MeasurementMode.LONG_DISTANCE -> R.string.long_distance
    MeasurementMode.SHORT_DISTANCE -> R.string.short_distance
    MeasurementMode.HEIGHT -> R.string.height_mode
    MeasurementMode.CALIBRATION -> R.string.calibration_mode
}

@StringRes
private fun MeasurementMode.description(): Int = when (this) {
    MeasurementMode.LONG_DISTANCE -> R.string.long_distance_description
    MeasurementMode.SHORT_DISTANCE -> R.string.short_distance_description
    MeasurementMode.HEIGHT -> R.string.height_description
    MeasurementMode.CALIBRATION -> R.string.calibration_description
}

@Composable
internal fun SelectionLists(
    destination: SelectionDestination,
    enabled: Boolean,
    modifier: Modifier,
    onBack: () -> Unit,
    onMode: (MeasurementMode) -> Unit,
    onSelect: (MeasurementMethod) -> Unit,
) {
    val transition = updateTransition(destination, label = "Card destination")
    val moving = transition.currentState != transition.targetState || transition.isRunning
    // Navigation belongs to the destination host. Only consume repeated Back while
    // the card itself is moving, including its return to the initial mode list.
    BackHandler(enabled = enabled && moving) {}
    transition.AnimatedContent(
        modifier = modifier,
        transitionSpec = {
            val forward = targetState is SelectionDestination.Methods
            slideInHorizontally(tween(220)) { if (forward) it else -it } togetherWith
                slideOutHorizontally(tween(220)) { if (forward) -it else it }
        },
    ) { level ->
        val interactive = enabled && level == destination && !moving
        NavigationInputGuard(!interactive, Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp)) {
                if (level is SelectionDestination.Methods) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        NavigationBackButton(onClick = onBack, label = stringResource(R.string.back_to_modes), enabled = interactive,
                            dimWhenDisabled = false)
                        Text(stringResource(R.string.select_method), Modifier.weight(1f).padding(horizontal = 8.dp).semantics { heading() },
                            style = MaterialTheme.typography.headlineSmall)
                    }
                    level.mode.methods.forEach { method ->
                        when (method) {
                            MeasurementMethod.KNOWN_OBJECT_HEIGHT -> SelectionEntry(
                                title = stringResource(R.string.known_height_method),
                                description = stringResource(R.string.known_height_description),
                                enabled = interactive, onClick = { onSelect(method) },
                            )
                        }
                    }
                } else {
                    Text(stringResource(R.string.select_mode), Modifier.padding(8.dp).semantics { heading() }, style = MaterialTheme.typography.headlineSmall)
                    MeasurementMode.entries.forEach { mode ->
                        SelectionEntry(
                            title = stringResource(mode.title()), description = stringResource(mode.description()),
                            enabled = interactive && mode.available,
                            available = mode.available,
                            supporting = if (!mode.available) stringResource(R.string.coming_soon) else null,
                            onClick = { onMode(mode) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SelectionEntry(
    title: String, description: String, enabled: Boolean, available: Boolean = true,
    supporting: String? = null, onClick: () -> Unit,
) {
    Surface(
        onClick = onClick, enabled = enabled, shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        // Temporary navigation locks preserve appearance; only unavailable catalog entries dim.
        contentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = if (available) 1f else 0.38f),
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                if (supporting == null) Text("›", Modifier.padding(start = 8.dp).clearAndSetSemantics {})
            }
            Text(description, style = MaterialTheme.typography.bodyMedium)
            supporting?.let { Text(it, style = MaterialTheme.typography.labelMedium) }
        }
    }
}
