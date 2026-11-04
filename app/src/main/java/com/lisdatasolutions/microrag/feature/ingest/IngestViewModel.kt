package com.lisdatasolutions.microrag.feature.ingest

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lisdatasolutions.microrag.domain.ingest.ImportDocumentsUseCase
import com.lisdatasolutions.microrag.domain.ingest.ObserveImportQueueUseCase
import com.lisdatasolutions.microrag.domain.ingest.QueueKey
import com.lisdatasolutions.microrag.domain.ingest.RemoveQueueItemUseCase
import com.lisdatasolutions.microrag.domain.ingest.RetryQueueItemUseCase
import com.lisdatasolutions.microrag.domain.ingest.StartIndexingUseCase
import com.lisdatasolutions.microrag.domain.library.FoldersResult
import com.lisdatasolutions.microrag.domain.library.ObserveFoldersUseCase
import com.lisdatasolutions.microrag.domain.rag.FolderList
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * P05 Importar documentos (F06). The queue lives in the repository, in the application scope, so
 * recreating the Activity or leaving the screen never loses or repeats an import (US4). F17 K6: the
 * destination folder starts as the route's and lives in the [SavedStateHandle]; each pick takes the one
 * shown then.
 */
class IngestViewModel(
    observeQueue: ObserveImportQueueUseCase,
    private val importDocuments: ImportDocumentsUseCase,
    private val startIndexing: StartIndexingUseCase,
    private val retryItem: RetryQueueItemUseCase,
    private val removeItem: RemoveQueueItemUseCase,
    observeFolders: ObserveFoldersUseCase,
    private val savedState: SavedStateHandle,
    backgroundDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {

    /** `Ingest.folderId`: the folder whose «＋» opened it; 0 is «General» in the saved keys. */
    private val destination = savedState.getStateFlow(KEY_DESTINATION, savedState.get<Long>(ROUTE_FOLDER) ?: GENERAL)
    private val choice = savedState.getStateFlow<Long?>(KEY_CHOICE, null)
    private val missing = MutableStateFlow(false)

    private val folders = observeFolders()
        .map { (it as? FoldersResult.Loaded)?.folders }
        .catch { error ->
            if (error is CancellationException) throw error
            emit(null)
        }
        .flowOn(backgroundDispatcher)
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private data class Local(val selected: QueueKey? = null, val confirming: QueueKey? = null, val starting: Boolean = false)

    private val local = MutableStateFlow(Local())
    private val events = Channel<IngestEvent>(Channel.BUFFERED)

    val ingestEvents: Flow<IngestEvent> = events.receiveAsFlow()

    private val destinationState = combine(destination, choice, missing, folders) { destination, choice, missing, folders ->
        DestinationState(destination.toFolder(), choice?.let { DestinationChoice(it.toFolder()) }, missing, folders)
    }

    val state: StateFlow<IngestUiState> = combine(observeQueue(), local, destinationState) { queue, local, destination ->
        IngestUiState(
            loading = false,
            queue = queue,
            selected = local.selected.takeIf { key -> queue.items.any { it.key == key } },
            confirmingRemoval = local.confirming.takeIf { key -> queue.items.any { it.key == key } },
            starting = local.starting,
            folders = destination.folders,
            destination = destination.destination,
            choosing = destination.choosing.takeIf { destination.folders != null },
            destinationMissing = destination.missing,
        )
    }.flowOn(backgroundDispatcher).stateIn(viewModelScope, SharingStarted.Eagerly, IngestUiState())

    init {
        // M3: a destination deleted meanwhile is «General» again, and the screen says so once. Checked on each
        // new list only: a folder just created may be chosen before the list that has it arrives.
        viewModelScope.launch {
            folders.filterNotNull().collect { list ->
                val id = destination.value
                if (id != GENERAL && list.folders.none { it.id == id }) {
                    savedState[KEY_DESTINATION] = GENERAL
                    missing.value = true
                }
            }
        }
    }

    /** URIs chosen in the system picker; an empty list is a cancelled picker and changes nothing (US1-4). */
    fun onFilesPicked(uris: List<String>) {
        importDocuments(uris, destination.value.toFolder())
    }

    /** K6: the sheet starts on the current destination. */
    fun onChooseDestination() {
        savedState[KEY_CHOICE] = destination.value
        missing.value = false
    }

    /** Also the folder just created with «Nueva carpeta…». */
    fun onDestinationSelect(folderId: Long?) {
        if (choice.value != null) savedState[KEY_CHOICE] = folderId ?: GENERAL
    }

    /** K6: a folder made with «Nueva carpeta…» is the destination at once. */
    fun onFolderCreated(folderId: Long) {
        if (choice.value == null) return
        onDestinationSelect(folderId)
        onConfirmDestination()
    }

    fun onConfirmDestination() {
        val chosen = choice.value ?: return
        savedState[KEY_DESTINATION] = chosen
        savedState[KEY_CHOICE] = null
    }

    fun onDismissDestination() {
        savedState[KEY_CHOICE] = null
    }


    fun onRowClick(key: QueueKey) {
        local.update { it.copy(selected = key) }
    }

    fun onDismissSheet() {
        local.update { it.copy(selected = null) }
    }

    fun onRetry(key: QueueKey) {
        local.update { it.copy(selected = null) }
        viewModelScope.launch { retryItem(key) }
    }

    /** A registered document asks for confirmation first, because its private copy is deleted (constitution V). */
    fun onRemove(key: QueueKey) {
        val item = state.value.items.firstOrNull { it.key == key } ?: return
        if (item.removalNeedsConfirmation) {
            local.update { it.copy(selected = null, confirming = key) }
        } else {
            local.update { it.copy(selected = null) }
            viewModelScope.launch { removeItem(key) }
        }
    }

    fun onConfirmRemoval() {
        val key = local.value.confirming ?: return
        local.update { it.copy(confirming = null) }
        viewModelScope.launch { removeItem(key) }
    }

    fun onDismissRemoval() {
        local.update { it.copy(confirming = null) }
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

    private class DestinationState(val destination: Long?, val choosing: DestinationChoice?, val missing: Boolean, val folders: FolderList?)

    private fun Long.toFolder(): Long? = takeIf { it != GENERAL }

    private companion object {
        const val ROUTE_FOLDER = "folderId"
        const val KEY_DESTINATION = "ingest_destination"
        const val KEY_CHOICE = "ingest_destination_choice"
        const val GENERAL = 0L
    }
}
