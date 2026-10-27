package com.malagel.viewy.measurement

import com.malagel.viewy.domain.MeasurementMethod
import com.malagel.viewy.domain.MeasurementMode
import org.junit.Assert.*
import org.junit.Test

class MeasurementSelectionTest {
    private val method = MeasurementMethod.KNOWN_OBJECT_HEIGHT
    private val mode = MeasurementMode.LONG_DISTANCE

    @Test fun freshLaunchIsInactiveAndAutomaticallyPresentsModes() {
        val initial = MeasurementSelection()
        assertNull(initial.activeMethod)
        assertTrue(initial.isOpen)
        assertEquals(SelectionDestination.Modes, initial.destination)
    }

    @Test fun unavailableModesAreVisibleCatalogEntriesWithNoActivationPath() {
        val initial = MeasurementSelection()
        for (placeholder in listOf(MeasurementMode.SHORT_DISTANCE, MeasurementMode.HEIGHT, MeasurementMode.CALIBRATION)) {
            assertFalse(placeholder.available)
            assertTrue(placeholder.methods.isEmpty())
            assertEquals(initial, initial.browse(placeholder))
        }
        assertEquals(listOf(method), mode.methods)
    }

    @Test fun dismissAndReopenAlwaysStartsAtModesWithoutActivatingAnything() {
        val dismissed = MeasurementSelection().browse(mode).dismiss()
        assertFalse(dismissed.isOpen)
        assertNull(dismissed.activeMethod)
        assertNull(dismissed.browsingMode)
        assertEquals(MeasurementSelection(), dismissed.openModes())
        assertEquals(dismissed, dismissed.dismiss())
    }

    @Test fun backFromMethodsReturnsToModesThenCloses() {
        val methods = MeasurementSelection().browse(mode)
        assertEquals(SelectionDestination.Methods(mode), methods.destination)
        assertEquals(mode, methods.browsingMode)
        val modes = methods.back()
        assertEquals(MeasurementSelection(), modes)
        assertFalse(modes.back().isOpen)
        assertEquals(modes.back(), modes.back().back())
    }

    @Test fun activationRequiresAnOpenCorrespondingMethodList() {
        val initial = MeasurementSelection()
        assertEquals(initial, initial.activate(method))
        assertEquals(initial.dismiss(), initial.dismiss().activate(method))
        assertEquals(initial.dismiss(), initial.dismiss().browse(mode))
        val selected = initial.browse(mode).activate(method)
        assertEquals(method, selected.activeMethod)
        assertFalse(selected.isOpen)
        assertNull(selected.browsingMode)
        assertEquals(selected, selected.activate(method)) // queued duplicate selection
    }

    @Test fun browsingDismissalAndReselectionKeepTheActiveMethod() {
        val selected = MeasurementSelection().browse(mode).activate(method)
        val browsing = selected.openModes().browse(mode)
        assertEquals(method, browsing.activeMethod)
        assertEquals(selected, browsing.dismiss())
        assertEquals(selected, browsing.activate(method))
        assertEquals(selected, browsing.back().back())
        assertEquals(selected.openModes(), browsing.openModes())
    }

    @Test fun newSessionDoesNotInheritAnEarlierInMemorySelection() {
        val selected = MeasurementSelection().browse(mode).activate(method)
        assertEquals(method, selected.activeMethod)
        assertNull(MeasurementSelection().activeMethod)
        // Activity recreation keeps this same ViewModel state, rather than constructing a new selection.
        assertEquals(selected, selected.copy())
    }

    @Test fun catalogIdentitiesAreUniqueAndStable() {
        assertEquals("long_distance", mode.id)
        assertEquals("calibration", MeasurementMode.CALIBRATION.id)
        assertEquals("known_object_height", method.id)
        assertEquals(MeasurementMode.entries.size, MeasurementMode.entries.map { it.id }.toSet().size)
        assertEquals(MeasurementMethod.entries.size, MeasurementMethod.entries.map { it.id }.toSet().size)
        assertTrue(method.requiresCamera)
        assertTrue(method.requiresOrientation)
    }
}
