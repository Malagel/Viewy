package com.malagel.viewy.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.malagel.viewy.R
import com.malagel.viewy.domain.KnownHeightResult
import com.malagel.viewy.measurement.HeightUnit
import com.malagel.viewy.measurement.MeasurementState
import com.malagel.viewy.measurement.MeasurementStage

private data class ResultContent(val estimate: KnownHeightResult.Estimate, val heightMeters: Double, val origin: KnownHeightResult.Estimate)

/** Retains only result pixels/content on exit. Camera ownership remains in the single live layer. */
@Composable
internal fun MeasurementResultOverlay(
    measurement: MeasurementState,
    unit: HeightUnit,
    entryMemory: ResultEntryMemory,
    interactive: Boolean,
    editingHeight: Boolean,
    onEditHeight: () -> Unit,
    onSurfaceCovered: (KnownHeightResult.Estimate) -> Unit,
) {
    val result = measurement.result.takeIf { measurement.stage == MeasurementStage.RESULT }
    val current = result?.let { ResultContent(it, measurement.heightMeters!!, measurement.resultOrigin ?: it) }
    var retained by remember { mutableStateOf(current) }
    SideEffect { if (current != null) retained = current }
    AnimatedVisibility(
        visible = current != null,
        enter = fadeIn(tween(ResultReveal.entranceMillis)),
        exit = fadeOut(tween(225)),
    ) {
        val latestOnSurfaceCovered by rememberUpdatedState(onSurfaceCovered)
        LaunchedEffect(current?.let { ResultIdentity(it.estimate) }, transition.currentState, transition.targetState, transition.isRunning) {
            if (transition.currentState == EnterExitState.Visible &&
                transition.targetState == EnterExitState.Visible && !transition.isRunning) {
                current?.let { latestOnSurfaceCovered(it.estimate) }
            }
        }
        (current ?: retained)?.let { content ->
            key(ResultIdentity(content.estimate)) {
                MeasurementResultPanel(content, unit, entryMemory, current != null,
                    interactive && current != null, editingHeight, onEditHeight)
            }
        }
    }
}

@Composable
private fun MeasurementResultPanel(
    content: ResultContent, unit: HeightUnit, entryMemory: ResultEntryMemory,
    active: Boolean, interactive: Boolean, editingHeight: Boolean, onEditHeight: () -> Unit,
) {
    val animateEntry = remember { entryMemory.enter(content.origin) }
    val elapsed = remember { Animatable(if (animateEntry) 0f else ResultReveal.totalMillis.toFloat()) }
    // Unit/locale/publication changes never replace this effect. Exit cancels it immediately.
    LaunchedEffect(active, editingHeight) {
        if (editingHeight) elapsed.snapTo(ResultReveal.totalMillis.toFloat())
        else if (active && elapsed.value < ResultReveal.totalMillis) {
            elapsed.animateTo(ResultReveal.totalMillis.toFloat(), tween(ResultReveal.totalMillis, easing = LinearEasing))
        }
    }
    val locale = LocalLocale.current.platformLocale
    val formatter = remember(content.estimate, unit, locale) {
        ResultNumberFormatter(content.estimate.horizontalDistanceMeters, unit, locale)
    }
    val finalNumber = formatter.finalText
    val fraction = LinearOutSlowInEasing.transform(ResultReveal.countFraction(elapsed.value))
    val number = formatter.frameText(fraction)
    val description = stringResource(R.string.horizontal_result, "$finalNumber ${formatter.unitLabel}")
    val height = distanceNumberText(content.heightMeters, unit, locale)
    val editDescription = stringResource(R.string.height_edit, height, unit.label)
    val separation = measurementNumberText(Math.toDegrees(content.estimate.diagnostics.elevationSeparationRadians), locale)
    val alphaHeight = ResultReveal.supportingAlpha(elapsed.value, 0)
    val alphaSeparation = ResultReveal.supportingAlpha(elapsed.value, 1)
    val alphaDisclaimer = ResultReveal.supportingAlpha(elapsed.value, 2)
    val alphaDistance = if (ResultReveal.distanceVisible(elapsed.value)) 1f else 0f

    Surface(Modifier.fillMaxSize().focusProperties { canFocus = active }
        .then(if (!active) Modifier.clearAndSetSemantics {}.pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
            }
        } else Modifier),
        color = MaterialTheme.colorScheme.surfaceContainer) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val minimumHeight = maxHeight
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).heightIn(min = minimumHeight)
                    .padding(horizontal = 20.dp, vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Spacer(Modifier.height(0.dp))
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.result_horizontal_distance), Modifier.resultReveal(alphaDistance), style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                    // Only the final value enters semantics; count-up frames never create announcements.
                    Box(Modifier.fillMaxWidth().graphicsLayer { alpha = alphaDistance }.clearAndSetSemantics {
                        if (alphaDistance > 0f) {
                            contentDescription = description
                            liveRegion = LiveRegionMode.Polite
                        }
                    }) {
                        ResultDistance(number, formatter.layoutText, formatter.unitLabel)
                    }
                    TextButton(onClick = onEditHeight, enabled = interactive && alphaHeight > 0f,
                        colors = ButtonDefaults.textButtonColors(disabledContentColor = MaterialTheme.colorScheme.primary),
                        modifier = Modifier.resultReveal(alphaHeight).clearAndSetSemantics {
                            if (active && alphaHeight > 0f) {
                                contentDescription = editDescription
                                role = Role.Button
                                if (interactive) onClick { onEditHeight(); true } else disabled()
                            }
                        }) { Text(editDescription, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyLarge) }
                    Text(stringResource(R.string.result_elevation_difference, separation), Modifier.resultReveal(alphaSeparation),
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center)
                }
                Text(stringResource(R.string.result_disclaimer), Modifier.padding(top = 20.dp).resultReveal(alphaDisclaimer),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center)
            }
        }
    }
}

