package com.malagel.viewy.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.provider.Settings
import androidx.annotation.StringRes
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.camera.view.PreviewView
import com.malagel.viewy.BuildConfig
import com.malagel.viewy.R
import com.malagel.viewy.ui.theme.ViewyTheme
import com.malagel.viewy.domain.KnownHeightDistance
import com.malagel.viewy.domain.KnownHeightResult
import com.malagel.viewy.domain.ViewingDirection
import com.malagel.viewy.camera.CameraPreview
import com.malagel.viewy.camera.CameraPreviewState
import com.malagel.viewy.domain.DistanceUncertainty
import com.malagel.viewy.measurement.*
import com.malagel.viewy.measurement.Alignment as AimAlignment
import com.malagel.viewy.orientation.CaptureStatus
import java.util.Locale

@Composable
fun ViewyScreen(state: MeasurementUiState, model: MeasurementViewModel) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    var permission by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    var requested by rememberSaveable { mutableStateOf(false) }
    var cameraAttempt by remember { mutableIntStateOf(0) }
    var editHeight by rememberSaveable { mutableStateOf(false) }
    var showDebug by rememberSaveable { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> permission = granted }
    // Settings can change permission while the Activity is stopped.
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) permission = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    val measurement = state.measurement
    Scaffold { insets ->
        BoxWithConstraints(Modifier.fillMaxSize().padding(insets), contentAlignment = Alignment.TopCenter) {
            val previewHeight = (maxHeight - 340.dp).coerceIn(240.dp, 580.dp)
            Column(
                Modifier.widthIn(max = 600.dp).fillMaxWidth()
                    .verticalScroll(rememberScrollState()).heightIn(min = maxHeight).padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineLarge)
                    Text(stringResource(R.string.measurement_method_subtitle), style = MaterialTheme.typography.bodyMedium)
                }
                key(state.selection.activeMethod) {
                    if (state.selection.activeMethod.requiresCamera) {
                        Box(
                            Modifier.fillMaxWidth().height(previewHeight).clip(RoundedCornerShape(32.dp))
                                .background(Color.Black), contentAlignment = Alignment.Center,
                        ) {
                            if (permission) {
                                key(cameraAttempt) { LivePreview(model::previewChanged) }
                                Reticle(Modifier.size(44.dp))
                            } else {
                                Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(stringResource(R.string.camera_permission_explanation), color = Color.White)
                                    Button(onClick = { requested = true; launcher.launch(Manifest.permission.CAMERA) }) {
                                        Text(if (requested) stringResource(R.string.camera_permission_retry) else stringResource(R.string.camera_permission_allow))
                                    }
                                    if (requested) TextButton(onClick = {
                                        context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:${context.packageName}".toUri()))
                                    }) { Text(stringResource(R.string.open_app_settings), color = Color.White) }
                                }
                            }
                            if (permission && !state.camera.ready) {
                                Column(Modifier.align(Alignment.BottomCenter).background(Color.Black.copy(alpha = 0.8f)).padding(16.dp)) {
                                    Text(stringResource(state.camera.messageRes), color = Color.White)
                                    TextButton(onClick = { cameraAttempt++ }) { Text(stringResource(R.string.camera_retry), color = Color.White) }
                                }
                            }
                        }
                    }
                    Text(
                        when (measurement.stage) {
                            MeasurementStage.TOP -> stringResource(R.string.measurement_stage_top)
                            MeasurementStage.BOTTOM, MeasurementStage.CORRECTION -> stringResource(R.string.measurement_stage_bottom)
                            MeasurementStage.RESULT -> stringResource(R.string.measurement_stage_result)
                        }, style = MaterialTheme.typography.titleLarge,
                    )
                    OutlinedButton(onClick = { editHeight = true }) {
                        Text(measurement.heightMeters?.let {
                            stringResource(R.string.height_edit, String.format(Locale.getDefault(), "%.6g", measurement.unit.fromMeters(it)), measurement.unit.label)
                        } ?: stringResource(R.string.enter_height))
                    }
                    measurement.result?.let { MeasurementResult(it, measurement.unit) }
                    if (measurement.result == null) {
                        Text(stringResource(guidance(state)), style = MaterialTheme.typography.bodyLarge)
                        measurement.notice?.let { Text(stringResource(noticeResource(it)), color = MaterialTheme.colorScheme.primary) }
                        if (measurement.cameraReady && !state.acquisition.running) {
                            TextButton(onClick = { model.restartSource(null) }) { Text(stringResource(R.string.orientation_retry)) }
                        }
                    }
                    MeasurementControls(
                        captureTop = measurement.top == null, canCapture = measurement.canCapture,
                        onRestart = model::restart, onCapture = model::capture,
                        modeControl = { ModeControl(state.selection, model::toggleModes, model::dismissSelection, model::browseMode) },
                    )
                    if (measurement.result != null) {
                        Button(onClick = model::restart) { Text(stringResource(R.string.new_measurement)) }
                        OutlinedButton(onClick = model::recaptureBottom) { Text(stringResource(R.string.recapture_bottom)) }
                    } else Text(if (measurement.top == null) stringResource(R.string.capture_top) else stringResource(R.string.capture_bottom), style = MaterialTheme.typography.labelLarge)
                    Text(stringResource(R.string.capture_instructions), style = MaterialTheme.typography.bodySmall)
                    Text(stringResource(R.string.aiming_limitation), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (BuildConfig.DEBUG) {
                        TextButton(onClick = { showDebug = !showDebug }) { Text(if (showDebug) stringResource(R.string.hide_diagnostics) else stringResource(R.string.show_diagnostics)) }
                        if (showDebug) {
                            Text(state.camera.diagnostic)
                            measurement.result?.let { result ->
                                Text("Height ${measurement.heightMeters} m · distance ${result.horizontalDistanceMeters} m\n" +
                                    "Top/bottom elevation ${Math.toDegrees(result.diagnostics.topElevationRadians)}° / ${Math.toDegrees(result.diagnostics.bottomElevationRadians)}°\n" +
                                    "Elevation separation ${Math.toDegrees(result.diagnostics.elevationSeparationRadians)}°\n" +
                                    "Bearing error ${Math.toDegrees(result.diagnostics.alignmentErrorRadians)}°\n" +
                                    "Duration ${measurement.bottom?.timestampNanos?.minus(measurement.top!!.timestampNanos)?.div(1e9)} s\n" +
                                    "Sensitivity top/bottom ${result.diagnostics.topSensitivityMetersPerRadian} / ${result.diagnostics.bottomSensitivityMetersPerRadian} m/rad")
                                listOf("Top" to measurement.top, "Bottom" to measurement.bottom).forEach { (label, observation) ->
                                    observation?.let {
                                        Text("$label sensor t ${it.timestampNanos} ns · capture age ${it.quality.ageNanos?.div(1e6)} ms\n" +
                                            "Captured RMS/max ${it.quality.rmsVariationRadians?.let(Math::toDegrees)}° / ${it.quality.maximumVariationRadians?.let(Math::toDegrees)}°")
                                    }
                                }
                            }
                            OrientationDiagnosticsPanel(state.diagnostics, model::captureDiagnostic, model::restartSource, model::clearDiagnostics)
                        }
                    }
                }
            }
        }
    }
    if (editHeight) HeightDialog(measurement, onDismiss = { editHeight = false }) { meters, unit ->
        model.setHeight(meters, unit)
        editHeight = false
    }
    state.selection.browsingMode?.let { mode ->
        MethodSelectionCarousel(mode, model::selectMethod, model::dismissSelection)
    }
}

