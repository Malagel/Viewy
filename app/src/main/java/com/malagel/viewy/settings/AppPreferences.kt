package com.malagel.viewy.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.malagel.viewy.measurement.HeightUnit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.emitAll

enum class MeasurementUnits(val heightUnit: HeightUnit) {
    METRIC(HeightUnit.METERS), IMPERIAL(HeightUnit.FEET);

    fun switched() = if (this == METRIC) IMPERIAL else METRIC
}

data class AppPreferences(
    val units: MeasurementUnits = MeasurementUnits.METRIC,
    val diagnosticsEnabled: Boolean = false,
    val onboardingVersion: Int = 0,
) {
    val onboardingCompleted get() = onboardingVersion >= CURRENT_ONBOARDING_VERSION

    companion object { const val CURRENT_ONBOARDING_VERSION = 1 }
}

// One application-context instance per file. Language belongs to Android/AppCompat.
private val Context.settingsDataStore by preferencesDataStore(name = "settings")

class AppPreferencesRepository(
    private val store: DataStore<Preferences>,
    private val initialUnits: () -> MeasurementUnits = { MeasurementUnits.METRIC },
) {
    constructor(context: Context) : this(context.applicationContext.settingsDataStore, ::deviceMeasurementUnits)

    // Initialize atomically before publishing anything. Existing keys (including legacy
    // saved choices) always win, even if a concurrent Settings edit arrives first.
    val preferences: Flow<AppPreferences> = flow {
        store.edit { values -> if (values[UNITS] == null) values[UNITS] = initialUnits().name }
        emitAll(store.data.map { values ->
            AppPreferences(
                units = MeasurementUnits.entries.firstOrNull { it.name == values[UNITS] } ?: MeasurementUnits.METRIC,
                diagnosticsEnabled = values[DIAGNOSTICS] ?: false,
                onboardingVersion = values[ONBOARDING_VERSION] ?: 0,
            )
        })
    }

    suspend fun switchUnits() {
        store.edit { values ->
            val current = MeasurementUnits.entries.firstOrNull { it.name == values[UNITS] }
                ?: if (values[UNITS] == null) initialUnits() else MeasurementUnits.METRIC
            values[UNITS] = current.switched().name
        }
    }

    suspend fun setDiagnostics(enabled: Boolean) { store.edit { it[DIAGNOSTICS] = enabled } }

    suspend fun completeOnboarding() {
        store.edit { it[ONBOARDING_VERSION] = maxOf(it[ONBOARDING_VERSION] ?: 0, AppPreferences.CURRENT_ONBOARDING_VERSION) }
    }

    private companion object {
        val UNITS = stringPreferencesKey("measurement_units")
        val DIAGNOSTICS = booleanPreferencesKey("diagnostics_enabled")
        val ONBOARDING_VERSION = intPreferencesKey("onboarding_version")
    }
}
