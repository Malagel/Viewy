package com.malagel.viewy.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.malagel.viewy.orientation.CaptureStatus
import com.malagel.viewy.orientation.ObservationComparison
import com.malagel.viewy.orientation.OrientationObservation
import com.malagel.viewy.orientation.OrientationSource
import com.malagel.viewy.orientation.bearingRadians
import com.malagel.viewy.orientation.elevationRadians
import java.util.Locale

/** Development-only controls, outside the production measurement flow. Angles are degrees. */
@Composable
fun OrientationDiagnosticsPanel(
    state: OrientationDiagnosticsState,
    onCapture: (Boolean) -> Unit,
    onRestart: (OrientationSource?) -> Unit,
    onClear: () -> Unit,
) {
    val acquisition = state.acquisition
    val quality = acquisition.quality
    val observation = acquisition.observation
    Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.large) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Phase B · Orientation diagnostics (debug)", style = MaterialTheme.typography.titleMedium)
            Text("Shared foreground acquisition. A/B diagnostics are separate from measurement endpoints.")
            Text("Available: ${acquisition.availableSources.joinToString().ifEmpty { "none" }}")
            Text("Active: ${acquisition.activeSource ?: "none"} · ${acquisition.sensorName ?: "—"}")
            Text("${acquisition.message} · ${quality?.status ?: "WAITING"}")
            Text("Frame: ${acquisition.referenceFrameId ?: "—"}\nLast reset: ${acquisition.interruption ?: "—"}")
            Text("Sensor t: ${observation?.timestampNanos ?: "—"} ns\nAge: ${quality?.ageNanos?.let { format(it / 1e6) } ?: "—"} ms")
            Text("Elevation: ${degrees(observation?.direction?.elevationRadians())}°\nBearing: ${degrees(observation?.direction?.bearingRadians())}° (atan2(Y,X))")
            Text("Samples: ${quality?.sampleCount ?: 0} · span ${quality?.spanNanos?.let { format(it / 1e6) } ?: "—"} ms")
            Text("Ray variation RMS / max: ${degrees(quality?.rmsVariationRadians)}° / ${degrees(quality?.maximumVariationRadians)}°")
            Text("Last full-orientation step: ${degrees(quality?.latestStepRadians)}°\nPlatform accuracy code: ${quality?.sensorAccuracy ?: "unknown"}")
            Text("Mapping: ${observation?.cameraMappingId ?: "—"}\nGame bearing is relative and may drift; rotation-vector bearing is magnetically referenced. Near vertical, bearing is unavailable. Stability limits are provisional.")
            Button(onClick = { onCapture(true) }, enabled = quality?.status == CaptureStatus.READY) { Text("Record A") }
            Button(onClick = { onCapture(false) }, enabled = quality?.status == CaptureStatus.READY && state.first != null) { Text("Record B / compare") }
            Text(state.captureMessage)
            state.first?.let { Text(captureLabel("A", it)) }
            state.second?.let { Text(captureLabel("B", it)) }
            when (val comparison = state.comparison) {
                is ObservationComparison.Comparable -> Text(
                    "B − A: elevation ${degrees(comparison.elevationChangeRadians)}°, bearing ${degrees(comparison.bearingChangeRadians)}°\n" +
                        "Ray separation ${degrees(comparison.angularSeparationRadians)}° over ${format(comparison.elapsedNanos / 1e9)} s\n" +
                        "Returning to the same fixed aim can reveal a discrepancy; its cause is not established by these sensors.",
                )
                is ObservationComparison.Rejected -> Text("Comparison rejected: ${comparison.reason}")
                null -> Unit
            }
            OutlinedButton(onClick = onClear) { Text("Clear A / B") }
            OutlinedButton(onClick = { onRestart(null) }) { Text("Restart preferred / fallback session") }
            OrientationSource.entries.forEach { source ->
                OutlinedButton(onClick = { onRestart(source) }, enabled = source in acquisition.availableSources) {
                    Text("New session: ${source.name}")
                }
            }
            Text("Keep the camera position fixed. Orientation sensors cannot detect translation or guarantee drift-free readings. Record B within 15 s of A.")
        }
    }
}

private fun captureLabel(label: String, observation: OrientationObservation): String =
    "$label @ ${observation.timestampNanos} ns: elevation ${degrees(observation.direction.elevationRadians())}°, bearing ${degrees(observation.direction.bearingRadians())}°"
private fun degrees(radians: Double?): String = radians?.let { format(Math.toDegrees(it)) } ?: "—"
private fun format(value: Double): String = String.format(Locale.ROOT, "%.3f", value)
