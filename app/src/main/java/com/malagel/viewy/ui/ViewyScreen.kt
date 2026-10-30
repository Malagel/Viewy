package com.malagel.viewy.ui

import android.view.ScaleGestureDetector
import android.view.MotionEvent
import android.graphics.Bitmap
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.annotation.StringRes
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.Image
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
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
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
import androidx.compose.ui.text.style.TextOverflow
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
import com.malagel.viewy.domain.KnownHeightResult
import com.malagel.viewy.measurement.*
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
    var editHeight by rememberSaveable { mutableStateOf(false) }
    var showWorkspace by rememberSaveable { mutableStateOf(false) }
    var showStatus by rememberSaveable { mutableStateOf(false) }
    var statusSnapshot by remember { mutableStateOf<MeasurementUiState?>(null) }
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
    DisposableEffect(model) {
        onDispose { model.visibilityChanged(MeasurementVisibility()) }
    }
    val openAppSettings = {
        if (!overlay.blocksMeasurement && state.canOpenSettings) {
            model.visibilityChanged(state.visibility.copy(covered = true))
            onSettings()
        }
    }
    val openHeight = {
        model.visibilityChanged(state.visibility.copy(heightEditing = true))
        editHeight = true
    }
    val measurement = state.measurement
    val resultEntryMemory = remember(model) { ResultEntryMemory(measurement.result) }
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val previewStatusLabel = stringResource(R.string.preview_status)
    val requestPermission = { requested = true; launcher.launch(Manifest.permission.CAMERA) }
    val openSettings = {
        context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:${context.packageName}".toUri()))
    }
    val hint = state.guidance
    val captureRestriction = when (measurement.alignment) {
        com.malagel.viewy.measurement.Alignment.TURN_LEFT -> stringResource(R.string.diag_left)
        com.malagel.viewy.measurement.Alignment.TURN_RIGHT -> stringResource(R.string.diag_right)
        else -> null
    }
    var homeDestination by remember { mutableStateOf<SelectionDestination>(SelectionDestination.Home) }
    SideEffect { if (state.selection.isOpen) homeDestination = state.selection.destination }
    val measurementDestination = state.selection.activeMethod != null
    val destinationTransition = updateTransition(measurementDestination, label = "Home and measurement")
    val navigating = destinationTransition.currentState != destinationTransition.targetState || destinationTransition.isRunning
    val navigationToken = state.tutorial.navigationToken
    LaunchedEffect(measurementDestination, navigating, navigationToken) {
        if (measurementDestination && !navigating) model.methodEntered(navigationToken)
    }
    BackHandler(enabled = !settingsOpen && !diagnosticsOpen && !editHeight && !showStatus && state.tutorial.card == null &&
        (measurementDestination || state.selection.browsingMode != null || navigating)) {
        if (!navigating) model.backSelection()
    }
    LaunchedEffect(measurementDestination, navigating) {
        if (!measurementDestination && !navigating) showWorkspace = false
    }
    Scaffold { insets ->
        BoxWithConstraints(Modifier.fillMaxSize().padding(insets), contentAlignment = Alignment.TopCenter) {
            val compact = maxHeight < 500.dp || LocalDensity.current.fontScale > 1.5f || maxWidth < 320.dp
            val workspace: @Composable (Boolean) -> Unit = { overflow ->
                MeasurementVisibilityEffect(model, permission, overlay.blocksMeasurement || showStatus || navigating, editHeight)

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
                        key(Unit) {
                            BoxWithConstraints(
                                Modifier.fillMaxWidth().then(if (overflow) Modifier.height(320.dp * LocalDensity.current.fontScale.coerceAtLeast(1f)) else Modifier.weight(1f)).clip(RoundedCornerShape(28.dp))
                                    .background(if (state.heightConfigured) Color.Black else MaterialTheme.colorScheme.secondaryContainer),
                                contentAlignment = Alignment.Center,
                            ) {
                                var resultBackdrop by remember { mutableStateOf<ResultPreviewBackdrop?>(null) }
                                LaunchedEffect(measurement.result?.let(::ResultIdentity)) {
                                    if (resultBackdrop?.estimate !== measurement.result) resultBackdrop = null
                                }
                                val hintText = hint?.let { stringResource(it) }
                                val hintStyle = MaterialTheme.typography.bodyMedium
                                val hintHeight = remember(hintText, hintStyle, maxWidth, density) {
                                    hintText?.let {
                                        textMeasurer.measure(it, hintStyle, constraints = Constraints(maxWidth = with(density) { (maxWidth - 52.dp).roundToPx().coerceAtLeast(1) })).size.height
                                    } ?: 0
                                }
                                val recoveryNeeded = state.recoveryVisible
                                val useStatusDialog = !overflow && ((compact && recoveryNeeded) || hintHeight > with(density) { (maxHeight / 2 - 54.dp).toPx() })
                                if (state.heightConfigured) {
                                    LivePreview(model, state) { estimate, bitmap -> resultBackdrop = ResultPreviewBackdrop(estimate, bitmap) }
                                    if (!measurement.completed) {
                                        CameraPreviewControls(state, unit, compact, model::requestZoom, openHeight,
                                            Modifier.align(Alignment.TopCenter))
                                        PreviewControlFade(state.previewVisible && state.acquisition.running, Modifier.align(Alignment.Center)) {
                                            Reticle(Modifier.size(40.dp))
                                        }
                                        AlignmentChevrons(state.alignmentCue)
                                    }
                                }
                                if (!state.heightConfigured) {
                                    Button(
                                        onClick = openHeight,
                                        modifier = Modifier.padding(20.dp).widthIn(min = 240.dp, max = 320.dp).heightIn(min = 64.dp),
                                        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp),
                                    ) { Text(stringResource(R.string.enter_height), style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center) }
                                } else {
                                    // Long recovery text has a scrollable dialog in constrained windows.
                                    if (measurement.completed) {
                                        // The result overlay owns all completed-measurement content.
                                    } else if (useStatusDialog) {
                                        FilledTonalIconButton(onClick = { statusSnapshot = state; showStatus = true }, modifier = Modifier.align(Alignment.BottomStart).padding(4.dp)
                                            .semantics { contentDescription = previewStatusLabel }) {
                                            Text(if (recoveryNeeded || hint != null) "!" else "i")
                                        }
                                    } else if (recoveryNeeded) {
                                        Surface(Modifier.fillMaxWidth().align(Alignment.BottomCenter), color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.95f)) {
                                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                                RecoveryControls(state, permission, requested, requestPermission, openSettings, model::retryCamera, model::restartOrientation)
                                            }
                                        }
                                    } else {
                                        hint?.let { message ->
                                            Surface(
                                                Modifier.align(Alignment.BottomCenter).padding(12.dp),
                                                color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.9f),
                                                shape = RoundedCornerShape(16.dp),
                                            ) {
                                                Text(stringResource(message), Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                                                    .semantics { liveRegion = LiveRegionMode.Polite }, style = MaterialTheme.typography.bodyMedium)
                                            }
                                        }
                                    }
                                }
                                resultBackdrop?.takeIf { it.estimate === measurement.result }?.let { (_, bitmap) ->
                                    Image(bitmap.asImageBitmap(), contentDescription = null, contentScale = ContentScale.FillBounds,
                                        modifier = Modifier.fillMaxSize())
                                }
                                MeasurementResultOverlay(measurement, unit, resultEntryMemory, state.viewfinderInteractive, openHeight) { estimate ->
                                    if (resultBackdrop?.estimate === estimate) resultBackdrop = null
                                }
                            }
                        }
                        MeasurementStageArea(
                            measurement, textMeasurer,
                            if (compact) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge,
                        )
                    }
                    MeasurementControls(
                        captureTop = measurement.top == null, canCapture = state.canCapture,
                        compact = compact, onRestart = model::restart, onCapture = model::capture,
                        captureRestriction = captureRestriction,
                        homeControl = { HomeControl(model::openModes) },
                    )
                }
            }
            // An exceptional window gets a readable, scrollable workspace dialog. Its
            // capture/navigation row stays fixed; the regular portrait screen never scrolls.
            val exceptional = maxHeight < 360.dp || maxWidth < 280.dp || LocalDensity.current.fontScale > 2f
            val workspaceDialogOpen = exceptional && showWorkspace && (measurementDestination || navigating)
            val home: @Composable (Boolean) -> Unit = { interactive ->
                HomeScreen(if (state.selection.isOpen) state.selection.destination else homeDestination,
                    openAppSettings, model::backSelection, model::browseMode, model::selectMethod, interactive)
            }
            // Match the dialog's backdrop while it animates back to Home, avoiding a window-removal flash.
            ScreenOverlayHost(overlayTransition, overlay, backdropOnly = workspaceDialogOpen, overlay = { destination, interactive ->
                when (destination) {
                    ScreenOverlay.SETTINGS -> settingsContent(workspaceDialogOpen, interactive)
                    ScreenOverlay.DIAGNOSTICS -> DiagnosticsScreen(state, unit, permission, model::dismissDiagnostics, workspaceDialogOpen, interactive)
                    ScreenOverlay.NONE -> Unit
                }
            }) {
                MeasurementDestinationHost(destinationTransition, blocked = overlay.blocksMeasurement || workspaceDialogOpen) { measuring ->
                    if (!measuring) {
                        SideEffect { model.visibilityChanged(MeasurementVisibility()) }
                        home(!navigating && !measurementDestination && !overlay.blocksMeasurement && !workspaceDialogOpen)
                    } else if (exceptional) {
                        if (!showWorkspace) SideEffect { model.visibilityChanged(MeasurementVisibility()) }
                        Column(Modifier.fillMaxSize().padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            ViewyHeader(compact, false, true, { openHelp(false) }, {}, openAppSettings, diagnosticsEnabled, { openHelp(true) })
                            TextButton(onClick = { showWorkspace = true }, modifier = Modifier.weight(1f)) {
                                Text(stringResource(R.string.open_viewfinder))
                            }
                            MeasurementControls(measurement.top == null, state.canCapture, true, model::restart, model::capture,
                                captureRestriction) { HomeControl(model::openModes) }
                        }
                    } else workspace(false)
                }
            }
            if (workspaceDialogOpen) Dialog(
                onDismissRequest = { if (!overlay.blocksMeasurement && !navigating) showWorkspace = false },
                properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = false),
            ) {
                BackHandler(enabled = !overlay.blocksMeasurement && !editHeight && !showStatus && state.tutorial.card == null) {
                    if (!navigating) model.backSelection()
                }
                Surface(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars.union(WindowInsets.displayCutout))) {
                    ScreenOverlayHost(overlayTransition, overlay, backdropOnly = false, overlay = { destination, interactive ->
                        when (destination) {
                            ScreenOverlay.SETTINGS -> settingsContent(false, interactive)
                            ScreenOverlay.DIAGNOSTICS -> DiagnosticsScreen(state, unit, permission, model::dismissDiagnostics, interactive = interactive)
                            ScreenOverlay.NONE -> Unit
                        }
                    }) {
                        MeasurementDestinationHost(destinationTransition, blocked = overlay.blocksMeasurement) { measuring ->
                            if (measuring) workspace(true) else home(false)
                        }
                    }
                }
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
                Text(stageText(measurement))
                if (!state.heightConfigured) TextButton(onClick = { showStatus = false; editHeight = true }) { Text(stringResource(R.string.enter_height)) }
                else {
                    // The dialog suspends hardware; retain the failure that prompted it.
                    RecoveryControls(statusSnapshot ?: state, permission, requested, requestPermission, openSettings,
                        { showStatus = false }, { showStatus = false })
                    statusSnapshot?.guidance?.let { Text(stringResource(it)) }
                }
            }
        }, confirmButton = { TextButton(onClick = { showStatus = false }) { Text(stringResource(R.string.close)) } },
    )
    state.tutorial.card?.let { MethodTutorialDialog(it, model::dismissTutorial, model::changeTutorialPage) }
}

