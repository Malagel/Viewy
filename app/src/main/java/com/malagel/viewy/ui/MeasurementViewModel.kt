package com.malagel.viewy.ui

import android.app.Application
import android.content.Context
import android.os.Build
import android.view.accessibility.AccessibilityManager
import android.os.SystemClock
import android.os.Handler
import android.os.Looper
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
    val cameraFailureVisible: Boolean = false,
    val guidance: Int? = null,
    val alignmentCue: AlignmentCueState = AlignmentCueState(),
) {
    val canCapture: Boolean get() = measurement.canCapture && camera.zoom.settled
    val recoveryVisible: Boolean get() = requiresCamera && heightConfigured && !measurement.completed &&
        (if (!visibility.permissionGranted) viewfinderInteractive
        else acquisitionEligible && (cameraFailureVisible || (camera.ready && !acquisition.running)))
    val canOpenSettings: Boolean get() = !diagnosticsOpen && !selection.isOpen && !tutorial.selectorExiting && tutorial.card == null && tutorial.pendingMethod == null
    val canOpenHelp: Boolean get() = selection.activeMethod != null && !selection.isOpen && !tutorial.selectorExiting && tutorial.card == null && tutorial.pendingMethod == null
    val requiresCamera: Boolean get() = selection.activeMethod?.requiresCamera == true
    val requiresOrientation: Boolean get() = selection.activeMethod?.requiresOrientation == true
    val heightConfigured: Boolean get() = measurement.heightMeters != null
    val viewfinderInteractive: Boolean get() = resumed && visibility.surfaceVisible && visibility.windowFocused &&
        !visibility.covered && !visibility.heightEditing && !diagnosticsOpen && !selection.isOpen &&
        !tutorial.selectorExiting && tutorial.card == null && tutorial.pendingMethod == null
    val acquisitionEligible: Boolean get() = requiresCamera && heightConfigured && !measurement.completed &&
        visibility.permissionGranted && viewfinderInteractive
    val previewVisible: Boolean get() = acquisitionEligible && camera.ready
    val previewHeightEditVisible: Boolean get() = requiresCamera && heightConfigured && viewfinderInteractive &&
        (previewVisible || recoveryVisible)
}

/** Owns ONE foreground acquisition; diagnostics only observes its snapshots. Everything runs on main. */
class MeasurementViewModel(application: Application) : AndroidViewModel(application) {
    private val workflow = KnownHeightWorkflow()
    private val alignmentGuidance = AlignmentGuidance()
    private val guidance = MeasurementGuidance {
        if (Build.VERSION.SDK_INT >= 29) application.getSystemService(AccessibilityManager::class.java)
            ?.getRecommendedTimeoutMillis(5_000, AccessibilityManager.FLAG_CONTENT_TEXT)?.toLong() ?: 5_000L
        else 5_000L
    }
    private val tutorials = TutorialCoordinator(
        LocalTutorialSeenStore(application.getSharedPreferences(LocalTutorialSeenStore.FILE_NAME, Context.MODE_PRIVATE)),
        methodTutorials.keys,
    )
    private val mutableState = MutableStateFlow(MeasurementUiState())
    val state = mutableState.asStateFlow()
    private val acquisition = AndroidOrientationAcquisition(application) { value ->
        workflow.update(value.observation, value.referenceFrameId, value.running, SystemClock.elapsedRealtimeNanos())
        val next = mutableState.value.copy(measurement = workflow.state, acquisition = value)
        publishGuidance(next)
    }

    private val recoveryHandler = Handler(Looper.getMainLooper())
    private val resources = MeasurementResources(workflow, acquisition::start, acquisition::stop,
        schedule = { delay, action ->
            val callback = Runnable { action() }
            recoveryHandler.postDelayed(callback, delay)
            val cancel: () -> Unit = { recoveryHandler.removeCallbacks(callback) }
            cancel
        }, onChanged = ::publish)

    fun resume() { mutableState.value = mutableState.value.copy(resumed = true); reconcile() }
    fun pause() { mutableState.value = mutableState.value.copy(resumed = false); reconcile() }

    fun visibilityChanged(visibility: MeasurementVisibility) {
        if (visibility == mutableState.value.visibility) return
        mutableState.value = mutableState.value.copy(visibility = visibility)
        reconcile()
    }

    fun startPreview(requestZoom: (Float) -> Unit = {}, release: (Boolean) -> Unit): Long? = resources.startPreview(requestZoom, release).also { publish() }

    fun previewChanged(token: Long, camera: CameraPreviewState) {
        if (resources.previewChanged(token, camera)) publish()
    }

    fun requestZoom(ratio: Float) = resources.requestZoom(ratio)
    fun scaleZoom(factor: Float) {
        if (factor.isFinite() && factor > 0f) requestZoom(resources.camera.zoom.ratio * factor)
    }

    fun stopPreview(token: Long) { resources.stopPreview(token); publish() }
    fun retryCamera() { resources.retryCamera(); publish() }

    private fun reconcile() {
        val state = mutableState.value
        resources.reconcile(state.acquisitionEligible, state.requiresOrientation,
            animateRelease = state.resumed && state.visibility.surfaceVisible && state.visibility.windowFocused && !state.measurement.completed)
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
        if (!mutableState.value.acquisitionEligible || !resources.camera.zoom.settled) return
        val captured = acquisition.capture()
        workflow.capture(captured, SystemClock.elapsedRealtimeNanos())
        publish()
        reconcile()
    }

    fun restartOrientation() {
        if (!mutableState.value.acquisitionEligible) return
        workflow.restart(MeasurementNotice.SENSOR_RESTARTED.takeIf { workflow.state.top != null && !workflow.state.completed })
        resources.restartOrientation()
        reconcile()
    }

    private fun publish() {
        val next = mutableState.value.copy(
            measurement = workflow.state, camera = resources.camera, previewEpoch = resources.previewEpoch,
            cameraFailureVisible = resources.cameraFailureVisible,
        )
        publishGuidance(next)
    }

    private fun publishGuidance(next: MeasurementUiState) {
        val now = SystemClock.elapsedRealtimeNanos()
        mutableState.value = next.copy(
            guidance = guidance.update(next, now / 1_000_000L),
            alignmentCue = alignmentGuidance.update(next, now),
        )
    }
    override fun onCleared() {
        resources.reconcile(false, false, animateRelease = false)
        acquisition.stop()
    }
}
