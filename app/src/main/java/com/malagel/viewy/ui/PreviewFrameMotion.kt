package com.malagel.viewy.ui

internal enum class PreviewFrameExit { FADE, WORKSPACE, RESULT, NAVIGATION }
internal enum class PreviewFrameAction { HOLD, FADE, CLEAR }

/** Decide when decorative pixels may leave; this never gates camera release or binding. */
internal fun previewFrameAction(
    exit: PreviewFrameExit, workspace: WorkspacePresentation, resultActive: Boolean, resultCovered: Boolean,
    resumed: Boolean = true, surfaceVisible: Boolean = true,
): PreviewFrameAction {
    // Incoming Home publishes an inactive workspace before the outgoing destination finishes.
    // Its decorative pixels still belong to that outgoing destination, never to acquisition.
    if (!resumed || (!surfaceVisible && exit != PreviewFrameExit.NAVIGATION)) return PreviewFrameAction.CLEAR
    return when (exit) {
        PreviewFrameExit.FADE -> PreviewFrameAction.FADE
        // The destination host already fades/moves the entire measurement, including this image.
        PreviewFrameExit.NAVIGATION -> PreviewFrameAction.HOLD
        PreviewFrameExit.WORKSPACE -> when {
            !workspace.holdSnapshot -> PreviewFrameAction.FADE
            workspace.opaqueCover -> PreviewFrameAction.CLEAR
            else -> PreviewFrameAction.HOLD
        }
        PreviewFrameExit.RESULT -> when {
            !resultActive -> PreviewFrameAction.FADE
            resultCovered -> PreviewFrameAction.CLEAR
            else -> PreviewFrameAction.HOLD
        }
    }
}