@Composable
private fun RecoveryControls(
    state: MeasurementUiState, permission: Boolean, requested: Boolean,
    onPermission: () -> Unit, onSettings: () -> Unit, onCameraRetry: () -> Unit, onOrientationRetry: () -> Unit,
) {
    when {
        !permission && state.recoveryVisible -> {
            Text(stringResource(R.string.camera_permission_explanation))
            TextButton(onClick = onPermission) { Text(stringResource(if (requested) R.string.camera_permission_retry else R.string.camera_permission_allow)) }
            if (requested) TextButton(onClick = onSettings) { Text(stringResource(R.string.open_app_settings)) }
        }
        state.cameraFailureVisible && state.acquisitionEligible -> {
            Text(stringResource(state.camera.messageRes))
            TextButton(onClick = onCameraRetry) { Text(stringResource(R.string.camera_retry)) }
        }
        state.orientationFailureVisible -> {
            Text(stringResource(R.string.guidance_orientation))
            TextButton(onClick = onOrientationRetry) { Text(stringResource(R.string.orientation_retry)) }
        }
    }
}

/** Report the window containing the actual viewfinder, including the exceptional dialog. */
@Composable
private fun MeasurementVisibilityEffect(model: MeasurementViewModel, permission: Boolean, covered: Boolean, editing: Boolean) {
    val focused = LocalWindowInfo.current.isWindowFocused
    SideEffect {
        model.visibilityChanged(MeasurementVisibility(
            surfaceVisible = true, windowFocused = focused, covered = covered,
            heightEditing = editing, permissionGranted = permission,
        ))
    }
}

