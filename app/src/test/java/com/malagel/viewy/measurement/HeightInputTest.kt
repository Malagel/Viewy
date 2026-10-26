package com.malagel.viewy.measurement

import org.junit.Assert.*
import org.junit.Test

class HeightInputTest {
    @Test fun positiveDecimalAndLocaleComma() {
        assertEquals(2.75, heightInMeters(" 2.75 ", HeightUnit.METERS)!!, 0.0)
        assertEquals(2.75, heightInMeters("2,75", HeightUnit.METERS)!!, 0.0)
        assertEquals(0.5, heightInMeters(".5", HeightUnit.METERS)!!, 0.0)
    }
    @Test fun feetConversionUsesExactInternationalFootWithoutPresentationRounding() {
        assertEquals(1.8288, heightInMeters("6", HeightUnit.FEET)!!, 1e-15)
        val meters = heightInMeters("6.123456789", HeightUnit.FEET)!!
        assertEquals(6.123456789, HeightUnit.FEET.fromMeters(meters), 1e-14)
    }
    @Test fun invalidAndNonpositiveValuesAreRejected() {
        for (text in listOf("", "0", "-1", "NaN", "Infinity", "1e3", "1.2.3", "1,000.5", "abc", "9".repeat(400))) {
            assertNull(text, heightInMeters(text, HeightUnit.METERS))
        }
    }
    @Test fun heightMustRemainRepresentableWhenSwitchingUnits() {
        assertNull(heightInMeters("1" + "0".repeat(308), HeightUnit.METERS))
    }
}
