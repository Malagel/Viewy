package com.malagel.viewy.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.background
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.malagel.viewy.R
import com.malagel.viewy.measurement.HeightDraft
import com.malagel.viewy.measurement.HeightEntryUnit

/** No text field or input connection: touch and hardware keys edit the same pure draft. */
@Composable
internal fun EmbeddedHeightEditor(
    draft: HeightDraft, interactive: Boolean, handleBack: Boolean, onKey: (Char) -> Unit, onBackspace: () -> Unit,
    onSwitchUnit: () -> Unit, onCancel: () -> Unit, onConfirm: () -> Unit,
) {
    val focus = remember { FocusRequester() }
    val locale = LocalLocale.current.platformLocale
    val decimal = HeightDraft.decimalSeparator(locale)
    val decimalDescription = stringResource(R.string.height_decimal)
    val display = draft.displayText(locale)
    val displayDescription = stringResource(R.string.height_display,
        display.ifEmpty { stringResource(R.string.height_empty) }, draft.unit.label)
    val limitDescription = pluralStringResource(R.plurals.height_digit_limit, HeightDraft.maximumDigits, HeightDraft.maximumDigits)
    val keyColors = ButtonDefaults.filledTonalButtonColors(
        containerColor = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        disabledContainerColor = MaterialTheme.colorScheme.secondaryContainer,
        disabledContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    )
    val enterColors = ButtonDefaults.buttonColors(
        containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary,
        disabledContainerColor = MaterialTheme.colorScheme.primary, disabledContentColor = MaterialTheme.colorScheme.onPrimary,
    )
    val canConfirm = draft.meters != null
    val enterAlpha by animateFloatAsState(if (canConfirm) 1f else 0.4f, tween(150), label = "Height confirmation availability")
    BackHandler(enabled = handleBack) { onCancel() }
    LaunchedEffect(interactive) { if (interactive) focus.requestFocus() }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surfaceContainer) {
        NavigationInputGuard(!interactive, Modifier.fillMaxSize()) {
            HeightEditorViewport(Modifier.fillMaxSize().focusRequester(focus).onPreviewKeyEvent { event ->
                val character = event.nativeKeyEvent.unicodeChar.toChar()
                val digitOrDecimal = character in '0'..'9' || character == '.' || character == ','
                val handled = digitOrDecimal || event.key == Key.Backspace || event.key == Key.Enter ||
                    event.key == Key.NumPadEnter || event.key == Key.Escape
                if (interactive && handled && event.type == KeyEventType.KeyDown) {
                    when {
                        digitOrDecimal -> onKey(character)
                        event.key == Key.Backspace -> onBackspace()
                        event.key == Key.Escape -> onCancel()
                        draft.meters != null -> onConfirm()
                    }
                }
                interactive && handled
            }.focusable()) {
                Column(Modifier.fillMaxWidth().padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onCancel, enabled = interactive,
                            colors = IconButtonDefaults.iconButtonColors(contentColor = MaterialTheme.colorScheme.onSurface,
                                disabledContentColor = MaterialTheme.colorScheme.onSurface)) {
                            Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.cancel))
                        }
                        Text(stringResource(R.string.object_height), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium,
                            maxLines = 2, overflow = TextOverflow.Ellipsis)
                        HeightUnits(draft, interactive, onSwitchUnit)
                    }
                    HeightNumberDisplay(draft, display, displayDescription, limitDescription)
                    if (draft.correctionFailed) Text(stringResource(R.string.height_correction_failed),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                    Column(Modifier.widthIn(max = 264.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("123", "456", "789", "${decimal}0\b").forEach { row ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                row.forEach { character ->
                                    FilledTonalButton(onClick = { if (character == '\b') onBackspace() else onKey(character) },
                                        enabled = interactive, colors = keyColors, shape = RoundedCornerShape(18.dp),
                                        contentPadding = PaddingValues(8.dp),
                                        modifier = Modifier.weight(1f).heightIn(min = 56.dp)) {
                                        if (character == '\b') Icon(painterResource(R.drawable.ic_backspace), stringResource(R.string.height_backspace))
                                        else Text(character.toString(), style = MaterialTheme.typography.headlineSmall.copy(fontFeatureSettings = "tnum"),
                                            modifier = if (character == decimal) Modifier.semantics {
                                                contentDescription = decimalDescription
                                            } else Modifier)
                                    }
                                }
                            }
                        }
                    }
                    Button(onClick = onConfirm, enabled = interactive && canConfirm,
                        colors = enterColors, shape = RoundedCornerShape(18.dp), contentPadding = PaddingValues(12.dp),
                        modifier = Modifier.widthIn(max = 264.dp).fillMaxWidth().heightIn(min = 48.dp)
                            .graphicsLayer { alpha = enterAlpha }) {
                        Text(stringResource(R.string.height_enter), style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }
    }
}

