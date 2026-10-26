package com.lisdatasolutions.microrag.feature.indexing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lisdatasolutions.microrag.domain.indexing.ObserveIndexingUseCase
import com.lisdatasolutions.microrag.domain.indexing.PauseIndexingUseCase
import com.lisdatasolutions.microrag.domain.indexing.ResumeIndexingUseCase
import com.lisdatasolutions.microrag.domain.indexing.RetryIndexingUseCase
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** P08 Estado de indexación (F07). The run lives in the repository, so leaving the screen changes nothing. */
class IndexViewModel(
    observe: ObserveIndexingUseCase,
    private val pause: PauseIndexingUseCase,
    private val resume: ResumeIndexingUseCase,
    private val retry: RetryIndexingUseCase,
    backgroundDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {

    private val busy = MutableStateFlow(false)
    private val events = Channel<IndexEvent>(Channel.BUFFERED)

    val indexEvents: Flow<IndexEvent> = events.receiveAsFlow()

    val state: StateFlow<IndexUiState> = combine(observe(), busy) { indexing, busy -> IndexUiState(indexing, busy) }
        .flowOn(backgroundDispatcher)
        .stateIn(viewModelScope, SharingStarted.Eagerly, IndexUiState())

    fun onPause() = apply { pause() }

    fun onResume() = apply { resume() }

    fun onRetry(documentId: Long) = apply { retry(documentId) }

    fun onPrepareModels() {
        events.trySend(IndexEvent.OpenSetup)
    }

    /** One action at a time: a double tap never pauses and resumes at once. */
    private fun apply(action: suspend () -> Unit) {
        if (busy.value) return
        busy.value = true
        viewModelScope.launch {
            try {
                action()
            } finally {
                busy.value = false
            }
        }
    }
}
