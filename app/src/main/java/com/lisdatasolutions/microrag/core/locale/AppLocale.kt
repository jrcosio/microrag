package com.lisdatasolutions.microrag.core.locale

import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import java.util.Locale

/**
 * The app speaks Spanish unless the user picked another app language (D-029). English is a
 * secondary language: on Android 13+ it can be chosen in the system's per-app language settings,
 * and F12 adds the in-app selector. The device language alone never switches the app to English.
 */
object AppLocale {
    val DEFAULT: Locale = Locale.forLanguageTag("es")

    fun resolve(context: Context): Locale {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val chosen = context.getSystemService(LocaleManager::class.java)?.applicationLocales
            if (chosen != null && !chosen.isEmpty) return chosen[0]
        }
        return DEFAULT
    }

    /** [base] with its configuration set to the app language; use it in `attachBaseContext`. */
    fun wrap(base: Context): Context {
        val configuration = Configuration(base.resources.configuration).apply { setLocales(LocaleList(resolve(base))) }
        return base.createConfigurationContext(configuration)
    }
}