private data class ResultPreviewBackdrop(val estimate: KnownHeightResult.Estimate, val bitmap: Bitmap)

private data class SuspendedPreviewFrame(val bitmap: Bitmap, val alpha: Float)

private const val CAMERA_FADE_MILLIS = 225

/** Retain outgoing controls visually while revoking touch, focus and accessibility immediately. */
@Composable
private fun PreviewControlFade(visible: Boolean, modifier: Modifier, content: @Composable () -> Unit) {
    val transition = updateTransition(visible, label = "Preview control visibility")
    transition.AnimatedVisibility(
        visible = { it }, modifier = modifier,
        enter = fadeIn(tween(CAMERA_FADE_MILLIS)),
        exit = fadeOut(tween(CAMERA_FADE_MILLIS)),
    ) {
        val blocked = !visible || transition.isRunning || transition.currentState != transition.targetState
        Box(Modifier.focusProperties { canFocus = !blocked }
            .then(if (blocked) Modifier.clearAndSetSemantics {} else Modifier)) {
            content()
            if (blocked) Box(Modifier.matchParentSize().pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
                }
            })
        }
    }
}

@Composable
private fun LivePreview(
    model: MeasurementViewModel, state: MeasurementUiState,
    onCompletionFrame: (KnownHeightResult.Estimate, Bitmap) -> Unit,
) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val view = remember(context) { PreviewView(context) }
    var frozen by remember { mutableStateOf<SuspendedPreviewFrame?>(null) }
    val liveAlpha by animateFloatAsState(
        if (state.previewVisible) 1f else 0f,
        tween(CAMERA_FADE_MILLIS), label = "Streaming preview",
    )
    val currentAlpha by rememberUpdatedState(liveAlpha)
    val animateSnapshot = state.resumed && state.visibility.surfaceVisible && !state.measurement.completed
    LaunchedEffect(animateSnapshot) { if (!animateSnapshot) frozen = null }
    DisposableEffect(view, model) {
        val detector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                model.scaleZoom(detector.scaleFactor)
                return true
            }
        })
        view.setOnTouchListener { touched, event ->
            detector.onTouchEvent(event)
            if (event.actionMasked == MotionEvent.ACTION_UP) touched.performClick()
            true
        }
        onDispose { view.setOnTouchListener(null) }
    }
    AndroidView(factory = { view }, modifier = Modifier.fillMaxSize()
        .focusProperties { canFocus = state.previewVisible }
        .then(if (!state.previewVisible) Modifier.clearAndSetSemantics {} else Modifier)
        .graphicsLayer { alpha = if (state.previewVisible) liveAlpha else 0f })
    frozen?.takeIf { animateSnapshot }?.let { frame ->
        // Initialize at the captured opacity during composition, before the first draw.
        // A LaunchedEffect-only snap would expose a black frame during this handoff.
        val opacity = remember(frame) { Animatable(frame.alpha) }
        Image(frame.bitmap.asImageBitmap(), contentDescription = null, contentScale = ContentScale.FillBounds,
            modifier = Modifier.fillMaxSize().graphicsLayer { alpha = opacity.value })
        LaunchedEffect(frame) {
            opacity.animateTo(0f, tween(CAMERA_FADE_MILLIS))
            if (frozen === frame) frozen = null
        }
    }
    DisposableEffect(view, owner, model, state.acquisitionEligible, state.previewEpoch) {
        var preview: CameraPreview? = null
        var released = false
        val token = if (state.acquisitionEligible) model.startPreview(requestZoom = { ratio -> preview?.requestZoom(ratio) }) { animate ->
            // Retain pixels beneath the result's color fill, never the camera session.
            model.state.value.measurement.result?.takeIf { currentAlpha > 0f }?.let { estimate ->
                runCatching { view.bitmap }.getOrNull()?.let { onCompletionFrame(estimate, it) }
            }
            // Other intentional visible suspensions keep their existing fade.
            frozen = if (animate && currentAlpha > 0f) {
                runCatching { view.bitmap }.getOrNull()?.let { SuspendedPreviewFrame(it, currentAlpha) }
            } else null
            released = true
            preview?.close()
            preview = null
        } else null
        preview = token?.let { CameraPreview(context, owner, view) { camera -> model.previewChanged(it, camera) } }
        if (released) { preview?.close(); preview = null }
        onDispose {
            preview?.close()
            if (token != null) model.stopPreview(token)
        }
    }
}

