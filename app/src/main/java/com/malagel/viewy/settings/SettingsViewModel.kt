package com.malagel.viewy.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import java.io.IOException
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

enum class SettingsDestination {
    CLOSED, MAIN, LANGUAGE, DEVELOPER;

    fun back() = when (this) {
        LANGUAGE, DEVELOPER -> MAIN
        MAIN, CLOSED -> CLOSED
    }
}

data class SettingsUiState(
    // Null is loading/unavailable, never a speculative default shown before disk is read.
    val preferences: AppPreferences? = null,
    val destination: SettingsDestination = SettingsDestination.CLOSED,
    val storageError: Boolean = false,
    val writing: Boolean = false,
    val reset: ResetStatus = ResetStatus.IDLE,
) {
    val resetLocked get() = reset == ResetStatus.REQUESTING || reset == ResetStatus.ACCEPTED
    val canEdit get() = preferences != null && !storageError && !writing && !resetLocked

    fun open() = navigate(SettingsDestination.MAIN)
    fun developer() = if (destination == SettingsDestination.MAIN) navigate(SettingsDestination.DEVELOPER) else this
    fun languages() = if (destination == SettingsDestination.MAIN) navigate(SettingsDestination.LANGUAGE) else this
    fun back() = navigate(destination.back())
    fun selectLanguage(language: AppLanguage, apply: (AppLanguage) -> Unit) {
        // Language changes update the current screen in place. Only an explicit
        // Back action should navigate from LANGUAGE to main settings.
        if (destination == SettingsDestination.LANGUAGE && !resetLocked) apply(language)
    }
    private fun navigate(next: SettingsDestination) = if (resetLocked) this else copy(destination = next)
}

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = AppPreferencesRepository(application)
    private val resetController = ResetController(androidAppDataReset(application))
    private val mutableState = MutableStateFlow(SettingsUiState())
    val state = mutableState.asStateFlow()
    private var reading: Job? = null

    init { retryLoading() }

    fun retryLoading() {
        if (mutableState.value.resetLocked || mutableState.value.writing) return
        reading?.cancel()
        mutableState.value = mutableState.value.copy(storageError = false)
        reading = viewModelScope.launch {
            try {
                repository.preferences.collect {
                    mutableState.value = mutableState.value.copy(preferences = it, storageError = false)
                }
            } catch (_: IOException) {
                mutableState.value = mutableState.value.copy(storageError = true)
            }
        }
    }

    fun open() { mutableState.value = mutableState.value.open() }
    fun developer() { mutableState.value = mutableState.value.developer() }
    fun languages() { mutableState.value = mutableState.value.languages() }
    fun back() { mutableState.value = mutableState.value.back() }
    fun selectLanguage(language: AppLanguage) { mutableState.value.selectLanguage(language, AppLanguages::select) }

    fun switchUnits() = write { repository.switchUnits() }
    fun setDiagnostics(enabled: Boolean) = write { repository.setDiagnostics(enabled) }
    fun completeOnboarding() = write { repository.completeOnboarding() }
    private fun write(action: suspend () -> Unit) {
        if (!mutableState.value.canEdit) return
        mutableState.value = mutableState.value.copy(writing = true)
        viewModelScope.launch {
            try {
                action()
                mutableState.value = mutableState.value.copy(preferences = repository.preferences.first())
            } catch (_: IOException) {
                mutableState.value = mutableState.value.copy(storageError = true)
            } finally {
                mutableState.value = mutableState.value.copy(writing = false)
            }
        }
    }

    fun askReset() {
        if (mutableState.value.destination != SettingsDestination.DEVELOPER || mutableState.value.writing) return
        resetController.ask()
        publishReset()
    }
    fun cancelReset() { resetController.cancel(); publishReset() }
    fun dismissResetError() { resetController.dismissError(); publishReset() }
    fun confirmReset() {
        if (mutableState.value.reset != ResetStatus.CONFIRMING) return
        // Lock the UI immediately, including while the preference collector is stopping.
        mutableState.value = mutableState.value.copy(reset = ResetStatus.REQUESTING)
        viewModelScope.launch {
            reading?.cancelAndJoin()
            resetController.confirm()
            publishReset()
            // No preferences, locale writes, relaunch or success claim after acceptance.
            if (resetController.status == ResetStatus.FAILED) retryLoading()
        }
    }
    private fun publishReset() { mutableState.value = mutableState.value.copy(reset = resetController.status) }
}
