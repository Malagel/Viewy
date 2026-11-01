package com.malagel.viewy.settings

import java.util.Locale
import org.junit.Assert.*
import org.junit.Test

class RegionalMeasurementUnitsTest {
    @Test fun modernAndroidUsesIcuAndMapsMixedSystemsToMetric() {
        for ((tag, system, expected) in listOf(
            Triple("es-CL", RegionalMeasurementSystem.METRIC, MeasurementUnits.METRIC),
            Triple("es-US", RegionalMeasurementSystem.US, MeasurementUnits.IMPERIAL),
            Triple("en-LR", RegionalMeasurementSystem.US, MeasurementUnits.IMPERIAL),
            Triple("en-CA", RegionalMeasurementSystem.UK, MeasurementUnits.METRIC),
        )) {
            var calls = 0
            assertEquals(expected, regionalMeasurementUnits(Locale.forLanguageTag(tag), 28) {
                calls++
                assertEquals(tag, it.toLanguageTag())
                system
            })
            assertEquals(1, calls)
        }
        assertEquals(MeasurementUnits.METRIC, regionalMeasurementUnits(Locale.UK, 37) { RegionalMeasurementSystem.US })
    }

    @Test fun api23Through27UseRegionFallbackWithoutCallingNewerApi() {
        for (sdk in 23..27) for (region in listOf("US", "LR", "MM", "CL", "GB", "CA", "FR", "ZZ", "")) {
            val expected = if (region in listOf("US", "LR", "MM")) MeasurementUnits.IMPERIAL else MeasurementUnits.METRIC
            assertEquals(expected, regionalMeasurementUnits(Locale.Builder().setLanguage("en").setRegion(region).build(), sdk) { error("ICU on old API") })
        }
    }

    @Test fun missingUnknownAndFailedInformationDefaultsToMetric() {
        for (locale in listOf(Locale.ROOT, Locale.ENGLISH, Locale.forLanguageTag("en-ZZ"))) {
            assertEquals(MeasurementUnits.METRIC, regionalMeasurementUnits(locale, 28) { error("Unknown region") })
        }
        assertEquals(MeasurementUnits.METRIC, regionalMeasurementUnits(Locale.US, 28) { null })
        assertEquals(MeasurementUnits.METRIC, regionalMeasurementUnits(Locale.US, 28) { throw IllegalArgumentException() })
    }

    @Test fun languageDoesNotDetermineUnitsAndRegionalExtensionsAreRecognized() {
        for (language in listOf("en", "es", "fr")) {
            assertEquals(MeasurementUnits.IMPERIAL, regionalMeasurementUnits(Locale.forLanguageTag("$language-US"), 23) { null })
            assertEquals(MeasurementUnits.METRIC, regionalMeasurementUnits(Locale.forLanguageTag("$language-CL"), 23) { null })
        }
        assertEquals(MeasurementUnits.IMPERIAL, regionalMeasurementUnits(Locale.forLanguageTag("es-CL-u-rg-uszzzz"), 23) { null })
        assertEquals(MeasurementUnits.METRIC, regionalMeasurementUnits(Locale.forLanguageTag("en-US-u-ms-metric"), 28) { null })
        assertEquals(MeasurementUnits.IMPERIAL, regionalMeasurementUnits(Locale.forLanguageTag("es-CL-u-ms-ussystem"), 28) { null })
        assertEquals(MeasurementUnits.METRIC, regionalMeasurementUnits(Locale.forLanguageTag("en-US-u-ms-uksystem"), 28) { null })
    }
}
