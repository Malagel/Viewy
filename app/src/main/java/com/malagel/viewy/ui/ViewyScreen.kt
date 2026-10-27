package com.malagel.viewy.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.annotation.StringRes
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.malagel.viewy.R
import com.malagel.viewy.camera.CameraPreview
import com.malagel.viewy.measurement.*
import kotlinx.coroutines.delay

@Composable
fun ViewyScreen(state: MeasurementUiState, model: MeasurementViewModel) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    var permission by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    var requested by rememberSaveable { mutableStateOf(false) }
    var cameraAttempt by remember { mutableIntStateOf(0) }
    var editHeight by rememberSaveable { mutableStateOf(false) }
    var showWorkspace by rememberSaveable { mutableStateOf(false) }
    var showStatus by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(state.selection.activeMethod) {
        if (state.selection.activeMethod == null) {
            editHeight = false
            showWorkspace = false
            showStatus = false
        }
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { permission = it }
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) permission = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    val measurement = state.measurement
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val previewStatusLabel = stringResource(R.string.preview_status)
    val resultDescription = measurement.result?.let {
        stringResource(R.string.horizontal_result, distance(it.horizontalDistanceMeters, measurement.unit))
    }
    val requestPermission = { requested = true; launcher.launch(Manifest.permission.CAMERA) }
    val openSettings = {
        context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:${context.packageName}".toUri()))
    }
    val candidate = contextualGuidance(state)
    // Delay changing sensor hints; capture eligibility itself is never delayed.
    var hint by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(candidate) {
        delay(if (candidate == null) 500 else 250)
        hint = candidate
    }
    MeasurementSelector(
        state.selection, model::dismissSelection, model::backSelection, model::browseMode, model::selectMethod,
        state.tutorial.navigationToken, model::selectionHidden,
    ) { methodBackdrop ->
        Scaffold(modifier = methodBackdrop) { insets ->
            BoxWithConstraints(Modifier.fillMaxSize().padding(insets), contentAlignment = Alignment.TopCenter) {
                val compact = maxHeight < 500.dp || LocalDensity.current.fontScale > 1.5f || maxWidth < 320.dp
                val workspace: @Composable (Boolean) -> Unit = { overflow ->
                    Column(
                        Modifier.widthIn(max = 600.dp).fillMaxSize().padding(start = if (compact) 12.dp else 20.dp, end = if (compact) 12.dp else 20.dp, top = 8.dp, bottom = if (compact) 16.dp else 32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Column(
                            Modifier.weight(1f).then(if (overflow) Modifier.verticalScroll(rememberScrollState()) else Modifier),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            ViewyHeader(compact, overflow, true, model::openTutorial, { showWorkspace = false })
                            key(state.selection.activeMethod) {
                                BoxWithConstraints(
                                    Modifier.fillMaxWidth().then(if (overflow) Modifier.height(320.dp * LocalDensity.current.fontScale.coerceAtLeast(1f)) else Modifier.weight(1f)).clip(RoundedCornerShape(28.dp))
                                        .background(if (state.needsCamera) Color.Black else MaterialTheme.colorScheme.secondaryContainer),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    val hintText = hint?.let { stringResource(it) }
                                    val hintStyle = MaterialTheme.typography.bodyMedium
                                    val hintHeight = remember(hintText, hintStyle, maxWidth, density) {
                                        hintText?.let {
                                            textMeasurer.measure(it, hintStyle, constraints = Constraints(maxWidth = with(density) { (maxWidth - 52.dp).roundToPx().coerceAtLeast(1) })).size.height
                                        } ?: 0
                                    }
                                    val recoveryNeeded = !permission || !state.camera.ready || !state.acquisition.running
                                    val useStatusDialog = !overflow && ((compact && recoveryNeeded) || hintHeight > with(density) { (maxHeight / 2 - 54.dp).toPx() })
                                    if (state.needsCamera && permission) {
                                        key(cameraAttempt) { LivePreview(model) }
                                        if (state.camera.ready && state.acquisition.running) Reticle(Modifier.size(40.dp))
                                    }
                                    if (!state.needsCamera) {
                                        Button(
                                            onClick = { editHeight = true },
                                            modifier = Modifier.padding(20.dp).widthIn(min = 240.dp, max = 320.dp).heightIn(min = 64.dp),
                                            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp),
                                        ) { Text(stringResource(R.string.enter_height), style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center) }
                                    } else {
                                        // Long recovery text has a scrollable dialog in constrained windows.
                                        if (useStatusDialog) {
                                            FilledTonalIconButton(onClick = { showStatus = true }, modifier = Modifier.align(Alignment.BottomStart).padding(4.dp)
                                                .semantics { contentDescription = previewStatusLabel }) {
                                                Text(if (!permission || !state.camera.ready || !state.acquisition.running || hint != null) "!" else "i")
                                            }
                                        } else if (recoveryNeeded) {
                                            Surface(Modifier.fillMaxWidth().align(Alignment.BottomCenter), color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.95f)) {
                                                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                                    RecoveryControls(state, permission, requested, requestPermission, openSettings, { cameraAttempt++ }, { model.restartSource(null) })
                                                }
                                            }
                                        } else {
                                            AnimatedContent(
                                                targetState = hint,
                                                modifier = Modifier.align(Alignment.BottomCenter).padding(12.dp),
                                                transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "Preview guidance",
                                            ) { message ->
                                                if (message != null) Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.9f), shape = RoundedCornerShape(16.dp)) {
                                                    Text(stringResource(message), Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                                                        .semantics { liveRegion = LiveRegionMode.Polite }, style = MaterialTheme.typography.bodyMedium)
                                                }
                                            }
                                        }
                                        val heightLabel = stringResource(R.string.height_edit, heightLabelText(measurement.unit.fromMeters(measurement.heightMeters!!), LocalLocale.current.platformLocale), measurement.unit.label)
                                        if (compact) {
                                            LabeledIconButton(R.string.object_height, "↕", { editHeight = true }, Modifier.align(Alignment.TopEnd), heightLabel)
                                        } else {
                                            FilledTonalButton(
                                                onClick = { editHeight = true }, shape = CircleShape,
                                                modifier = Modifier.align(Alignment.TopEnd).padding(12.dp),
                                                colors = ButtonDefaults.filledTonalButtonColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.92f)),
                                            ) { Text(heightLabel) }
                                        }
                                    }
                                }
                            }
                            MeasurementStageArea(
                                measurement, resultDescription, textMeasurer,
                                if (compact) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge,
                            )
                        }
                        MeasurementControls(
                            captureTop = measurement.top == null, canCapture = measurement.canCapture,
                            compact = compact, onRestart = model::restart, onCapture = model::capture,
                            modeControl = { ModeControl(model::openModes) },
                        )
                    }
                }
                // An exceptional window gets a readable, scrollable workspace dialog. Its
                // capture/navigation row stays fixed; the regular portrait screen never scrolls.
                val exceptional = maxHeight < 360.dp || maxWidth < 280.dp || LocalDensity.current.fontScale > 2f
                if (state.selection.activeMethod == null) {
                    InactiveMeasurementScreen(compact) { ModeControl(model::openModes) }
                } else if (exceptional) {
                    Column(Modifier.fillMaxSize().padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        ViewyHeader(compact, false, true, model::openTutorial, {})
                        TextButton(onClick = { showWorkspace = true }, modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.open_viewfinder))
                        }
                        MeasurementControls(measurement.top == null, measurement.canCapture, true, model::restart, model::capture) {
                            ModeControl(model::openModes)
                        }
                    }
                    if (showWorkspace) Dialog(onDismissRequest = { showWorkspace = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
                        Surface(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars.union(WindowInsets.displayCutout)).then(methodBackdrop)) { workspace(true) }
                    }
                } else workspace(false)
            }
        }
        if (editHeight && state.selection.activeMethod != null) HeightDialog(measurement, onDismiss = { editHeight = false }) { meters, unit ->
            model.setHeight(meters, unit)
            editHeight = false
        }
        if (showStatus && state.selection.activeMethod != null) AlertDialog(
            onDismissRequest = { showStatus = false }, title = { Text(stringResource(R.string.preview_status)) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.measurement_method_subtitle))
                    Text(stageText(measurement))
                    if (!state.needsCamera) TextButton(onClick = { showStatus = false; editHeight = true }) { Text(stringResource(R.string.enter_height)) }
                    else {
                        RecoveryControls(state, permission, requested, requestPermission, openSettings, { cameraAttempt++ }, { model.restartSource(null) })
                        candidate?.let { Text(stringResource(it)) }
                    }
                }
            }, confirmButton = { TextButton(onClick = { showStatus = false }) { Text(stringResource(R.string.close)) } },
        )
    }
    state.tutorial.card?.let { MethodTutorialDialog(it, model::dismissTutorial, model::changeTutorialPage) }
}

