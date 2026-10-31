package com.malagel.viewy.measurement

import com.malagel.viewy.domain.KnownHeightDistance
import com.malagel.viewy.domain.ViewingDirection
import com.malagel.viewy.orientation.*
import java.util.Locale
import org.junit.Assert.*
import org.junit.Test

class HeightEditorTest {
    private fun type(text: String, preference: HeightUnit = HeightUnit.METERS): HeightDraft =
        text.fold(HeightDraft.open(null, preference)) { draft, key -> draft.key(key) }

    private fun completed(topSlope: Double = 0.5): KnownHeightWorkflow {
        val workflow = KnownHeightWorkflow()
        workflow.setHeight(2.0)
        workflow.cameraReady(true)
        val quality = OrientationQuality(13, 480_000_000L, 0, 0.0, 0.0, 0.0, 3, CaptureStatus.READY)
        listOf(topSlope, 0.0).forEachIndexed { index, z ->
            val time = 2_000_000_000L + index * 1_000_000_000L
            val observation = OrientationObservation(ViewingDirection(1.0, 0.0, z), time,
                "frame", OrientationSource.GAME_ROTATION_VECTOR, quality, "camera")
            workflow.update(observation, "frame", true, time)
            workflow.capture(CaptureResult.Captured(observation), time)
        }
        workflow.cameraReady(false)
        return workflow
    }

    @Test fun numericKeysNormalizeZeroAndKeepFractionalInput() {
        assertEquals("0.05", type("000.05").text)
        assertEquals("12.34", type("0012.34").text)
        assertEquals("0.5", type(".5").text)
        assertEquals("1.25", type("1.,25").text)
        assertEquals("0.5", type(",5").text)
        assertEquals("1234567890", type("1234567890").text)
        val draft = type("1.25")
        assertEquals("1.2", draft.backspace().text)
        assertEquals("1.", draft.backspace().backspace().text)
        assertEquals("", type("1").backspace().backspace().text)
        for (key in "-+eE abc_\n") assertSame(draft, draft.key(key))
    }

    @Test fun emptyZeroAndUnsupportedMagnitudesCannotConfirm() {
        for (text in listOf("", "0", ".", "0.000", "0." + "0".repeat(400) + "1")) {
            assertNull(text, type(text).meters)
        }
        val editor = HeightEditor(KnownHeightWorkflow())
        editor.open(HeightUnit.METERS)
        assertFalse(editor.confirm())
        editor.key('0')
        assertFalse(editor.confirm())
        assertNotNull(editor.draft)
    }

    @Test fun metricAndImperialSwitchesConvertValueAndKeepPhysicalPrecision() {
        for ((preference, text, converted) in listOf(
            Triple(HeightUnit.METERS, "1.75", "175"),
            Triple(HeightUnit.FEET, "5.5", "66"),
        )) {
            val original = type(text, preference)
            var draft = original.switchUnit()
            assertEquals(converted, draft.text)
            assertEquals(if (preference == HeightUnit.METERS) 1.75 else 1.6764, draft.meters!!, 0.0)
            repeat(1000) { draft = draft.switchUnit() }
            assertEquals(converted, draft.text)
            assertEquals(original.meters, draft.meters)
            assertEquals(text, draft.switchUnit().text)
        }
        var draft = HeightDraft.open(Math.PI, HeightUnit.FEET)
        repeat(1001) { draft = draft.switchUnit() }
        assertEquals(Math.PI, draft.meters!!, 0.0)
        val inches = type("1", HeightUnit.FEET).switchUnit().backspace().backspace().key('1')
        assertEquals(0.0254, inches.meters!!, 0.0)
        for (invalid in listOf("", "0", "0.")) {
            val switched = type(invalid).switchUnit()
            assertEquals(invalid, switched.text)
            assertNull(switched.meters)
        }
    }

    @Test fun localeAffectsDisplayOnlyAndBothDecimalKeysAreAccepted() {
        val draft = type("1,75")
        assertEquals("1.75", draft.displayText(Locale.US))
        assertEquals("1,75", draft.displayText(Locale.forLanguageTag("es-CL")))
        assertEquals(1.75, draft.meters!!, 0.0)
        assertEquals(',', HeightDraft.decimalSeparator(Locale.forLanguageTag("es")))
    }

