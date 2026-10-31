package com.malagel.viewy.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class PreviewFrameMotionTest {
    private val camera = workspacePresentation(WorkspaceCover.NONE, WorkspaceCover.NONE, false)

    @Test fun workspaceRetainsItsFrameUntilOpaqueAndReversalFadesItInsteadOfLeavingItStuck() {
        for (cover in listOf(WorkspaceCover.HEIGHT_EDITOR, WorkspaceCover.HEIGHT_PROMPT)) {
            val entering = workspacePresentation(WorkspaceCover.NONE, cover, false)
            assertEquals(PreviewFrameAction.HOLD, previewFrameAction(PreviewFrameExit.WORKSPACE, entering, false, false))
            val opaque = workspacePresentation(cover, cover, false)
            assertEquals(PreviewFrameAction.CLEAR, previewFrameAction(PreviewFrameExit.WORKSPACE, opaque, false, false))
            val reversing = workspacePresentation(WorkspaceCover.NONE, WorkspaceCover.NONE, true)
            assertEquals(PreviewFrameAction.FADE, previewFrameAction(PreviewFrameExit.WORKSPACE, reversing, false, false))
        }
    }

    @Test fun completionKeepsNativePixelsUntilTheResultSurfaceActuallyCoversThem() {
        assertEquals(PreviewFrameAction.HOLD, previewFrameAction(PreviewFrameExit.RESULT, camera, true, false))
        assertEquals(PreviewFrameAction.CLEAR, previewFrameAction(PreviewFrameExit.RESULT, camera, true, true))
        // Restart/Home during result entry cannot leave its image over the next destination.
        assertEquals(PreviewFrameAction.FADE, previewFrameAction(PreviewFrameExit.RESULT, camera, false, false))
        assertEquals(PreviewFrameAction.FADE, previewFrameAction(PreviewFrameExit.RESULT, camera, false, true))
    }

    @Test fun ordinarySuspensionsFadeWhileHomeKeepsTheImageForItsWholeDestinationExit() {
        for (cover in WorkspaceCover.entries) {
            val workspace = workspacePresentation(cover, cover, false)
            for (active in listOf(false, true)) for (covered in listOf(false, true)) {
                assertEquals(PreviewFrameAction.FADE, previewFrameAction(PreviewFrameExit.FADE, workspace, active, covered))
                assertEquals(PreviewFrameAction.HOLD, previewFrameAction(PreviewFrameExit.NAVIGATION, workspace, active, covered))
            }
        }
        assertEquals(PreviewFrameAction.FADE, previewFrameAction(PreviewFrameExit.WORKSPACE, camera, false, false))
    }

    @Test fun incomingHomeInactivityCannotClearTheOutgoingImageButBackgroundingAlwaysDoes() {
        assertEquals(PreviewFrameAction.HOLD,
            previewFrameAction(PreviewFrameExit.NAVIGATION, camera, false, false, resumed = true, surfaceVisible = false))
        for (exit in PreviewFrameExit.entries) {
            assertEquals(PreviewFrameAction.CLEAR,
                previewFrameAction(exit, camera, true, false, resumed = false, surfaceVisible = true))
            assertEquals(PreviewFrameAction.CLEAR,
                previewFrameAction(exit, camera, true, true, resumed = false, surfaceVisible = false))
            if (exit != PreviewFrameExit.NAVIGATION) {
                assertEquals(PreviewFrameAction.CLEAR,
                    previewFrameAction(exit, camera, true, false, resumed = true, surfaceVisible = false))
            }
        }
    }
}
