package com.malagel.viewy.settings

import android.content.res.Configuration
import android.os.LocaleList
import androidx.core.app.LocaleManagerCompat
import androidx.core.os.LocaleListCompat
import androidx.test.platform.app.InstrumentationRegistry
import java.util.Locale
import org.junit.Assert.*
import org.junit.Test

/** Runs against the OS's ICU data, rather than the JVM tests' injected systems. */
class RegionalMeasurementUnitsAndroidTest {
    @Test fun androidIcuResolvesRegionalDefaultsAndOverrides() {
        for ((tag, expected) in listOf(
            "es-CL" to MeasurementUnits.METRIC,
            "en-CL" to MeasurementUnits.METRIC,
            "es-ES" to MeasurementUnits.METRIC,
            "en-US" to MeasurementUnits.IMPERIAL,
            "es-US" to MeasurementUnits.IMPERIAL,
            "en-GB" to MeasurementUnits.METRIC,
            "en-CA" to MeasurementUnits.METRIC,
            "es-CL-u-ms-metric" to MeasurementUnits.METRIC,
            "es-CL-u-ms-ussystem" to MeasurementUnits.IMPERIAL,
            "en-US-u-ms-metric" to MeasurementUnits.METRIC,
            "en-GB-u-ms-ussystem" to MeasurementUnits.IMPERIAL,
            "es-CL-u-rg-uszzzz" to MeasurementUnits.IMPERIAL,
            "en-US-u-rg-clzzzz" to MeasurementUnits.METRIC,
            "en-US-u-rg-419zzzz" to MeasurementUnits.IMPERIAL,
            "en-US-u-rg-invalid" to MeasurementUnits.IMPERIAL,
            "en-US-u-rg-zzzzzz" to MeasurementUnits.METRIC,
            "en-ZZ" to MeasurementUnits.METRIC,
            "en" to MeasurementUnits.METRIC,
        )) {
            assertEquals(tag, expected,
                regionalMeasurementUnits(Locale.forLanguageTag(tag), ::androidMeasurementSystem))
        }
        assertEquals(RegionalMeasurementSystem.METRIC, androidMeasurementSystem(Locale.forLanguageTag("es-CL")))
        assertEquals(RegionalMeasurementSystem.US, androidMeasurementSystem(Locale.US))
        assertEquals(RegionalMeasurementSystem.UK, androidMeasurementSystem(Locale.UK))
    }

    @Test fun localizedContextsCannotReplacePrimarySystemLocale() {
        val application = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
        val systemLocales = LocaleManagerCompat.getSystemLocales(application)
        val expected = regionalMeasurementUnits(systemLocales[0] ?: Locale.ROOT, ::androidMeasurementSystem)
        for (tag in listOf("en-US", "es-CL", "en-CL")) {
            val configuration = Configuration(application.resources.configuration).apply {
                setLocales(LocaleList.forLanguageTags(tag))
            }
            val localized = application.createConfigurationContext(configuration)
            assertEquals(tag, localized.resources.configuration.locales[0].toLanguageTag())
            assertEquals(tag, expected, deviceMeasurementUnits(localized))
        }
    }

    @Test fun emptySystemLocaleInformationFallsBackToMetricWithoutLanguageInference() {
        val empty = LocaleListCompat.getEmptyLocaleList()
        assertEquals(MeasurementUnits.METRIC,
            regionalMeasurementUnits(empty[0] ?: Locale.ROOT, ::androidMeasurementSystem))
    }
}
