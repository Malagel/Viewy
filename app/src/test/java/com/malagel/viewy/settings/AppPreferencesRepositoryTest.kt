package com.malagel.viewy.settings

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class AppPreferencesRepositoryTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun defaultsEditsAndReopeningUseRealDataStorePersistence() = runBlocking {
        val file = File(temporary.root, "settings.preferences_pb")
        suspend fun withRepository(block: suspend (AppPreferencesRepository) -> Unit) {
            val job = SupervisorJob()
            val store = PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.IO + job), produceFile = { file })
            try { block(AppPreferencesRepository(store)) } finally { job.cancelAndJoin() }
        }
        withRepository { repository ->
            assertEquals(AppPreferences(), repository.preferences.first())
            repository.switchUnits()
            repository.setDiagnostics(true)
            assertEquals(AppPreferences(MeasurementUnits.IMPERIAL, true), repository.preferences.first())
        }
        withRepository { repository ->
            assertEquals(AppPreferences(MeasurementUnits.IMPERIAL, true), repository.preferences.first())
            repository.switchUnits()
            assertEquals(AppPreferences(MeasurementUnits.METRIC, true), repository.preferences.first())
            repository.setDiagnostics(false)
            assertEquals(AppPreferences(), repository.preferences.first())
        }
    }

    @Test fun aFreshStorageLocationStartsWithFreshDefaults() = runBlocking {
        val job = SupervisorJob()
        val store = PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.IO + job),
            produceFile = { File(temporary.root, "fresh.preferences_pb") })
        try { assertEquals(AppPreferences(), AppPreferencesRepository(store).preferences.first()) }
        finally { job.cancelAndJoin() }
        // No test calls Android's data-clear API or deletes application storage.
    }
}
