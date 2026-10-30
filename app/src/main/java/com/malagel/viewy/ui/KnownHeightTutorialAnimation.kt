package com.malagel.viewy.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlin.math.atan2
import kotlin.math.min

/**
 * Known height -> top observation -> bottom observation -> horizontal distance.
 *
 * Supply [contentDescription] with stringResource from the host application's resources.
 * It describes the entire sequence once; there are no changing/live announcements.
 * The canvas has no background. Its default aspect ratio is 2:1; constrain the width
 * to 300-400 dp for a 150-200 dp illustration. All geometry scales uniformly.
 *
 * [animate] can opt out of motion in addition to the system animator duration scale.
 * When motion is off, the final distance diagram and completion check remain visible.
 * [previewTimeMillis] freezes the actual renderer at a time in 0..7000, for previews
 * and screenshot review only. Leave null in production. It starts no animation job.
 *
 * A new composition starts at the beginning. Backgrounding stops the clock; returning
 * to STARTED restarts it. No permissions, camera, sensors, or measurement state are used.
 */
@Composable
fun KnownHeightTutorialAnimation(
    contentDescription: String,
    modifier: Modifier = Modifier,
    animate: Boolean = true,
    previewTimeMillis: Int? = null,
) {
    val colors = MaterialTheme.colorScheme
    val stationary = colors.onSurfaceVariant
    val accent = colors.primary
    val heightAccent = lerp(accent, Color.Black, 0.18f)
    val phoneFill = colors.surface
    val objectFill = colors.secondaryContainer
    val lifecycleOwner = LocalLifecycleOwner.current

    // Android's Compose recomposer supplies and observes the system duration scale.
    // Reading it here switches to a static result at zero, including live changes.
    // Animatable itself applies nonzero scales; do not multiply the duration again.
    val durationScale = rememberCoroutineScope().coroutineContext[MotionDurationScale]
    val motionEnabled = animate && (durationScale?.scaleFactor ?: 1f) > 0f
    val clock = remember { Animatable(0f) }
    LaunchedEffect(motionEnabled, previewTimeMillis, lifecycleOwner) {
        if (motionEnabled && previewTimeMillis == null) {
            lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                clock.snapTo(0f)
                clock.animateTo(
                    targetValue = KnownHeightScene.CycleMillis.toFloat(),
                    animationSpec = infiniteRepeatable(
                        animation = tween(KnownHeightScene.CycleMillis, easing = LinearEasing),
                        repeatMode = RepeatMode.Restart,
                    ),
                )
            }
        }
    }

    // Paths/styles are created once, not during each frame.
    val person = remember {
        Path().apply {
            moveTo(54f, 112f)
            lineTo(44f, 153f)
            moveTo(54f, 112f)
            lineTo(64f, 153f)
            moveTo(54f, 74f)
            lineTo(64f, 90f)
            lineTo(78f, 86f)
            moveTo(54f, 78f)
            lineTo(61f, 97f)
            lineTo(75f, 93f)
        }
    }
    val completionTick = remember {
        Path().apply {
            moveTo(192.5f, 100f)
            lineTo(197.5f, 105f)
            lineTo(207.5f, 95f)
        }
    }
    val completionStroke = remember { Stroke(1.8f, cap = StrokeCap.Round, join = StrokeJoin.Round) }
    val figureStroke = remember { Stroke(2.4f, cap = StrokeCap.Round, join = StrokeJoin.Round) }
    val objectStroke = remember { Stroke(2.2f) }
    val phoneStroke = remember { Stroke(2f) }

    Canvas(
        modifier = modifier.fillMaxWidth().aspectRatio(2f).semantics {
            this.contentDescription = contentDescription
            role = Role.Image
        },
    ) {
        // Read the animated value only in the draw phase, avoiding per-frame recomposition.
        val time = previewTimeMillis?.coerceIn(0, KnownHeightScene.CycleMillis)?.toFloat()
            ?: if (motionEnabled) clock.value else KnownHeightScene.StaticMillis
        val scale = min(size.width / KnownHeightScene.Width, size.height / KnownHeightScene.Height)
        if (scale <= 0f) return@Canvas
        withTransform({
            translate(
                (size.width - KnownHeightScene.Width * scale) / 2f,
                (size.height - KnownHeightScene.Height * scale) / 2f,
            )
            scale(scale, scale, pivot = Offset.Zero)
        }) {
            // Slightly heavier torso, with the limbs retaining their lighter stroke.
            drawLine(stationary, Offset(54f, 70f), Offset(54f, 112f), 3.2f, StrokeCap.Round)
            drawPath(person, stationary, style = figureStroke)
            drawCircle(stationary, 11f, Offset(54f, 59f))
            drawRoundRect(
                color = objectFill,
                topLeft = KnownHeightScene.ObjectOrigin,
                size = KnownHeightScene.ObjectSize,
                cornerRadius = CornerRadius(4f),
            )
            drawRoundRect(
                color = stationary,
                topLeft = KnownHeightScene.ObjectOrigin,
                size = KnownHeightScene.ObjectSize,
                cornerRadius = CornerRadius(4f),
                style = objectStroke,
            )

            drawDimension(
                time, KnownHeightScene.KnownHeight,
                KnownHeightScene.HeightBottom, KnownHeightScene.HeightTop,
                heightAccent, width = 1.4f, tick = Offset(4f, 0f),
            )
            drawObservation(time, KnownHeightScene.TopObservation, KnownHeightScene.Top, accent)
            drawObservation(time, KnownHeightScene.BottomObservation, KnownHeightScene.Bottom, accent)
            drawDimension(
                time, KnownHeightScene.Distance,
                KnownHeightScene.DistanceStart, KnownHeightScene.DistanceEnd,
                accent, width = 1.8f, tick = Offset(0f, 4.5f),
            )

            // A calm completion cue after the distance has finished drawing.
            // It confirms completion only, not measurement accuracy.
            val completionAlpha = smoothInterval(time, 5400f, 5650f) *
                (1f - smoothInterval(time, 6300f, 6550f))
            if (completionAlpha > 0f) {
                drawCircle(
                    accent, radius = 16f, center = Offset(200f, 100f),
                    alpha = completionAlpha, style = completionStroke,
                )
                drawPath(completionTick, accent, alpha = completionAlpha, style = completionStroke)
            }

            // Phone's rear edge and lens face right at zero degrees. Rotate ONLY
            // the body around the lens. The head, arms, object and lens never move.
            val down = smoothInterval(time, 3000f, 3350f)
            val reset = smoothInterval(time, 6600f, 7000f)
            val angle = KnownHeightScene.TopAngle +
                (KnownHeightScene.BottomAngle - KnownHeightScene.TopAngle) * down * (1f - reset)
            rotate(angle, pivot = KnownHeightScene.Camera) {
                drawRoundRect(
                    phoneFill, KnownHeightScene.PhoneOrigin, KnownHeightScene.PhoneSize,
                    CornerRadius(2.4f),
                )
                drawRoundRect(
                    stationary, KnownHeightScene.PhoneOrigin, KnownHeightScene.PhoneSize,
                    CornerRadius(2.4f), style = phoneStroke,
                )
            }
            // Draw after the phone so the line's exact fixed origin stays legible.
            drawCircle(stationary, radius = 1.8f, center = KnownHeightScene.Camera)
        }
    }
}

