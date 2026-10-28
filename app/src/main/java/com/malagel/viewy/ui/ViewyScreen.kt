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
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
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
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.withTimeoutOrNull

@Composable
fun ViewyScreen(
    state: MeasurementUiState, model: MeasurementViewModel, unit: HeightUnit, onSettings: () -> Unit,
    diagnosticsEnabled: Boolean, settingsOpen: Boolean, settingsContent: @Composable (backdropOnly: Boolean, interactive: Boolean) -> Unit,
) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    var permission by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    var requested by rememberSaveable { mutableStateOf(false) }
    var cameraAttempt by remember { mutableIntStateOf(0) }
    var editHeight by rememberSaveable { mutableStateOf(false) }
    var showWorkspace by rememberSaveable { mutableStateOf(false) }
    var showStatus by rememberSaveable { mutableStateOf(false) }
    val diagnosticsOpen = state.diagnosticsOpen && diagnosticsEnabled && !settingsOpen
    val overlayTarget = screenOverlay(settingsOpen, diagnosticsOpen)
    val overlayTransition = updateTransition(overlayTarget, label = "Full-screen destination")
    var retainedOverlay by remember { mutableStateOf(overlayTarget) }
    SideEffect { if (overlayTarget != ScreenOverlay.NONE) retainedOverlay = overlayTarget }
    val overlay = overlayPresentation(overlayTransition.currentState, overlayTransition.targetState, overlayTransition.isRunning, retainedOverlay)
    LaunchedEffect(diagnosticsEnabled, settingsOpen) {
        if (!diagnosticsEnabled || settingsOpen) model.dismissDiagnostics()
    }
    val openHelp: (Boolean) -> Unit = { longPress ->
        if (!overlay.blocksMeasurement && !editHeight && !showStatus) model.help(longPress, diagnosticsEnabled)
    }
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
    val openAppSettings = {
        if (!overlay.blocksMeasurement && state.canOpenSettings) onSettings()
    }
    val measurement = state.measurement
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val previewStatusLabel = stringResource(R.string.preview_status)
    val resultDescription = measurement.result?.let {
        stringResource(R.string.horizontal_result, distance(it.horizontalDistanceMeters, unit))
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
                            ViewyHeader(compact, overflow, true, { openHelp(false) }, { showWorkspace = false }, openAppSettings, diagnosticsEnabled, { openHelp(true) })
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
                                                    RecoveryControls(state, permission, requested, requestPermission, openSettings, { cameraAttempt++ }, model::restartOrientation)
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
                                        val heightLabel = stringResource(R.string.height_edit, heightLabelText(unit.fromMeters(measurement.heightMeters!!), LocalLocale.current.platformLocale), unit.label)
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
                                measurement, unit, resultDescription, textMeasurer,
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
                val workspaceDialogOpen = exceptional && state.selection.activeMethod != null && showWorkspace
                // Cover the Activity as well as the workspace window: the backdrop
                // remains visible while Android tears down/recreates that dialog.
                ScreenOverlayHost(overlayTransition, overlay, backdropOnly = workspaceDialogOpen, overlay = { destination, interactive ->
                    when (destination) {
                        ScreenOverlay.SETTINGS -> settingsContent(workspaceDialogOpen, interactive)
                        ScreenOverlay.DIAGNOSTICS -> DiagnosticsScreen(state, unit, permission, model::dismissDiagnostics, workspaceDialogOpen, interactive)
                        ScreenOverlay.NONE -> Unit
                    }
                }) {
                    if (state.selection.activeMethod == null) {
                        InactiveMeasurementScreen(compact, openAppSettings) { ModeControl(model::openModes) }
                    } else if (exceptional) {
                        Column(Modifier.fillMaxSize().padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            ViewyHeader(compact, false, true, { openHelp(false) }, {}, openAppSettings, diagnosticsEnabled, { openHelp(true) })
                            TextButton(onClick = { showWorkspace = true }, modifier = Modifier.weight(1f)) {
                                Text(stringResource(R.string.open_viewfinder))
                            }
                            MeasurementControls(measurement.top == null, measurement.canCapture, true, model::restart, model::capture) {
                                ModeControl(model::openModes)
                            }
                        }
                        if (showWorkspace) Dialog(
                            onDismissRequest = { if (!overlay.blocksMeasurement) showWorkspace = false },
                            properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = !overlay.blocksMeasurement),
                        ) {
                            Surface(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars.union(WindowInsets.displayCutout)).then(methodBackdrop)) {
                                ScreenOverlayHost(overlayTransition, overlay, backdropOnly = false, overlay = { destination, interactive ->
                                    when (destination) {
                                        ScreenOverlay.SETTINGS -> settingsContent(false, interactive)
                                        ScreenOverlay.DIAGNOSTICS -> DiagnosticsScreen(state, unit, permission, model::dismissDiagnostics, interactive = interactive)
                                        ScreenOverlay.NONE -> Unit
                                    }
                                }) { workspace(true) }
                            }
                        }
                    } else workspace(false)
                }
            }
        }
        if (editHeight && state.selection.activeMethod != null) HeightDialog(measurement, unit, onDismiss = { editHeight = false }) { meters ->
            model.setHeight(meters)
            editHeight = false
        }
        if (showStatus && state.selection.activeMethod != null) AlertDialog(
            onDismissRequest = { showStatus = false }, title = { Text(stringResource(R.string.preview_status)) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.measurement_method_subtitle))
                    Text(stageText(measurement, unit))
                    if (!state.needsCamera) TextButton(onClick = { showStatus = false; editHeight = true }) { Text(stringResource(R.string.enter_height)) }
                    else {
                        RecoveryControls(state, permission, requested, requestPermission, openSettings, { cameraAttempt++ }, model::restartOrientation)
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
    unit: HeightUnit,
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
                stageText(measurement, unit), Modifier.fillMaxWidth().semantics {
                    liveRegion = LiveRegionMode.Polite
                    if (resultDescription != null) contentDescription = resultDescription
                }, textAlign = TextAlign.Center, style = style,
            )
        }
    }
}

