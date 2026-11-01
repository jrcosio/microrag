package com.lisdatasolutions.microrag.core.locale

import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import java.io.File
import java.io.IOException
import java.util.Locale

/**
 * The app speaks Spanish unless the user picked another app language (D-029); the device language
 * alone never switches it (F12 P1). Where the choice lives depends on the version (research R2):
 * - Android 13+: the system's per-app language (`LocaleManager`), the same one its settings show;
 * - Android 12: a private file without backup, read once in [init] before StrictMode.
 */
object AppLocale {
    val DEFAULT: Locale = Locale.forLanguageTag("es")

    /** Tags the app has texts for; anything else reads as Spanish. */
    private val SUPPORTED = setOf("es", "en")

    @Volatile
    private var stored: String? = null

    /** True when the Android 12 file exists but could not be read, so Spanish is in use (S8). */
    @Volatile
    var readFailed: Boolean = false
        private set

    /** Where Android 12 keeps the choice: one line, `es` or `en`, out of every backup (constitution I). */
    fun file(context: Context): File = File(context.noBackupFilesDir, "settings/app_language")

    /** Reads the Android 12 choice into memory; call it from `attachBaseContext`, before StrictMode (P3). */
    fun init(base: Context) {
        stored = null
        readFailed = false
        if (perAppLanguages()) return
        val file = file(base)
        if (!file.exists()) return
        try {
            stored = file.readText().trim().takeIf { it in SUPPORTED }
        } catch (e: IOException) {
            readFailed = true
        }
    }

    fun resolve(context: Context): Locale {
        if (perAppLanguages()) {
            val chosen = context.getSystemService(LocaleManager::class.java)?.applicationLocales
            if (chosen != null && !chosen.isEmpty) return chosen[0]
        } else {
            stored?.let { return Locale.forLanguageTag(it) }
        }
        return DEFAULT
    }

    /** [base] with its configuration set to the app language; use it in `attachBaseContext`. */
    fun wrap(base: Context): Context {
        val configuration = Configuration(base.resources.configuration).apply { setLocales(LocaleList(resolve(base))) }
        return base.createConfigurationContext(configuration)
    }

    /**
     * [context] in the app language of this moment, for texts built outside an activity such as
     * notifications (P5): unlike the context wrapped at startup, it follows a change without a restart.
     */
    fun localized(context: Context): Context = wrap(context)

    /** The Android 12 choice after a successful write ([PlatformAppLanguageController]). */
    internal fun remember(tag: String) {
        stored = tag.takeIf { it in SUPPORTED }
        readFailed = false
    }

    /**
     * P4, from Android 13: a file left by Android 12 becomes the system's per-app language when the
     * system has none yet, and is deleted, so it is applied only once. A choice already in the system wins.
     */
    fun migrate(context: Context) {
        if (!perAppLanguages()) return
        val file = file(context)
        if (!file.exists()) return
        val manager = context.getSystemService(LocaleManager::class.java)
        if (manager != null && manager.applicationLocales.isEmpty) {
            val tag = try {
                file.readText().trim().takeIf { it in SUPPORTED }
            } catch (e: IOException) {
                null
            }
            if (tag != null) manager.applicationLocales = LocaleList.forLanguageTags(tag)
        }
        file.delete()
    }

    private fun perAppLanguages(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
}
