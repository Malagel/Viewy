package com.malagel.viewy.ui

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.malagel.viewy.R
import com.malagel.viewy.domain.MeasurementMethod
import com.malagel.viewy.domain.MeasurementMode
import com.malagel.viewy.measurement.MeasurementSelection
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
internal fun ModeControl(onOpen: () -> Unit) {
    MeasurementActionButton(label = stringResource(R.string.mode), onClick = onOpen) {
        val color = LocalContentColor.current
        Canvas(Modifier.size(24.dp)) {
            val top = Offset(size.width * 0.5f, size.height * 0.3f)
            drawLine(color, Offset(size.width * 0.2f, size.height * 0.65f), top, 2.dp.toPx(), StrokeCap.Round)
            drawLine(color, top, Offset(size.width * 0.8f, size.height * 0.65f), 2.dp.toPx(), StrokeCap.Round)
        }
    }
}

/** One modal window. Navigation changes only its content; the camera stays in the content slot. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MeasurementSelector(
    selection: MeasurementSelection,
    onDismiss: () -> Unit,
    onBack: () -> Unit,
    onMode: (MeasurementMode) -> Unit,
    onSelect: (MeasurementMethod) -> Unit,
    dismissalToken: Long,
    onHidden: (Long) -> Unit,
    content: @Composable (backdrop: Modifier) -> Unit,
) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var mounted by remember { mutableStateOf(selection.isOpen) }
    var sheetHeight by remember { mutableIntStateOf(0) }
    val windowHeight = LocalWindowInfo.current.containerSize.height
    val maxPanelHeight = with(LocalDensity.current) { windowHeight.toDp() * 0.85f }
    // Offset follows the actual entrance, exit and drag, rather than a separate animation clock.
    val progress by remember(sheet, windowHeight) {
        derivedStateOf {
            if (sheetHeight == 0) 0f else {
                val offset = runCatching { sheet.requireOffset() }.getOrDefault(windowHeight.toFloat())
                ((windowHeight - offset) / sheetHeight).coerceIn(0f, 1f)
            }
        }
    }
    LaunchedEffect(selection.isOpen) {
        if (selection.isOpen) mounted = true else if (mounted) {
            sheet.hide()
            mounted = false
        }
    }
    val currentOnHidden by rememberUpdatedState(onHidden)
    // A separate effect runs after the composition removing ModalBottomSheet commits.
    // Recreation with a closed selection has no sheet to hide and can acknowledge at once.
    LaunchedEffect(selection.isOpen, mounted, dismissalToken) {
        if (!selection.isOpen && !mounted) currentOnHidden(dismissalToken)
    }
    // Blur is a no-op below Android 12; Material's animated scrim still dims those versions.
    content(Modifier.blur(12.dp * progress))
    if (mounted) {
        // Preserve the last list during reverse sheet motion after selection/dismissal.
        var lastDestination by remember { mutableStateOf(selection.destination) }
        if (selection.isOpen) SideEffect { lastDestination = selection.destination }
        val destination = if (selection.isOpen) selection.destination else lastDestination
        ModalBottomSheet(
            onDismissRequest = onDismiss,
            sheetState = sheet,
            modifier = Modifier.onSizeChanged { sheetHeight = it.height },
            properties = ModalBottomSheetProperties(shouldDismissOnBackPress = false),
        ) {
            // ComponentDialog supplies the sheet's own back dispatcher. Outside taps/drag
            // use onDismissRequest; only Back navigates up from methods.
            BackHandler(enabled = selection.isOpen, onBack = onBack)
            SelectionLists(
                destination = destination, enabled = selection.isOpen,
                modifier = Modifier.fillMaxWidth().heightIn(max = maxPanelHeight).height(440.dp),
                onBack = onBack, onMode = onMode, onSelect = onSelect,
            )
        }
    }
}

@Composable
private fun SelectionLists(
    destination: SelectionDestination,
    enabled: Boolean,
    modifier: Modifier,
    onBack: () -> Unit,
    onMode: (MeasurementMode) -> Unit,
    onSelect: (MeasurementMethod) -> Unit,
) {
    AnimatedContent(
        targetState = destination, modifier = modifier,
        transitionSpec = {
            val forward = targetState is SelectionDestination.Methods
            slideInHorizontally(tween(220)) { if (forward) it else -it } togetherWith
                slideOutHorizontally(tween(220)) { if (forward) -it else it }
        }, label = "Measurement selection level",
    ) { level ->
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp)) {
            if (level is SelectionDestination.Methods) {
                TextButton(onClick = onBack, enabled = enabled) { Text(stringResource(R.string.back_to_modes)) }
                Text(stringResource(level.mode.title()), Modifier.padding(8.dp).semantics { heading() }, style = MaterialTheme.typography.headlineSmall)
                level.mode.methods.forEach { method ->
                    when (method) {
                        MeasurementMethod.KNOWN_OBJECT_HEIGHT -> SelectionEntry(
                            title = stringResource(R.string.known_height_method),
                            description = stringResource(R.string.known_height_description),
                            enabled = enabled, onClick = { onSelect(method) },
                        )
                    }
                }
            } else {
                Text(stringResource(R.string.select_mode), Modifier.padding(8.dp).semantics { heading() }, style = MaterialTheme.typography.headlineSmall)
                MeasurementMode.entries.forEach { mode ->
                    SelectionEntry(
                        title = stringResource(mode.title()), description = stringResource(mode.description()),
                        enabled = enabled && mode.available,
                        supporting = if (!mode.available) stringResource(R.string.coming_soon) else null,
                        onClick = { onMode(mode) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SelectionEntry(title: String, description: String, enabled: Boolean, supporting: String? = null, onClick: () -> Unit) {
    Surface(
        onClick = onClick, enabled = enabled, shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else 0.38f),
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(description, style = MaterialTheme.typography.bodyMedium)
            supporting?.let { Text(it, style = MaterialTheme.typography.labelMedium) }
        }
    }
}
