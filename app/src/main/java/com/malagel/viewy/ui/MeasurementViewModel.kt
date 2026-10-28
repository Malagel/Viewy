package com.malagel.viewy.ui

import android.app.Application
import android.content.Context
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import com.malagel.viewy.camera.CameraPreviewState
import com.malagel.viewy.camera.PreviewSession
import com.malagel.viewy.domain.MeasurementMethod
import com.malagel.viewy.domain.MeasurementMode
import com.malagel.viewy.measurement.MeasurementSelection
import com.malagel.viewy.measurement.KnownHeightWorkflow
import com.malagel.viewy.measurement.MeasurementState
import com.malagel.viewy.measurement.MeasurementNotice
import com.malagel.viewy.orientation.*
import com.malagel.viewy.tutorial.LocalTutorialSeenStore
import com.malagel.viewy.tutorial.TutorialCoordinator
import com.malagel.viewy.tutorial.TutorialPage
import com.malagel.viewy.tutorial.TutorialState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class MeasurementUiState(
    val selection: MeasurementSelection = MeasurementSelection(),
    val measurement: MeasurementState = MeasurementState(),
    val camera: CameraPreviewState = CameraPreviewState(),
    val acquisition: AcquisitionState = AcquisitionState(),
    val diagnosticsOpen: Boolean = false,
    val tutorial: TutorialState = TutorialState(),
) {
    val canOpenSettings: Boolean get() = !diagnosticsOpen && !selection.isOpen && tutorial.card == null && tutorial.pendingMethod == null
    val canOpenHelp: Boolean get() = selection.activeMethod != null && !selection.isOpen && tutorial.card == null && tutorial.pendingMethod == null
    val needsCamera: Boolean get() = selection.activeMethod?.requiresCamera == true && measurement.heightMeters != null
    fun needsOrientation(resumed: Boolean): Boolean = resumed && needsCamera && camera.ready &&
        selection.activeMethod?.requiresOrientation == true
}

/** Owns ONE foreground acquisition; diagnostics only observes its snapshots. Everything runs on main. */
class MeasurementViewModel(application: Application) : AndroidViewModel(application) {
    private val workflow = KnownHeightWorkflow()
    private val tutorials = TutorialCoordinator(
        LocalTutorialSeenStore(application.getSharedPreferences(LocalTutorialSeenStore.FILE_NAME, Context.MODE_PRIVATE)),
        methodTutorials.keys,
    )
    private val mutableState = MutableStateFlow(MeasurementUiState())
    val state = mutableState.asStateFlow()
    private val previewSession = PreviewSession()
    private var resumed = false
    private var activeMapping: String? = null
    private val acquisition = AndroidOrientationAcquisition(application) { value ->
        workflow.update(value.observation, value.referenceFrameId, value.running, SystemClock.elapsedRealtimeNanos())
        mutableState.value = mutableState.value.copy(measurement = workflow.state, acquisition = value)
    }

    fun resume() { resumed = true; reconcile() }
    fun pause() { resumed = false; reconcile() }

    fun startPreview(): Long? {
        if (!mutableState.value.needsCamera) return null
        val token = previewSession.start()
        mutableState.value = mutableState.value.copy(camera = previewSession.state)
        reconcile()
        return token
    }

    fun previewChanged(token: Long, camera: CameraPreviewState) {
        if (!mutableState.value.needsCamera || !previewSession.update(token, camera)) return
        mutableState.value = mutableState.value.copy(camera = previewSession.state)
        reconcile()
    }

    fun stopPreview(token: Long) {
        if (!previewSession.stop(token)) return
        mutableState.value = mutableState.value.copy(camera = previewSession.state)
        reconcile()
    }

    private fun reconcile() {
        val camera = mutableState.value.camera
        val mapping = camera.mapping
        val desired = if (mutableState.value.needsOrientation(resumed)) mapping else null
        workflow.cameraReady(desired != null)
        if (desired?.id != activeMapping) {
            activeMapping = desired?.id
            if (desired == null) acquisition.stop() else acquisition.start(mapping = desired)
        }
        publish()
    }

    private fun updateSelection(next: MeasurementSelection, activated: MeasurementMethod? = null) {
        tutorials.selectionChanged(next, activated)
        mutableState.value = mutableState.value.copy(selection = next, tutorial = tutorials.state)
    }

    fun openModes() { if (mutableState.value.canOpenSettings) updateSelection(mutableState.value.selection.openModes()) }
    fun backSelection() = updateSelection(mutableState.value.selection.back())
    fun dismissSelection() = updateSelection(mutableState.value.selection.dismiss())
    fun browseMode(mode: MeasurementMode) = updateSelection(mutableState.value.selection.browse(mode))

    fun selectionHidden(token: Long) { tutorials.selectorHidden(token); publishTutorial() }
    fun help(longPress: Boolean, diagnosticsEnabled: Boolean) {
        when (helpAction(mutableState.value, longPress, diagnosticsEnabled)) {
            HelpAction.TUTORIAL -> { tutorials.openManually(); publishTutorial() }
            HelpAction.DIAGNOSTICS -> { mutableState.value = mutableState.value.withDiagnostics(open = true, enabled = diagnosticsEnabled) }
            HelpAction.NONE -> Unit
        }
    }
    fun dismissDiagnostics() { mutableState.value = mutableState.value.withDiagnostics(open = false) }
    fun dismissTutorial(cardId: Long) { tutorials.dismiss(cardId); publishTutorial() }
    fun changeTutorialPage(cardId: Long, page: TutorialPage) { tutorials.changePage(cardId, page); publishTutorial() }
    private fun publishTutorial() { mutableState.value = mutableState.value.copy(tutorial = tutorials.state) }

    fun selectMethod(method: MeasurementMethod) {
        if (mutableState.value.diagnosticsOpen) return
        val previous = mutableState.value.selection
        val next = previous.activate(method)
        if (next.activeMethod != previous.activeMethod) {
            // A different workflow must never inherit captures or a hardware session.
            workflow.restart()
            activeMapping = null
            acquisition.stop()
            previewSession.invalidate()
            mutableState.value = mutableState.value.copy(camera = previewSession.state)
        }
        updateSelection(next, activated = method)
        if (next.activeMethod != previous.activeMethod) reconcile()
    }

    fun setHeight(meters: Double) { workflow.setHeight(meters); publish(); reconcile() }
    fun restart() { workflow.restart(); publish() }
    fun recaptureBottom() { workflow.recaptureBottom(); publish() }
    fun capture() {
        // Freshness is recomputed NOW, not trusted from the last 100 ms UI snapshot.
        val captured = acquisition.capture()
        workflow.capture(captured, SystemClock.elapsedRealtimeNanos())
        publish()
    }

    fun restartOrientation() {
        workflow.restart(MeasurementNotice.SENSOR_RESTARTED)
        activeMapping = null
        acquisition.stop()
        reconcile()
    }

    private fun publish() { mutableState.value = mutableState.value.copy(measurement = workflow.state) }
    override fun onCleared() = acquisition.stop()
}
