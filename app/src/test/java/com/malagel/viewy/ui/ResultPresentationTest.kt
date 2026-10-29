package com.malagel.viewy.ui

import com.malagel.viewy.domain.KnownHeightDistance
import com.malagel.viewy.domain.KnownHeightResult
import com.malagel.viewy.domain.ViewingDirection
import com.malagel.viewy.measurement.HeightUnit
import java.math.BigDecimal
import java.text.NumberFormat
import java.util.Locale
import org.junit.Assert.*
import org.junit.Test

class ResultPresentationTest {
    private val spanish = Locale.forLanguageTag("es-CL")
    private fun estimate() = KnownHeightDistance.estimate(20.0,
        ViewingDirection(1.0, 0.0, 0.5), ViewingDirection(1.0, 0.0, 0.0)) as KnownHeightResult.Estimate

    @Test fun ordinaryDecimalsTrimPaddingAndKeepNonIntegerIdentityAfterRounding() {
        val cases = mapOf(730.0 to "730", 730.5 to "730.5", 730.45 to "730.45", 7.3 to "7.3",
            1500.0 to "1,500", 730.001 to "730.0", 730.999 to "731.0", 1.005 to "1.01",
            730.4501 to "730.45", 0.01 to "0.01", 0.00999 to "0.01")
        cases.forEach { (value, expected) -> assertEquals(expected, measurementNumberText(value, Locale.US)) }
    }

    @Test fun tinyValuesRetainTwoSignificantDigitsInsteadOfRoundingToZero() {
        assertEquals("0.0012", measurementNumberText(0.0012345, Locale.US))
        assertEquals("0.00000012", measurementNumberText(0.00000012345, Locale.US))
        val minimum = measurementNumberText(Double.MIN_VALUE, Locale.US)
        assertTrue(minimum.startsWith("0."))
        assertTrue(BigDecimal(minimum) > BigDecimal.ZERO)
        assertFalse(minimum.contains('E', ignoreCase = true))
    }

    @Test fun metricImperialAndLocaleAreAppliedOnlyToPresentation() {
        assertEquals("730.45 m", distanceText(730.45, HeightUnit.METERS, Locale.US))
        assertEquals("10.00 ft", distanceText(3.048, HeightUnit.FEET, Locale.US))
        assertEquals("10.01 ft", distanceText(3.05, HeightUnit.FEET, Locale.US))
        assertEquals("1.500,00 m", distanceText(1500.0, HeightUnit.METERS, spanish))
        assertEquals("730,45 m", distanceText(730.45, HeightUnit.METERS, spanish))
        assertEquals("1,6", measurementNumberText(1.6, spanish))
        assertEquals("0,00000012", measurementNumberText(0.00000012345, spanish))
    }

    @Test fun largeValuesNeverUseAnExponentEvenIfFeetWouldOverflowDouble() {
        for (locale in listOf(Locale.US, spanish)) for (unit in HeightUnit.entries) {
            for (value in listOf(1e8, 1e20, Double.MAX_VALUE)) {
                val text = distanceNumberText(value, unit, locale)
                assertFalse(text, text.contains('e', ignoreCase = true))
                assertFalse(text, text.contains('∞'))
                assertTrue(text, text.any(Char::isDigit))
                val scaled = ResultNumberFormatter(value, unit, locale)
                assertFalse(scaled.finalText, scaled.finalText.contains('e', ignoreCase = true))
                assertFalse(scaled.finalText, scaled.finalText.contains('∞'))
            }
        }
        assertEquals("123,456,789,012,345", measurementNumberText(123456789012345.0, Locale.US))
    }

    @Test fun resultDistanceScalesAtTenKilometersOrOneMileUsingTheUnroundedDistance() {
        assertEquals("9,999.00 m", distanceText(9999.0, HeightUnit.METERS, Locale.US))
        assertEquals("10.00 km", distanceText(10_000.0, HeightUnit.METERS, Locale.US))
        assertEquals("10.73 km", distanceText(10_730.0, HeightUnit.METERS, Locale.US))
        assertEquals("10,05 km", distanceText(10_050.0, HeightUnit.METERS, spanish))
        assertEquals("5,279.00 ft", distanceText(1609.0392, HeightUnit.FEET, Locale.US))
        assertEquals("1.00 mi", distanceText(1609.344, HeightUnit.FEET, Locale.US))
        assertEquals("2.00 mi", distanceText(3218.688, HeightUnit.FEET, Locale.US))
        assertEquals("2,50 mi", distanceText(4023.36, HeightUnit.FEET, spanish))
        assertEquals("m", ResultNumberFormatter(Math.nextDown(10_000.0), HeightUnit.METERS, Locale.US).unitLabel)
        assertEquals("ft", ResultNumberFormatter(Math.nextDown(1609.344), HeightUnit.FEET, Locale.US).unitLabel)
        // Object heights use base units regardless of the result-distance thresholds.
        assertEquals("10,000", distanceNumberText(10_000.0, HeightUnit.METERS, Locale.US))
        assertEquals("5,280", distanceNumberText(1609.344, HeightUnit.FEET, Locale.US))
    }

    @Test fun counterKeepsItsDestinationUnitFromZeroToCompletion() {
        for ((meters, preference, label) in listOf(
            Triple(10_000.0, HeightUnit.METERS, "km"), Triple(1609.344, HeightUnit.FEET, "mi"),
        )) {
            val formatter = ResultNumberFormatter(meters, preference, Locale.US)
            assertEquals("0.00", formatter.frameText(0f))
            repeat(101) { frame ->
                formatter.frameText(frame / 100f)
                assertEquals(label, formatter.unitLabel)
            }
            assertEquals(distanceText(meters, preference, Locale.US), "${formatter.frameText(1f)} ${formatter.unitLabel}")
        }
    }