/** All coordinates are logical view-box units, never physical pixels or screen dp. */
private object KnownHeightScene {
    const val Width = 400f
    const val Height = 200f
    const val CycleMillis = 7000
    const val StaticMillis = 5800f
    val Camera = Offset(84f, 75f)
    val Top = Offset(312f, 57f)
    val Bottom = Offset(Top.x, 147f)
    val ObjectOrigin = Top
    val ObjectSize = Size(28f, Bottom.y - Top.y)
    val PhoneOrigin = Camera + Offset(-8f, -5f)
    val PhoneSize = Size(8f, 27f)
    val HeightBottom = Offset(354f, Bottom.y)
    val HeightTop = Offset(354f, Top.y)
    // This is a detached dimension, not a ground plane. Its x endpoints are the
    // camera and the vertical line through the two observed left corners.
    val DistanceStart = Offset(Camera.x, 174f)
    val DistanceEnd = Offset(Top.x, DistanceStart.y)
    val TopAngle = angleTo(Top)
    val BottomAngle = angleTo(Bottom)

    // start, fully drawn, end of hold, fully faded; all share one 7 s clock.
    val KnownHeight = DrawingCue(300f, 1050f, 6300f, 6550f)
    val TopObservation = DrawingCue(1800f, 2350f, 2700f, 2950f)
    val BottomObservation = DrawingCue(3450f, 4000f, 4350f, 4600f)
    val Distance = DrawingCue(4750f, 5300f, 6300f, 6550f)