/** A shared row gives the height editor only the space left after zoom and a fixed gap. */
@Composable
private fun CameraPreviewControls(
    state: MeasurementUiState, unit: HeightUnit, compact: Boolean,
    onZoom: (Float) -> Unit, onEditHeight: () -> Unit, modifier: Modifier = Modifier,
) {
    var retainedZoomState by remember { mutableStateOf(state) }
    SideEffect { if (state.previewVisible) retainedZoomState = state }
    val number = heightLabelText(unit.fromMeters(state.measurement.heightMeters!!), LocalLocale.current.platformLocale)
    val description = stringResource(R.string.height_edit, number, unit.label)
    Row(modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        PreviewControlFade(state.previewVisible, Modifier) {
            // Keep the outgoing ratio and enabled appearance until its fade completes.
            ZoomControls(if (state.previewVisible) state else retainedZoomState, onZoom)
        }
        Box(Modifier.weight(1f), contentAlignment = Alignment.TopEnd) {
            PreviewControlFade(state.previewHeightEditVisible, Modifier) {
                if (compact) {
                    LabeledIconButton(R.string.object_height, "↕", onEditHeight, description = description)
                } else {
                    CameraHeightButton(number, unit.label, description, onEditHeight)
                }
            }
        }
    }
}

@Composable
private fun CameraHeightButton(number: String, unit: String, description: String, onClick: () -> Unit) {
    val measurer = rememberTextMeasurer()
    val style = MaterialTheme.typography.labelLarge
    val density = LocalDensity.current
    BoxWithConstraints(Modifier.padding(4.dp), contentAlignment = Alignment.TopEnd) {
        val fullLabelFits = remember(description, style, constraints.maxWidth, density) {
            measurer.measure(description, style, softWrap = false).size.width <=
                (constraints.maxWidth - with(density) { 32.dp.roundToPx() })
        }
        FilledTonalButton(
            onClick = onClick, shape = CircleShape,
            modifier = Modifier.widthIn(max = maxWidth).semantics { contentDescription = description },
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
            colors = ButtonDefaults.filledTonalButtonColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.92f)),
        ) {
            if (fullLabelFits) {
                Text(description, Modifier.clearAndSetSemantics {}, maxLines = 1)
            } else {
                // Keep the height cue and unit readable; only the visible number may be shortened.
                Row(Modifier.clearAndSetSemantics {}, verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("↕")
                    Text(number, Modifier.weight(1f, fill = false), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(unit, maxLines = 1)
                }
            }
        }
    }
}

