package com.malagel.viewy.ui

internal enum class CameraPermissionAction { NONE, REQUEST, OPEN_SETTINGS }

/** Permission requests are pending until Android returns a result, never denied on launch. */
internal data class CameraPermissionRecovery(
    val granted: Boolean,
    val requesting: Boolean = false,
    val needsSettings: Boolean = false,
) {
    val action get() = when {
        granted || requesting -> CameraPermissionAction.NONE
        needsSettings -> CameraPermissionAction.OPEN_SETTINGS
        else -> CameraPermissionAction.REQUEST
    }

    fun request() = if (action == CameraPermissionAction.REQUEST) copy(requesting = true) else this

    fun result(granted: Boolean, canRequestAgain: Boolean) = copy(
        granted = granted, requesting = false, needsSettings = !granted && !canRequestAgain,
    )

    fun refresh(granted: Boolean) = copy(granted = granted, needsSettings = !granted && needsSettings)
}