    @Test fun primaryDistanceFillsItsReservedDecimalSlotInsteadOfLeavingAGap() {
        for ((meters, expected) in listOf(
            9.8 to "9.80", 9.0 to "9.00", 9.801 to "9.80", 9.999 to "10.00",
            730.5 to "730.50", 0.0012345 to "0.0012",
        )) {
            val formatter = ResultNumberFormatter(meters, HeightUnit.METERS, Locale.US)
            assertEquals(expected, formatter.finalText)
            assertEquals(expected, formatter.layoutText)
            assertEquals(expected, formatter.frameText(1f))
        }
        val english = ResultNumberFormatter(9.8, HeightUnit.METERS, Locale.US)
        assertEquals("0.00", english.frameText(0f))
        assertEquals("4.90", english.frameText(0.5f))
        assertEquals("9.80 m", distanceText(9.8, HeightUnit.METERS, Locale.US))
        assertEquals("9,80 m", distanceText(9.8, HeightUnit.METERS, spanish))
        assertEquals("1.00 ft", distanceText(0.3048, HeightUnit.FEET, Locale.US))
        assertEquals("10.50 km", distanceText(10_500.0, HeightUnit.METERS, Locale.US))
        assertEquals("1.50 mi", distanceText(2414.016, HeightUnit.FEET, Locale.US))
        // Secondary quantities and editable height keep their existing compact precision policy.
        assertEquals("9.8", distanceNumberText(9.8, HeightUnit.METERS, Locale.US))
        assertEquals("1.6", measurementNumberText(1.6, Locale.US))
        assertEquals("9.8", heightInputText(9.8, Locale.US))
    }

    @Test fun counterUsesStablePrecisionAndEndsAtExactlyTheNormalFormattedValue() {
        for (value in listOf(730.0, 730.001, 999.999, 0.0000012345, 1609.344, 10_000.0, 1e20)) {
            for (locale in listOf(Locale.US, spanish)) for (unit in HeightUnit.entries) {
                val formatter = ResultNumberFormatter(value, unit, locale)
                assertEquals(0.0, NumberFormat.getNumberInstance(locale).parse(formatter.frameText(0f))!!.toDouble(), 0.0)
                assertEquals(distanceText(value, unit, locale), "${formatter.frameText(1f)} ${formatter.unitLabel}")
                val parser = NumberFormat.getNumberInstance(locale)
                var last = 0.0
                for (i in 0..100) {
                    val frame = formatter.frameText(i / 100f)
                    val current = parser.parse(frame)!!.toDouble()
                    assertTrue(frame, current >= last)
                    assertTrue(frame, frame.length <= formatter.layoutText.length)
                    assertTrue(frame, current <= parser.parse(formatter.finalText)!!.toDouble())
                    last = current
                }
            }
        }
    }

    @Test fun entriesAreConsumedOncePerResultReferenceAcrossUnrelatedUpdatesAndInterruptedEntry() {
        val result = estimate()
        val memory = ResultEntryMemory(null)
        assertTrue(memory.enter(result))
        repeat(20) {
            // Unit changes, locale changes, sensor publications and remounting keep the estimate.
            distanceText(result.horizontalDistanceMeters, HeightUnit.FEET, spanish)
            assertFalse(memory.enter(result))
        }
        assertTrue(memory.enter(estimate())) // A new measurement may have the same numerical value.
        assertFalse(ResultEntryMemory(result).enter(result)) // Activity recreation initializes settled.
    }

    @Test fun equalGeometryFromANewCaptureReplacesEvenAnUnfinishedExit() {
        val first = estimate()
        val next = estimate()
        assertEquals(first, next)
        assertEquals(ResultIdentity(first), ResultIdentity(first))
        assertEquals(ResultIdentity(first).hashCode(), ResultIdentity(first).hashCode())
        assertNotEquals(ResultIdentity(first), ResultIdentity(next))
        val memory = ResultEntryMemory(first)
        assertTrue(memory.enter(next))
        assertFalse(memory.enter(next))
    }

    @Test fun revealSequenceKeepsLaterContentHiddenUntilPreviousContentFinishes() {
        assertEquals(0f, ResultReveal.countFraction(0f), 0f)
        assertTrue(ResultReveal.countFraction(600f) < 1f)
        (0..2).forEach { assertEquals(0f, ResultReveal.supportingAlpha(600f, it), 0f) }
        for (index in 0..2) {
            val start = (ResultReveal.countMillis + index * ResultReveal.fadeMillis).toFloat()
            assertEquals(1f, ResultReveal.countFraction(start), 0f)
            assertEquals(0f, ResultReveal.supportingAlpha(start, index), 0f)
            assertEquals(0.5f, ResultReveal.supportingAlpha(start + 90f, index), 0f)
            assertEquals(1f, ResultReveal.supportingAlpha(start + 180f, index), 0f)
            for (later in index + 1..2) assertEquals(0f, ResultReveal.supportingAlpha(start + 180f, later), 0f)
        }
        // Compose reaches the same final timeline frame immediately at animation scale zero.
        assertEquals(1f, ResultReveal.countFraction(ResultReveal.totalMillis.toFloat()), 0f)
        (0..2).forEach { assertEquals(1f, ResultReveal.supportingAlpha(ResultReveal.totalMillis.toFloat(), it), 0f) }
    }
}
