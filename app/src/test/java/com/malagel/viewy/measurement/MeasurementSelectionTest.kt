package com.malagel.viewy.measurement

import com.malagel.viewy.domain.MeasurementMethod
import com.malagel.viewy.domain.MeasurementMode
import org.junit.Assert.*
import org.junit.Test

class MeasurementSelectionTest {
    private val method = MeasurementMethod.KNOWN_OBJECT_HEIGHT
    private val mode = MeasurementMode.LONG_DISTANCE

    @Test fun freshLaunchStartsAtHome() {
        val initial = MeasurementSelection()
        assertNull(initial.activeMethod)
        assertTrue(initial.isOpen)
        assertEquals(SelectionDestination.Home, initial.destination)
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

    @Test fun backReturnsHomeFromMethodsAndMeasurementAndIsIdempotentAtHome() {
        val home = MeasurementSelection()
        val methods = home.browse(mode)
        assertEquals(SelectionDestination.Methods(mode), methods.destination)
        assertEquals(mode, methods.browsingMode)
        assertEquals(home, methods.back())
        val selected = methods.activate(method)
        assertEquals(SelectionDestination.Measurement(method), selected.destination)
        assertEquals(method, selected.activeMethod)
        assertFalse(selected.isOpen)
        assertEquals(home, selected.back())
        assertEquals(home, selected.openModes())
        assertEquals(home, home.back())
        assertNull(selected.openModes().activeMethod)
    }

    @Test fun activationRequiresCorrespondingMethodsAndDuplicateActionsAreHarmless() {
        val home = MeasurementSelection()
        assertEquals(home, home.activate(method))
        val methods = home.browse(mode)
        assertEquals(methods, methods.browse(mode))
        val selected = methods.activate(method)
        assertEquals(selected, selected.activate(method))
        assertEquals(selected, selected.browse(mode))
        assertEquals(selected, selected.openModes().browse(mode).activate(method))
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
