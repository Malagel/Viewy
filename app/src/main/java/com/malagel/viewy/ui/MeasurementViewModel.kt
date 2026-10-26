package com.malagel.viewy.ui

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import com.malagel.viewy.camera.CameraPreviewState
import com.malagel.viewy.measurement.HeightUnit
import com.malagel.viewy.measurement.KnownHeightWorkflow
import com.malagel.viewy.measurement.MeasurementState
import com.malagel.viewy.measurement.MeasurementNotice
import com.malagel.viewy.orientation.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class MeasurementUiState(
    val measurement: MeasurementState = MeasurementState(),
    val camera: CameraPreviewState = CameraPreviewState(),
    val acquisition: AcquisitionState = AcquisitionState(),
    val diagnostics: OrientationDiagnosticsState = OrientationDiagnosticsState(),
)

/** Shares ONE foreground acquisition with measurement and debug tools. Everything runs on main. */
class MeasurementViewModel(application: Application) : AndroidViewModel(application) {
    private val workflow = KnownHeightWorkflow()
    private val mutableState = MutableStateFlow(MeasurementUiState())
    val state = mutableState.asStateFlow()
    private var resumed = false
    private var activeMapping: String? = null
    private var requestedSource: OrientationSource? = null
    private val acquisition = AndroidOrientationAcquisition(application) { value ->
        workflow.update(value.observation, value.referenceFrameId, value.running, SystemClock.elapsedRealtimeNanos())
        val previous = mutableState.value.diagnostics
        val diagnostics = if (previous.acquisition.referenceFrameId != value.referenceFrameId) {
            OrientationDiagnosticsState(acquisition = value, captureMessage = "Session changed; A/B cleared")
        } else previous.copy(acquisition = value)
        mutableState.value = mutableState.value.copy(measurement = workflow.state, acquisition = value, diagnostics = diagnostics)
    }

    fun resume() { resumed = true; reconcile() }
    fun pause() { resumed = false; reconcile() }

    fun previewChanged(camera: CameraPreviewState) {
        mutableState.value = mutableState.value.copy(camera = camera)
        workflow.cameraReady(camera.ready && resumed)
        reconcile()
    }

    private fun reconcile() {
        val camera = mutableState.value.camera
        val mapping = camera.mapping
        val desired = if (resumed && camera.ready) mapping else null
        workflow.cameraReady(desired != null)
        if (desired?.id != activeMapping) {
            activeMapping = desired?.id
            if (desired == null) acquisition.stop() else acquisition.start(requestedSource, desired)
        }
        publish()
    }

    fun setHeight(meters: Double, unit: HeightUnit) { workflow.setHeight(meters, unit); publish() }
    fun restart() { workflow.restart(); publish() }
    fun recaptureBottom() { workflow.recaptureBottom(); publish() }
    fun capture() {
        // Freshness is recomputed NOW, not trusted from the last 100 ms UI snapshot.
        val captured = acquisition.capture()
        workflow.capture(captured, SystemClock.elapsedRealtimeNanos())
        publish()
    }

    fun restartSource(source: OrientationSource?) {
        requestedSource = source
        workflow.restart(MeasurementNotice.SENSOR_RESTARTED)
        activeMapping = null
        acquisition.stop()
        reconcile()
    }

    fun clearDiagnostics() {
        mutableState.value = mutableState.value.copy(diagnostics = OrientationDiagnosticsState(acquisition = mutableState.value.acquisition))
    }

    fun captureDiagnostic(first: Boolean) {
        val previous = mutableState.value.diagnostics
        val next = when (val capture = acquisition.capture()) {
            is CaptureResult.Rejected -> previous.copy(captureMessage = "Capture rejected: ${capture.status}")
            is CaptureResult.Captured -> if (first) previous.copy(
                first = capture.observation, second = null, comparison = null, captureMessage = "A recorded",
            ) else previous.first?.let {
                previous.copy(second = capture.observation, comparison = compareObservations(it, capture.observation), captureMessage = "B recorded")
            } ?: previous.copy(captureMessage = "Record A first")
        }
        mutableState.value = mutableState.value.copy(diagnostics = next)
    }

    private fun publish() { mutableState.value = mutableState.value.copy(measurement = workflow.state) }
    override fun onCleared() = acquisition.stop()
}
