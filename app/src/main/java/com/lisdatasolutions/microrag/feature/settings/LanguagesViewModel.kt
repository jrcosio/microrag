package com.lisdatasolutions.microrag.feature.settings

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lisdatasolutions.microrag.domain.settings.AppLanguage
import com.lisdatasolutions.microrag.domain.settings.LanguageSettings
import com.lisdatasolutions.microrag.domain.settings.ObserveLanguageSettingsUseCase
import com.lisdatasolutions.microrag.domain.settings.SaveLanguageSettingsUseCase
import com.lisdatasolutions.microrag.domain.settings.SaveResult
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
 * P10 (contracts/settings-api.md, research R4): the selection survives rotations and recreations, a
 * second tap while saving does nothing, and a successful or empty save closes the screen. The close
 * event waits in this ViewModel, which outlives the recreation of Android 13+.
 */
class LanguagesViewModel(
    private val observe: ObserveLanguageSettingsUseCase,
    private val save: SaveLanguageSettingsUseCase,
    private val savedState: SavedStateHandle,
) : ViewModel() {

    private val stored = MutableStateFlow<LanguageSettings?>(null)
    private val saving = MutableStateFlow(false)
    private val saveFailed = MutableStateFlow(false)
    private val closing = Channel<LanguagesEvent>(Channel.BUFFERED)

    val events: Flow<LanguagesEvent> = closing.receiveAsFlow()

    val state: StateFlow<LanguagesUiState> = combine(
        stored,
        savedState.getStateFlow<String?>(SELECTED, null),
        saving,
        saveFailed,
    ) { stored, selected, saving, failed ->
        LanguagesUiState(saved = stored, selectedApp = selected?.let(AppLanguage::fromTag), saving = saving, saveFailed = failed)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, LanguagesUiState())

    init {
        load()
    }

    /** The screen is shown again, maybe recreated after the system changed the app language (P19). */
    fun onStart() = load()

    private fun load() {
        viewModelScope.launch {
            val settings = observe().first()
            stored.value = settings
            if (savedState.get<Boolean>(TOUCHED) != true) savedState[SELECTED] = settings.app.tag
        }
    }

    fun onSelectApp(language: AppLanguage) {
        savedState[TOUCHED] = true
        savedState[SELECTED] = language.tag
    }

    fun onSave() {
        if (saving.value) return
        val app = savedState.get<String>(SELECTED)?.let(AppLanguage::fromTag) ?: return
        saving.value = true
        saveFailed.value = false
        viewModelScope.launch {
            val result = save(app)
            saving.value = false
            when (result) {
                SaveResult.Unchanged -> closing.send(LanguagesEvent.Close(recreate = false))
                SaveResult.Failed -> saveFailed.value = true
                is SaveResult.Saved -> {
                    stored.value = LanguageSettings(app)
                    closing.send(LanguagesEvent.Close(recreate = result.appLanguageChanged && !save.recreatesItself))
                }
            }
        }
    }

    fun onSaveFailureShown() {
        saveFailed.value = false
    }

    private companion object {
        const val SELECTED = "languages_selected_app"
        const val TOUCHED = "languages_app_touched"
    }
}
