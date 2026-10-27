package com.malagel.viewy.tutorial

import android.content.SharedPreferences
import androidx.core.content.edit

/** A dedicated app-private preference file stores only per-method tutorial completion. */
internal class LocalTutorialSeenStore(private val preferences: SharedPreferences) : TutorialSeenStore {
    override fun hasSeen(methodId: String): Boolean = preferences.getBoolean(key(methodId), false)

    override fun markSeen(methodId: String) {
        preferences.edit { putBoolean(key(methodId), true) }
    }

    private fun key(methodId: String) = "seen.$methodId"

    companion object {
        const val FILE_NAME = "method_tutorials"
    }
}
