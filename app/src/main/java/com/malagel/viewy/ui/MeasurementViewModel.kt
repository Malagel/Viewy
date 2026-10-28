package com.malagel.viewy.ui

import android.app.Application
import android.content.Context
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import com.malagel.viewy.camera.CameraPreviewState
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
    val visibility: MeasurementVisibility = MeasurementVisibility(),
    val resumed: Boolean = false,
    val previewEpoch: Long = 0,
) {
    val canOpenSettings: Boolean get() = !diagnosticsOpen && !selection.isOpen && !tutorial.selectorExiting && tutorial.card == null && tutorial.pendingMethod == null
    val canOpenHelp: Boolean get() = selection.activeMethod != null && !selection.isOpen && !tutorial.selectorExiting && tutorial.card == null && tutorial.pendingMethod == null
    val requiresCamera: Boolean get() = selection.activeMethod?.requiresCamera == true
    val requiresOrientation: Boolean get() = selection.activeMethod?.requiresOrientation == true
    val heightConfigured: Boolean get() = measurement.heightMeters != null
    val acquisitionEligible: Boolean get() = requiresCamera && heightConfigured && !measurement.completed &&
        resumed && visibility.surfaceVisible && visibility.windowFocused && visibility.permissionGranted &&
        !visibility.covered && !visibility.heightEditing && !diagnosticsOpen && !selection.isOpen &&
        !tutorial.selectorExiting && tutorial.card == null && tutorial.pendingMethod == null
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
    private val acquisition = AndroidOrientationAcquisition(application) { value ->
        workflow.update(value.observation, value.referenceFrameId, value.running, SystemClock.elapsedRealtimeNanos())
        mutableState.value = mutableState.value.copy(measurement = workflow.state, acquisition = value)
    }

    private val resources = MeasurementResources(workflow, acquisition::start, acquisition::stop)

    fun resume() { mutableState.value = mutableState.value.copy(resumed = true); reconcile() }
    fun pause() { mutableState.value = mutableState.value.copy(resumed = false); reconcile() }

    fun visibilityChanged(visibility: MeasurementVisibility) {
        if (visibility == mutableState.value.visibility) return
        mutableState.value = mutableState.value.copy(visibility = visibility)
        reconcile()
    }

    fun startPreview(release: (Boolean) -> Unit): Long? = resources.startPreview(release).also { publish() }

    fun previewChanged(token: Long, camera: CameraPreviewState) {
        if (resources.previewChanged(token, camera)) publish()
    }

    fun stopPreview(token: Long) { resources.stopPreview(token); publish() }

    private fun reconcile() {
        val state = mutableState.value
        resources.reconcile(state.acquisitionEligible, state.requiresOrientation,
            animateRelease = state.resumed && state.visibility.surfaceVisible && state.visibility.windowFocused)
        publish()
    }

    private fun updateSelection(next: MeasurementSelection, activated: MeasurementMethod? = null) {
        tutorials.selectionChanged(next, activated)
        mutableState.value = mutableState.value.copy(selection = next, tutorial = tutorials.state)
        reconcile()
    }

    fun openModes() { if (mutableState.value.canOpenSettings) updateSelection(mutableState.value.selection.openModes()) }
    fun backSelection() = updateSelection(mutableState.value.selection.back())
    fun dismissSelection() = updateSelection(mutableState.value.selection.dismiss())
    fun browseMode(mode: MeasurementMode) = updateSelection(mutableState.value.selection.browse(mode))

    fun selectionHidden(token: Long) { tutorials.selectorHidden(token); publishTutorial() }
    fun help(longPress: Boolean, diagnosticsEnabled: Boolean) {
        when (helpAction(mutableState.value, longPress, diagnosticsEnabled)) {
            HelpAction.TUTORIAL -> { tutorials.openManually(); publishTutorial() }
            HelpAction.DIAGNOSTICS -> { mutableState.value = mutableState.value.withDiagnostics(open = true, enabled = diagnosticsEnabled); reconcile() }
            HelpAction.NONE -> Unit
        }
    }
    fun dismissDiagnostics() { mutableState.value = mutableState.value.withDiagnostics(open = false); reconcile() }
    fun dismissTutorial(cardId: Long) { tutorials.dismiss(cardId); publishTutorial() }
    fun changeTutorialPage(cardId: Long, page: TutorialPage) { tutorials.changePage(cardId, page); publishTutorial() }
    private fun publishTutorial() { mutableState.value = mutableState.value.copy(tutorial = tutorials.state); reconcile() }

    fun selectMethod(method: MeasurementMethod) {
        if (mutableState.value.diagnosticsOpen) return
        val previous = mutableState.value.selection
        val next = previous.activate(method)
        if (next.activeMethod != previous.activeMethod) {
            // A different workflow must never inherit captures or a hardware session.
            workflow.restart()
            resources.reconcile(false, false, animateRelease = false)
        }
        updateSelection(next, activated = method)
    }

    fun setHeight(meters: Double) { workflow.setHeight(meters); publish(); reconcile() }
    fun restart() { workflow.restart(); publish(); reconcile() }
    fun recaptureBottom() { workflow.recaptureBottom(); publish(); reconcile() }
    fun capture() {
        // Freshness is recomputed NOW, not trusted from the last 100 ms UI snapshot.
        if (!mutableState.value.acquisitionEligible) return
        val captured = acquisition.capture()
        workflow.capture(captured, SystemClock.elapsedRealtimeNanos())
        publish()
        reconcile()
    }

    fun restartOrientation() {
        if (!mutableState.value.acquisitionEligible) return
        workflow.restart(MeasurementNotice.SENSOR_RESTARTED)
        resources.restartOrientation()
        reconcile()
    }

    private fun publish() { mutableState.value = mutableState.value.copy(
        measurement = workflow.state, camera = resources.camera, previewEpoch = resources.previewEpoch,
    ) }
    override fun onCleared() {
        resources.reconcile(false, false, animateRelease = false)
        acquisition.stop()
    }
}
