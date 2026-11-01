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
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import com.malagel.viewy.onboarding.OnboardingViewModel
import com.malagel.viewy.ui.OnboardingScreen
import com.malagel.viewy.ui.NavigationInputGuard
import com.malagel.viewy.ui.MeasurementViewModel
import com.malagel.viewy.ui.ViewyScreen
import com.malagel.viewy.ui.theme.ViewyTheme

class MainActivity : AppCompatActivity() {
    private val measurement by lazy { ViewModelProvider(this)[MeasurementViewModel::class.java] }

    private val settings by lazy { ViewModelProvider(this)[SettingsViewModel::class.java] }
    private val onboarding by lazy { ViewModelProvider(this)[OnboardingViewModel::class.java] }

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
                var onboardingPresented by rememberSaveable { mutableStateOf(false) }
                when {
                    preferences.reset == ResetStatus.ACCEPTED -> Surface(Modifier.fillMaxSize()) {
                        Box(contentAlignment = Alignment.Center) { Text(stringResource(R.string.reset_requested)) }
                    }
                    loaded != null -> {
                        if (!loaded.onboardingCompleted) SideEffect { onboardingPresented = true }
                        val startup = updateTransition(loaded.onboardingCompleted, label = "Application startup")
                        val moving = startup.currentState != startup.targetState || startup.isRunning
                        startup.AnimatedContent(Modifier.fillMaxSize(), transitionSpec = {
                            (fadeIn(tween(180)) togetherWith fadeOut(tween(120))).using(null)
                        }) { completed ->
                            NavigationInputGuard(moving || completed != loaded.onboardingCompleted, Modifier.fillMaxSize()) {
                                if (!completed) {
                                    val introduction by onboarding.state.collectAsStateWithLifecycle()
                                    OnboardingScreen(introduction, onboarding, preferences, settings::completeOnboarding, settings::retryLoading,
                                        animateEntry = savedInstanceState == null)
                                } else {
                                    ViewyScreen(state, measurement, loaded.units.heightUnit, settings::open,
                                        diagnosticsEnabled = loaded.diagnosticsEnabled,
                                        settingsOpen = preferences.destination != SettingsDestination.CLOSED,
                                        settingsContent = { backdropOnly, interactive -> SettingsScreen(preferences, settings, backdropOnly, interactive) },
                                        animateHomeEntry = savedInstanceState == null && !onboardingPresented,
                                    )
                                }
                            }
                        }
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
