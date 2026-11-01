package com.lisdatasolutions.microrag.domain.settings

/**
 * The app language on the platform (F12 contracts/settings-api.md P1–P4): `LocaleManager` on Android 13+,
 * a private file below it. The device language alone never counts (D-029).
 */
interface AppLanguageController {
    fun current(): AppLanguage

    /** True when the Android 12 file exists but could not be read, so Spanish is in use (S8). */
    val readFailed: Boolean

    /** False when it could not be stored (Android 12 file write); nothing changes then. */
    suspend fun apply(language: AppLanguage): Boolean

    /** True from Android 13: the system recreates the activities. Below it the screen calls recreate(). */
    val recreatesItself: Boolean
}
