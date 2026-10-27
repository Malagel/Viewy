package com.malagel.viewy.measurement

import com.malagel.viewy.domain.MeasurementMethod
import com.malagel.viewy.domain.MeasurementMode
import org.junit.Assert.*
import org.junit.Test

class MeasurementSelectionTest {
    private val method = MeasurementMethod.KNOWN_OBJECT_HEIGHT
    private val mode = MeasurementMode.LONG_DISTANCE

    @Test fun startsInDefaultMethodAndOnlyImplementedModesCanBeBrowsed() {
        val initial = MeasurementSelection()
        assertEquals(method, initial.activeMethod)
        assertFalse(initial.modeMenuOpen)
        for (placeholder in listOf(MeasurementMode.SHORT_DISTANCE, MeasurementMode.HEIGHT)) {
            assertFalse(placeholder.available)
            assertEquals(initial, initial.browse(placeholder))
        }
        assertEquals(listOf(method), mode.methods)
        assertEquals(mode, initial.browse(mode).browsingMode)
    }

    @Test fun activationRequiresBrowsingTheCorrespondingMode() {
        val initial = MeasurementSelection()
        assertEquals(initial, initial.activate(method))
        val selected = initial.browse(mode).activate(method)
        assertEquals(method, selected.activeMethod)
        assertNull(selected.browsingMode)
        assertFalse(selected.modeMenuOpen)
    }

    @Test fun browsingDismissalAndReselectionKeepTheActiveMethod() {
        val selected = MeasurementSelection().browse(mode).activate(method)
        assertEquals(selected, selected.toggleModes().toggleModes())
        assertEquals(selected, selected.toggleModes().dismiss())
        val browsing = selected.toggleModes().browse(mode)
        assertEquals(method, browsing.activeMethod)
        assertEquals(selected, browsing.dismiss())
        assertEquals(selected, browsing.activate(method))
        assertEquals(selected, browsing.dismiss().toggleModes().browse(mode).dismiss())
    }

    @Test fun missingOrUnavailableSavedIdsFallBackWithoutOpeningSelection() {
        for (saved in listOf(null, "", "removed_method", "KNOWN_OBJECT_HEIGHT", "height")) {
            assertEquals(MeasurementSelection(), MeasurementSelection.restore(saved))
        }
    }

    @Test fun savedStableIdsRestoreOnlyTheActiveMethod() {
        for (available in MeasurementMethod.entries) {
            val selected = MeasurementSelection().browse(available.mode).activate(available)
            val restored = MeasurementSelection.restore(selected.activeMethod.id)
            assertEquals(available, restored.activeMethod)
            assertNull(restored.browsingMode)
            assertFalse(restored.modeMenuOpen)
            assertEquals(restored, restored.toggleModes().browse(available.mode).dismiss())
        }
    }

    @Test fun catalogIdentitiesAreUniqueAndStable() {
        assertEquals("long_distance", mode.id)
        assertEquals("known_object_height", method.id)
        assertEquals(MeasurementMode.entries.size, MeasurementMode.entries.map { it.id }.toSet().size)
        assertEquals(MeasurementMethod.entries.size, MeasurementMethod.entries.map { it.id }.toSet().size)
        assertTrue(method.requiresCamera)
        assertTrue(method.requiresOrientation)
    }
}
