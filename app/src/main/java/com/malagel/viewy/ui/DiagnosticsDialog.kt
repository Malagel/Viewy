package com.malagel.viewy.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.malagel.viewy.R

/** Retained for a future developer-only entry; the production header opens method help. */
@Composable
internal fun DiagnosticsDialog(state: MeasurementUiState, model: MeasurementViewModel, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss, title = { Text(stringResource(R.string.show_diagnostics)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(state.camera.diagnostic)
                state.measurement.result?.let { result ->
                    val measurement = state.measurement
                    Text("Uncertainty: ${result.uncertainty}")
                    Text("Height ${measurement.heightMeters} m · horizontal distance ${result.horizontalDistanceMeters} m\n" +
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
        }, confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } },
    )
}
