package com.lisdatasolutions.microrag.feature.settings

import androidx.annotation.StringRes
import com.lisdatasolutions.microrag.R
import com.lisdatasolutions.microrag.domain.settings.ThemePreference
import com.lisdatasolutions.microrag.domain.settings.ThemeSettings

/** Tema (F16 data-model §4). Mirrors [LanguagesUiState]. */
data class ThemeUiState(
    /** Null while loading. */
    val saved: ThemeSettings? = null,
    /** What the user marked, kept in the SavedStateHandle; follows a new read while untouched (U1). */
    val selected: ThemePreference? = null,
    val saving: Boolean = false,
    /** A4: the previous theme is kept and «Guardar preferencias» can be tapped again. */
    val saveFailed: Boolean = false,
)

sealed interface ThemeEvent {
    /** Back to Ajustes; the system recreates the activities in the new theme, never the screen (research R5). */
    data object Close : ThemeEvent
}

/** The name of a theme, in Tema and as the subtitle of the «Tema» row (A1, A2). */
@StringRes
internal fun ThemePreference.label(): Int = when (this) {
    ThemePreference.SYSTEM -> R.string.theme_system
    ThemePreference.LIGHT -> R.string.theme_light
    ThemePreference.DARK -> R.string.theme_dark
}