    private fun angleTo(endpoint: Offset): Float =
        (atan2((endpoint.y - Camera.y).toDouble(), (endpoint.x - Camera.x).toDouble()) *
            180.0 / Math.PI).toFloat()
}

private class DrawingCue(
    val start: Float,
    val drawn: Float,
    val fadeStart: Float,
    val end: Float,
) {
    fun progress(time: Float): Float = smoothInterval(time, start, drawn)
    fun opacity(time: Float): Float =
        if (time <= start || time >= end) 0f else 1f - smoothInterval(time, fadeStart, end)
}

// Smoothstep has zero velocity at both ends, with no overshoot or bouncing.
private fun smoothInterval(time: Float, start: Float, end: Float): Float {
    val t = ((time - start) / (end - start)).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

private fun DrawScope.drawObservation(time: Float, cue: DrawingCue, endpoint: Offset, color: Color) {
    val alpha = cue.opacity(time)
    if (alpha <= 0f) return
    val progress = cue.progress(time)
    val camera = KnownHeightScene.Camera
    drawLine(color, camera, camera + (endpoint - camera) * progress, 1.4f, StrokeCap.Round, alpha = alpha)
    // A small target dot settles in only as the ray arrives; no pulse or flash.
    val arrival = smoothInterval(progress, 0.86f, 1f)
    if (arrival > 0f) drawCircle(color, 2.5f, endpoint, alpha = alpha * arrival)
}

private fun DrawScope.drawDimension(
    time: Float,
    cue: DrawingCue,
    start: Offset,
    end: Offset,
    color: Color,
    width: Float,
    tick: Offset,
) {
    val alpha = cue.opacity(time)
    if (alpha <= 0f) return
    val progress = cue.progress(time)
    drawLine(color, start, start + (end - start) * progress, width, StrokeCap.Round, alpha = alpha)
    val startAlpha = alpha * smoothInterval(progress, 0f, 0.12f)
    val endAlpha = alpha * smoothInterval(progress, 0.86f, 1f)
    drawLine(color, start - tick, start + tick, width, StrokeCap.Round, alpha = startAlpha)
    drawLine(color, end - tick, end + tick, width, StrokeCap.Round, alpha = endAlpha)
}

// Review helpers: these render the SAME Canvas as production. Preview-only English
// does not enter the component's runtime semantics. Integration uses localized strings.
private const val PreviewDescription =
    "Use the known object height. Keep the camera in one position, aim at the top " +
        "and capture, then rotate down and capture the bottom. Viewy estimates the " +
        "horizontal distance to the vertical line through those corners, not the sloping distance."

internal class KnownHeightFrameProvider : PreviewParameterProvider<Int> {
    override val values = sequenceOf(1300, 2550, 3200, 4200, 5800, 6800)
}

@Preview(name = "Frames - 320 x 160", widthDp = 320, heightDp = 160)
@Preview(name = "Frames - 400 x 200", widthDp = 400, heightDp = 200)
@Composable
private fun KnownHeightFramesPreview(@PreviewParameter(KnownHeightFrameProvider::class) time: Int) {
    MaterialTheme(colorScheme = darkColorScheme()) {
        Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh) {
            KnownHeightTutorialAnimation(PreviewDescription, previewTimeMillis = time)
        }
    }
}

@Preview(name = "Narrow - reduced motion", widthDp = 240, heightDp = 120)
@Composable
private fun KnownHeightReducedMotionPreview() {
    MaterialTheme(colorScheme = darkColorScheme()) {
        Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh) {
            KnownHeightTutorialAnimation(PreviewDescription, animate = false)
        }
    }
}

@Preview(name = "Live - use Interactive Preview", widthDp = 360, heightDp = 180)
@Composable
private fun KnownHeightLivePreview() {
    MaterialTheme(colorScheme = darkColorScheme()) {
        Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh) {
            KnownHeightTutorialAnimation(PreviewDescription)
        }
    }
}
