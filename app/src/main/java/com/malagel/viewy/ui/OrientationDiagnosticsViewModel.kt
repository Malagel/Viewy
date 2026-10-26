package com.malagel.viewy.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.malagel.viewy.orientation.AcquisitionState
import com.malagel.viewy.orientation.AndroidOrientationAcquisition
import com.malagel.viewy.orientation.CaptureResult
import com.malagel.viewy.orientation.ObservationComparison
import com.malagel.viewy.orientation.OrientationObservation
import com.malagel.viewy.orientation.OrientationSource
import com.malagel.viewy.orientation.compareObservations
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class OrientationDiagnosticsState(
    val acquisition: AcquisitionState = AcquisitionState(),
    val first: OrientationObservation? = null,
    val second: OrientationObservation? = null,
    val comparison: ObservationComparison? = null,
    val captureMessage: String = "No captures",
)

class OrientationDiagnosticsViewModel(application: Application) : AndroidViewModel(application) {
    private val mutableState = MutableStateFlow(OrientationDiagnosticsState())
    val state = mutableState.asStateFlow()
    private var requestedSource: OrientationSource? = null
    private val acquisition = AndroidOrientationAcquisition(application) { value ->
        mutableState.update { previous ->
            val invalidated = previous.acquisition.referenceFrameId != value.referenceFrameId
            if (invalidated) OrientationDiagnosticsState(
                acquisition = value,
                captureMessage = if (previous.first != null) "Captures cleared: reference frame changed" else previous.captureMessage,
            ) else previous.copy(acquisition = value)
        }
    }

    fun start() = acquisition.start(requestedSource)
    fun stop() = acquisition.stop()
    fun restart(source: OrientationSource?) {
        requestedSource = source
        acquisition.start(source)
    }
    fun clearCaptures() {
        mutableState.update { OrientationDiagnosticsState(acquisition = it.acquisition) }
    }
    fun capture(first: Boolean) {
        when (val result = acquisition.capture()) {
            is CaptureResult.Rejected -> mutableState.update { it.copy(captureMessage = "Capture rejected: ${result.status}") }
            is CaptureResult.Captured -> mutableState.update {
                if (first) it.copy(first = result.observation, second = null, comparison = null, captureMessage = "A recorded")
                else if (it.first == null) it.copy(captureMessage = "Record A first")
                else it.copy(second = result.observation, comparison = compareObservations(it.first, result.observation), captureMessage = "B recorded")
            }
        }
    }
    override fun onCleared() = acquisition.stop()
}
