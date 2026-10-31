package com.malagel.viewy.measurement

enum class HeightUnit(val metersPerUnit: Double, val label: String) {
    METERS(1.0, "m"), FEET(0.3048, "ft");

    fun fromMeters(meters: Double): Double = meters / metersPerUnit
}

/** Accept a decimal point or comma, never grouping separators, exponents or special values. */
fun heightInMeters(text: String, unit: HeightUnit): Double? {
    val decimal = text.trim().replace(',', '.')
    if (!decimal.matches(Regex("[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+"))) return null
    return decimal.toDoubleOrNull()?.times(unit.metersPerUnit)?.takeIf {
        isSupportedHeight(it)
    }
}

internal fun isSupportedHeight(meters: Double): Boolean = meters.isFinite() && meters > 0.0 &&
    HeightUnit.entries.all { it.fromMeters(meters).isFinite() }