/** Measure the destination once: fixed number/unit positions and tabular digits throughout counting. */
@Composable
internal fun ResultDistance(number: String, layoutNumber: String, unit: String) {
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val base = MaterialTheme.typography.displayLarge.copy(fontWeight = FontWeight.Bold, fontFeatureSettings = "tnum")
    BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        val available = constraints.maxWidth
        val style = remember(base, layoutNumber, unit, available, density) {
            val size = readableTextSize(96, 28, 2) {
                val candidate = base.copy(fontSize = it.sp, lineHeight = (it * 1.15f).sp)
                val approximation = candidate.copy(fontSize = candidate.fontSize * 0.6f, lineHeight = candidate.lineHeight * 0.6f)
                val unitStyle = candidate.copy(fontSize = candidate.fontSize * 0.5f, lineHeight = candidate.lineHeight * 0.5f)
                val width = measurer.measure(layoutNumber, candidate, softWrap = false).size.width +
                    measurer.measure("≈", approximation, softWrap = false).size.width +
                    measurer.measure(unit, unitStyle, softWrap = false).size.width
                // Measure the actual three text styles, their two gaps and a small glyph-edge allowance.
                width + with(density) { 18.dp.roundToPx() } <= available
            }
            base.copy(fontSize = size.sp, lineHeight = (size * 1.15f).sp)
        }
        val numberWidth = remember(layoutNumber, style, density, measurer) {
            with(density) { measurer.measure(layoutNumber, style, softWrap = false).size.width.toDp() }
        }
        // Exceptional magnitudes remain fully readable by scrolling, without shrinking below 28 sp.
        Row(Modifier.horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("≈", style = style.copy(fontSize = style.fontSize * 0.6f, lineHeight = style.lineHeight * 0.6f), color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(number, Modifier.width(numberWidth), style = style, textAlign = TextAlign.End, softWrap = false)
            Text(unit, style = style.copy(fontSize = style.fontSize * 0.5f, lineHeight = style.lineHeight * 0.5f), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun Modifier.resultReveal(alpha: Float): Modifier = graphicsLayer { this.alpha = alpha }
    .then(if (alpha <= 0f) Modifier.clearAndSetSemantics {} else Modifier)
