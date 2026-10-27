package com.malagel.viewy.settings

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

enum class AppLanguage(val tags: String) {
    DEVICE(""), ENGLISH("en"), SPANISH("es");

    companion object {
        fun fromTags(tags: String): AppLanguage = when (tags.substringBefore(',').substringBefore('-')) {
            "en" -> ENGLISH
            "es" -> SPANISH
            else -> DEVICE
        }
    }
}

/** Android is the only source of truth, including changes made in system Settings. */
object AppLanguages {
    fun current(): AppLanguage = AppLanguage.fromTags(AppCompatDelegate.getApplicationLocales().toLanguageTags())
    fun select(language: AppLanguage) {
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(language.tags))
    }
}
