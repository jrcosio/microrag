package com.lisdatasolutions.microrag.feature.settings

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lisdatasolutions.microrag.domain.chat.ClearChatHistoryUseCase
import com.lisdatasolutions.microrag.domain.chat.CountConversationsUseCase
import com.lisdatasolutions.microrag.domain.chat.IsAnswerInProgressUseCase
import com.lisdatasolutions.microrag.domain.model.ModelId
import com.lisdatasolutions.microrag.domain.onboarding.RefreshLocalAiUseCase
import com.lisdatasolutions.microrag.domain.settings.AppLanguage
import com.lisdatasolutions.microrag.domain.settings.ObserveLanguageSettingsUseCase
import com.lisdatasolutions.microrag.domain.settings.ObserveModelOverviewUseCase
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * P09 (contracts/settings-api.md): the model rows follow the Preparation (P13), the open sheet and the
 * clearing confirmation survive a rotation, and «Borrar historial de chat» goes through the chat session (P12).
 * The rows' state reads the model files, so, as in the Preparation, it is gathered on [backgroundDispatcher].
 */
class SettingsViewModel(
    observeModels: ObserveModelOverviewUseCase,
    private val refreshLocalAi: RefreshLocalAiUseCase,
    private val observeLanguage: ObserveLanguageSettingsUseCase,
    private val countConversations: CountConversationsUseCase,
    private val isAnswering: IsAnswerInProgressUseCase,
    private val clearHistory: ClearChatHistoryUseCase,
    private val savedState: SavedStateHandle,
    private val backgroundDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {

    private val models = observeModels().flowOn(backgroundDispatcher).stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    private val clearing = MutableStateFlow(false)
    private val message = MutableStateFlow<SettingsMessage?>(null)
    private val appLanguage = MutableStateFlow(AppLanguage.SPANISH)

    private val dialogs = combine(
        savedState.getStateFlow<String?>(OPEN_SHEET, null),
        savedState.getStateFlow<Boolean?>(CONFIRM_CLEAR, null),
        clearing,
        message,
    ) { sheet, confirm, clearing, message -> Dialogs(sheet?.let(ModelId::valueOf), confirm?.let(::ConfirmClear), clearing, message) }

    val state: StateFlow<SettingsUiState> = combine(models, appLanguage, dialogs) { models, language, dialogs ->
        SettingsUiState(
            models = models,
            appLanguage = language,
            openSheet = dialogs.sheet,
            confirmClear = dialogs.confirm,
            clearing = dialogs.clearing,
            message = dialogs.message,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, SettingsUiState())

    private data class Dialogs(val sheet: ModelId?, val confirm: ConfirmClear?, val clearing: Boolean, val message: SettingsMessage?)

    init {
        readLanguage()
    }

    private fun readLanguage() {
        viewModelScope.launch { appLanguage.value = observeLanguage().first().app }
    }

    /** Back to the screen: the files are rescanned, so a model deleted meanwhile stops saying «Local». */
    fun onResume() {
        viewModelScope.launch { refreshLocalAi() }
        // S3: back from Idiomas, or from the system's per-app language settings.
        readLanguage()
    }

    fun onOpenModel(id: ModelId) {
        savedState[OPEN_SHEET] = id.name
    }

    fun onCloseSheet() {
        savedState[OPEN_SHEET] = null
    }

    /** P12: nothing to delete is a notice; otherwise the confirmation, saying whether an answer will stop. */
    fun onClearHistory() {
        if (clearing.value) return
        viewModelScope.launch {
            if (countConversations() == 0) {
                message.value = SettingsMessage.NO_CONVERSATIONS
            } else {
                savedState[CONFIRM_CLEAR] = isAnswering()
            }
        }
    }

    fun onDismissClear() {
        savedState[CONFIRM_CLEAR] = null
    }

    fun onConfirmClear() {
        savedState[CONFIRM_CLEAR] = null
        clearing.value = true
        viewModelScope.launch {
            val cleared = clearHistory()
            clearing.value = false
            message.value = if (cleared) SettingsMessage.HISTORY_CLEARED else SettingsMessage.CLEAR_FAILED
        }
    }

    fun onMessageShown() {
        message.value = null
    }

    private companion object {
        const val OPEN_SHEET = "settings_open_sheet"
        const val CONFIRM_CLEAR = "settings_confirm_clear"
    }
}
