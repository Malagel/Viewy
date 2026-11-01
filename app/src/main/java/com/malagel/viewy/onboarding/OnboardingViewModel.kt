package com.malagel.viewy.onboarding

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class OnboardingPage { WELCOME, PERMISSIONS, COMPLETION }
enum class OnboardingPermission { CAMERA }
enum class PermissionStatus { NOT_REQUESTED, REQUESTING, DENIED, GRANTED }

data class OnboardingState(
    val page: OnboardingPage = OnboardingPage.WELCOME,
    val permissions: Map<OnboardingPermission, PermissionStatus> = OnboardingPermission.entries.associateWith { PermissionStatus.NOT_REQUESTED },
) {
    val requesting get() = PermissionStatus.REQUESTING in permissions.values
    val canContinue get() = !requesting
    fun next(from: OnboardingPage = page) = if (!canContinue || from != page) this else copy(page = when (page) {
        OnboardingPage.WELCOME -> OnboardingPage.PERMISSIONS
        OnboardingPage.PERMISSIONS, OnboardingPage.COMPLETION -> OnboardingPage.COMPLETION
    })
    fun back(from: OnboardingPage = page) = if (requesting || from != page) this else copy(page = when (page) {
        OnboardingPage.COMPLETION -> OnboardingPage.PERMISSIONS
        OnboardingPage.PERMISSIONS, OnboardingPage.WELCOME -> OnboardingPage.WELCOME
    })
    fun request(permission: OnboardingPermission): OnboardingState =
        if (page != OnboardingPage.PERMISSIONS || requesting || permissions[permission] == PermissionStatus.GRANTED) this
        else copy(permissions = permissions + (permission to PermissionStatus.REQUESTING))
    fun result(permission: OnboardingPermission, granted: Boolean) = copy(permissions = permissions +
        (permission to if (granted) PermissionStatus.GRANTED else PermissionStatus.DENIED))
    fun refresh(permission: OnboardingPermission, granted: Boolean): OnboardingState = when {
        granted -> copy(permissions = permissions + (permission to PermissionStatus.GRANTED))
        permissions[permission] == PermissionStatus.GRANTED -> copy(permissions = permissions + (permission to PermissionStatus.NOT_REQUESTED))
        else -> this
    }
    fun finish(persist: () -> Unit) {
        if (page == OnboardingPage.COMPLETION && !requesting) persist()
    }
}

/** Temporary navigation survives Activity recreation; only the final DataStore write survives restart. */
class OnboardingViewModel : ViewModel() {
    private val mutableState = MutableStateFlow(OnboardingState())
    val state = mutableState.asStateFlow()

    fun next(from: OnboardingPage) { mutableState.value = mutableState.value.next(from) }
    fun back(from: OnboardingPage) { mutableState.value = mutableState.value.back(from) }
    fun finish(persist: () -> Unit) { mutableState.value.finish(persist) }
    fun request(permission: OnboardingPermission): Boolean {
        val before = mutableState.value
        mutableState.value = before.request(permission)
        return mutableState.value != before
    }
    fun result(permission: OnboardingPermission, granted: Boolean) { mutableState.value = mutableState.value.result(permission, granted) }
    fun refresh(permission: OnboardingPermission, granted: Boolean) { mutableState.value = mutableState.value.refresh(permission, granted) }
}