@Composable
private fun LivePreview(onState: (CameraPreviewState) -> Unit) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val latestCallback by rememberUpdatedState(onState)
    val view = remember(context) { PreviewView(context) }
    AndroidView(factory = { view }, modifier = Modifier.fillMaxSize())
    DisposableEffect(view, owner) {
        val preview = CameraPreview(context, owner, view) { latestCallback(it) }
        onDispose {
            preview.close()
            latestCallback(CameraPreviewState(messageRes = R.string.camera_stopped))
        }
    }
}

@Composable
private fun HeightDialog(state: MeasurementState, onDismiss: () -> Unit, onSave: (Double, HeightUnit) -> Unit) {
    var unit by remember { mutableStateOf(state.unit) }
    var text by remember { mutableStateOf(state.heightMeters?.let { decimalText(state.unit.fromMeters(it)) } ?: "") }
    val meters = heightInMeters(text, unit)
    AlertDialog(
        onDismissRequest = onDismiss, title = { Text(stringResource(R.string.object_height)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
                    value = text, onValueChange = { text = it }, label = { Text(stringResource(R.string.height_label, unit.label)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true,
                    isError = text.isNotEmpty() && meters == null,
                    supportingText = { Text(if (text.isNotEmpty() && meters == null) stringResource(R.string.height_invalid) else stringResource(R.string.height_hint)) },
                )
            }
        },
        confirmButton = { TextButton(onClick = { meters?.let { onSave(it, unit) } }, enabled = meters != null) { Text(stringResource(R.string.save_height)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@StringRes
private fun guidance(state: MeasurementUiState): Int {
    val measurement = state.measurement
    if (!measurement.cameraReady) return R.string.guidance_camera
    if (!state.acquisition.running) return R.string.guidance_orientation
    if (measurement.heightMeters == null) return R.string.guidance_height
    if (measurement.latest?.direction?.let { kotlin.math.hypot(it.x, it.y) < MeasurementPolicy.minimumHorizontalNorm } == true) {
        return R.string.guidance_near_vertical
    }
    return when (measurement.latest?.quality?.status) {
        CaptureStatus.MOVING -> R.string.guidance_moving
        CaptureStatus.STALE, CaptureStatus.WAITING, null -> R.string.guidance_waiting
        CaptureStatus.UNRELIABLE -> R.string.guidance_unreliable
        CaptureStatus.SETTLING -> R.string.guidance_settling
        CaptureStatus.READY -> when (measurement.alignment) {
            AimAlignment.TURN_LEFT -> R.string.guidance_left
            AimAlignment.TURN_RIGHT -> R.string.guidance_right
            AimAlignment.NEAR_VERTICAL -> R.string.guidance_near_vertical
            AimAlignment.ALIGNED -> R.string.guidance_bottom
            null -> R.string.guidance_top
        }
    }
}

/** Two significant digits for estimates; precision of internal calculations is retained. */
private fun distance(meters: Double, unit: HeightUnit): String = "${String.format(Locale.getDefault(), "%.2g", unit.fromMeters(meters))} ${unit.label}"
private fun number(value: Double): String = String.format(Locale.getDefault(), "%.2f", value)
private fun decimalText(value: Double): String = java.math.BigDecimal.valueOf(value).stripTrailingZeros().toPlainString()

@Composable
private fun Reticle(modifier: Modifier = Modifier) {
    val label = stringResource(R.string.reticle_description)
    Canvas(modifier.semantics { contentDescription = label }) {
        val gap = 6.dp.toPx()
        // Dark outline keeps the white reticle visible on bright and dark scenes.
        listOf(Color.Black to 4.dp.toPx(), Color.White to 1.5.dp.toPx()).forEach { (color, stroke) ->
            drawLine(color, Offset(center.x, 0f), Offset(center.x, center.y - gap), stroke, StrokeCap.Round)
            drawLine(color, Offset(center.x, center.y + gap), Offset(center.x, size.height), stroke, StrokeCap.Round)
            drawLine(color, Offset(0f, center.y), Offset(center.x - gap, center.y), stroke, StrokeCap.Round)
            drawLine(color, Offset(center.x + gap, center.y), Offset(size.width, center.y), stroke, StrokeCap.Round)
            drawCircle(color, radius = 2.dp.toPx(), style = Stroke(stroke))
        }
    }
}

@Composable
private fun MeasurementResult(result: KnownHeightResult.Estimate, unit: HeightUnit) {
    Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.large) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.distance_estimate, distance(result.horizontalDistanceMeters, unit)), style = MaterialTheme.typography.headlineLarge)
            Text(stringResource(R.string.distance_assumption))
            Text(stringResource(R.string.alignment_difference, number(Math.toDegrees(result.diagnostics.alignmentErrorRadians))))
            when (val uncertainty = result.uncertainty) {
                DistanceUncertainty.Unavailable -> Text(stringResource(R.string.uncertainty_unavailable))
                is DistanceUncertainty.Bounded -> Text(stringResource(R.string.uncertainty_bounded, distance(uncertainty.lowerMeters, unit), distance(uncertainty.upperMeters, unit)))
                is DistanceUncertainty.Unbounded -> Text(stringResource(R.string.uncertainty_unbounded, distance(uncertainty.lowerMeters, unit)))
                DistanceUncertainty.NumericallyIndeterminate -> Text(stringResource(R.string.uncertainty_indeterminate))
            }
        }
    }
}

@Composable
private fun MeasurementControls(
    captureTop: Boolean,
    canCapture: Boolean,
    onRestart: () -> Unit,
    onCapture: () -> Unit,
    modeControl: @Composable () -> Unit,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = onRestart, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.restart_measurement)) }
        val captureLabel = if (captureTop) stringResource(R.string.capture_top) else stringResource(R.string.capture_bottom)
        Button(
            onClick = onCapture, enabled = canCapture,
            modifier = Modifier.size(88.dp).semantics { contentDescription = captureLabel },
            shape = CircleShape, contentPadding = PaddingValues(0.dp),
        ) {
            val color = LocalContentColor.current
            Canvas(Modifier.size(42.dp)) {
                drawCircle(color, style = Stroke(width = 2.dp.toPx()))
                drawCircle(color, radius = 12.dp.toPx())
            }
        }
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { modeControl() }
    }
}

