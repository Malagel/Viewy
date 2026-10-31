package com.malagel.viewy.ui

/** The camera/result layer stays mounted; only these opaque workspace covers change. */
internal enum class WorkspaceCover { NONE, HEIGHT_PROMPT, HEIGHT_EDITOR }

internal fun workspaceCover(heightConfigured: Boolean, editing: Boolean): WorkspaceCover = when {
    editing -> WorkspaceCover.HEIGHT_EDITOR
    !heightConfigured -> WorkspaceCover.HEIGHT_PROMPT
    else -> WorkspaceCover.NONE
}

internal data class WorkspacePresentation(
    val moving: Boolean,
    val editorBlocks: Boolean,
    val editorInteractive: Boolean,
    val opaqueCover: Boolean,
    val holdSnapshot: Boolean,
    val retainCamera: Boolean,
)

internal fun workspacePresentation(
    current: WorkspaceCover, target: WorkspaceCover, running: Boolean,
): WorkspacePresentation {
    val moving = current != target || running
    return WorkspacePresentation(
        moving = moving,
        // A reversed editor entrance can still be exiting while both states are NONE.
        editorBlocks = moving || target == WorkspaceCover.HEIGHT_EDITOR,
        editorInteractive = !moving && target == WorkspaceCover.HEIGHT_EDITOR,
        opaqueCover = !moving && target != WorkspaceCover.NONE,
        holdSnapshot = target != WorkspaceCover.NONE,
        retainCamera = moving || target != WorkspaceCover.HEIGHT_PROMPT,
    )
}
