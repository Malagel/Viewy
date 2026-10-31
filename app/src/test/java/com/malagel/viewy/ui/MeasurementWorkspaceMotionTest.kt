package com.malagel.viewy.ui

import com.malagel.viewy.measurement.HeightEditor
import com.malagel.viewy.measurement.HeightUnit
import com.malagel.viewy.measurement.KnownHeightWorkflow
import org.junit.Assert.*
import org.junit.Test

class MeasurementWorkspaceMotionTest {
    @Test fun initialConfirmationGoesDirectlyFromKeypadToCameraWithoutAnOutgoingPrompt() {
        val workflow = KnownHeightWorkflow()
        val editor = HeightEditor(workflow)
        fun destination() = workspaceCover(workflow.state.heightMeters != null, editor.draft != null)
        assertEquals(WorkspaceCover.HEIGHT_PROMPT, destination())
        editor.open(HeightUnit.METERS)
        assertEquals(WorkspaceCover.HEIGHT_EDITOR, destination())
        editor.key('2')
        assertTrue(editor.confirm())
        assertEquals(WorkspaceCover.NONE, destination())
        val exit = workspacePresentation(WorkspaceCover.HEIGHT_EDITOR, destination(), false)
        assertTrue(exit.editorBlocks)
        assertFalse(exit.editorInteractive)
        assertTrue(exit.retainCamera)
        assertFalse(exit.opaqueCover)
        assertFalse(exit.holdSnapshot)
        assertFalse(workspacePresentation(destination(), destination(), false).editorBlocks)
    }

    @Test fun cancellationRetainsTheExistingHeightAndRestoresTheCorrectUnderlyingDestination() {
        for (saved in listOf<Double?>(null, 2.0)) {
            val workflow = KnownHeightWorkflow()
            saved?.let { workflow.setHeight(it) }
            val editor = HeightEditor(workflow)
            editor.open(HeightUnit.METERS)
            editor.key('9')
            editor.cancel()
            assertEquals(saved, workflow.state.heightMeters)
            assertEquals(if (saved == null) WorkspaceCover.HEIGHT_PROMPT else WorkspaceCover.NONE,
                workspaceCover(workflow.state.heightMeters != null, editor.draft != null))
        }
    }

    @Test fun promptReturnRetainsCameraPresentationOnlyUntilTheCoverIsOpaque() {
        val returning = workspacePresentation(WorkspaceCover.NONE, WorkspaceCover.HEIGHT_PROMPT, false)
        assertTrue(returning.moving)
        assertTrue(returning.retainCamera)
        assertTrue(returning.holdSnapshot)
        assertFalse(returning.opaqueCover)
        val done = workspacePresentation(WorkspaceCover.HEIGHT_PROMPT, WorkspaceCover.HEIGHT_PROMPT, false)
        assertFalse(done.moving)
        assertFalse(done.retainCamera)
        assertTrue(done.opaqueCover)
    }

    @Test fun interruptedAndRestoredCoversKeepEditorInputAndAcquisitionGatesConsistent() {
        for (from in WorkspaceCover.entries) for (to in WorkspaceCover.entries) {
            val moving = workspacePresentation(from, to, true)
            assertTrue(moving.moving)
            assertFalse(moving.editorInteractive)
            assertFalse(moving.opaqueCover)
            assertTrue(moving.retainCamera)
            assertTrue(moving.editorBlocks)
        }
        val restored = workspacePresentation(WorkspaceCover.HEIGHT_EDITOR, WorkspaceCover.HEIGHT_EDITOR, false)
        assertTrue(restored.editorBlocks)
        assertTrue(restored.editorInteractive)
        assertTrue(restored.opaqueCover)
        val reversed = workspacePresentation(WorkspaceCover.NONE, WorkspaceCover.NONE, true)
        assertTrue(reversed.moving)
        assertTrue(reversed.editorBlocks)
        assertFalse(reversed.editorInteractive)
        assertFalse(reversed.holdSnapshot) // A reversed keypad entrance must release its held image.
    }
}
