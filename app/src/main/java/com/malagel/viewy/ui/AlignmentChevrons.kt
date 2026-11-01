package com.malagel.viewy.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/** Decorative strokes around, and clear of, the centered 40 dp aiming reticle. */
@Composable
internal fun AlignmentChevrons(state: AlignmentCueState, modifier: Modifier = Modifier) {
    if (state.cue == AlignmentCue.HIDDEN) return // Disposal cancels even an unfinished success fade.
    val left = remember { Animatable(0f) }
    val right = remember { Animatable(0f) }
    LaunchedEffect(state.cue, state.recoveryId) {
        if (state.cue == AlignmentCue.RECOVERED) {
            left.snapTo(1f)
            right.snapTo(1f)
            coroutineScope {
                launch { left.animateTo(0f, tween(450, easing = LinearEasing)) }
                launch { right.animateTo(0f, tween(450, easing = LinearEasing)) }
            }
        } else {
            coroutineScope {
                launch { left.animateTo(if (state.cue == AlignmentCue.LEFT) 1f else 0f, tween(140)) }
                launch { right.animateTo(if (state.cue == AlignmentCue.RIGHT) 1f else 0f, tween(140)) }
            }
        }
    }
    val nudge = if (state.cue == AlignmentCue.LEFT || state.cue == AlignmentCue.RIGHT) {
        // Compose scales motion with system settings and suspends infinite motion at scale zero.
        val motion = rememberInfiniteTransition(label = "Alignment nudge")
        motion.animateFloat(0f, 1f,
            infiniteRepeatable(tween(700, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label = "Alignment offset")
    } else null
    Canvas(modifier.size(128.dp, 48.dp).clearAndSetSemantics {}) {
        // Movement invalidates drawing without recomposing the chevrons every frame.
        val offset = (nudge?.value ?: 0f) * 8.dp.toPx()
        for ((direction, opacity) in listOf(-1f to left.value, 1f to right.value)) {
            if (opacity <= 0f) continue
            val x = center.x + direction * (40.dp.toPx() + offset)
            val path = Path().apply {
                moveTo(x - direction * 4.5.dp.toPx(), center.y - 9.dp.toPx())
                lineTo(x + direction * 4.5.dp.toPx(), center.y)
                lineTo(x - direction * 4.5.dp.toPx(), center.y + 9.dp.toPx())
            }
            // A fine black outline keeps the white strokes visible against bright camera scenes.
            drawPath(path, Color.Black.copy(alpha = opacity), style = Stroke(3.50.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            drawPath(path, Color.White.copy(alpha = opacity), style = Stroke(2.00.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}
