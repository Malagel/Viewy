package com.malagel.viewy.ui

import com.malagel.viewy.domain.MeasurementMethod
import com.malagel.viewy.measurement.MeasurementSelection
import com.malagel.viewy.measurement.SelectionDestination
import com.malagel.viewy.measurement.MeasurementState
import com.malagel.viewy.settings.AppPreferences
import com.malagel.viewy.settings.SettingsDestination
import com.malagel.viewy.settings.SettingsUiState
import org.junit.Assert.*
import org.junit.Test

class ScreenNavigationMotionTest {
    @Test fun measurementBackAlwaysPresentsModesWithoutAnIntermediateMethodList() {
        val method = MeasurementMethod.KNOWN_OBJECT_HEIGHT
        val browsing = MeasurementSelection().browse(method.mode)
        assertEquals(browsing.destination, homePresentationDestination(browsing.destination))
        val selected = browsing.activate(method)
        // Navigation presentation does not depend on height entry, top capture or camera state.
        for (measurement in listOf(MeasurementState(), MeasurementState(heightMeters = 2.0))) {
            val state = MeasurementUiState(selection = selected, measurement = measurement)
            assertEquals(SelectionDestination.Home, homePresentationDestination(state.selection.destination))
            val back = state.selection.back()
            assertEquals(SelectionDestination.Home, back.destination)
            assertEquals(back.openModes(), back)
            assertEquals(SelectionDestination.Home, homePresentationDestination(back.destination))
            assertNull(back.browsingMode)
        }
    }

    @Test fun measurementNavigationUsesComplementaryDirectionsOnlyOnDestinationChanges() {
        assertEquals(MeasurementMotion.ENTER, measurementMotion(false, true))
        assertEquals(MeasurementMotion.RETURN_HOME, measurementMotion(true, false))
        assertEquals(MeasurementMotion.NONE, measurementMotion(true, true))
        assertEquals(MeasurementMotion.NONE, measurementMotion(false, false))
    }

    @Test fun inactiveMeasurementIsInteractiveAndDoesNotRetainAnOldOverlay() {
        val presentation = overlayPresentation(ScreenOverlay.NONE, ScreenOverlay.NONE, false, ScreenOverlay.SETTINGS)
        assertEquals(ScreenOverlay.NONE, presentation.displayed)
        assertFalse(presentation.blocksMeasurement)
        assertFalse(presentation.interactive)
        assertEquals(ScreenOverlay.NONE, screenOverlay(settingsOpen = false, diagnosticsOpen = false))
    }

    @Test fun settingsAndDiagnosticsShareEntryExitAndInputRules() {
        for (destination in listOf(ScreenOverlay.SETTINGS, ScreenOverlay.DIAGNOSTICS)) {
            // Also guard the first frame before Compose reports isRunning.
            val entering = overlayPresentation(ScreenOverlay.NONE, destination, false)
            assertEquals(destination, entering.displayed)
            assertTrue(entering.blocksMeasurement)
            assertFalse(entering.interactive)
            val moving = overlayPresentation(ScreenOverlay.NONE, destination, true)
            assertFalse(moving.interactive)
            val shown = overlayPresentation(destination, destination, false)
            assertEquals(destination, shown.displayed)
            assertTrue(shown.blocksMeasurement)
            assertTrue(shown.interactive)
            for (running in listOf(false, true)) {
                val exiting = overlayPresentation(destination, ScreenOverlay.NONE, running)
                assertEquals(destination, exiting.displayed)
                assertTrue(exiting.blocksMeasurement)
                assertFalse(exiting.interactive)
            }
        }
    }

    @Test fun cancelingEntranceRetainsItsSurfaceAndBlocksInputUntilExitFinishes() {
        for (destination in listOf(ScreenOverlay.SETTINGS, ScreenOverlay.DIAGNOSTICS)) {
            val canceled = overlayPresentation(ScreenOverlay.NONE, ScreenOverlay.NONE, true, destination)
            assertEquals(destination, canceled.displayed)
            assertTrue(canceled.blocksMeasurement)
            assertFalse(canceled.interactive)
            val done = overlayPresentation(ScreenOverlay.NONE, ScreenOverlay.NONE, false, destination)
            assertEquals(ScreenOverlay.NONE, done.displayed)
            assertFalse(done.blocksMeasurement)
        }
    }

    @Test fun openingAndReversingUsesOneDestinationWithSettingsTakingPrecedence() {
        assertEquals(ScreenOverlay.SETTINGS, screenOverlay(true, false))
        assertEquals(ScreenOverlay.DIAGNOSTICS, screenOverlay(false, true))
        assertEquals(ScreenOverlay.SETTINGS, screenOverlay(true, true))
        val reopened = overlayPresentation(ScreenOverlay.SETTINGS, ScreenOverlay.SETTINGS, true)
        assertTrue(reopened.blocksMeasurement)
        assertFalse(reopened.interactive)
    }

    @Test fun settingsChildrenHaveOppositeForwardAndBackDirections() {
        for (child in listOf(SettingsDestination.LANGUAGE, SettingsDestination.DEVELOPER)) {
            assertEquals(SettingsMotion.FORWARD, settingsMotion(SettingsDestination.MAIN, child))
            assertEquals(SettingsMotion.BACKWARD, settingsMotion(child, SettingsDestination.MAIN))
            assertEquals(SettingsMotion.NONE, settingsMotion(child, child))
            assertEquals(child, displayedSettingsPage(SettingsDestination.CLOSED, child))
            assertEquals(SettingsDestination.MAIN, displayedSettingsPage(SettingsDestination.MAIN, child))
        }
        assertEquals(SettingsDestination.MAIN, displayedSettingsPage(SettingsDestination.CLOSED, SettingsDestination.CLOSED))
        assertEquals(SettingsMotion.NONE, settingsMotion(SettingsDestination.CLOSED, SettingsDestination.MAIN))
        assertEquals(SettingsMotion.NONE, settingsMotion(SettingsDestination.MAIN, SettingsDestination.CLOSED))
    }

    @Test fun restoredDestinationsStartSettledAndPreferenceChangesDoNotNavigate() {
        for (overlay in listOf(ScreenOverlay.SETTINGS, ScreenOverlay.DIAGNOSTICS)) {
            val restored = overlayPresentation(overlay, overlay, false)
            assertTrue(restored.interactive)
            assertEquals(overlay, restored.displayed)
        }
        val saved = SettingsUiState(preferences = AppPreferences()).open().languages()
        val edited = saved.copy(preferences = AppPreferences(diagnosticsEnabled = true))
        assertEquals(SettingsMotion.NONE, settingsMotion(saved.destination, edited.destination))
        assertEquals(SettingsDestination.LANGUAGE, edited.destination)
        // Rendering motion is separate from the retained navigation and preferences.
        displayedSettingsPage(saved.back().destination, saved.destination)
        assertEquals(SettingsDestination.LANGUAGE, saved.destination)
        assertSame(saved.preferences, saved.back().preferences)
    }
}
