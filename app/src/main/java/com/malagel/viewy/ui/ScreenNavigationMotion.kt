package com.malagel.viewy.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Transition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import com.malagel.viewy.settings.SettingsDestination

internal enum class ScreenOverlay { NONE, SETTINGS, DIAGNOSTICS }

internal fun screenOverlay(settingsOpen: Boolean, diagnosticsOpen: Boolean): ScreenOverlay = when {
    settingsOpen -> ScreenOverlay.SETTINGS
    diagnosticsOpen -> ScreenOverlay.DIAGNOSTICS
    else -> ScreenOverlay.NONE
}

internal data class OverlayPresentation(
    val displayed: ScreenOverlay,
    val blocksMeasurement: Boolean,
    val interactive: Boolean,
)

/** Retain an exiting destination until Compose finishes it; this never changes navigation state. */
internal fun overlayPresentation(
    current: ScreenOverlay, target: ScreenOverlay, running: Boolean, retained: ScreenOverlay = current,
): OverlayPresentation {
    val blocking = current != ScreenOverlay.NONE || target != ScreenOverlay.NONE || running
    return OverlayPresentation(
        displayed = when {
            !blocking -> ScreenOverlay.NONE
            target != ScreenOverlay.NONE -> target
            current != ScreenOverlay.NONE -> current
            else -> retained // An entrance can reverse before currentState ever becomes visible.
        },
        blocksMeasurement = blocking,
        interactive = target != ScreenOverlay.NONE && current == target && !running,
    )
}

internal fun displayedSettingsPage(requested: SettingsDestination, retained: SettingsDestination): SettingsDestination =
    requested.takeUnless { it == SettingsDestination.CLOSED }
        ?: retained.takeUnless { it == SettingsDestination.CLOSED } ?: SettingsDestination.MAIN

internal enum class SettingsMotion { FORWARD, BACKWARD, NONE }

internal fun settingsMotion(from: SettingsDestination, to: SettingsDestination): SettingsMotion = when {
    from == to || from == SettingsDestination.CLOSED || to == SettingsDestination.CLOSED -> SettingsMotion.NONE
    from == SettingsDestination.MAIN -> SettingsMotion.FORWARD
    to == SettingsDestination.MAIN -> SettingsMotion.BACKWARD
    else -> SettingsMotion.NONE
}

/** Shared timing for destination changes only; sensor and preference updates are not motion triggers. */
internal object ScreenMotion {
    const val enterMillis = 240
    const val exitMillis = 180
    const val pageMillis = 240
    const val fadeMillis = 150
}

/** Block pointer, focus and accessibility access to outgoing/covered controls, including blank areas. */
@Composable
internal fun NavigationInputGuard(blocked: Boolean, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(modifier, contentAlignment = Alignment.TopCenter) {
        Box(Modifier.fillMaxSize().focusProperties { canFocus = !blocked }
            .then(if (blocked) Modifier.clearAndSetSemantics {} else Modifier),
            contentAlignment = Alignment.TopCenter,
        ) { content() }
        if (blocked) Box(Modifier.fillMaxSize().pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
            }
        })
    }
}

/** Animate only the covering surface: the measurement/camera stays mounted at identical bounds. */
@Composable
internal fun ScreenOverlayHost(
    transition: Transition<ScreenOverlay>, presentation: OverlayPresentation, backdropOnly: Boolean,
    overlay: @Composable (ScreenOverlay, interactive: Boolean) -> Unit,
    content: @Composable () -> Unit,
) {
    // Suppress repeated Back during entry/exit instead of falling through to the
    // workspace dialog or Activity after the destination has already closed.
    BackHandler(enabled = !backdropOnly && presentation.blocksMeasurement && !presentation.interactive) {}
    val offset = with(LocalDensity.current) { 24.dp.roundToPx() }
    Box(Modifier.fillMaxSize().clipToBounds(), contentAlignment = Alignment.TopCenter) {
        NavigationInputGuard(presentation.blocksMeasurement, Modifier.fillMaxSize(), content)
        transition.AnimatedVisibility(
            visible = { it != ScreenOverlay.NONE },
            enter = fadeIn(tween(ScreenMotion.enterMillis)) + slideInVertically(
                tween(ScreenMotion.enterMillis, easing = FastOutSlowInEasing), initialOffsetY = { offset }),
            exit = fadeOut(tween(ScreenMotion.exitMillis)) + slideOutVertically(
                tween(ScreenMotion.exitMillis, easing = FastOutSlowInEasing), targetOffsetY = { offset }),
        ) {
            NavigationInputGuard(!presentation.interactive || backdropOnly, Modifier.fillMaxSize()) {
                overlay(presentation.displayed, presentation.interactive && !backdropOnly)
            }
        }
    }
}
