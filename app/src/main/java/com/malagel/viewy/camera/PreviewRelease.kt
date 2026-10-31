package com.malagel.viewy.camera

/** Resource suspension and Compose disposal can release the same owner, in either order. */
internal class PreviewRelease(private val onRelease: (animate: Boolean) -> Unit) {
    var released = false
        private set

    fun release(animate: Boolean) {
        if (released) return
        // Revoke first: platform cleanup can synchronously publish state or trigger another release.
        released = true
        onRelease(animate)
    }
}
