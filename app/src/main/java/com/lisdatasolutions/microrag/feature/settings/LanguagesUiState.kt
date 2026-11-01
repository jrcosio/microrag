package com.lisdatasolutions.microrag.feature.settings

import com.lisdatasolutions.microrag.domain.settings.AppLanguage
import com.lisdatasolutions.microrag.domain.settings.LanguageSettings

/**
 * P10 (data-model §5). The answer language has no state: the chat always answers in Spanish (D-061), and
 * Idiomas shows «Español» as its only option, selected (S12).
 */
data class LanguagesUiState(
    /** Null while loading. */
    val saved: LanguageSettings? = null,
    /** What the user marked, kept in the SavedStateHandle; follows a system change while untouched (P19). */
    val selectedApp: AppLanguage? = null,
    val saving: Boolean = false,
    /** S8: the previous preferences are kept and «Guardar preferencias» can be tapped again. */
    val saveFailed: Boolean = false,
)

sealed interface LanguagesEvent {
    /** Back to where Idiomas was opened from; [recreate] on Android 12, where the screen recreates itself. */
    data class Close(val recreate: Boolean) : LanguagesEvent
}
