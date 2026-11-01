package com.malagel.viewy.camera

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CaptureResult
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.TotalCaptureResult
import android.hardware.display.DisplayManager
import android.os.Handler
import android.os.SystemClock
import android.os.Looper
import androidx.annotation.StringRes
import com.malagel.viewy.R
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.Camera2Interop
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.CameraState
import androidx.camera.core.Preview
import androidx.camera.core.CameraSelector
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.Observer

data class CameraPreviewState(
    val ready: Boolean = false,
    val zoom: PreviewZoom = PreviewZoom(),
    @StringRes val messageRes: Int = R.string.camera_starting,
    val mapping: CameraDirectionMapping? = null,
    val logicalCameraId: String? = null,
    val physicalCameraId: String? = null,
    val open: Boolean = false,
    val streaming: Boolean = false,
    val failed: Boolean = false,
    val retryable: Boolean = true,
    val errorDetail: String? = null,
)

/** Logical rear-camera preview with CameraX zoom and observed physical-lens mapping. */
@androidx.annotation.OptIn(ExperimentalCamera2Interop::class)
class CameraPreview(
    private val context: Context,
    private val owner: LifecycleOwner,
    private val view: PreviewView,
    private val onState: (CameraPreviewState) -> Unit,
) : DefaultLifecycleObserver {
    private val handler = Handler(Looper.getMainLooper())
    private val displayManager = context.getSystemService(DisplayManager::class.java)
    private var disposed = false
    private var provider: ProcessCameraProvider? = null
    private var preview: Preview? = null
    private var cameraInfo: androidx.camera.core.CameraInfo? = null
    private var cameraControl: androidx.camera.core.CameraControl? = null
    private val zoom = PreviewZoomSession()
    private var bindingEpoch = 0L
    private var identityRequired = false
    private var identityVerified = false
    private var lastFrameNumber = -1L
    private var acknowledgedZoomToken: Long? = null
    private var zoomTimeout: Runnable? = null
    private var zoomSettling: Runnable? = null
    private var description = CameraPreviewState()
    private var hasOpened = false
    private var open = false
    private var streaming = false
    private var failed = false
    private val presentation = PreviewPresentationGate()
    private var cancelPreviewDraw: (() -> Unit)? = null
    private val watchdog = PreviewWatchdog()
    private val timeout = Runnable {
        if (!disposed && !failed && owner.lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED) &&
            watchdog.expired(SystemClock.elapsedRealtime())
        ) fail(if (watchdog.hasStreamed) R.string.camera_interrupted else R.string.camera_timeout, null)
    }
    private val streamObserver = Observer<PreviewView.StreamState> {
        streaming = it == PreviewView.StreamState.STREAMING
        publish()
    }
    private val cameraObserver = Observer<CameraState> {
        val wasOpen = open
        open = it.type == CameraState.Type.OPEN
        if (it.error != null) {
            val code = it.error!!.code
            val message = when (code) {
                CameraState.ERROR_STREAM_CONFIG -> R.string.camera_configuration_failed
                CameraState.ERROR_CAMERA_DISABLED, CameraState.ERROR_DO_NOT_DISTURB_MODE_ENABLED,
                CameraState.ERROR_CAMERA_FATAL_ERROR -> R.string.camera_system_unavailable
                else -> R.string.camera_unavailable
            }
            fail(message,
                code.toString(), retryable = code == CameraState.ERROR_CAMERA_IN_USE ||
                    code == CameraState.ERROR_MAX_CAMERAS_IN_USE || code == CameraState.ERROR_OTHER_RECOVERABLE_ERROR)
        } else {
            if (wasOpen && !open) {
                // CameraX resets controls on closure, even if the use case remains bound.
                // Revoke acknowledgements immediately; the readiness watchdog owns recovery.
                zoom.request(zoom.state.ratio)
                identityVerified = false
                lastFrameNumber = -1L
                acknowledgedZoomToken = null
                zoomTimeout?.let(handler::removeCallbacks)
                zoomSettling?.let(handler::removeCallbacks)
            }
            if (open && !wasOpen && hasOpened) initializeZoom()
            if (open) hasOpened = true
            publish()
        }
    }
    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) = Unit
        override fun onDisplayRemoved(displayId: Int) = Unit
        override fun onDisplayChanged(displayId: Int) {
            if (disposed || failed) return
            if (view.display?.displayId == displayId && preview?.targetRotation != view.display?.rotation) {
                // Even a 180° change may not recreate the Activity. Rebind and invalidate the pair.
                description = description.copy(ready = false, messageRes = R.string.camera_display_changed)
                onState(description)
                provider?.let { bind(it) }
            }
        }
    }

    init {
        view.implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        view.scaleType = PreviewView.ScaleType.FILL_CENTER
        owner.lifecycle.addObserver(this)
        view.previewStreamState.observe(owner, streamObserver)
        displayManager.registerDisplayListener(displayListener, handler)
        scheduleStartupTimeout()
        try {
            val future = ProcessCameraProvider.getInstance(context)
            future.addListener({
                if (!disposed && !failed) try {
                    provider = future.get()
                    // Wait until attached: display rotation may be unavailable during composition.
                    view.post { if (!disposed && !failed) bind(provider!!) }
                } catch (error: Exception) {
                    fail(R.string.camera_start_failed, error.javaClass.simpleName, retryable = cameraExceptionRetryable(error))
                }
            }, ContextCompat.getMainExecutor(context))
        } catch (error: Exception) {
            fail(R.string.camera_initialization_failed, error.javaClass.simpleName, retryable = cameraExceptionRetryable(error))
        }
    }

    private fun bind(cameraProvider: ProcessCameraProvider) {
        if (disposed || failed) return
        clearZoom()
        cameraInfo?.cameraState?.removeObserver(cameraObserver)
        preview?.let { cameraProvider.unbind(it) }
        open = false
        hasOpened = false
        streaming = false
        failed = false
        try {
            val rear = CameraSelector.DEFAULT_BACK_CAMERA.filter(cameraProvider.availableCameraInfos).firstOrNull()
                ?: return fail(R.string.camera_no_rear, null, retryable = false)
            val logicalId = Camera2CameraInfo.from(rear).cameraId
            val manager = context.getSystemService(CameraManager::class.java)
            val logical = manager.getCameraCharacteristics(logicalId)
            val physicalIds = logical.physicalCameraIds
            // Keep the logical output unpinned; physical metadata supplies aiming axes.
            val physicalCandidates = physicalIds.mapNotNull { id ->
                runCatching { id to manager.getCameraCharacteristics(id) }.getOrNull()
            }.filter { (_, c) -> c[CameraCharacteristics.LENS_FACING] == CameraCharacteristics.LENS_FACING_BACK }
                .sortedBy { it.first }
            val physical = physicalCandidates.maxByOrNull { (_, c) ->
                    c[CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE]?.let { it.width * it.height } ?: 0f
                }
            if (physicalIds.isNotEmpty() && physical == null) {
                return fail(R.string.camera_no_consistent_lens, null, retryable = false)
            }
            val selectedId = physical?.first ?: logicalId
            val characteristics = physical?.second ?: logical
            val reference = characteristics[CameraCharacteristics.LENS_POSE_REFERENCE]
            val mapping = CameraDirectionMapping.forRearCamera(
                "rear-$logicalId/physical-$selectedId", reference, characteristics[CameraCharacteristics.LENS_POSE_ROTATION],
            )
            val builder = Preview.Builder()
                .setTargetRotation(view.display?.rotation ?: android.view.Surface.ROTATION_0)
                .setResolutionSelector(ResolutionSelector.Builder()
                    .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY).build())
                .setPreviewStabilizationEnabled(false)
            val interop = Camera2Interop.Extender(builder)
            identityRequired = physicalIds.isNotEmpty()
            if (identityRequired) {
                val mappings = physicalCandidates.associate { (id, c) ->
                    id to CameraDirectionMapping.forRearCamera("rear-$logicalId/physical-$id",
                        c[CameraCharacteristics.LENS_POSE_REFERENCE],
                        c[CameraCharacteristics.LENS_POSE_ROTATION])
                }
                val epoch = bindingEpoch
                interop.setSessionCaptureCallback(object : CameraCaptureSession.CaptureCallback() {
                    override fun onCaptureCompleted(session: CameraCaptureSession, request: CaptureRequest, result: TotalCaptureResult) {
                        val id = result[CaptureResult.LOGICAL_MULTI_CAMERA_ACTIVE_PHYSICAL_ID]
                        val frame = result.frameNumber
                        // Observe metadata only; CameraX owns requests and session lifecycle.
                        handler.post {
                            if (disposed || failed || epoch != bindingEpoch || !open || frame <= lastFrameNumber) return@post
                            lastFrameNumber = frame
                            val currentMapping = mappings[id]
                                ?: return@post fail(R.string.camera_no_consistent_lens, "Unavailable/unknown active rear camera: $id", retryable = false)
                            identityVerified = true
                            val changed = id != description.physicalCameraId
                            if (changed) {
                                description = description.copy(physicalCameraId = id, mapping = currentMapping)
                                zoom.restartSettling()
                                zoomSettling?.let(handler::removeCallbacks)
                                acknowledgedZoomToken?.takeIf(zoom::owns)?.let(::scheduleZoomSettling)
                            }
                            if (changed || !description.ready) publish()
                        }
                    }
                })
            }
            interop.setCaptureRequestOption(CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE, CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE_OFF)
            interop.setCaptureRequestOption(CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE, CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE_OFF)
            // No unsupported principal-point correction: preview processing/crop/distortion and
            // focus-dependent intrinsics have not been established as a calibrated pixel model.
            description = CameraPreviewState(mapping = mapping, logicalCameraId = logicalId, physicalCameraId = physical?.first)
            onState(description)
            scheduleStartupTimeout()
            val useCase = builder.build()
            preview = useCase
            useCase.setSurfaceProvider(view.surfaceProvider)
            val selector = CameraSelector.Builder().addCameraFilter { infos ->
                infos.filter { Camera2CameraInfo.from(it).cameraId == logicalId }
            }.build()
            val camera = cameraProvider.bindToLifecycle(owner, selector, useCase)
            cameraInfo = camera.cameraInfo
            cameraControl = camera.cameraControl
            initializeZoom()
            if (disposed || failed) return
            camera.cameraInfo.cameraState.observe(owner, cameraObserver)
        } catch (error: Exception) {
            fail(R.string.camera_configuration_failed, error.javaClass.simpleName,
                retryable = cameraExceptionRetryable(error, configuring = true))
        }
    }

    private fun publish() {
        if (disposed || failed) return
        val drawToken = presentation.update(open && streaming && (!identityRequired || identityVerified), zoom.state.settled)
        if (!presentation.awaitingFrame) {
            cancelPreviewDraw?.invoke()
            cancelPreviewDraw = null
        }
        drawToken?.let { token ->
            cancelPreviewDraw = afterPreviewDraw(view) {
                if (!disposed && !failed && presentation.presented(token)) {
                    cancelPreviewDraw = null
                    publish()
                }
            }
        }
        val ready = presentation.ready
        scheduleWatchdog(ready)
        description = description.copy(zoom = zoom.state, ready = ready, open = open, streaming = streaming, messageRes = if (ready) R.string.camera_ready else R.string.camera_waiting)
        onState(description)
    }

    private fun fail(@StringRes messageRes: Int, diagnostic: String?, retryable: Boolean = true) {
        if (disposed || failed) return
        failed = true
        clearZoom()
        handler.removeCallbacks(timeout)
        cameraInfo?.cameraState?.removeObserver(cameraObserver)
        preview?.let { useCase -> runCatching { provider?.unbind(useCase) } }
        description = description.copy(zoom = zoom.state, ready = false, open = false, streaming = false, failed = true, retryable = retryable, messageRes = messageRes, errorDetail = diagnostic)
        onState(description)
    }

    private fun initializeZoom() {
        val limits = cameraInfo?.zoomState?.value
        val initial = limits?.let { zoom.reset(it.minZoomRatio, it.maxZoomRatio) }
        if (initial == null) {
            fail(R.string.camera_configuration_failed, "Invalid CameraX zoom capabilities", retryable = false)
        } else applyZoom(initial)
    }

    fun requestZoom(ratio: Float) {
        if (disposed || failed || !open || !streaming) return
        if (!ratio.isFinite() || !zoom.state.available) return
        val bounded = ratio.coerceIn(zoom.state.minimum, zoom.state.maximum)
        if (bounded == zoom.state.ratio) return
        zoom.request(bounded)?.let(::applyZoom)
    }

    private fun applyZoom(token: Long) {
        acknowledgedZoomToken = null
        zoomTimeout?.let(handler::removeCallbacks)
        zoomSettling?.let(handler::removeCallbacks)
        publish() // Block capture before submitting platform work.
        val deadline = Runnable {
            if (zoom.owns(token) && !disposed && !failed) fail(R.string.camera_timeout, "Zoom acknowledgement timed out")
        }
        zoomTimeout = deadline
        handler.postDelayed(deadline, if (open && streaming) 4_000L else 12_000L)
        try {
            val future = cameraControl!!.setZoomRatio(zoom.state.ratio)
            future.addListener({
                if (!disposed && !failed && zoom.owns(token)) {
                    try {
                        future.get() // LiveData reports the target immediately; only this future acknowledges application.
                        acknowledgedZoomToken = token
                        handler.removeCallbacks(deadline)
                        scheduleZoomSettling(token)
                    } catch (error: Exception) {
                        fail(R.string.camera_interrupted, "Zoom: " + error.javaClass.simpleName)
                    }
                }
            }, ContextCompat.getMainExecutor(context))
        } catch (error: Exception) {
            fail(R.string.camera_interrupted, "Zoom: " + error.javaClass.simpleName)
        }
    }

    private fun scheduleZoomSettling(token: Long) {
        if (!zoom.applied(token, SystemClock.elapsedRealtime())) return
        zoomSettling?.let(handler::removeCallbacks)
        lateinit var settle: Runnable
        settle = Runnable {
            if (disposed || failed || !zoom.owns(token)) return@Runnable
            if (zoom.settle(token, SystemClock.elapsedRealtime())) publish()
            else handler.postDelayed(settle, 1L)
        }
        zoomSettling = settle
        handler.postDelayed(settle, PreviewZoomSession.SETTLE_MILLIS)
    }

    private fun clearZoom() {
        bindingEpoch++
        presentation.reset()
        cancelPreviewDraw?.invoke()
        cancelPreviewDraw = null
        identityVerified = false
        lastFrameNumber = -1L
        acknowledgedZoomToken = null
        zoom.close() // Revoke queued futures and drain callbacks before releasing the camera.
        cameraControl = null
        zoomTimeout?.let(handler::removeCallbacks)
        zoomSettling?.let(handler::removeCallbacks)
        zoomTimeout = null
        zoomSettling = null
    }

    // Provider initialization can finish while STOPPED. CameraX waits for STARTED
    // before opening the camera; that intentional pause must not consume its timeout.
    private fun scheduleStartupTimeout() {
        watchdog.reset(SystemClock.elapsedRealtime())
        scheduleWatchdog(description.ready)
    }

    private fun scheduleWatchdog(ready: Boolean) {
        handler.removeCallbacks(timeout)
        if (disposed || failed || !owner.lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)) return
        val now = SystemClock.elapsedRealtime()
        watchdog.readiness(ready, now)?.let { deadline ->
            handler.postDelayed(timeout, (deadline - now).coerceAtLeast(0L))
        }
    }

    override fun onStop(owner: LifecycleOwner) {
        streaming = false
        publish()
        handler.removeCallbacks(timeout)
    }

    override fun onStart(owner: LifecycleOwner) {
        scheduleStartupTimeout()
    }

    // Foreground loss must not wait for Compose disposal or a presentation animation.
    override fun onPause(owner: LifecycleOwner) = close()

    override fun onDestroy(owner: LifecycleOwner) = close()

    fun close() {
        if (disposed) return
        disposed = true
        clearZoom()
        handler.removeCallbacks(timeout)
        displayManager.unregisterDisplayListener(displayListener)
        owner.lifecycle.removeObserver(this)
        view.previewStreamState.removeObserver(streamObserver)
        cameraInfo?.cameraState?.removeObserver(cameraObserver)
        preview?.let { provider?.unbind(it) }
    }
}
