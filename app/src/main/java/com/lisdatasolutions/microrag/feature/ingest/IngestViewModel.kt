package com.lisdatasolutions.microrag.feature.ingest

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lisdatasolutions.microrag.domain.ingest.ImportDocumentsUseCase
import com.lisdatasolutions.microrag.domain.ingest.ObserveImportQueueUseCase
import com.lisdatasolutions.microrag.domain.ingest.QueueKey
import com.lisdatasolutions.microrag.domain.ingest.StartIndexingUseCase
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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * P05 Importar documentos (F06). The queue lives in the repository, in the application scope, so
 * recreating the Activity or leaving the screen never loses or repeats an import (US4).
 */
class IngestViewModel(
    observeQueue: ObserveImportQueueUseCase,
    private val importDocuments: ImportDocumentsUseCase,
    private val startIndexing: StartIndexingUseCase,
    backgroundDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {

    private data class Local(val selected: QueueKey? = null, val starting: Boolean = false)

    private val local = MutableStateFlow(Local())
    private val events = Channel<IngestEvent>(Channel.BUFFERED)

    val ingestEvents: Flow<IngestEvent> = events.receiveAsFlow()

    val state: StateFlow<IngestUiState> = combine(observeQueue(), local) { queue, local ->
        IngestUiState(
            loading = false,
            queue = queue,
            selected = local.selected.takeIf { key -> queue.items.any { it.key == key } },
            starting = local.starting,
        )
    }.flowOn(backgroundDispatcher).stateIn(viewModelScope, SharingStarted.Eagerly, IngestUiState())

    /** URIs chosen in the system picker; an empty list is a cancelled picker and changes nothing (US1-4). */
    fun onFilesPicked(uris: List<String>) {
        importDocuments(uris)
    }

    fun onRowClick(key: QueueKey) {
        local.update { it.copy(selected = key) }
    }

    fun onDismissSheet() {
        local.update { it.copy(selected = null) }
    }

    /** Enqueues the Ready documents once, even with repeated taps, and opens the indexing screen (FR-011). */
    fun onStartIndexing() {
        if (local.value.starting) return
        local.update { it.copy(starting = true) }
        viewModelScope.launch {
            try {
                if (startIndexing() > 0) events.send(IngestEvent.OpenIndex)
            } finally {
                local.update { it.copy(starting = false) }
            }
        }
    }
}
