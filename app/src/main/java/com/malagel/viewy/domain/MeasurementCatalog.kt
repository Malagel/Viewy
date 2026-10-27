package com.malagel.viewy.domain

enum class MeasurementMode(val id: String) {
    LONG_DISTANCE("long_distance"),
    SHORT_DISTANCE("short_distance"),
    HEIGHT("height"),
    CALIBRATION("calibration"),
    ;

    val methods: List<MeasurementMethod> get() = MeasurementMethod.entries.filter { it.mode == this }
    val available: Boolean get() = methods.isNotEmpty()
}

// Only implemented methods belong here; unavailable modes have no method identities.
enum class MeasurementMethod(
    val id: String,
    val mode: MeasurementMode,
    val requiresCamera: Boolean,
    val requiresOrientation: Boolean,
) {
    KNOWN_OBJECT_HEIGHT("known_object_height", MeasurementMode.LONG_DISTANCE, true, true),
}
