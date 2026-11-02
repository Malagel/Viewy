package com.malagel.viewy.settings

import android.content.Context
import android.icu.util.LocaleData
import android.icu.util.ULocale
import android.os.Build
import android.util.Log
import androidx.core.app.LocaleManagerCompat
import com.malagel.viewy.BuildConfig
import java.util.Locale

internal enum class RegionalMeasurementSystem { METRIC, US, UK }

private fun effectiveRegion(locale: Locale): String {
    val override = locale.getUnicodeLocaleType("rg")
    return (if (override?.matches(Regex("[a-zA-Z]{2}[zZ]{4}")) == true)
        override.take(2) else locale.country).uppercase(Locale.ROOT)
}

/** No language inference: a missing/unknown region cannot confidently imply units. */
internal fun regionalMeasurementUnits(
    locale: Locale,
    icuSystem: (Locale) -> RegionalMeasurementSystem?,
): MeasurementUnits {
    val region = effectiveRegion(locale)
    if (region !in Locale.getISOCountries()) return MeasurementUnits.METRIC
    // Android regional preferences may carry an explicit measurement-system override.
    when (locale.getUnicodeLocaleType("ms")) {
        "metric", "uksystem" -> return MeasurementUnits.METRIC
        "ussystem" -> return MeasurementUnits.IMPERIAL
    }
    if (region == "GB") return MeasurementUnits.METRIC
    val system = try {
        // ICU also understands numeric rg regions, which this policy rejects. Give
        // ICU exactly the validated region, with no second interpretation of rg.
        val resolved = if (locale.getUnicodeLocaleType("rg") == null) locale else
            Locale.Builder().setLocale(locale).setRegion(region)
                .setUnicodeLocaleKeyword("rg", null).build()
        icuSystem(resolved)
    } catch (_: RuntimeException) { null }
    return if (system == RegionalMeasurementSystem.US) MeasurementUnits.IMPERIAL else MeasurementUnits.METRIC
}

internal fun androidMeasurementSystem(locale: Locale): RegionalMeasurementSystem? =
    when (LocaleData.getMeasurementSystem(ULocale.forLocale(locale))) {
        LocaleData.MeasurementSystem.US -> RegionalMeasurementSystem.US
        LocaleData.MeasurementSystem.UK -> RegionalMeasurementSystem.UK
        LocaleData.MeasurementSystem.SI -> RegionalMeasurementSystem.METRIC
        else -> null
    }

internal fun deviceMeasurementUnits(context: Context): MeasurementUnits {
    val locales = LocaleManagerCompat.getSystemLocales(context.applicationContext)
    val locale = locales[0] ?: Locale.ROOT
    var icuResult = "not consulted (region/override policy)"
    val units = regionalMeasurementUnits(locale) {
        try {
            androidMeasurementSystem(it).also { result -> icuResult = result?.name ?: "unknown" }
        } catch (failure: RuntimeException) {
            icuResult = "failed (${failure.javaClass.simpleName})"
            throw failure
        }
    }
    if (BuildConfig.DEBUG) Log.d("RegionalUnits",
        "detection: sdk=${Build.VERSION.SDK_INT}, systemLocales=${locales.toLanguageTags()}, " +
            "primary=${locale.toLanguageTag()}, country=${locale.country}, " +
            "rg=${locale.getUnicodeLocaleType("rg")}, effectiveRegion=${effectiveRegion(locale)}, " +
            "ms=${locale.getUnicodeLocaleType("ms")}, icu=$icuResult, detected=$units")
    return units
}
