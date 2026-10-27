package com.malagel.viewy.ui

import androidx.annotation.StringRes
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.stateDescription
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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
            (anchorBounds.bottom - popupContentSize.height).coerceIn(0, (windowSize.height - popupContentSize.height).coerceAtLeast(0)),
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
    val density = LocalDensity.current
    val window = LocalWindowInfo.current.containerSize
    val menuWidth = with(density) { (window.width.toDp() - 24.dp).coerceIn(0.dp, 296.dp) }
    val menuMaxHeight = with(density) { (window.height.toDp() - 24.dp).coerceAtLeast(0.dp) }
    val scrollMenu = menuMaxHeight < 260.dp * density.fontScale.coerceAtLeast(1f)
    val progress = remember { List(MeasurementMode.entries.size) { Animatable(0f) } }
    val heights = remember { mutableStateListOf(0, 0, 0) }
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(selection.modeMenuOpen) {
        if (selection.modeMenuOpen) visible = true
        coroutineScope {
            progress.forEachIndexed { index, animation ->
                launch {
                    delay((if (selection.modeMenuOpen) index else progress.lastIndex - index) * 65L)
                    animation.animateTo(if (selection.modeMenuOpen) 1f else 0f, spring(dampingRatio = 0.85f, stiffness = 450f))
                }
            }
        }
        if (!selection.modeMenuOpen) visible = false
    }
    val label = stringResource(R.string.mode)
    val expanded = stringResource(if (selection.modeMenuOpen) R.string.expanded else R.string.collapsed)
    Box {
        MeasurementActionButton(
            label = label, onClick = onToggle,
            modifier = Modifier.semantics { stateDescription = expanded },
        ) {
            val color = LocalContentColor.current
            Canvas(Modifier.size(24.dp)) {
                // Three choices make the circular launcher recognizable without an icon dependency.
                listOf(0.2f, 0.5f, 0.8f).forEach { y ->
                    drawCircle(color, radius = 2.dp.toPx(), center = Offset(size.width * 0.2f, size.height * y))
                    drawLine(color, Offset(size.width * 0.4f, size.height * y), Offset(size.width, size.height * y), 2.dp.toPx())
                }
            }
        }
        if (visible) Popup(
            popupPositionProvider = ModeMenuPosition, onDismissRequest = onDismiss,
            properties = PopupProperties(focusable = selection.modeMenuOpen),
        ) {
            Column(Modifier.width(menuWidth).heightIn(max = menuMaxHeight).pointerInput(onDismiss) { detectTapGestures { onDismiss() } }, horizontalAlignment = Alignment.End) {
                Column(
                    Modifier.weight(1f, fill = false).then(if (scrollMenu) Modifier.verticalScroll(rememberScrollState()) else Modifier).padding(vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    // Nearest choice opens first; all pills share the same horizontal position.
                    MeasurementMode.entries.reversed().forEachIndexed { row, mode ->
                        val index = MeasurementMode.entries.indexOf(mode)
                        Box(Modifier.fillMaxWidth()) {
                            ElevatedButton(
                                onClick = { onMode(mode) }, enabled = mode.available && selection.modeMenuOpen,
                                modifier = Modifier.fillMaxWidth()
                                    .onSizeChanged { heights[row] = it.height }
                                    .graphicsLayer {
                                        val fraction = progress[index].value
                                        translationY = (heights.drop(row + 1).sum() + with(density) { (64.dp + 10.dp * (2 - row)).toPx() }) * (1f - fraction)
                                        transformOrigin = TransformOrigin(1f, 1f)
                                        alpha = fraction
                                        scaleX = 0.25f + 0.75f * fraction
                                        scaleY = scaleX
                                    }.then(if (!selection.modeMenuOpen) Modifier.clearAndSetSemantics {} else Modifier)
                                    .heightIn(min = 56.dp),
                                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp), shape = RoundedCornerShape(32.dp),
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
                // Reserve the animation's path to the real launcher without drawing it again.
                // Tapping this transparent area dismisses through the popup's tap handler.
                Spacer(Modifier.size(56.dp))
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
    var comingSoon by remember { mutableStateOf(false) }
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
                                TextButton(onClick = { comingSoon = true }) { Text(stringResource(R.string.know_more)) }
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
    if (comingSoon) AlertDialog(
        onDismissRequest = { comingSoon = false }, title = { Text(stringResource(R.string.know_more)) },
        text = { Text(stringResource(R.string.documentation_coming_soon)) },
        confirmButton = { TextButton(onClick = { comingSoon = false }) { Text(stringResource(R.string.close)) } },
    )

}
