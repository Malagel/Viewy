package com.malagel.viewy.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.widget.FrameLayout
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.malagel.viewy.camera.CameraPreview
import com.malagel.viewy.camera.PreviewRelease

internal const val CAMERA_FADE_MILLIS = 225

// Identity matters when a second release reuses the same bitmap during a rapid reversal.
private class PreviewSnapshot(val bitmap: Bitmap, val alpha: Float, val exit: PreviewFrameExit)
private data class PreviewFrame(val bitmap: Bitmap, val alpha: Float)

/** A stable native host owns live pixels, the waiting cover and retained pixels together. */
private class PreviewSurface(context: Context) : FrameLayout(context) {
    private val cover = View(context)
    private var color = 0
    private var live = false
    private var fraction = 0f
    private var snapshot: PreviewSnapshot? = null
    private var touchListener: OnTouchListener? = null
    var preview: PreviewView? = null
        private set

    init {
        addView(cover, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }

    fun color(value: Int) {
        color = value
        setBackgroundColor(value)
        preview?.setBackgroundColor(value)
        cover.setBackgroundColor(value)
    }

    fun touch(listener: OnTouchListener?) {
        touchListener = listener
        preview?.setOnTouchListener(listener)
    }

    /** Only a real new binding replaces the camera child; release never replaces the host. */
    fun newPreview(): PreviewView {
        live = false
        fraction = 0f
        cover.alpha = 1f
        preview?.let { it.setOnTouchListener(null); removeView(it) }
        val next = PreviewView(context)
        next.implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        next.setBackgroundColor(color)
        next.setOnTouchListener(touchListener)
        addView(next, 0, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        preview = next
        live = true
        return next
    }

    fun reveal(source: PreviewView?, value: Float) {
        // A canceled coroutine from the previous binding cannot uncover a released/new child.
        if (!live || source !== preview) return
        fraction = value.coerceIn(0f, 1f)
        cover.alpha = 1f - fraction
    }

    fun captureFrame(): PreviewFrame? {
        if (live && fraction > 0f) {
            runCatching { preview?.bitmap }.getOrNull()?.let { return PreviewFrame(it, fraction) }
        }
        // During fast overlay reversals a previous decorative frame may still be fading.
        return snapshot?.let { frame ->
            val alpha = (foreground?.alpha ?: 0) / 255f
            frame.takeIf { alpha > 0f }?.let { PreviewFrame(it.bitmap, alpha) }
        }
    }

    fun freeze(frame: PreviewSnapshot?) {
        snapshot = frame
        foreground = frame?.let {
            BitmapDrawable(resources, it.bitmap).apply {
                gravity = Gravity.FILL
                alpha = (it.alpha.coerceIn(0f, 1f) * 255).toInt()
            }
        }
        // The snapshot is installed BEFORE concealing/unbinding the live texture. It stays
        // in this same native render node until its own fade or the incoming cover completes.
        live = false
        fraction = 0f
        cover.alpha = 1f
    }

    fun opacity(frame: PreviewSnapshot, value: Float) {
        if (snapshot === frame) foreground?.alpha = (value.coerceIn(0f, 1f) * 255).toInt()
    }

    fun clear(frame: PreviewSnapshot) {
        if (snapshot !== frame) return
        snapshot = null
        foreground = null
    }
}

/** Camera ownership may change epochs; the visible AndroidView host does not. */
@Composable
internal fun LivePreview(
    model: MeasurementViewModel, state: MeasurementUiState, waitingColor: Color,
    workspace: WorkspacePresentation, resultCovered: Boolean,
) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val surface = remember(context) { PreviewSurface(context).apply { color(waitingColor.toArgb()) } }
    var frozen by remember(surface) { mutableStateOf<PreviewSnapshot?>(null) }
    val reveal = remember(surface, state.previewEpoch) { Animatable(0f) }
    LaunchedEffect(surface, state.previewEpoch, state.previewVisible) {
        val source = surface.preview
        if (state.previewVisible) {
            reveal.animateTo(1f, tween(CAMERA_FADE_MILLIS)) { surface.reveal(source, value) }
        } else {
            reveal.snapTo(0f)
            surface.reveal(source, 0f)
        }
    }
    DisposableEffect(surface, model) {
        val detector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                model.scaleZoom(detector.scaleFactor)
                return true
            }
        })
        surface.touch { touched, event ->
            detector.onTouchEvent(event)
            if (event.actionMasked == MotionEvent.ACTION_UP) touched.performClick()
            true
        }
        onDispose { surface.touch(null) }
    }
    key(surface) {
        AndroidView(factory = { surface }, update = { it.color(waitingColor.toArgb()) },
            modifier = Modifier.fillMaxSize()
                .focusProperties { canFocus = state.previewVisible }
                .then(if (state.previewVisible) Modifier else Modifier.clearAndSetSemantics {}))
    }
    frozen?.let { frame ->
        val opacity = remember(frame) { Animatable(frame.alpha) }
        val action = previewFrameAction(frame.exit, workspace, state.measurement.completed, resultCovered,
            state.resumed, state.visibility.surfaceVisible)
        LaunchedEffect(frame, action) {
            when (action) {
                PreviewFrameAction.HOLD -> return@LaunchedEffect
                PreviewFrameAction.FADE -> opacity.animateTo(0f, tween(CAMERA_FADE_MILLIS)) { surface.opacity(frame, value) }
                PreviewFrameAction.CLEAR -> Unit
            }
            surface.clear(frame)
            if (frozen === frame) frozen = null
        }
    }
    DisposableEffect(surface, owner, model, state.acquisitionEligible, state.previewEpoch) {
        var preview: CameraPreview? = null
        val release = PreviewRelease { animate ->
            val destination = model.state.value
            val foreground = destination.resumed && destination.visibility.surfaceVisible && destination.visibility.windowFocused
            val frame = if (animate || (foreground && destination.measurement.completed)) surface.captureFrame() else null
            val exit = when {
                destination.selection.activeMethod == null -> PreviewFrameExit.NAVIGATION
                destination.measurement.completed -> PreviewFrameExit.RESULT
                workspaceCover(destination.heightConfigured, destination.heightEditor != null) != WorkspaceCover.NONE -> PreviewFrameExit.WORKSPACE
                else -> PreviewFrameExit.FADE
            }
            val retained = frame?.let { PreviewSnapshot(it.bitmap, it.alpha, exit) }
            surface.freeze(retained)
            frozen = retained
            preview?.close()
            preview = null
        }
        val token = if (state.acquisitionEligible) {
            model.startPreview(requestZoom = { preview?.requestZoom(it) }, release = release::release)
        } else null
        preview = token?.let {
            val view = surface.newPreview()
            CameraPreview(context, owner, view) { camera -> model.previewChanged(it, camera) }
        }
        if (release.released) { preview?.close(); preview = null }
        onDispose {
            if (token != null) {
                release.release(animate = false)
                model.stopPreview(token)
            }
        }
    }
}