@Composable
private fun RecoveryControls(
    state: MeasurementUiState, permission: Boolean, requested: Boolean,
    onPermission: () -> Unit, onSettings: () -> Unit, onCameraRetry: () -> Unit, onOrientationRetry: () -> Unit,
) {
    when {
        !permission -> {
            Text(stringResource(R.string.camera_permission_explanation))
            TextButton(onClick = onPermission) { Text(stringResource(if (requested) R.string.camera_permission_retry else R.string.camera_permission_allow)) }
            if (requested) TextButton(onClick = onSettings) { Text(stringResource(R.string.open_app_settings)) }
        }
        !state.camera.ready -> {
            Text(stringResource(state.camera.messageRes))
            TextButton(onClick = onCameraRetry) { Text(stringResource(R.string.camera_retry)) }
        }
        !state.acquisition.running -> {
            Text(stringResource(R.string.guidance_orientation))
            TextButton(onClick = onOrientationRetry) { Text(stringResource(R.string.orientation_retry)) }
        }
    }
}

@Composable
private fun LivePreview(model: MeasurementViewModel) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val view = remember(context) { PreviewView(context) }
    AndroidView(factory = { view }, modifier = Modifier.fillMaxSize())
    DisposableEffect(view, owner, model) {
        val token = model.startPreview()
        val preview = token?.let { CameraPreview(context, owner, view) { camera -> model.previewChanged(it, camera) } }
        onDispose {
            preview?.close()
            if (token != null) model.stopPreview(token)
        }
    }
}

