package com.malagel.viewy.settings

import android.content.res.Resources
import android.icu.util.LocaleData
import android.icu.util.ULocale
import java.util.Locale

internal enum class RegionalMeasurementSystem { METRIC, US, UK }

/** No language inference: a missing/unknown region cannot confidently imply units. */
internal fun regionalMeasurementUnits(
    locale: Locale,
    icuSystem: (Locale) -> RegionalMeasurementSystem?,
): MeasurementUnits {
    val regionOverride = locale.getUnicodeLocaleType("rg")
    val region = (if (regionOverride?.matches(Regex("[a-zA-Z]{2}[zZ]{4}")) == true)
        regionOverride.take(2) else locale.country).uppercase(Locale.ROOT)
    if (region !in Locale.getISOCountries()) return MeasurementUnits.METRIC
    // Android regional preferences may carry an explicit measurement-system override.
    when (locale.getUnicodeLocaleType("ms")) {
        "metric", "uksystem" -> return MeasurementUnits.METRIC
        "ussystem" -> return MeasurementUnits.IMPERIAL
    }
    if (region == "GB") return MeasurementUnits.METRIC
    val system = try { icuSystem(locale) } catch (_: RuntimeException) { null }
    return if (system == RegionalMeasurementSystem.US) MeasurementUnits.IMPERIAL else MeasurementUnits.METRIC
}

internal fun deviceMeasurementUnits(): MeasurementUnits {
    // System resources bypass Android/AppCompat's application-language override.
    val configuration = Resources.getSystem().configuration
    val locale = if (configuration.locales.isEmpty) null else configuration.locales[0]
    return regionalMeasurementUnits(locale ?: Locale.ROOT) { deviceLocale ->
        when (LocaleData.getMeasurementSystem(ULocale.forLocale(deviceLocale))) {
            LocaleData.MeasurementSystem.US -> RegionalMeasurementSystem.US
            LocaleData.MeasurementSystem.UK -> RegionalMeasurementSystem.UK
            LocaleData.MeasurementSystem.SI -> RegionalMeasurementSystem.METRIC
            else -> null
        }
    }
}
