package com.lisdatasolutions.microrag.feature.settings

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lisdatasolutions.microrag.domain.model.ModelId
import com.lisdatasolutions.microrag.domain.onboarding.RefreshLocalAiUseCase
import com.lisdatasolutions.microrag.domain.settings.ObserveModelOverviewUseCase
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** P09 (contracts/settings-api.md): the model rows follow the Preparation (P13), and the open sheet survives. */
class SettingsViewModel(
    observeModels: ObserveModelOverviewUseCase,
    private val refreshLocalAi: RefreshLocalAiUseCase,
    private val savedState: SavedStateHandle,
) : ViewModel() {

    private val models = observeModels().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val state: StateFlow<SettingsUiState> = combine(models, savedState.getStateFlow<String?>(OPEN_SHEET, null)) { models, sheet ->
        SettingsUiState(models = models, openSheet = sheet?.let(ModelId::valueOf))
    }.stateIn(viewModelScope, SharingStarted.Eagerly, SettingsUiState())

    /** Back to the screen: the files are rescanned, so a model deleted meanwhile stops saying «Local». */
    fun onResume() {
        viewModelScope.launch { refreshLocalAi() }
    }

    fun onOpenModel(id: ModelId) {
        savedState[OPEN_SHEET] = id.name
    }

    fun onCloseSheet() {
        savedState[OPEN_SHEET] = null
    }

    private companion object {
        const val OPEN_SHEET = "settings_open_sheet"
    }
}
