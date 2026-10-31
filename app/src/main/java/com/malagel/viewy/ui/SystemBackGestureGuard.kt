package com.malagel.viewy.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.systemGestures
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection

/** Physical window edges, independent of reading direction; zero insets reserve no app space. */
internal fun startsInSystemBackEdge(x: Float, width: Int, left: Int, right: Int): Boolean =
    x >= 0f && x < width && ((left > 0 && x < left) || (right > 0 && x >= width - right))

/** Apply to a full-window parent, before system-inset padding, so coordinates match Android's insets. */
@Composable
internal fun Modifier.reserveSystemBackGestures(enabled: Boolean): Modifier {
    val density = LocalDensity.current
    val direction = LocalLayoutDirection.current
    val left = WindowInsets.systemGestures.getLeft(density, direction)
    val right = WindowInsets.systemGestures.getRight(density, direction)
    return pointerInput(enabled, left, right) {
        if (enabled && (left > 0 || right > 0)) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                if (down.type == PointerType.Touch && startsInSystemBackEdge(down.position.x, size.width, left, right)) {
                    // Block movement before descendants can scroll, while retaining stationary taps.
                    // No exclusion is requested: Android still owns recognition/commit/cancellation.
                    var moved = false
                    do {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        moved = moved || event.changes.any { it.position != it.previousPosition }
                        if (moved) event.changes.forEach { it.consume() }
                    } while (event.changes.any { it.pressed })
                }
            }
        }
    }
}