/** One snapshot drives gestures, button bounds and the persistent ratio label. */
@Composable
private fun ZoomControls(state: MeasurementUiState, onZoom: (Float) -> Unit, modifier: Modifier = Modifier) {
    val zoom = state.camera.zoom
    val editable = zoom.editable(state.camera.ready, state.measurement.top != null, state.measurement.completed)
    val zoomIn = stringResource(R.string.zoom_in)
    val zoomOut = stringResource(R.string.zoom_out)
    val ratioLabel = String.format(LocalLocale.current.platformLocale, "%.1f×", zoom.ratio)
    val description = stringResource(R.string.zoom_ratio, ratioLabel)
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.95f)) {
            Column {
                IconButton(onClick = { onZoom(zoom.ratio + 0.5f) }, enabled = editable && zoom.ratio < zoom.maximum,
                    modifier = Modifier.size(48.dp).semantics { contentDescription = zoomIn; stateDescription = description }) {
                    Text("+", style = MaterialTheme.typography.titleLarge)
                }
                IconButton(onClick = { onZoom(zoom.ratio - 0.5f) }, enabled = editable && zoom.ratio > zoom.minimum,
                    modifier = Modifier.size(48.dp).semantics { contentDescription = zoomOut; stateDescription = description }) {
                    Text("−", style = MaterialTheme.typography.titleLarge)
                }
            }
        }
        Surface(Modifier.padding(top = 4.dp), shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.95f)) {
            Text(ratioLabel, Modifier.padding(horizontal = 8.dp, vertical = 4.dp).semantics { contentDescription = description },
                style = MaterialTheme.typography.labelMedium)
        }
    }
}

