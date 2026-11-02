package com.malagel.viewy.settings

import java.util.Locale
import org.junit.Assert.*
import org.junit.Test

class RegionalMeasurementUnitsTest {
    @Test fun icuDeterminesUnitsAndMapsMixedSystemsToMetric() {
        for ((tag, system, expected) in listOf(
            Triple("es-CL", RegionalMeasurementSystem.METRIC, MeasurementUnits.METRIC),
            Triple("en-CL", RegionalMeasurementSystem.METRIC, MeasurementUnits.METRIC),
            Triple("es-ES", RegionalMeasurementSystem.METRIC, MeasurementUnits.METRIC),
            Triple("en-US", RegionalMeasurementSystem.US, MeasurementUnits.IMPERIAL),
            Triple("es-US", RegionalMeasurementSystem.US, MeasurementUnits.IMPERIAL),
            Triple("en-LR", RegionalMeasurementSystem.US, MeasurementUnits.IMPERIAL),
            Triple("en-MM", RegionalMeasurementSystem.METRIC, MeasurementUnits.METRIC),
            Triple("en-CA", RegionalMeasurementSystem.UK, MeasurementUnits.METRIC),
        )) {
            var calls = 0
            assertEquals(expected, regionalMeasurementUnits(Locale.forLanguageTag(tag)) {
                calls++
                assertEquals(tag, it.toLanguageTag())
                system
            })
            assertEquals(1, calls)
        }
    }

    @Test fun ukDefaultsToMetricWithoutConsultingIcu() {
        for (tag in listOf("en-GB", "es-US-u-rg-gbzzzz")) {
            assertEquals(MeasurementUnits.METRIC, regionalMeasurementUnits(Locale.forLanguageTag(tag)) {
                throw AssertionError("UK defaults to Metric")
            })
        }
    }

    @Test fun missingUnknownAndFailedInformationDefaultsToMetric() {
        for (locale in listOf(Locale.ROOT, Locale.ENGLISH, Locale.forLanguageTag("en-ZZ"))) {
            assertEquals(MeasurementUnits.METRIC, regionalMeasurementUnits(locale) { throw AssertionError("Unknown region") })
        }
        assertEquals(MeasurementUnits.METRIC, regionalMeasurementUnits(Locale.US) { null })
        assertEquals(MeasurementUnits.METRIC, regionalMeasurementUnits(Locale.US) { throw IllegalArgumentException() })
    }

    @Test fun languageDoesNotDetermineUnitsAndRegionalExtensionsAreRecognized() {
        for (language in listOf("en", "es", "fr")) {
            assertEquals(MeasurementUnits.IMPERIAL, regionalMeasurementUnits(Locale.forLanguageTag("$language-US")) { RegionalMeasurementSystem.US })
            assertEquals(MeasurementUnits.METRIC, regionalMeasurementUnits(Locale.forLanguageTag("$language-CL")) { RegionalMeasurementSystem.METRIC })
        }
        assertEquals(MeasurementUnits.IMPERIAL, regionalMeasurementUnits(Locale.forLanguageTag("es-CL-u-rg-uszzzz")) {
            assertEquals("US", it.country)
            assertNull(it.getUnicodeLocaleType("rg"))
            RegionalMeasurementSystem.US
        })
        assertEquals(MeasurementUnits.METRIC, regionalMeasurementUnits(Locale.forLanguageTag("en-US-u-ms-metric")) { throw AssertionError("Explicit override") })
        assertEquals(MeasurementUnits.IMPERIAL, regionalMeasurementUnits(Locale.forLanguageTag("es-CL-u-ms-ussystem")) { throw AssertionError("Explicit override") })
        assertEquals(MeasurementUnits.METRIC, regionalMeasurementUnits(Locale.forLanguageTag("en-US-u-ms-uksystem")) { throw AssertionError("Explicit override") })
    }

    @Test fun icuReceivesTheValidatedRegionEvenForUnsupportedNumericOverrides() {
        for ((tag, region) in listOf(
            "en-US-u-rg-419zzzz" to "US",
            "en-US-u-rg-invalid" to "US",
            "es-CL-u-rg-uszzzz" to "US",
            "en-US-u-rg-clzzzz" to "CL",
            "en-u-rg-uszzzz" to "US",
        )) {
            val expected = if (region == "US") MeasurementUnits.IMPERIAL else MeasurementUnits.METRIC
            assertEquals(expected, regionalMeasurementUnits(Locale.forLanguageTag(tag)) {
                assertEquals(region, it.country)
                assertNull(it.getUnicodeLocaleType("rg"))
                if (it.country == "US") RegionalMeasurementSystem.US else RegionalMeasurementSystem.METRIC
            })
        }
        assertEquals(MeasurementUnits.METRIC,
            regionalMeasurementUnits(Locale.forLanguageTag("es-CL-u-ms-metric")) {
                throw AssertionError("Explicit override")
            })
    }

    @Test fun invalidRegionalOverridesUseCountryAndMissingRegionsRemainSafe() {
        assertEquals(MeasurementUnits.IMPERIAL, regionalMeasurementUnits(Locale.forLanguageTag("en-US-u-rg-invalid")) {
            RegionalMeasurementSystem.US
        })
        for (tag in listOf("en-US-u-rg-zzzzzz", "en-u-ms-ussystem", "en-ZZ-u-ms-ussystem")) {
            assertEquals(MeasurementUnits.METRIC, regionalMeasurementUnits(Locale.forLanguageTag(tag)) {
                throw AssertionError("Missing or unknown region")
            })
        }
        assertEquals(MeasurementUnits.IMPERIAL, regionalMeasurementUnits(Locale.forLanguageTag("en-GB-u-ms-ussystem")) {
            throw AssertionError("Explicit override takes priority over UK default")
        })
    }
}