/** Reserve both caption layouts even before height entry, including wrapping at large text sizes. */
@Composable
private fun MeasurementStageArea(
    measurement: MeasurementState,
    resultDescription: String?,
    textMeasurer: TextMeasurer,
    style: TextStyle,
) {
    val top = stringResource(R.string.measurement_stage_top)
    val bottom = stringResource(R.string.measurement_stage_bottom)
    val density = LocalDensity.current
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val width = constraints.maxWidth
        val reservedHeight = remember(top, bottom, style, width, density, textMeasurer) {
            maxOf(
                textMeasurer.measure(top, style, constraints = Constraints(maxWidth = width)).size.height,
                textMeasurer.measure(bottom, style, constraints = Constraints(maxWidth = width)).size.height,
            )
        }
        Box(Modifier.fillMaxWidth().heightIn(min = with(density) { reservedHeight.toDp() }), contentAlignment = Alignment.Center) {
            if (measurement.heightMeters != null) Text(
                stageText(measurement), Modifier.fillMaxWidth().semantics {
                    liveRegion = LiveRegionMode.Polite
                    if (resultDescription != null) contentDescription = resultDescription
                }, textAlign = TextAlign.Center, style = style,
            )
        }
    }
}

@Composable
private fun stageText(state: MeasurementState): String = state.result?.let {
    stringResource(R.string.distance_estimate, distance(it.horizontalDistanceMeters, state.unit))
} ?: stringResource(when {
    state.heightMeters == null -> R.string.known_height_method
    state.stage == MeasurementStage.TOP -> R.string.measurement_stage_top
    else -> R.string.measurement_stage_bottom
})

@Composable
internal fun LabeledIconButton(@StringRes label: Int, symbol: String, onClick: () -> Unit, modifier: Modifier = Modifier, description: String = stringResource(label)) {
    FilledTonalIconButton(onClick = onClick, modifier = modifier.semantics { contentDescription = description }) {
        Text(symbol, fontSize = with(LocalDensity.current) { 24.dp.toSp() }, modifier = Modifier.clearAndSetSemantics {})
    }
}

/** Two significant digits for estimates; precision of internal calculations is retained. */
@Composable
private fun distance(meters: Double, unit: HeightUnit): String = "${String.format(LocalLocale.current.platformLocale, "%.2g", unit.fromMeters(meters))} ${unit.label}"
private fun decimalText(value: Double): String = java.math.BigDecimal.valueOf(value).stripTrailingZeros().toPlainString()

