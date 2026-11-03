package com.lisdatasolutions.microrag.domain.settings

/**
 * The app's copy of the theme it last saved (F16 research R2): the system keeps the real per-app night
 * mode but cannot be asked for it. Never read on the main thread.
 */
interface AppThemeStore {
    /** SYSTEM with readFailed when the copy cannot be read (or was corrupt and got replaced); SYSTEM without a copy. */
    suspend fun read(): ThemeSettings

    /** False when it could not be written; nothing changes then. */
    suspend fun write(preference: ThemePreference): Boolean
}
