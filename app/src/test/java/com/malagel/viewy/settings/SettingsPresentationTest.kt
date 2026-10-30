package com.malagel.viewy.settings

import com.malagel.viewy.domain.MeasurementMethod
import com.malagel.viewy.domain.ViewingDirection
import com.malagel.viewy.measurement.*
import com.malagel.viewy.orientation.*
import com.malagel.viewy.ui.MeasurementUiState
import com.malagel.viewy.tutorial.TutorialCard
import com.malagel.viewy.tutorial.TutorialState
import org.junit.Assert.*
import org.junit.Test

class SettingsPresentationTest {
    @Test fun deviceEnglishAndSpanishMapToAndroidLanguageTags() {
        assertEquals("", AppLanguage.DEVICE.tags)
        assertEquals("en", AppLanguage.ENGLISH.tags)
        assertEquals("es", AppLanguage.SPANISH.tags)
        for (choice in AppLanguage.entries) assertEquals(choice, AppLanguage.fromTags(choice.tags))
        assertEquals(AppLanguage.SPANISH, AppLanguage.fromTags("es-CL,en"))
        assertEquals(AppLanguage.ENGLISH, AppLanguage.fromTags("en-US"))
        assertEquals(AppLanguage.DEVICE, AppLanguage.fromTags(""))
    }

    @Test fun settingsBackReturnsThroughTheParentAndFreshSessionsStartClosed() {
        val fresh = SettingsUiState()
        assertSame(fresh, fresh.developer())
        assertSame(fresh, fresh.languages())
        val saved = AppPreferences(MeasurementUnits.IMPERIAL, true)
        val settings = fresh.copy(preferences = saved).open()
        assertEquals(SettingsDestination.MAIN, settings.destination)
        assertEquals(SettingsDestination.DEVELOPER, settings.developer().destination)
        assertEquals(SettingsDestination.LANGUAGE, settings.languages().destination)
        for (child in listOf(settings.developer(), settings.languages())) {
            assertEquals(settings, child.back())
            assertSame(saved, child.preferences)
            assertEquals(SettingsDestination.CLOSED, child.back().back().destination)
        }
        assertEquals(SettingsDestination.CLOSED, SettingsUiState().destination)
        assertNull(SettingsUiState().preferences)
        assertFalse(SettingsUiState().canEdit)
        for (status in listOf(ResetStatus.REQUESTING, ResetStatus.ACCEPTED)) {
            val locked = settings.copy(reset = status)
            assertFalse(locked.canEdit)
            assertSame(locked, locked.back())
            assertSame(locked, locked.developer())
            assertSame(locked, locked.languages())
            assertSame(locked, locked.open())
        }
        assertFalse(SettingsUiState(preferences = AppPreferences(), writing = true).canEdit)
        assertFalse(SettingsUiState(preferences = AppPreferences(), storageError = true).canEdit)
    }

    @Test fun selectionAndPendingOrVisibleTutorialsExcludeSettings() {
        val method = MeasurementMethod.KNOWN_OBJECT_HEIGHT
        val inactive = MeasurementUiState(selection = MeasurementSelection().openModes())
        assertTrue(inactive.canOpenSettings)
        assertTrue(MeasurementUiState().canOpenSettings)
        val active = inactive.copy(selection = inactive.selection.openModes().browse(method.mode).activate(method))
        assertTrue(active.canOpenSettings)
        assertFalse(active.copy(tutorial = TutorialState(pendingMethod = method)).canOpenSettings)
        assertFalse(active.copy(tutorial = TutorialState(card = TutorialCard(1, method))).canOpenSettings)
        assertTrue(active.copy(selection = active.selection.openModes()).canOpenSettings)
    }

    @Test fun languageApplicationKeepsTheSelectorVisibleUntilExplicitBack() {
        val settings = SettingsUiState(preferences = AppPreferences()).open().languages()
        val requests = mutableListOf<AppLanguage>()
        for (language in listOf(AppLanguage.SPANISH, AppLanguage.ENGLISH, AppLanguage.DEVICE, AppLanguage.ENGLISH)) {
            settings.selectLanguage(language) { choice ->
                // Locale application must keep the language selector active.
                assertEquals(SettingsDestination.LANGUAGE, settings.destination)
                requests += choice
            }
            assertEquals(SettingsDestination.LANGUAGE, settings.destination)
        }
        assertEquals(listOf(AppLanguage.SPANISH, AppLanguage.ENGLISH, AppLanguage.DEVICE, AppLanguage.ENGLISH), requests)
        assertEquals(SettingsDestination.MAIN, settings.back().destination)
        for (blocked in listOf(settings.back(), settings.back().back(),
            settings.copy(reset = ResetStatus.REQUESTING), settings.copy(reset = ResetStatus.ACCEPTED))) {
            blocked.selectLanguage(AppLanguage.SPANISH) { fail("Locale change outside an unlocked language selector") }
        }
    }

    @Test fun globalUnitChangesAndSettingsBrowsingLeaveCapturesAndCalculatedResultUntouched() {
        val workflow = KnownHeightWorkflow()
        workflow.setHeight(heightInMeters("6,125", MeasurementUnits.IMPERIAL.heightUnit)!!)
        workflow.cameraReady(true)
        val quality = OrientationQuality(13, 480_000_000L, 0, 0.0, 0.0, 0.0, 3, CaptureStatus.READY)
        fun observation(z: Double, time: Long) = OrientationObservation(
            ViewingDirection(1.0, 0.0, z), time, "frame", OrientationSource.GAME_ROTATION_VECTOR, quality, "camera")
        val top = observation(0.5, 2_000_000_000L)
        val bottom = observation(0.0, 3_000_000_000L)
        workflow.update(top, "frame", true, top.timestampNanos)
        workflow.capture(CaptureResult.Captured(top), top.timestampNanos)
        workflow.update(bottom, "frame", true, bottom.timestampNanos)
        workflow.capture(CaptureResult.Captured(bottom), bottom.timestampNanos)
        val before = workflow.state
        assertNotNull(before.result)
        val method = MeasurementMethod.KNOWN_OBJECT_HEIGHT
        val screen = MeasurementUiState(selection = MeasurementSelection().browse(method.mode).activate(method),
            measurement = before)
        for (destination in SettingsDestination.entries) {
            for (units in MeasurementUnits.entries) {
                val settings = SettingsUiState(AppPreferences(units), destination)
                assertEquals(6.125, MeasurementUnits.IMPERIAL.heightUnit.fromMeters(before.heightMeters!!), 1e-12)
                assertTrue(settings.preferences!!.units.heightUnit.fromMeters(before.result!!.horizontalDistanceMeters) > 0)
                assertSame(before, workflow.state)
                assertSame(before.result, screen.measurement.result)
                assertSame(top, screen.measurement.top)
                assertSame(bottom, screen.measurement.bottom)
                assertTrue(screen.requiresCamera)
                assertFalse(screen.acquisitionEligible)
            }
        }
        // Resource interruption preserves the completed immutable measurement.
        workflow.cameraReady(false)
        assertEquals(before.heightMeters, workflow.state.heightMeters)
        assertSame(before.top, workflow.state.top)
        assertSame(before.result, workflow.state.result)
        assertEquals(MeasurementUnits.METRIC, MeasurementUnits.IMPERIAL.switched())
        assertEquals(MeasurementUnits.IMPERIAL, MeasurementUnits.METRIC.switched())
    }
}
