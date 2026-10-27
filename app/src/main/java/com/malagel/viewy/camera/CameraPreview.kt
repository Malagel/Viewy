package com.malagel.viewy.camera

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Handler
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
    @StringRes val messageRes: Int = R.string.camera_starting,
    val mapping: CameraDirectionMapping? = null,
    val diagnostic: String = "",
)

/** Preview only. One explicit rear camera/physical output, no images, zoom gestures or storage. */
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
    private var description = CameraPreviewState()
    private var open = false
    private var streaming = false
    private var failed = false
    private val timeout = Runnable {
        if (!disposed && !description.ready) fail(R.string.camera_timeout, "Preview startup timeout")
    }
    private val streamObserver = Observer<PreviewView.StreamState> {
        streaming = it == PreviewView.StreamState.STREAMING
        publish()
    }
    private val cameraObserver = Observer<CameraState> {
        open = it.type == CameraState.Type.OPEN
        if (it.error != null) {
            fail(R.string.camera_unavailable, "Camera error ${it.error?.code}")
        } else publish()
    }
    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) = Unit
        override fun onDisplayRemoved(displayId: Int) = Unit
        override fun onDisplayChanged(displayId: Int) {
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
                    fail(R.string.camera_start_failed, error.javaClass.simpleName)
                }
            }, ContextCompat.getMainExecutor(context))
        } catch (error: Exception) {
            fail(R.string.camera_initialization_failed, error.javaClass.simpleName)
        }
    }

    private fun bind(cameraProvider: ProcessCameraProvider) {
        if (disposed || failed) return
        cameraInfo?.cameraState?.removeObserver(cameraObserver)
        preview?.let { cameraProvider.unbind(it) }
        open = false
        streaming = false
        failed = false
        try {
            val rear = CameraSelector.DEFAULT_BACK_CAMERA.filter(cameraProvider.availableCameraInfos).firstOrNull()
                ?: return fail(R.string.camera_no_rear, "No rear camera")
            val logicalId = Camera2CameraInfo.from(rear).cameraId
            val manager = context.getSystemService(CameraManager::class.java)
            val logical = manager.getCameraCharacteristics(logicalId)
            val physicalIds = if (Build.VERSION.SDK_INT >= 28) logical.physicalCameraIds else emptySet()
            // A logical camera may switch lenses even at 1×. Pin its largest-area rear imager.
            // If no physical metadata can be read, fail rather than silently allow lens switching.
            val physical = physicalIds.mapNotNull { id ->
                runCatching { id to manager.getCameraCharacteristics(id) }.getOrNull()
            }.filter { (_, c) -> c[CameraCharacteristics.LENS_FACING] == CameraCharacteristics.LENS_FACING_BACK }
                .sortedBy { it.first }.maxByOrNull { (_, c) ->
                    c[CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE]?.let { it.width * it.height } ?: 0f
                }
            if (physicalIds.isNotEmpty() && physical == null) {
                return fail(R.string.camera_no_consistent_lens, "Cannot inspect physical rear cameras")
            }
            val selectedId = physical?.first ?: logicalId
            val characteristics = physical?.second ?: logical
            val reference = if (Build.VERSION.SDK_INT >= 28) characteristics[CameraCharacteristics.LENS_POSE_REFERENCE] else null
            val mapping = CameraDirectionMapping.forRearCamera(
                "rear-$logicalId/physical-$selectedId", reference, characteristics[CameraCharacteristics.LENS_POSE_ROTATION],
            )
            val builder = Preview.Builder()
                .setTargetRotation(view.display?.rotation ?: android.view.Surface.ROTATION_0)
                .setResolutionSelector(ResolutionSelector.Builder()
                    .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY).build())
                .setPreviewStabilizationEnabled(false)
            val interop = Camera2Interop.Extender(builder)
            if (Build.VERSION.SDK_INT >= 28 && physical != null) interop.setPhysicalCameraId(selectedId)
            interop.setCaptureRequestOption(CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE, CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE_OFF)
            interop.setCaptureRequestOption(CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE, CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE_OFF)
            // No unsupported principal-point correction: preview processing/crop/distortion and
            // focus-dependent intrinsics have not been established as a calibrated pixel model.
            description = CameraPreviewState(mapping = mapping, diagnostic =
                "Camera $logicalId · physical $selectedId · fixed output, default zoom\n" +
                    "Pose reference: $reference · pose: ${characteristics[CameraCharacteristics.LENS_POSE_ROTATION]?.joinToString()}\n" +
                    "Image rotation: ${characteristics[CameraCharacteristics.SENSOR_ORIENTATION]}° (not optical pose)\n" +
                    "Intrinsics: ${characteristics[CameraCharacteristics.LENS_INTRINSIC_CALIBRATION]?.joinToString() ?: "absent"}\n" +
                    "${mapping.evidence}\nCentered fill crop; EIS/OIS requested OFF; continuous default autofocus")
            onState(description)
            val useCase = builder.build()
            preview = useCase
            useCase.setSurfaceProvider(view.surfaceProvider)
            val selector = CameraSelector.Builder().addCameraFilter { infos ->
                infos.filter { Camera2CameraInfo.from(it).cameraId == logicalId }
            }.build()
            val camera = cameraProvider.bindToLifecycle(owner, selector, useCase)
            cameraInfo = camera.cameraInfo
            camera.cameraInfo.cameraState.observe(owner, cameraObserver)
            scheduleStartupTimeout()
        } catch (error: Exception) {
            fail(R.string.camera_configuration_failed, error.javaClass.simpleName)
        }
    }

    private fun publish() {
        if (disposed || failed) return
        val ready = open && streaming
        if (ready) handler.removeCallbacks(timeout)
        description = description.copy(ready = ready, messageRes = if (ready) R.string.camera_ready else R.string.camera_waiting)
        onState(description)
    }

    private fun fail(@StringRes messageRes: Int, diagnostic: String) {
        if (disposed) return
        failed = true
        handler.removeCallbacks(timeout)
        cameraInfo?.cameraState?.removeObserver(cameraObserver)
        preview?.let { useCase -> runCatching { provider?.unbind(useCase) } }
        description = description.copy(ready = false, messageRes = messageRes, diagnostic = "${description.diagnostic}\n$diagnostic")
        onState(description)
    }

    // Provider initialization can finish while STOPPED. CameraX waits for STARTED
    // before opening the camera; that intentional pause must not consume its timeout.
    private fun scheduleStartupTimeout() {
        handler.removeCallbacks(timeout)
        if (!disposed && !failed && !description.ready &&
            owner.lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)
        ) handler.postDelayed(timeout, 12_000)
    }

    override fun onStop(owner: LifecycleOwner) {
        streaming = false
        publish()
        handler.removeCallbacks(timeout)
    }

    override fun onStart(owner: LifecycleOwner) {
        scheduleStartupTimeout()
    }

    fun close() {
        disposed = true
        handler.removeCallbacks(timeout)
        displayManager.unregisterDisplayListener(displayListener)
        owner.lifecycle.removeObserver(this)
        view.previewStreamState.removeObserver(streamObserver)
        cameraInfo?.cameraState?.removeObserver(cameraObserver)
        preview?.let { provider?.unbind(it) }
    }
}
