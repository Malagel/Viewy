package com.malagel.viewy.ui

import com.malagel.viewy.measurement.HeightUnit
import com.malagel.viewy.measurement.heightInMeters
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class HeightLabelTextTest {
    @Test fun wholeHeightsHaveOneDecimal() {
        assertEquals("2.0", heightLabelText(2.0, Locale.US))
        assertEquals("120.0", heightLabelText(120.0, Locale.US))
        assertEquals("100000.0", heightLabelText(100000.0, Locale.US))
    }

    @Test fun fractionalHeightsDoNotGainZeroPadding() {
        assertEquals("2.343", heightLabelText(2.343, Locale.US))
        assertEquals("2.3", heightLabelText(2.3, Locale.US))
        assertEquals("0.125", heightLabelText(0.125, Locale.US))
    }

    @Test fun existingPrecisionCapAndCompactExponentsRemain() {
        assertEquals("2.34312", heightLabelText(2.343123456, Locale.US))
        assertEquals("2.0", heightLabelText(2.0000001, Locale.US))
        assertEquals("1.23457e+08", heightLabelText(123456789.0, Locale.US))
        assertEquals("1.23457e-06", heightLabelText(0.00000123456789, Locale.US))
        assertEquals("2.0e+06", heightLabelText(2000000.0, Locale.US))
    }

    @Test fun localeDecimalSeparatorAndDigitsArePreserved() {
        assertEquals("2,0", heightLabelText(2.0, Locale.GERMANY))
        assertEquals("2,343", heightLabelText(2.343, Locale.GERMANY))
        assertEquals("٢٫٠", heightLabelText(2.0, Locale.forLanguageTag("ar")))
    }

    @Test fun feetAreFormattedAfterUnitConversion() {
        val whole = heightInMeters("6", HeightUnit.FEET)!!
        val fractional = heightInMeters("6.12345", HeightUnit.FEET)!!
        assertEquals("6.0", heightLabelText(HeightUnit.FEET.fromMeters(whole), Locale.US))
        assertEquals("6.12345", heightLabelText(HeightUnit.FEET.fromMeters(fractional), Locale.US))
    }
    @Test fun globalHeightEntryAndDistanceFormattingUseTheDisplayLocaleWithoutGrouping() {
        val spanish = Locale.forLanguageTag("es-CL")
        val meters = heightInMeters("6,125", HeightUnit.FEET)!!
        val input = heightInputText(HeightUnit.FEET.fromMeters(meters), spanish)
        assertEquals("6,125", input)
        assertEquals(meters, heightInMeters(input, HeightUnit.FEET)!!, 1e-15)
        assertEquals("2,343123456", heightInputText(2.343123456, spanish))
        assertEquals("0.0000001", heightInputText(0.0000001, Locale.US))
        assertEquals("3.1 m", distanceText(3.05, HeightUnit.METERS, Locale.US))
        assertEquals("3,1 m", distanceText(3.05, HeightUnit.METERS, spanish))
        assertEquals("10 ft", distanceText(3.05, HeightUnit.FEET, Locale.US))
        assertEquals("6,125", heightLabelText(HeightUnit.FEET.fromMeters(meters), spanish))
    }
}
