package com.lisdatasolutions.microrag.feature.settings

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lisdatasolutions.microrag.domain.multimodal.ObserveImportModeUseCase
import com.lisdatasolutions.microrag.domain.multimodal.SetDefaultImportModeUseCase
import com.lisdatasolutions.microrag.domain.rag.IndexMode
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

/** «Modo al importar PDF» (F19 contract U6, wireframe §4). */
data class ImportModeUiState(
    /** Null while loading. */
    val saved: IndexMode? = null,
    /** What the user marked, kept in the SavedStateHandle. */
    val selected: IndexMode? = null,
    val saving: Boolean = false,
    /** The previous mode is kept and «Guardar» can be tapped again. */
    val saveFailed: Boolean = false,
)

sealed interface ImportModeEvent {
    data object Close : ImportModeEvent
}

/**
 * Shaped as [ThemeViewModel] (F16): marking only changes the selection, which survives a rotation; a second tap
 * while saving does nothing; a successful or unchanged save closes the screen, and a failed one says so.
 */
class ImportModeViewModel(
    private val observe: ObserveImportModeUseCase,
    private val save: SetDefaultImportModeUseCase,
    private val savedState: SavedStateHandle,
) : ViewModel() {

    private val stored = MutableStateFlow<IndexMode?>(null)
    private val saving = MutableStateFlow(false)
    private val saveFailed = MutableStateFlow(false)
    private val closing = Channel<ImportModeEvent>(Channel.BUFFERED)

    val events: Flow<ImportModeEvent> = closing.receiveAsFlow()

    val state: StateFlow<ImportModeUiState> = combine(
        stored,
        savedState.getStateFlow<String?>(SELECTED, null),
        saving,
        saveFailed,
    ) { stored, selected, saving, failed ->
        ImportModeUiState(saved = stored, selected = selected?.let(IndexMode::valueOf), saving = saving, saveFailed = failed)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, ImportModeUiState())

    init {
        viewModelScope.launch {
            val mode = observe().first().defaultMode
            stored.value = mode
            if (savedState.get<String>(SELECTED) == null) savedState[SELECTED] = mode.name
        }
    }

    fun onSelect(mode: IndexMode) {
        savedState[SELECTED] = mode.name
    }

    fun onSave() {
        if (saving.value) return
        val mode = savedState.get<String>(SELECTED)?.let(IndexMode::valueOf) ?: return
        if (mode == stored.value) {
            closing.trySend(ImportModeEvent.Close)
            return
        }
        saving.value = true
        saveFailed.value = false
        viewModelScope.launch {
            val saved = save(mode)
            saving.value = false
            if (saved) {
                stored.value = mode
                closing.send(ImportModeEvent.Close)
            } else {
                saveFailed.value = true
            }
        }
    }

    fun onSaveFailureShown() {
        saveFailed.value = false
    }

    private companion object {
        const val SELECTED = "import_mode_selected"
    }
}
