package com.lisdatasolutions.microrag.domain.settings

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/** What Tema and the «Tema» row open with (F16 theme-api P2): the stored copy, read again each time. */
class ObserveThemeSettingsUseCase(private val store: AppThemeStore) {
    operator fun invoke(): Flow<ThemeSettings> = flow { emit(store.read()) }
}

/**
 * «Guardar preferencias» in Tema (theme-api P3, research R3): the user's intent is written first, then the
 * system applies it. A refused apply puts the previous copy back; a good save leaves «Preferencias
 * guardadas» for the shell to announce once the recreation, if any, is over.
 */
class SaveThemeSettingsUseCase(
    private val store: AppThemeStore,
    private val controller: AppThemeController,
    private val notices: SettingsNotices,
) {
    suspend operator fun invoke(preference: ThemePreference): ThemeSaveResult {
        val previous = store.read()
        // An unreadable copy is always written, so a save is what fixes it (FR-016).
        if (!previous.readFailed && previous.preference == preference) return ThemeSaveResult.Unchanged
        if (!store.write(preference)) return ThemeSaveResult.Failed
        if (!controller.apply(preference)) {
            store.write(if (previous.readFailed) ThemePreference.SYSTEM else previous.preference)
            return ThemeSaveResult.Failed
        }
        notices.announceSaved(settle = true)
        return ThemeSaveResult.Saved
    }
}

/**
 * Once per process start, off the main thread (theme-api P5): the copy is applied again, so a process
 * that died between writing it and applying it still shows what the row says. An unreadable copy applies
 * SYSTEM, which is what Tema then marks (FR-016). Re-applying the value in force recreates nothing (T015).
 */
class ReconcileThemeUseCase(
    private val store: AppThemeStore,
    private val controller: AppThemeController,
) {
    suspend operator fun invoke() {
        val stored = store.read()
        controller.apply(if (stored.readFailed) ThemePreference.SYSTEM else stored.preference)
    }
}