/** Measure once at a readable reference width, then fit the whole editor without a scroll owner. */
@Composable
private fun HeightEditorViewport(modifier: Modifier, content: @Composable () -> Unit) {
    Layout(content = content, modifier = modifier.clipToBounds()) { measurables, constraints ->
        val referenceWidth = constraints.maxWidth.coerceIn(320.dp.roundToPx(), 400.dp.roundToPx())
        val child = measurables.single().measure(Constraints(
            minWidth = referenceWidth, maxWidth = referenceWidth,
        ))
        val scale = minOf(1f, constraints.maxWidth.toFloat() / child.width,
            constraints.maxHeight.toFloat() / child.height)
        layout(constraints.maxWidth, constraints.maxHeight) {
            child.placeWithLayer((constraints.maxWidth - child.width) / 2, (constraints.maxHeight - child.height) / 2) {
                scaleX = scale
                scaleY = scale
                transformOrigin = TransformOrigin.Center
            }
        }
    }
}

/** A fixed unit slot never participates in numeric overflow or the rejection motion. */
@Composable
private fun HeightNumberDisplay(draft: HeightDraft, display: String, description: String, limitDescription: String) {
    val shake = remember { Animatable(0f) }
    var consumedRejection by remember { mutableLongStateOf(draft.rejectedDigitId) }
    LaunchedEffect(draft.rejectedDigitId) {
        if (draft.rejectedDigitId != consumedRejection) {
            consumedRejection = draft.rejectedDigitId
            shake.snapTo(0f)
            shake.animateTo(0f, keyframes {
                durationMillis = 240
                0f at 0
                -6f at 40
                6f at 80
                -4f at 120
                4f at 160
                0f at 240
            })
        }
    }
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val base = MaterialTheme.typography.displayMedium.copy(fontFeatureSettings = "tnum")
    val text = display.ifEmpty { "—" }
    Row(Modifier.fillMaxWidth().clearAndSetSemantics {
        contentDescription = description
        if (draft.atDigitLimit && draft.rejectedDigitId > 0) {
            stateDescription = limitDescription
            liveRegion = LiveRegionMode.Polite
        }
    }, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        // Reserve the full shake travel inside the number slot, before the fixed unit gap.
        BoxWithConstraints(Modifier.weight(1f).clipToBounds().padding(horizontal = 8.dp), contentAlignment = Alignment.CenterEnd) {
            val style = remember(base, text, constraints.maxWidth, density) {
                (base.fontSize.value.toInt() downTo 1).map { base.copy(fontSize = it.sp) }.firstOrNull {
                    measurer.measure(text, it, softWrap = false).size.width <= constraints.maxWidth
                } ?: base.copy(fontSize = 1.sp)
            }
            Text(text, Modifier.fillMaxWidth().offset { IntOffset(shake.value.dp.roundToPx(), 0) },
                style = style, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.End,
                color = if (display.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface)
        }
        Text(draft.unit.label, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Two stable labels over one moving selection surface, with independent accessible targets. */
@Composable
private fun HeightUnits(draft: HeightDraft, interactive: Boolean, onSwitchUnit: () -> Unit) {
    val units = listOf(draft.unit, draft.unit.alternative).sortedBy { it.ordinal }
    val selectedIndex = units.indexOf(draft.unit)
    val shape = RoundedCornerShape(18.dp)
    BoxWithConstraints(Modifier.width(112.dp).height(48.dp).clip(shape)
        .background(MaterialTheme.colorScheme.surfaceContainerLow).selectableGroup()) {
        val segmentWidth = maxWidth / 2
        val offset by animateDpAsState(segmentWidth * selectedIndex, tween(180), label = "Height unit selection")
        Box(Modifier.offset { IntOffset(offset.roundToPx(), 0) }.width(segmentWidth).fillMaxHeight(),
            contentAlignment = Alignment.Center) {
            Box(Modifier.size(width = 44.dp, height = 40.dp).clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest))
        }
        Row(Modifier.fillMaxSize()) {
            units.forEach { unit ->
                val label = unitDescription(unit)
                val interactions = remember(unit) { MutableInteractionSource() }
                Box(Modifier.weight(1f).fillMaxHeight().selectable(
                    selected = unit == draft.unit, enabled = interactive, role = Role.RadioButton,
                    interactionSource = interactions, indication = null,
                    onClick = { if (unit != draft.unit) onSwitchUnit() },
                ).semantics { contentDescription = label }, contentAlignment = Alignment.Center) {
                    Box(Modifier.size(width = 44.dp, height = 40.dp),
                        contentAlignment = Alignment.Center) {
                        Text(unit.label, Modifier.clearAndSetSemantics {}, style = MaterialTheme.typography.labelLarge,
                            color = if (unit == draft.unit) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun unitDescription(unit: HeightEntryUnit): String = stringResource(when (unit) {
    HeightEntryUnit.METERS -> R.string.height_unit_meters
    HeightEntryUnit.CENTIMETERS -> R.string.height_unit_centimeters
    HeightEntryUnit.FEET -> R.string.height_unit_feet
    HeightEntryUnit.INCHES -> R.string.height_unit_inches
})