/** Reserve both caption layouts even before height entry, including wrapping at large text sizes. */
@Composable
private fun MeasurementStageArea(
    measurement: MeasurementState,
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
            if (measurement.heightMeters != null && !measurement.completed) Text(
                stageText(measurement),
                Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite }, textAlign = TextAlign.Center, style = style,
            )
        }
    }
}

@Composable
private fun stageText(state: MeasurementState): String = stringResource(when {
    state.heightMeters == null -> R.string.known_height_method
    state.completed -> R.string.result_horizontal_distance
    state.stage == MeasurementStage.TOP -> R.string.measurement_stage_top
    else -> R.string.measurement_stage_bottom
})

@Composable
internal fun LabeledIconButton(@StringRes label: Int, symbol: String, onClick: () -> Unit, modifier: Modifier = Modifier, description: String = stringResource(label)) {
    FilledTonalIconButton(onClick = onClick, modifier = modifier.semantics { contentDescription = description }) {
        Text(symbol, fontSize = with(LocalDensity.current) { 24.dp.toSp() }, modifier = Modifier.clearAndSetSemantics {})
    }
}

@Composable
private fun MeasurementControls(
    captureTop: Boolean, canCapture: Boolean, compact: Boolean,
    onRestart: () -> Unit, onCapture: () -> Unit, captureRestriction: String? = null, homeControl: @Composable () -> Unit,
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
            modifier = Modifier.size(if (compact) 72.dp else 88.dp).semantics {
                contentDescription = captureLabel
                captureRestriction?.let { stateDescription = it }
            },
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
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { homeControl() }
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
        // Slightly fuller arms keep their outer footprint and leave space around the aiming dot.
        val tipInset = 0.125.dp.toPx()
        val gap = 5.5.dp.toPx()
        listOf(Color.Black to 2.25.dp.toPx(), Color.White to 1.25.dp.toPx()).forEach { (color, stroke) ->
            drawLine(color, Offset(center.x, tipInset), Offset(center.x, center.y - gap), stroke, StrokeCap.Round)
            drawLine(color, Offset(center.x, center.y + gap), Offset(center.x, size.height - tipInset), stroke, StrokeCap.Round)
            drawLine(color, Offset(tipInset, center.y), Offset(center.x - gap, center.y), stroke, StrokeCap.Round)
            drawLine(color, Offset(center.x + gap, center.y), Offset(size.width - tipInset, center.y), stroke, StrokeCap.Round)
        }
        drawCircle(Color.Black, radius = 1.75.dp.toPx(), center = center)
        drawCircle(Color.White, radius = 1.dp.toPx(), center = center)
    }
}
