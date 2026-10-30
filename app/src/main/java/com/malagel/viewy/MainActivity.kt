package com.malagel.viewy

import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.malagel.viewy.settings.SettingsViewModel
import com.malagel.viewy.settings.ResetStatus
import com.malagel.viewy.settings.SettingsDestination
import com.malagel.viewy.ui.SettingsScreen
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import com.malagel.viewy.ui.MeasurementViewModel
import com.malagel.viewy.ui.ViewyScreen
import com.malagel.viewy.ui.theme.ViewyTheme

class MainActivity : AppCompatActivity() {
    private val measurement by lazy { ViewModelProvider(this)[MeasurementViewModel::class.java] }

    private val settings by lazy { ViewModelProvider(this)[SettingsViewModel::class.java] }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        setContent {
            ViewyTheme {
                val state by measurement.state.collectAsStateWithLifecycle()
                val preferences by settings.state.collectAsStateWithLifecycle()
                val loaded = preferences.preferences
                when {
                    preferences.reset == ResetStatus.ACCEPTED -> Surface(Modifier.fillMaxSize()) {
                        Box(contentAlignment = Alignment.Center) { Text(stringResource(R.string.reset_requested)) }
                    }
                    loaded != null -> {
                        ViewyScreen(state, measurement, loaded.units.heightUnit, settings::open,
                            diagnosticsEnabled = loaded.diagnosticsEnabled,
                            settingsOpen = preferences.destination != SettingsDestination.CLOSED,
                            settingsContent = { backdropOnly, interactive -> SettingsScreen(preferences, settings, backdropOnly, interactive) },
                            animateHomeEntry = savedInstanceState == null,
                        )
                    }
                    else -> Surface(Modifier.fillMaxSize()) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                            if (preferences.storageError) {
                                Text(stringResource(R.string.settings_storage_error))
                                TextButton(onClick = settings::retryLoading) { Text(stringResource(R.string.retry)) }
                            } else CircularProgressIndicator()
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        measurement.resume()
    }

    override fun onPause() {
        measurement.pause()
        super.onPause()
    }
}