    @Test fun initialCancelAndConfirmDoNotSaveInvalidOrDuplicateDrafts() {
        val workflow = KnownHeightWorkflow()
        val editor = HeightEditor(workflow)
        editor.open(HeightUnit.METERS)
        assertEquals("", editor.draft!!.text)
        editor.key('3')
        editor.cancel()
        assertNull(workflow.state.heightMeters)
        assertFalse(editor.confirm())
        editor.open(HeightUnit.FEET)
        editor.key('6')
        assertTrue(editor.confirm())
        assertEquals(1.8288, workflow.state.heightMeters!!, 0.0)
        val saved = workflow.state
        assertFalse(editor.confirm())
        assertSame(saved, workflow.state)
        editor.open(HeightUnit.METERS)
        editor.key('9')
        editor.cancel()
        assertSame(saved, workflow.state)
    }

    @Test fun completedCancelAndUntouchedOrUnitOnlyConfirmKeepExactStateAndIdentity() {
        val workflow = completed()
        val before = workflow.state
        val editor = HeightEditor(workflow)
        editor.open(HeightUnit.FEET)
        editor.backspace()
        editor.cancel()
        assertSame(before, workflow.state)
        editor.open(HeightUnit.FEET)
        editor.open(HeightUnit.METERS) // Repeated opening cannot replace the current draft.
        assertEquals(HeightEntryUnit.FEET, editor.draft!!.unit)
        repeat(101) { editor.switchUnit() }
        assertTrue(editor.confirm())
        assertSame(before, workflow.state)
        assertTrue(workflow.correctCompletedHeight(2.0))
        assertSame(before, workflow.state)
    }

    @Test fun cancellingCameraHeightEditRetainsSavedHeightAndDoesNotReturnToTheInitialPrompt() {
        val workflow = KnownHeightWorkflow()
        val editor = HeightEditor(workflow)
        editor.open(HeightUnit.METERS)
        editor.key('2')
        assertTrue(editor.confirm())
        workflow.cameraReady(true)
        editor.open(HeightUnit.METERS)
        assertEquals(2.0, editor.draft!!.meters!!, 0.0)
        editor.backspace()
        editor.key('9')
        editor.cancel()
        assertNull(editor.draft)
        assertEquals(2.0, workflow.state.heightMeters!!, 0.0)
        assertNull(workflow.state.result)
        editor.open(HeightUnit.METERS)
        assertEquals("2", editor.draft!!.text)
        editor.cancel()
        assertFalse(editor.confirm())
        assertEquals(2.0, workflow.state.heightMeters!!, 0.0)
    }

    @Test fun cameraReturnReopensSavedHeightAndPreservesItAcrossSuspensionAndConfirmation() {
        for (unit in HeightUnit.entries) {
            val workflow = KnownHeightWorkflow()
            val editor = HeightEditor(workflow)
            editor.open(unit)
            "2.3456789".forEach(editor::key)
            assertTrue(editor.confirm())
            val saved = workflow.state.heightMeters!!
            workflow.cameraReady(true)
            editor.open(unit)
            workflow.cameraReady(false, intentionalSuspension = true)
            assertEquals(saved, editor.draft!!.meters!!, 0.0)
            assertTrue(editor.confirm())
            assertNull(editor.draft)
            assertEquals(saved, workflow.state.heightMeters!!, 0.0)
            assertEquals(MeasurementStage.TOP, workflow.state.stage)
            editor.open(unit)
            editor.backspace()
            editor.cancel()
            assertEquals(saved, workflow.state.heightMeters!!, 0.0)
        }
    }

    @Test fun completedCorrectionUsesOriginalObservationsAndDiagnosticsWithoutRestart() {
        val workflow = completed()
        val before = workflow.state
        val editor = HeightEditor(workflow)
        editor.open(HeightUnit.METERS)
        editor.backspace()
        editor.key('4')
        assertTrue(editor.confirm())
        val after = workflow.state
        assertSame(before.top, after.top)
        assertSame(before.bottom, after.bottom)
        assertSame(before.resultOrigin, after.resultOrigin)
        assertEquals(MeasurementStage.RESULT, after.stage)
        assertEquals(before.noticeId, after.noticeId)
        assertFalse(after.cameraReady)
        assertFalse(after.canCapture)
        assertEquals(KnownHeightDistance.estimate(4.0, before.top!!.direction, before.bottom!!.direction), after.result)
        assertEquals(before.result!!.horizontalDistanceMeters * 2, after.result!!.horizontalDistanceMeters, 0.0)
        assertEquals(before.result.diagnostics.topSensitivityMetersPerRadian!! * 2,
            after.result.diagnostics.topSensitivityMetersPerRadian!!, 0.0)
        assertEquals(before.result.uncertainty, after.result.uncertainty)
    }

