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
