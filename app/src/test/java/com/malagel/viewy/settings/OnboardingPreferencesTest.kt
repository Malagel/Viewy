package com.malagel.viewy.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import java.io.File
import java.io.IOException
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class OnboardingPreferencesTest {
    @get:Rule val temporary = TemporaryFolder()

    private suspend fun withStore(file: File, block: suspend (DataStore<Preferences>) -> Unit) {
        val job = SupervisorJob()
        val store = PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.IO + job), produceFile = { file })
        try { block(store) } finally { job.cancelAndJoin() }
    }

    @Test fun incompleteRestartCompletedRestartAndFreshAppData() = runBlocking {
        val file = File(temporary.root, "settings.preferences_pb")
        withStore(file) { store ->
            val repository = AppPreferencesRepository(store) { MeasurementUnits.IMPERIAL }
            assertFalse(repository.preferences.first().onboardingCompleted)
            repository.setDiagnostics(true)
            // Exiting before the final action does not write completion.
        }
        withStore(file) { store ->
            val repository = AppPreferencesRepository(store) { error("Must retain initialized units") }
            assertEquals(AppPreferences(MeasurementUnits.IMPERIAL, true), repository.preferences.first())
            repository.completeOnboarding()
            val saved = repository.preferences.first()
            assertTrue(saved.onboardingCompleted)
            assertEquals(AppPreferences.CURRENT_ONBOARDING_VERSION, saved.onboardingVersion)
            assertEquals(MeasurementUnits.IMPERIAL, saved.units)
            assertTrue(saved.diagnosticsEnabled)
        }
        withStore(file) { store -> assertTrue(AppPreferencesRepository(store).preferences.first().onboardingCompleted) }
        withStore(File(temporary.root, "reset.preferences_pb")) { store ->
            val defaults = AppPreferencesRepository(store) { MeasurementUnits.METRIC }.preferences.first()
            assertEquals(AppPreferences(), defaults)
            assertFalse(defaults.onboardingCompleted)
        }
    }

    @Test fun regionalInitializationPublishesCorrectFirstSnapshotAndSettingsAlwaysWins() = runBlocking {
        withStore(File(temporary.root, "settings.preferences_pb")) { store ->
            var detections = 0
            val repository = AppPreferencesRepository(store) { detections++; MeasurementUnits.IMPERIAL }
            assertEquals(MeasurementUnits.IMPERIAL, repository.preferences.first().units)
            assertEquals(1, detections)
            assertEquals(MeasurementUnits.IMPERIAL, repository.preferences.first().units)
            assertEquals(1, detections)
            repository.switchUnits()
            assertEquals(MeasurementUnits.METRIC, repository.preferences.first().units)
            assertEquals(MeasurementUnits.METRIC, AppPreferencesRepository(store) { error("Settings override lost") }.preferences.first().units)
            repository.completeOnboarding()
            assertEquals(MeasurementUnits.METRIC, repository.preferences.first().units)
            assertEquals(1, detections)
        }
    }

    @Test fun upgradedExplicitPreferencesAndNewerCompletionVersionsArePreserved() = runBlocking {
        for (units in MeasurementUnits.entries) withStore(File(temporary.root, "${units.name}.preferences_pb")) { store ->
            store.edit {
                it[stringPreferencesKey("measurement_units")] = units.name
                it[booleanPreferencesKey("diagnostics_enabled")] = true
            }
            val repository = AppPreferencesRepository(store) { error("Existing explicit preference must win") }
            assertEquals(AppPreferences(units, true), repository.preferences.first())
            store.edit { it[intPreferencesKey("onboarding_version")] = 2 }
            repository.completeOnboarding()
            assertEquals(AppPreferences(units, true, 2), repository.preferences.first())
        }
    }

    @Test fun aSettingsWriteBeforeFirstCollectionTakesPriorityOverRegionalDefaults() = runBlocking {
        withStore(File(temporary.root, "settings.preferences_pb")) { store ->
            val repository = AppPreferencesRepository(store) { MeasurementUnits.IMPERIAL }
            repository.switchUnits()
            assertEquals(MeasurementUnits.METRIC, repository.preferences.first().units)
        }
    }

    private class ControlledStore(val delegate: DataStore<Preferences>) : DataStore<Preferences> {
        override val data: Flow<Preferences> get() = delegate.data
        var fail = false
        var hold = false
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences {
            if (fail) throw IOException("Storage unavailable")
            if (hold) { started.complete(Unit); release.await() }
            return delegate.updateData(transform)
        }
    }

    @Test fun initializationFailurePublishesNoSpeculativeUnitsAndCanBeRetried() = runBlocking {
        withStore(File(temporary.root, "settings.preferences_pb")) { real ->
            val store = ControlledStore(real).also { it.fail = true }
            val repository = AppPreferencesRepository(store) { MeasurementUnits.IMPERIAL }
            var published = false
            try {
                repository.preferences.onEach { published = true }.first()
                fail("Expected storage error")
            } catch (_: IOException) { }
            assertFalse(published)
            store.fail = false
            assertEquals(MeasurementUnits.IMPERIAL, repository.preferences.first().units)
        }
    }

    @Test fun failedOrInterruptedCompletionDoesNotCompleteAndRetryPersists() = runBlocking {
        val file = File(temporary.root, "settings.preferences_pb")
        withStore(file) { real ->
            val store = ControlledStore(real)
            val repository = AppPreferencesRepository(store)
            assertFalse(repository.preferences.first().onboardingCompleted)
            store.fail = true
            try { repository.completeOnboarding(); fail("Expected storage error") } catch (_: IOException) { }
            assertNull(real.data.first()[intPreferencesKey("onboarding_version")])
            store.fail = false
            store.hold = true
            val interrupted = launch { repository.completeOnboarding() }
            store.started.await()
            assertNull(real.data.first()[intPreferencesKey("onboarding_version")])
            interrupted.cancelAndJoin()
        }
        withStore(file) { real ->
            val repository = AppPreferencesRepository(real)
            assertFalse(repository.preferences.first().onboardingCompleted)
            repository.completeOnboarding()
            assertTrue(repository.preferences.first().onboardingCompleted)
        }
    }

    @Test fun completionIsNotPublishedBeforeStorageCommitFinishes() = runBlocking {
        withStore(File(temporary.root, "settings.preferences_pb")) { real ->
            val store = ControlledStore(real)
            val repository = AppPreferencesRepository(store)
            assertFalse(repository.preferences.first().onboardingCompleted)
            store.hold = true
            val completing = launch { repository.completeOnboarding() }
            store.started.await()
            assertFalse(completing.isCompleted)
            assertNull(real.data.first()[intPreferencesKey("onboarding_version")])
            store.hold = false
            store.release.complete(Unit)
            completing.join()
            assertTrue(repository.preferences.first().onboardingCompleted)
        }
    }
}
