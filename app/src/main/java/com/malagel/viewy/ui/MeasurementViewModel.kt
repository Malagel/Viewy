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
import com.malagel.viewy.measurement.HeightDraft
import com.malagel.viewy.measurement.HeightEditor
import com.malagel.viewy.measurement.HeightUnit
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
    val heightEditor: HeightDraft? = null,
    val tutorial: TutorialState = TutorialState(),
    val visibility: MeasurementVisibility = MeasurementVisibility(),
    val resumed: Boolean = false,
    val previewEpoch: Long = 0,
    /** Changes only for a new method activation; scopes presentation retained by Compose. */
    val methodActivationId: Long = 0,
    val cameraFailureVisible: Boolean = false,
    val guidance: Int? = null,
    val alignmentCue: AlignmentCueState = AlignmentCueState(),
) {
    internal fun navigateTo(
        next: MeasurementSelection, nextTutorial: TutorialState, nextMeasurement: MeasurementState,
    ): MeasurementUiState {
        val enteringMethod = next.activeMethod != null && next.activeMethod != selection.activeMethod
        return copy(selection = next, tutorial = nextTutorial, measurement = nextMeasurement, heightEditor = null,
            methodActivationId = methodActivationId + if (enteringMethod) 1 else 0)
    }

    val canCapture: Boolean get() = heightEditor == null && measurement.canCapture && camera.zoom.settled
    internal val backRestartsMeasurement: Boolean get() = selection.activeMethod != null && measurement.completed
    internal val backReturnsToHeightPrompt: Boolean get() = requiresCamera && heightConfigured && !measurement.completed
    val orientationFailureVisible: Boolean get() = acquisitionEligible && camera.ready &&
        !acquisition.running && acquisition.failure != null
    val recoveryVisible: Boolean get() = requiresCamera && heightConfigured && !measurement.completed &&
        (if (!visibility.permissionGranted) viewfinderInteractive
        else acquisitionEligible && (cameraFailureVisible || orientationFailureVisible))
    val canOpenSettings: Boolean get() = !visibility.covered && !diagnosticsOpen && !tutorial.methodEntering && tutorial.card == null && tutorial.pendingMethod == null
    val canOpenHelp: Boolean get() = !visibility.covered && selection.activeMethod != null && !selection.isOpen && !tutorial.methodEntering && tutorial.card == null && tutorial.pendingMethod == null
    val canEditHeight: Boolean get() = heightEditor != null && resumed && visibility.surfaceVisible &&
        visibility.windowFocused && !visibility.covered && !diagnosticsOpen && !selection.isOpen &&
        !tutorial.methodEntering && tutorial.card == null && tutorial.pendingMethod == null
    val requiresCamera: Boolean get() = selection.activeMethod?.requiresCamera == true
    val requiresOrientation: Boolean get() = selection.activeMethod?.requiresOrientation == true
    val heightConfigured: Boolean get() = measurement.heightMeters != null
    val viewfinderInteractive: Boolean get() = resumed && visibility.surfaceVisible && visibility.windowFocused &&
        !visibility.covered && heightEditor == null && !visibility.heightEditing && !diagnosticsOpen && !selection.isOpen &&
        !tutorial.methodEntering && tutorial.card == null && tutorial.pendingMethod == null
    val acquisitionEligible: Boolean get() = requiresCamera && heightConfigured && !measurement.completed &&
        visibility.permissionGranted && viewfinderInteractive
    val previewVisible: Boolean get() = acquisitionEligible && camera.ready
    val previewHeightEditVisible: Boolean get() = requiresCamera && heightConfigured && viewfinderInteractive &&
        (previewVisible || recoveryVisible)
}