@Composable
private fun stageText(state: MeasurementState, unit: HeightUnit): String = state.result?.let {
    stringResource(R.string.distance_estimate, distance(it.horizontalDistanceMeters, unit))
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
private fun distance(meters: Double, unit: HeightUnit): String = distanceText(meters, unit, LocalLocale.current.platformLocale)

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

@OptIn(ExperimentalLayoutApi::class, FlowPreview::class)
@Composable
private fun HeightDialog(state: MeasurementState, unit: HeightUnit, onDismiss: () -> Unit, onSave: (Double) -> Unit) {
    val locale = LocalLocale.current.platformLocale
    val originalText = rememberSaveable { state.heightMeters?.let { heightInputText(unit.fromMeters(it), locale) } ?: "" }
    var text by rememberSaveable { mutableStateOf(originalText) }
    val meters = heightInMeters(text, unit)
    val opacity = remember { Animatable(0f) }
    var pendingClose by remember { mutableStateOf<(() -> Unit)?>(null) }
    val closing = pendingClose != null
    val close: (() -> Unit) -> Unit = { action ->
        if (pendingClose == null) pendingClose = action
    }
    Dialog(
        onDismissRequest = { close(onDismiss) },
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        val focusRequester = remember { FocusRequester() }
        val keyboard = LocalSoftwareKeyboardController.current
        val focusManager = LocalFocusManager.current
        val imeVisible by rememberUpdatedState(WindowInsets.isImeVisible)
        val windowInfo = LocalWindowInfo.current
        var requestedInitialFocus by remember { mutableStateOf(false) }
        var cardVisible by remember { mutableStateOf(false) }
        var placement by remember { mutableStateOf(HeightDialogPlacement()) }
        val keyboardTarget = WindowInsets.imeAnimationTarget
        val keyboardCurrent = WindowInsets.ime
        val density = LocalDensity.current
        // One dialog-owned animation job: dismissal cancels entry and continues
        // from its current opacity. Remove the window only after exit completes.
        LaunchedEffect(cardVisible, closing) {
            if (closing) {
                // Hide while the focused decimal editor is still attached. Removing
                // it first can restart input with default editor options mid-hide.
                keyboard?.hide()
                if (opacity.value > 0f) opacity.animateTo(0f, tween(170, easing = LinearEasing))
                // Visibility can change before the hide animation finishes; require
                // the animated bottom inset to reach zero too. Bound faulty IMEs.
                withTimeoutOrNull(750L) {
                    snapshotFlow { !imeVisible && keyboardCurrent.getBottom(density) == 0 }.first { it }
                }
                focusManager.clearFocus(force = true)
                withFrameNanos { }
                pendingClose?.invoke()
            } else if (cardVisible) {
                opacity.animateTo(1f, tween(175))
            }
        }
        LaunchedEffect(requestedInitialFocus, closing) {
            if (!requestedInitialFocus || closing) return@LaunchedEffect
            // Wait out transient initial inset reports and retain their maximum.
            // The timeout also leaves hardware/floating-keyboard entry reachable.
            withTimeoutOrNull(1_000L) {
                snapshotFlow { maxOf(keyboardCurrent.getBottom(density), keyboardTarget.getBottom(density)) }
                    .onEach { placement = placement.observe(it) }
                    .debounce(120L)
                    .first { it > 0 }
            }
            placement = placement.freeze()
            withFrameNanos { }
            cardVisible = true
        }
        LaunchedEffect(windowInfo.isWindowFocused, closing) {
            if (windowInfo.isWindowFocused && !requestedInitialFocus && !closing) {
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
                .windowInsetsPadding(WindowInsets.systemBars.union(WindowInsets.displayCutout)
                    .union(WindowInsets(bottom = placement.frozenKeyboardBottomPx ?: 0)))
                .padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 20.dp),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Surface(
                modifier = Modifier.widthIn(max = 560.dp).fillMaxWidth()
                    .graphicsLayer { alpha = opacity.value }
                    .then(if (cardVisible && !closing) Modifier else Modifier.clearAndSetSemantics {}),
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
                        OutlinedTextField(
                            modifier = Modifier.focusRequester(focusRequester),
                            value = text, onValueChange = { text = it }, label = { Text(stringResource(R.string.height_label, unit.label)) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true,
                            isError = text.isNotEmpty() && meters == null,
                            supportingText = { Text(if (text.isNotEmpty() && meters == null) stringResource(R.string.height_invalid) else stringResource(R.string.height_hint)) },
                        )
                    }
                    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { close(onDismiss) }, enabled = cardVisible && !closing) { Text(stringResource(R.string.cancel)) }
                        TextButton(onClick = {
                            meters?.let { parsed ->
                                val saved = if (text == originalText) state.heightMeters ?: parsed else parsed
                                close { onSave(saved) }
                            }
                        }, enabled = cardVisible && !closing && meters != null) { Text(stringResource(R.string.save_height)) }
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
