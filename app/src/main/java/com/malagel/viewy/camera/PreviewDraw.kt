package com.malagel.viewy.camera

import android.os.Build
import android.view.View
import android.view.ViewTreeObserver

/** Wait for a draw submitted after startup readiness; never use a fixed startup delay. */
internal fun afterPreviewDraw(view: View, onDrawn: () -> Unit): () -> Unit {
    val observer = view.viewTreeObserver
    var cancelled = false
    val complete = Runnable { if (!cancelled) onDrawn() }
    if (Build.VERSION.SDK_INT >= 29 && view.isHardwareAccelerated) {
        // The callback may originate off main; camera state and ownership are main-thread only.
        val committed = Runnable { view.post(complete) }
        observer.registerFrameCommitCallback(committed)
        view.invalidate()
        return {
            cancelled = true
            if (observer.isAlive) observer.unregisterFrameCommitCallback(committed)
            view.removeCallbacks(complete)
        }
    }
    val listener = object : ViewTreeObserver.OnDrawListener {
        private var posted = false
        override fun onDraw() {
            if (posted) return
            posted = true
            // Removal/publication must happen outside the draw dispatch.
            view.post {
                if (observer.isAlive) observer.removeOnDrawListener(this)
                complete.run()
            }
        }
    }
    observer.addOnDrawListener(listener)
    view.invalidate()
    return {
        cancelled = true
        if (observer.isAlive) observer.removeOnDrawListener(listener)
    }
}
