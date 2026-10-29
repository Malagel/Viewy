package com.malagel.viewy.ui

import java.text.DecimalFormatSymbols
import java.text.NumberFormat
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import java.util.Locale
import com.malagel.viewy.measurement.HeightUnit

/** Compact presentation only: retain the six-significant-digit cap and one fractional digit. */
internal fun heightLabelText(value: Double, locale: Locale): String {
    val rounded = BigDecimal.valueOf(value).round(MathContext(6, RoundingMode.HALF_UP)).stripTrailingZeros()
    return decimalText(rounded, locale, minimumFraction = 1)
}

/** Editable values keep full precision, no grouping or exponent, using a supported decimal separator. */
internal fun heightInputText(value: Double, locale: Locale): String {
    val separator = DecimalFormatSymbols.getInstance(locale).decimalSeparator.takeIf { it == ',' } ?: '.'
    return java.math.BigDecimal.valueOf(value).stripTrailingZeros().toPlainString().replace('.', separator)
}

internal fun distanceText(meters: Double, unit: com.malagel.viewy.measurement.HeightUnit, locale: Locale): String =
    ResultNumberFormatter(meters, unit, locale).let { "${it.finalText} ${it.unitLabel}" }

/** Decimal-only display; conversion can exceed Double range without changing stored meters. */
internal fun distanceNumberText(meters: Double, unit: com.malagel.viewy.measurement.HeightUnit, locale: Locale): String =
    measurementNumberText(displayDistance(meters, unit), locale)

private fun displayDistance(meters: Double, unit: com.malagel.viewy.measurement.HeightUnit): BigDecimal =
    BigDecimal.valueOf(meters).divide(BigDecimal.valueOf(unit.metersPerUnit), MathContext.DECIMAL128)

/** Result distances alone scale up; object heights and editable values retain their base unit. */
internal enum class ResultDistanceUnit(val metersPerUnit: Double, val label: String) {
    METERS(1.0, "m"), KILOMETERS(1000.0, "km"), FEET(0.3048, "ft"), MILES(1609.344, "mi");

    companion object {
        fun forDistance(meters: Double, preference: HeightUnit): ResultDistanceUnit = when (preference) {
            HeightUnit.METERS -> if (meters >= 10_000.0) KILOMETERS else METERS
            HeightUnit.FEET -> if (meters >= MILES.metersPerUnit) MILES else FEET
        }
    }
}

/** Counter and final result fill the same decimal slot, with at least two fractional digits. */
internal class ResultNumberFormatter(meters: Double, unit: com.malagel.viewy.measurement.HeightUnit, private val locale: Locale) {
    private val displayUnit = ResultDistanceUnit.forDistance(meters, unit)
    val unitLabel = displayUnit.label
    private val value = BigDecimal.valueOf(meters).divide(BigDecimal.valueOf(displayUnit.metersPerUnit), MathContext.DECIMAL128)
    private val fractionDigits = maxOf(2, roundedMeasurement(value).scale())
    val finalText = decimalText(value.setScale(fractionDigits, RoundingMode.HALF_UP), locale, fractionDigits)
    val layoutText = finalText

    fun frameText(fraction: Float): String = when {
        fraction >= 1f -> finalText
        fraction <= 0f -> decimalText(BigDecimal.ZERO, locale, fractionDigits)
        else -> {
            val frame = value.multiply(BigDecimal.valueOf(fraction.toDouble())).setScale(fractionDigits, RoundingMode.HALF_UP)
            decimalText(frame, locale, fractionDigits)
        }
    }
}

internal fun measurementNumberText(value: Double, locale: Locale): String =
    measurementNumberText(BigDecimal.valueOf(value), locale)

/**
 * HALF_UP to hundredths for ordinary values, two significant digits below 0.01.
 * A non-integer retains at least one decimal even when rounding reaches a whole
 * number (730.001 -> 730.0). Large integers stay grouped plain decimals.
 * These are display digits, never an accuracy claim or an edit to stored evidence.
 */
private fun measurementNumberText(value: BigDecimal, locale: Locale): String {
    val nonInteger = value.stripTrailingZeros().scale() > 0
    val rounded = roundedMeasurement(value)
    return decimalText(rounded.stripTrailingZeros(), locale, if (nonInteger) 1 else 0)
}

private fun roundedMeasurement(value: BigDecimal): BigDecimal =
    if (value.signum() != 0 && value.abs() < BigDecimal("0.01")) value.round(MathContext(2, RoundingMode.HALF_UP))
    else value.setScale(2, RoundingMode.HALF_UP)

private fun decimalText(value: BigDecimal, locale: Locale, minimumFraction: Int): String =
    NumberFormat.getNumberInstance(locale).apply {
        roundingMode = RoundingMode.HALF_UP
        maximumIntegerDigits = 400
        minimumFractionDigits = minimumFraction
        maximumFractionDigits = maxOf(minimumFraction, value.scale())
    }.format(value)
