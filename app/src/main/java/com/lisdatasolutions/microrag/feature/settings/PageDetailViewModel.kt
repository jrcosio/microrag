package com.lisdatasolutions.microrag.feature.settings

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lisdatasolutions.microrag.domain.multimodal.ObservePageDetailUseCase
import com.lisdatasolutions.microrag.domain.multimodal.PageDetail
import com.lisdatasolutions.microrag.domain.multimodal.SetPageDetailUseCase
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

/** «Detalle de las páginas» (F20 contract U2, wireframe §2). */
data class PageDetailUiState(
    /** Null while loading. */
    val saved: PageDetail? = null,
    /** What the user marked, kept in the SavedStateHandle. */
    val selected: PageDetail? = null,
    val saving: Boolean = false,
    /** The previous detail is kept and «Guardar» can be tapped again. */
    val saveFailed: Boolean = false,
)

sealed interface PageDetailEvent {
    data object Close : PageDetailEvent
}

/**
 * Shaped as [ImportModeViewModel]: marking only changes the selection, which survives a rotation; a successful or
 * unchanged save closes the screen, and a failed one says so. With the mode off, there is nothing to choose: it closes.
 */
class PageDetailViewModel(
    private val observe: ObservePageDetailUseCase,
    private val save: SetPageDetailUseCase,
    private val savedState: SavedStateHandle,
) : ViewModel() {

    private val stored = MutableStateFlow<PageDetail?>(null)
    private val saving = MutableStateFlow(false)
    private val saveFailed = MutableStateFlow(false)
    private val closing = Channel<PageDetailEvent>(Channel.BUFFERED)

    val events: Flow<PageDetailEvent> = closing.receiveAsFlow()

    val state: StateFlow<PageDetailUiState> = combine(
        stored,
        savedState.getStateFlow<String?>(SELECTED, null),
        saving,
        saveFailed,
    ) { stored, selected, saving, failed ->
        PageDetailUiState(saved = stored, selected = selected?.let(PageDetail::valueOf), saving = saving, saveFailed = failed)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, PageDetailUiState())

    init {
        viewModelScope.launch {
            val settings = observe().first()
            if (!settings.on) {
                closing.send(PageDetailEvent.Close)
                return@launch
            }
            stored.value = settings.detail
            if (savedState.get<String>(SELECTED) == null) savedState[SELECTED] = settings.detail.name
        }
    }

    fun onSelect(detail: PageDetail) {
        savedState[SELECTED] = detail.name
    }

    fun onSave() {
        if (saving.value) return
        val detail = savedState.get<String>(SELECTED)?.let(PageDetail::valueOf) ?: return
        if (detail == stored.value) {
            closing.trySend(PageDetailEvent.Close)
            return
        }
        saving.value = true
        saveFailed.value = false
        viewModelScope.launch {
            val saved = save(detail)
            saving.value = false
            if (saved) {
                stored.value = detail
                closing.send(PageDetailEvent.Close)
            } else {
                saveFailed.value = true
            }
        }
    }

    fun onSaveFailureShown() {
        saveFailed.value = false
    }

    private companion object {
        const val SELECTED = "page_detail_selected"
    }
}