@Preview(name = "Measurement · Light", showBackground = true)
@Preview(name = "Measurement · Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun MeasurementPreview() {
    val result = KnownHeightDistance.estimate(
        1.2, ViewingDirection(1.0, 0.0, 0.2), ViewingDirection(1.0, 0.0, -0.2),
    ) as KnownHeightResult.Estimate
    ViewyTheme {
        Surface {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                MeasurementResult(result, HeightUnit.METERS)
                MeasurementControls(captureTop = false, canCapture = true, onRestart = {}, onCapture = {}, modeControl = { TextButton(onClick = {}) { Text(stringResource(R.string.mode)) } })
            }
        }
    }
}

@StringRes
private fun noticeResource(notice: MeasurementNotice): Int = when (notice) {
    MeasurementNotice.CAMERA_INTERRUPTED -> R.string.notice_camera_interrupted
    MeasurementNotice.SESSION_CHANGED -> R.string.notice_session_changed
    MeasurementNotice.CAPTURE_EXPIRED -> R.string.notice_expired
    MeasurementNotice.WAIT_FOR_STABILITY -> R.string.notice_wait_stability
    MeasurementNotice.NEAR_VERTICAL -> R.string.notice_near_vertical
    MeasurementNotice.INCOMPATIBLE_CAPTURES -> R.string.notice_incompatible
    MeasurementNotice.ALIGN_WITH_TOP -> R.string.notice_align
    MeasurementNotice.AIM_BELOW_TOP -> R.string.notice_below_top
    MeasurementNotice.UNUSABLE_ENDPOINTS -> R.string.notice_unusable
    MeasurementNotice.SMALL_SEPARATION -> R.string.notice_small_separation
    MeasurementNotice.SENSOR_RESTARTED -> R.string.notice_sensor_restarted
}
