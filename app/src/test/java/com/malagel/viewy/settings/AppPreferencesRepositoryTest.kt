package com.malagel.viewy.settings

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.malagel.viewy.domain.MeasurementMethod
import com.malagel.viewy.measurement.MeasurementSelection
import com.malagel.viewy.ui.HelpAction
import com.malagel.viewy.ui.MeasurementUiState
import com.malagel.viewy.ui.helpAction
import com.malagel.viewy.ui.withDiagnostics
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
        val method = MeasurementMethod.KNOWN_OBJECT_HEIGHT
        val measurement = MeasurementUiState(selection = MeasurementSelection().browse(method.mode).activate(method))
        suspend fun snapshot(repository: AppPreferencesRepository): AppPreferences {
            val saved = repository.preferences.first()
            // Reusing the retained measurement with a new Settings collector mirrors
            // recreation; navigation itself never enters the preference file.
            assertEquals(HelpAction.TUTORIAL, helpAction(measurement, false, saved.diagnosticsEnabled))
            assertEquals(if (saved.diagnosticsEnabled) HelpAction.DIAGNOSTICS else HelpAction.NONE,
                helpAction(measurement, true, saved.diagnosticsEnabled))
            assertEquals(saved.diagnosticsEnabled, measurement.withDiagnostics(true, saved.diagnosticsEnabled).diagnosticsOpen)
            return saved
        }
        withRepository { repository ->
            assertEquals(AppPreferences(), snapshot(repository))
            repository.switchUnits()
            repository.setDiagnostics(true)
            assertEquals(AppPreferences(MeasurementUnits.IMPERIAL, true), snapshot(repository))
        }
        withRepository { repository ->
            assertEquals(AppPreferences(MeasurementUnits.IMPERIAL, true), snapshot(repository))
            repository.switchUnits()
            assertEquals(AppPreferences(MeasurementUnits.METRIC, true), snapshot(repository))
            repository.setDiagnostics(false)
            assertEquals(AppPreferences(), snapshot(repository))
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
