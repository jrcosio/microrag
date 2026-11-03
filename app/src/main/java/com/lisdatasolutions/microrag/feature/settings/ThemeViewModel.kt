package com.lisdatasolutions.microrag.feature.settings

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lisdatasolutions.microrag.domain.settings.ObserveThemeSettingsUseCase
import com.lisdatasolutions.microrag.domain.settings.SaveThemeSettingsUseCase
import com.lisdatasolutions.microrag.domain.settings.ThemePreference
import com.lisdatasolutions.microrag.domain.settings.ThemeSaveResult
import com.lisdatasolutions.microrag.domain.settings.ThemeSettings
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Tema (F16 theme-ui U1–U4), shaped as [LanguagesViewModel]: marking only changes the selection, which
 * survives rotations and recreations; a second tap while saving does nothing; a successful or empty save
 * closes the screen. The close event waits in this ViewModel, which outlives the recreation the new theme
 * brings.
 */
class ThemeViewModel(
    private val observe: ObserveThemeSettingsUseCase,
    private val save: SaveThemeSettingsUseCase,
    private val savedState: SavedStateHandle,
) : ViewModel() {

    private val stored = MutableStateFlow<ThemeSettings?>(null)
    private val saving = MutableStateFlow(false)
    private val saveFailed = MutableStateFlow(false)
    private val closing = Channel<ThemeEvent>(Channel.BUFFERED)

    val events: Flow<ThemeEvent> = closing.receiveAsFlow()

    val state: StateFlow<ThemeUiState> = combine(
        stored,
        savedState.getStateFlow<String?>(SELECTED, null),
        saving,
        saveFailed,
    ) { stored, selected, saving, failed ->
        ThemeUiState(saved = stored, selected = selected?.let(ThemePreference::fromName), saving = saving, saveFailed = failed)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, ThemeUiState())

    init {
        load()
    }

    /** The screen is shown again, maybe recreated in the new theme (U1). */
    fun onStart() = load()

    private fun load() {
        viewModelScope.launch {
            val settings = observe().first()
            stored.value = settings
            if (savedState.get<Boolean>(TOUCHED) != true) savedState[SELECTED] = settings.preference.name
        }
    }

    fun onSelect(preference: ThemePreference) {
        savedState[TOUCHED] = true
        savedState[SELECTED] = preference.name
    }

    fun onSave() {
        if (saving.value) return
        val preference = savedState.get<String>(SELECTED)?.let(ThemePreference::fromName) ?: return
        saving.value = true
        saveFailed.value = false
        viewModelScope.launch {
            val result = save(preference)
            saving.value = false
            when (result) {
                ThemeSaveResult.Failed -> saveFailed.value = true
                ThemeSaveResult.Unchanged -> closing.send(ThemeEvent.Close)
                ThemeSaveResult.Saved -> {
                    stored.value = ThemeSettings(preference)
                    closing.send(ThemeEvent.Close)
                }
            }
        }
    }

    fun onSaveFailureShown() {
        saveFailed.value = false
    }

    private companion object {
        const val SELECTED = "theme_selected"
        const val TOUCHED = "theme_touched"
    }
}
