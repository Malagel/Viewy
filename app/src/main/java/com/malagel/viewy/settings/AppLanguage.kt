package com.malagel.viewy.settings

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import java.util.Locale

enum class AppLanguage(val tags: String) {
    ENGLISH("en"), SPANISH("es");

    companion object {
        /** Android places app overrides before device preferences; an empty override stays automatic. */
        fun resolve(applicationTags: String, deviceTags: String): AppLanguage =
            sequenceOf(applicationTags, deviceTags)
                .flatMap { it.split(',').asSequence() }
                .map { Locale.forLanguageTag(it).language }
                .firstNotNullOfOrNull { tag -> entries.firstOrNull { it.tags == tag } }
                ?: ENGLISH
    }
}

/** Android is the only source of truth, including changes made in system Settings. */
object AppLanguages {
    fun current(configuredTags: String): AppLanguage = AppLanguage.resolve(
        AppCompatDelegate.getApplicationLocales().toLanguageTags(), configuredTags,
    )

    fun select(language: AppLanguage) {
        // Even selecting the currently effective device language creates a manual override.
        // Never persist an automatically resolved language: empty locales must keep following the device.
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(language.tags))
    }
}
