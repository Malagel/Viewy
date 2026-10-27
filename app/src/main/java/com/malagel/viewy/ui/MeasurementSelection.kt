package com.malagel.viewy.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.malagel.viewy.R
import com.malagel.viewy.domain.MeasurementMethod
import com.malagel.viewy.domain.MeasurementMode
import com.malagel.viewy.measurement.MeasurementSelection

@StringRes
private fun MeasurementMode.title(): Int = when (this) {
    MeasurementMode.LONG_DISTANCE -> R.string.long_distance
    MeasurementMode.SHORT_DISTANCE -> R.string.short_distance
    MeasurementMode.HEIGHT -> R.string.height_mode
}

/** Anchor above Mode when space permits; keep the menu inside the window otherwise. */
private object ModeMenuPosition : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val x = if (layoutDirection == LayoutDirection.Ltr) anchorBounds.right - popupContentSize.width else anchorBounds.left
        return IntOffset(
            x.coerceIn(0, (windowSize.width - popupContentSize.width).coerceAtLeast(0)),
            (anchorBounds.top - popupContentSize.height).coerceIn(0, (windowSize.height - popupContentSize.height).coerceAtLeast(0)),
        )
    }
}

@Composable
internal fun ModeControl(
    selection: MeasurementSelection,
    onToggle: () -> Unit,
    onDismiss: () -> Unit,
    onMode: (MeasurementMode) -> Unit,
) {
    val menuMaxHeight = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.height.toDp() * 0.7f }
    Box {
        TextButton(onClick = onToggle) { Text(stringResource(R.string.mode)) }
        if (selection.modeMenuOpen) {
            Popup(
                popupPositionProvider = ModeMenuPosition, onDismissRequest = onDismiss,
                properties = PopupProperties(focusable = true),
            ) {
                Column(
                    Modifier.widthIn(max = 280.dp).width(IntrinsicSize.Max)
                        .heightIn(max = menuMaxHeight)
                        .verticalScroll(rememberScrollState()).padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    MeasurementMode.entries.forEach { mode ->
                        ElevatedButton(
                            onClick = { onMode(mode) }, enabled = mode.available,
                            modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(28.dp),
                            elevation = ButtonDefaults.elevatedButtonElevation(defaultElevation = 4.dp),
                            colors = ButtonDefaults.elevatedButtonColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                            ),
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(stringResource(mode.title()))
                                if (!mode.available) Text(stringResource(R.string.not_available), style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun MethodSelectionCarousel(
    mode: MeasurementMode,
    onSelect: (MeasurementMethod) -> Unit,
    onDismiss: () -> Unit,
) {
    // Empty slots deliberately have no method identity or activation behavior.
    val cards: List<MeasurementMethod?> = remember(mode) { mode.methods + listOf(null, null) }
    val pager = rememberPagerState { cards.size }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        // Cards consume taps; unhandled taps around them dismiss.
        BoxWithConstraints(
            Modifier.fillMaxSize().pointerInput(onDismiss) { detectTapGestures { onDismiss() } }
                .safeDrawingPadding().padding(16.dp),
            contentAlignment = Alignment.Center,
        ) {
            val cardHeight = (maxHeight - 48.dp).coerceIn(0.dp, 480.dp)
            Column(
                Modifier.widthIn(max = 480.dp).fillMaxWidth().heightIn(max = maxHeight),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                HorizontalPager(
                    state = pager, contentPadding = PaddingValues(horizontal = 24.dp), pageSpacing = 12.dp,
                    modifier = Modifier.height(cardHeight),
                    verticalAlignment = Alignment.CenterVertically,
                ) { page ->
                    val method = cards[page]
                    val shape = RoundedCornerShape(28.dp)
                    if (method != null) {
                        ElevatedCard(
                            onClick = { onSelect(method) }, modifier = Modifier.fillMaxSize(), shape = shape,
                            elevation = CardDefaults.elevatedCardElevation(defaultElevation = 8.dp),
                            colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                        ) {
                            Column(
                                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp),
                            ) {
                                Text(stringResource(R.string.known_height_method), Modifier.semantics { heading() }, style = MaterialTheme.typography.titleLarge)
                                Text(stringResource(R.string.method_estimates))
                                Text(stringResource(R.string.method_procedure))
                                Text(stringResource(R.string.method_requires), style = MaterialTheme.typography.bodyMedium)
                                Text(stringResource(R.string.method_limitations), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    } else {
                        ElevatedCard(
                            modifier = Modifier.fillMaxSize(), shape = shape,
                            elevation = CardDefaults.elevatedCardElevation(defaultElevation = 4.dp),
                        ) {
                            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                Text(stringResource(R.string.method_placeholder), style = MaterialTheme.typography.titleLarge)
                                Text(stringResource(R.string.not_available))
                            }
                        }
                    }
                }
                Surface(shape = RoundedCornerShape(28.dp), tonalElevation = 3.dp, shadowElevation = 4.dp) {
                    Text(
                        stringResource(R.string.method_page, pager.currentPage + 1, cards.size),
                        Modifier.padding(horizontal = 16.dp, vertical = 6.dp).semantics { liveRegion = LiveRegionMode.Polite },
                    )
                }
            }
        }
    }
}
