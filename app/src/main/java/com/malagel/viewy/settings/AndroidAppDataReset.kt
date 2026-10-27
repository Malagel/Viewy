package com.malagel.viewy.settings

import android.app.ActivityManager
import android.app.LocaleManager
import android.content.Context
import android.os.Build
import android.os.LocaleList

fun androidAppDataReset(context: Context): AppDataReset {
    val app = context.applicationContext
    return LocaleSafeAppDataReset(
        clearUserData = { app.getSystemService(ActivityManager::class.java).clearApplicationUserData() },
        locales = object : ResetLocales {
            // Before API 33, AppCompat's autoStoreLocales file is app-private and will
            // be erased by Android. Do not enqueue a compat disk write/recreation here.
            override fun read(): String = if (Build.VERSION.SDK_INT >= 33) {
                app.getSystemService(LocaleManager::class.java).applicationLocales.toLanguageTags()
            } else ""

            override fun set(tags: String) {
                if (Build.VERSION.SDK_INT >= 33) {
                    app.getSystemService(LocaleManager::class.java).applicationLocales = LocaleList.forLanguageTags(tags)
                }
            }
        },
    )
}