/** Owns ONE foreground acquisition; diagnostics only observes its snapshots. Everything runs on main. */
class MeasurementViewModel(application: Application) : AndroidViewModel(application) {
    private val workflow = KnownHeightWorkflow()
    private val heightEditor = HeightEditor(workflow)
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
        val previous = mutableState.value.selection
        if (next == previous) return
        val leavingMethod = previous.activeMethod != null && previous.activeMethod != next.activeMethod
        heightEditor.cancel()
        tutorials.selectionChanged(next, activated)
        // Publish navigation before synchronous release callbacks can observe or revive the old method.
        if (leavingMethod) workflow.clear()
        mutableState.value = mutableState.value.navigateTo(next, tutorials.state, workflow.state)
        if (leavingMethod) {
            resources.clear()
            acquisition.clear()
        }
        reconcile()
    }

    /** Cover acquisition atomically, preserving the editor and saved measurement until Settings returns. */
    fun prepareSettings(): Boolean {
        val state = mutableState.value
        if (!state.canOpenSettings) return false
        mutableState.value = state.copy(visibility = state.visibility.copy(covered = true))
        reconcile()
        return true
    }

    fun openModes() { if (mutableState.value.canOpenSettings) updateSelection(mutableState.value.selection.openModes()) }
    fun backSelection() = updateSelection(mutableState.value.selection.back())
    /** Called only by the uncovered destination's Android Back handlers. */
    fun systemBack() {
        val state = mutableState.value
        when {
            state.backRestartsMeasurement -> restart()
            state.backReturnsToHeightPrompt -> returnToHeightPrompt()
            else -> backSelection()
        }
    }
    fun browseMode(mode: MeasurementMode) = updateSelection(mutableState.value.selection.browse(mode))

    fun methodEntered(token: Long) { tutorials.methodEntered(token); publishTutorial() }
    fun help(longPress: Boolean, diagnosticsEnabled: Boolean) {
        when (helpAction(mutableState.value, longPress, diagnosticsEnabled)) {
            HelpAction.TUTORIAL -> {
                tutorials.openManually()
                publishTutorial()
            }
            HelpAction.DIAGNOSTICS -> {
                mutableState.value = mutableState.value.withDiagnostics(open = true, enabled = diagnosticsEnabled)
                reconcile()
            }
            HelpAction.NONE -> Unit
        }
    }
    fun dismissDiagnostics() { mutableState.value = mutableState.value.withDiagnostics(open = false); reconcile() }
    fun dismissTutorial(cardId: Long) { tutorials.dismiss(cardId); publishTutorial() }
    fun openTutorialDocumentation(cardId: Long) { tutorials.openDocumentation(cardId); publishTutorial() }
    fun backTutorial(cardId: Long) { tutorials.back(cardId); publishTutorial() }
    fun changeTutorialPage(cardId: Long, page: TutorialPage) { tutorials.changePage(cardId, page); publishTutorial() }
    private fun publishTutorial() { mutableState.value = mutableState.value.copy(tutorial = tutorials.state, heightEditor = heightEditor.draft); reconcile() }

    fun selectMethod(method: MeasurementMethod) {
        if (mutableState.value.diagnosticsOpen) return
        val previous = mutableState.value.selection
        val next = previous.activate(method)
        if (next == previous) return
        updateSelection(next, activated = method)
    }

    fun openHeightEditor(unit: HeightUnit) {
        val state = mutableState.value
        if (!state.viewfinderInteractive || state.selection.activeMethod == null || heightEditor.draft != null) return
        heightEditor.open(unit)
        // Publish the gate before synchronous resource release can call back.
        mutableState.value = state.copy(heightEditor = heightEditor.draft,
            visibility = state.visibility.copy(heightEditing = true))
        reconcile()
    }

    fun heightKey(character: Char) { if (!mutableState.value.canEditHeight) return; heightEditor.key(character); publish() }
    fun heightBackspace() { if (!mutableState.value.canEditHeight) return; heightEditor.backspace(); publish() }
    fun switchHeightUnit() { if (!mutableState.value.canEditHeight) return; heightEditor.switchUnit(); publish() }
    fun heightPreferenceChanged(unit: HeightUnit) { heightEditor.preferenceChanged(unit); publish() }
    fun cancelHeightEditor() { heightEditor.cancel(); publish(); reconcile() }

    private fun returnToHeightPrompt() {
        heightEditor.cancel()
        workflow.clear()
        // Publish the height gate before synchronous resource-release callbacks.
        publish()
        val state = mutableState.value
        resources.clear(animateRelease = state.resumed && state.visibility.surfaceVisible && state.visibility.windowFocused)
        acquisition.clear()
        reconcile()
    }
    fun confirmHeightEditor() { if (!mutableState.value.canEditHeight) return; heightEditor.confirm(); publish(); reconcile() }

    fun restart() { heightEditor.cancel(); workflow.restart(); publish(); reconcile() }
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
            measurement = workflow.state, heightEditor = heightEditor.draft, camera = resources.camera, previewEpoch = resources.previewEpoch,
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