@Composable
private fun MeasurementControls(
    captureTop: Boolean, canCapture: Boolean, compact: Boolean,
    onRestart: () -> Unit, onCapture: () -> Unit, modeControl: @Composable () -> Unit,
) {
    Row(Modifier.widthIn(max = 320.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
            MeasurementActionButton(label = stringResource(R.string.restart_measurement), onClick = onRestart) {
                Icon(painterResource(R.drawable.ic_restart), contentDescription = null)
            }
        }
        val captureLabel = stringResource(if (captureTop) R.string.capture_top else R.string.capture_bottom)
        Button(
            onClick = onCapture, enabled = canCapture,
            modifier = Modifier.size(if (compact) 72.dp else 88.dp).semantics { contentDescription = captureLabel },
            shape = CircleShape, contentPadding = PaddingValues(0.dp),
            border = BorderStroke(2.dp, if (canCapture) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                contentColor = MaterialTheme.colorScheme.primary,
                disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
            ),
        ) {
            val color = LocalContentColor.current
            Canvas(Modifier.fillMaxSize().padding(6.dp)) { drawCircle(color) }
        }
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { modeControl() }
    }
}

@Composable
private fun HeightDialog(state: MeasurementState, onDismiss: () -> Unit, onSave: (Double, HeightUnit) -> Unit) {
    var unit by remember { mutableStateOf(state.unit) }
    var text by remember { mutableStateOf(state.heightMeters?.let { decimalText(state.unit.fromMeters(it)) } ?: "") }
    val meters = heightInMeters(text, unit)
    // Only this dialog responds to the IME. Top anchoring prevents the card from
    // recentering when the keyboard opens; constrained content scrolls above it.
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        val focusRequester = remember { FocusRequester() }
        val keyboard = LocalSoftwareKeyboardController.current
        val windowInfo = LocalWindowInfo.current
        var requestedInitialFocus by remember { mutableStateOf(false) }
        LaunchedEffect(windowInfo.isWindowFocused) {
            if (windowInfo.isWindowFocused && !requestedInitialFocus) {
                // Wait for the dialog's field to attach; never steal focus again on recomposition
                // or reopen a keyboard the user has deliberately dismissed.
                withFrameNanos { }
                focusRequester.requestFocus()
                keyboard?.show()
                requestedInitialFocus = true
            }
        }
        Box(
            Modifier.fillMaxSize()
                .windowInsetsPadding(WindowInsets.systemBars.union(WindowInsets.displayCutout))
                .imePadding().padding(24.dp),
            contentAlignment = Alignment.TopCenter,
        ) {
            Surface(
                modifier = Modifier.widthIn(max = 560.dp).fillMaxWidth(),
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
            ) {
                Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(stringResource(R.string.object_height), style = MaterialTheme.typography.headlineSmall)
                    Column(
                        Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(stringResource(R.string.height_instructions))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            HeightUnit.entries.forEach { choice ->
                                FilterChip(selected = unit == choice, onClick = {
                                    val current = heightInMeters(text, unit)
                                    if (current != null) text = decimalText(choice.fromMeters(current))
                                    unit = choice
                                }, label = { Text(choice.label) })
                            }
                        }
                        OutlinedTextField(
                            modifier = Modifier.focusRequester(focusRequester),
                            value = text, onValueChange = { text = it }, label = { Text(stringResource(R.string.height_label, unit.label)) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true,
                            isError = text.isNotEmpty() && meters == null,
                            supportingText = { Text(if (text.isNotEmpty() && meters == null) stringResource(R.string.height_invalid) else stringResource(R.string.height_hint)) },
                        )
                    }
                    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
                        TextButton(onClick = { meters?.let { onSave(it, unit) } }, enabled = meters != null) { Text(stringResource(R.string.save_height)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun Reticle(modifier: Modifier = Modifier) {
    val label = stringResource(R.string.reticle_description)
    Canvas(modifier.semantics { contentDescription = label }) {
        // A fine dark outline keeps the thin plus visible on bright scenes.
        listOf(Color.Black to 2.dp.toPx(), Color.White to 1.dp.toPx()).forEach { (color, stroke) ->
            drawLine(color, Offset(center.x, 0f), Offset(center.x, size.height), stroke, StrokeCap.Round)
            drawLine(color, Offset(0f, center.y), Offset(size.width, center.y), stroke, StrokeCap.Round)
        }
    }
}
