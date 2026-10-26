package com.malagel.viewy.domain

enum class MeasurementMode(val id: String) {
    LONG_DISTANCE("long_distance"),
}

enum class MeasurementMethod(val id: String, val mode: MeasurementMode) {
    KNOWN_OBJECT_HEIGHT("known_object_height", MeasurementMode.LONG_DISTANCE),
}
