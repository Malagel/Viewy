package com.malagel.viewy.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.malagel.viewy.measurement.HeightUnit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class MeasurementUnits(val heightUnit: HeightUnit) {
    METRIC(HeightUnit.METERS), IMPERIAL(HeightUnit.FEET);

    fun switched() = if (this == METRIC) IMPERIAL else METRIC
}

data class AppPreferences(
    val units: MeasurementUnits = MeasurementUnits.METRIC,
    val diagnosticsEnabled: Boolean = false,
)

// One application-context instance per file. Language belongs to Android/AppCompat.
private val Context.settingsDataStore by preferencesDataStore(name = "settings")

class AppPreferencesRepository(private val store: DataStore<Preferences>) {
    constructor(context: Context) : this(context.applicationContext.settingsDataStore)

    val preferences: Flow<AppPreferences> = store.data.map { values ->
        AppPreferences(
            units = MeasurementUnits.entries.firstOrNull { it.name == values[UNITS] } ?: MeasurementUnits.METRIC,
            diagnosticsEnabled = values[DIAGNOSTICS] ?: false,
        )
    }

    suspend fun switchUnits() {
        store.edit { values ->
            val current = MeasurementUnits.entries.firstOrNull { it.name == values[UNITS] } ?: MeasurementUnits.METRIC
            values[UNITS] = current.switched().name
        }
    }

    suspend fun setDiagnostics(enabled: Boolean) { store.edit { it[DIAGNOSTICS] = enabled } }

    private companion object {
        val UNITS = stringPreferencesKey("measurement_units")
        val DIAGNOSTICS = booleanPreferencesKey("diagnostics_enabled")
    }
}
