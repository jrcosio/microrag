package com.lisdatasolutions.microrag.domain.settings

/**
 * The app theme the user picked (D-067): the phone's dark mode unless a save says otherwise. The system
 * applies it; the app keeps a copy only to show it (F16 research R1, R2).
 */
enum class ThemePreference {
    SYSTEM,
    LIGHT,
    DARK,
    ;

    companion object {
        /** The preference of a stored name; SYSTEM for anything else (theme-api P1). */
        fun fromName(name: String?): ThemePreference = entries.firstOrNull { it.name == name } ?: SYSTEM
    }
}

/** What Tema and the «Tema» row show; [readFailed] when the stored copy could not be read (FR-016). */
data class ThemeSettings(
    val preference: ThemePreference,
    val readFailed: Boolean = false,
)

/** Outcome of «Guardar preferencias» in Tema (theme-api P3). */
sealed interface ThemeSaveResult {
    data object Saved : ThemeSaveResult

    data object Unchanged : ThemeSaveResult

    data object Failed : ThemeSaveResult
}
