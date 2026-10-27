package com.malagel.viewy.ui

import java.text.DecimalFormatSymbols
import java.util.Locale

/** Compact presentation only: retain the six-significant-digit cap and one fractional digit. */
internal fun heightLabelText(value: Double, locale: Locale): String {
    val formatted = String.format(locale, "%.6g", value)
    val exponentAt = formatted.indexOf('e', ignoreCase = true).takeIf { it >= 0 } ?: formatted.length
    val mantissa = formatted.substring(0, exponentAt)
    val symbols = DecimalFormatSymbols.getInstance(locale)
    val compact = if (symbols.decimalSeparator in mantissa) mantissa.trimEnd(symbols.zeroDigit) else mantissa
    val decimal = when {
        compact.endsWith(symbols.decimalSeparator) -> compact + symbols.zeroDigit
        symbols.decimalSeparator !in compact -> compact + symbols.decimalSeparator + symbols.zeroDigit
        else -> compact
    }
    return decimal + formatted.substring(exponentAt)
}

/** Editable values keep full precision, no grouping or exponent, using a supported decimal separator. */
internal fun heightInputText(value: Double, locale: Locale): String {
    val separator = DecimalFormatSymbols.getInstance(locale).decimalSeparator.takeIf { it == ',' } ?: '.'
    return java.math.BigDecimal.valueOf(value).stripTrailingZeros().toPlainString().replace('.', separator)
}

internal fun distanceText(meters: Double, unit: com.malagel.viewy.measurement.HeightUnit, locale: Locale): String =
    "${String.format(locale, "%.2g", unit.fromMeters(meters))} ${unit.label}"