    @Test fun unusableCorrectionIsAtomicAndLeavesEditorAvailableToFixOrCancel() {
        val workflow = completed(topSlope = 0.01)
        val before = workflow.state
        // Supported domain heights can still overflow distance arithmetic; UI limits do not alter domain policy.
        assertFalse(workflow.correctCompletedHeight(1e307))
        assertSame(before, workflow.state)
        for (invalid in listOf(0.0, -1.0, Double.NaN, Double.POSITIVE_INFINITY)) {
            assertFalse(workflow.correctCompletedHeight(invalid))
            assertSame(before, workflow.state)
        }
        assertFalse(KnownHeightWorkflow().correctCompletedHeight(2.0))
    }
    @Test fun tenDigitLimitRejectsOnlyExtraDigitsAndRetainsPhysicalValue() {
        val ten = type("1234567890")
        assertTrue(ten.atDigitLimit)
        val rejected = ten.key('1')
        assertEquals(ten.text, rejected.text)
        assertEquals(ten.meters, rejected.meters)
        assertEquals(ten.rejectedDigitId + 1, rejected.rejectedDigitId)
        assertEquals(rejected.rejectedDigitId + 1, rejected.key('2').rejectedDigitId)
        val decimal = rejected.key(',')
        assertEquals("1234567890.", decimal.text)
        assertEquals(rejected.rejectedDigitId, decimal.rejectedDigitId)
        assertEquals(decimal.text, decimal.key('3').text)
        assertEquals("1234567895", ten.backspace().key('5').text)
        val fraction = type("0.123456789")
        assertTrue(fraction.atDigitLimit)
        assertEquals(fraction.text, fraction.key('0').text)
        val largerConversion = ten.switchUnit()
        assertTrue(largerConversion.text.count(Char::isDigit) > HeightDraft.maximumDigits)
        assertEquals(ten.meters, largerConversion.meters)
        assertEquals(largerConversion.text, largerConversion.key('1').text)
    }

    @Test fun convertedAndReopenedTextHasAtMostFourDecimalsWithoutChangingSavedPrecision() {
        val exact = 2.343123456
        var draft = HeightDraft.open(exact, HeightUnit.METERS)
        assertEquals("2.3431", draft.text)
        assertEquals(exact, draft.meters!!, 0.0)
        draft = draft.inSystem(HeightUnit.FEET)
        assertEquals("7.6874", draft.text)
        repeat(101) { draft = draft.inSystem(if (it % 2 == 0) HeightUnit.METERS else HeightUnit.FEET) }
        assertEquals(exact, draft.meters!!, 0.0)
        assertTrue(draft.text.substringAfter('.', "").length <= 4)
        val centimeters = "123.456789".fold(HeightDraft.open(null, HeightUnit.METERS).switchUnit()) { value, key -> value.key(key) }
        assertEquals("1.2345", centimeters.switchUnit().text)
        val tiny = HeightDraft.open(0.0000001, HeightUnit.METERS)
        assertEquals("<0.0001", tiny.displayText(Locale.US))
        assertEquals("<0,0001", tiny.displayText(Locale.forLanguageTag("es")))
        assertEquals(0.0000001, tiny.meters!!, 0.0)
    }

    @Test fun settingsUnitChangesRetainDraftAndOnlyConfirmationUpdatesTheSavedHeight() {
        val workflow = KnownHeightWorkflow().also { it.setHeight(2.0) }
        val before = workflow.state
        val editor = HeightEditor(workflow)
        editor.open(HeightUnit.METERS)
        editor.backspace()
        "1.23456789".forEach(editor::key)
        editor.preferenceChanged(HeightUnit.FEET)
        assertEquals(HeightEntryUnit.FEET, editor.draft!!.unit)
        assertEquals("4.0504", editor.draft!!.text)
        assertSame(before, workflow.state)
        editor.preferenceChanged(HeightUnit.METERS)
        assertEquals("1.2345", editor.draft!!.text)
        assertTrue(editor.confirm())
        assertEquals(1.23456789, workflow.state.heightMeters!!, 0.0)
        editor.open(HeightUnit.FEET)
        editor.preferenceChanged(HeightUnit.METERS)
        editor.cancel()
        assertEquals(1.23456789, workflow.state.heightMeters!!, 0.0)
    }
}
