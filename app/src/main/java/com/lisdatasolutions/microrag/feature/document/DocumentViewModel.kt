package com.lisdatasolutions.microrag.feature.document

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.lisdatasolutions.microrag.core.navigation.Document
import com.lisdatasolutions.microrag.domain.indexing.RetryIndexingUseCase
import com.lisdatasolutions.microrag.domain.library.DeleteDocumentUseCase
import com.lisdatasolutions.microrag.domain.library.DetailResult
import com.lisdatasolutions.microrag.domain.library.DiscardReindexUseCase
import com.lisdatasolutions.microrag.domain.library.DocumentDetail
import com.lisdatasolutions.microrag.domain.library.DocumentDetailState
import com.lisdatasolutions.microrag.domain.library.LoadPreviewUseCase
import com.lisdatasolutions.microrag.domain.library.ObserveDocumentUseCase
import com.lisdatasolutions.microrag.domain.library.ReindexDocumentUseCase
import com.lisdatasolutions.microrag.domain.library.ReindexState
import com.lisdatasolutions.microrag.domain.library.RemoveFromIndexUseCase
import com.lisdatasolutions.microrag.domain.library.SetIncludedUseCase
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * P07 Detalle del documento (F08). The route carries only ids (documentId and, from a citation,
 * chunkId); everything else comes from the repository and follows the index while the screen is open.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DocumentViewModel(
    savedState: SavedStateHandle,
    observeDocument: ObserveDocumentUseCase,
    private val loadPreview: LoadPreviewUseCase,
    private val setIncluded: SetIncludedUseCase,
    private val deleteDocument: DeleteDocumentUseCase,
    private val removeFromIndex: RemoveFromIndexUseCase,
    private val reindexDocument: ReindexDocumentUseCase,
    private val discardReindex: DiscardReindexUseCase,
    private val retryIndexing: RetryIndexingUseCase,
    backgroundDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {

    private val route = savedState.toRoute<Document>()
    private val documentId = route.documentId

    private data class Local(
        val included: Boolean? = null,
        val dialog: DocumentDialog? = null,
        val deleting: Boolean = false,
        val busy: Boolean = false,
        val announcement: DocumentAnnouncement? = null,
    )

    private val local = MutableStateFlow(Local())
    private val eventChannel = Channel<DocumentEvent>(Channel.BUFFERED)
    private var left = false

    val events: Flow<DocumentEvent> = eventChannel.receiveAsFlow()

    private val detail: StateFlow<DetailResult?> = observeDocument(documentId)
        .catch { error ->
            if (error is CancellationException) throw error
            emit(DetailResult.Unavailable)
        }
        .flowOn(backgroundDispatcher)
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /**
     * Loaded once, and again when what it shows may have changed: the document got indexed, its
     * chunks changed or a reindex was published (research R7).
     */
    private val preview: StateFlow<PreviewContent> = detail
        .filterIsInstance<DetailResult.Loaded>()
        .map { it.detail.previewKey() }
        .distinctUntilChanged()
        .mapLatest { key ->
            if (key.state != DocumentDetailState.Indexed) {
                PreviewContent.None
            } else {
                loadPreview(documentId, route.chunkId)?.let { PreviewContent.Shown(it) } ?: PreviewContent.None
            }
        }
        .flowOn(backgroundDispatcher)
        .stateIn(viewModelScope, SharingStarted.Eagerly, PreviewContent.Loading)

    val state: StateFlow<DocumentUiState> = combine(detail, preview, local) { detail, preview, local ->
        val loaded = (detail as? DetailResult.Loaded)?.detail
        DocumentUiState(
            content = when (detail) {
                is DetailResult.Loaded -> DocumentContent.Loaded(detail.detail)
                DetailResult.Unavailable -> DocumentContent.Unavailable
                DetailResult.NotFound, null -> DocumentContent.Loading
            },
            preview = preview,
            included = local.included ?: loaded?.included ?: true,
            dialog = local.dialog.takeIf { loaded != null },
            deleting = local.deleting,
            busy = local.busy,
            announcement = local.announcement,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, DocumentUiState())

    init {
        viewModelScope.launch {
            detail.collect { result ->
                when (result) {
                    // R8: once, and not when the user's own delete removed it.
                    DetailResult.NotFound -> leave(if (local.value.deleting) DocumentEvent.Deleted else DocumentEvent.Gone)
                    is DetailResult.Loaded -> if (local.value.included == result.detail.included) {
                        local.value = local.value.copy(included = null)
                    }
                    else -> Unit
                }
            }
        }
    }

    fun onIncludedChange(included: Boolean) {
        val shown = (state.value.content as? DocumentContent.Loaded)?.detail ?: return
        if (!shown.canChangeInclusion) return
        local.value = local.value.copy(
            included = included,
            announcement = if (included) DocumentAnnouncement.INCLUDED else DocumentAnnouncement.EXCLUDED,
        )
        viewModelScope.launch { setIncluded(documentId, included) }
    }

    /** D8: deleting asks first. */
    fun onDelete() = openDialog(DocumentDialog.DELETE)

    /** D8: «Quitar del índice» asks first. */
    fun onRemoveFromIndex() = openDialog(DocumentDialog.REMOVE_FROM_INDEX)

    fun onDismissDialog() {
        local.value = local.value.copy(dialog = null)
    }

    fun onConfirmDialog() {
        when (local.value.dialog) {
            null -> Unit
            DocumentDialog.DELETE -> {
                // «Eliminando…» until the document is gone; then back to the Library (Deleted).
                local.value = local.value.copy(dialog = null, deleting = true)
                viewModelScope.launch {
                    try {
                        deleteDocument(documentId)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        local.value = local.value.copy(deleting = false)
                    }
                }
            }
            DocumentDialog.REMOVE_FROM_INDEX -> {
                local.value = local.value.copy(dialog = null)
                act(DocumentAnnouncement.REMOVED_FROM_INDEX) { removeFromIndex(documentId) }
            }
        }
    }

    /** Rule L6: indexed or out of the index; the repository decides what it means for each state. */
    fun onReindex() = act(DocumentAnnouncement.REINDEXING) { reindexDocument(documentId) }

    /** D3 retries the document; D6 its failed reindex shadow (F07's retry). */
    fun onRetry() {
        val shown = (state.value.content as? DocumentContent.Loaded)?.detail ?: return
        val target = when {
            shown.state is DocumentDetailState.Failed -> documentId
            shown.reindex is ReindexState.Failed -> shown.reindex.shadowId
            else -> return
        }
        act(null) { retryIndexing(target) }
    }

    /** D6: drops the failed shadow; the indexed version stays. */
    fun onDiscardReindex() = act(null) { discardReindex(documentId) }

    private fun openDialog(dialog: DocumentDialog) {
        if (state.value.content !is DocumentContent.Loaded || local.value.deleting) return
        local.value = local.value.copy(dialog = dialog)
    }

    /** One action at a time: a double tap never reindexes or removes twice. */
    private fun act(announcement: DocumentAnnouncement?, action: suspend () -> Unit) {
        if (local.value.busy || local.value.deleting) return
        local.value = local.value.copy(busy = true)
        viewModelScope.launch {
            try {
                action()
                if (announcement != null) local.value = local.value.copy(announcement = announcement)
            } finally {
                local.value = local.value.copy(busy = false)
            }
        }
    }

    private fun leave(event: DocumentEvent) {
        if (left) return
        left = true
        eventChannel.trySend(event)
    }

    private data class PreviewKey(val state: DocumentDetailState, val chunks: Int?, val reindexing: Boolean)

    private fun DocumentDetail.previewKey() = PreviewKey(
        state = if (state == DocumentDetailState.Indexed) state else DocumentDetailState.Pending,
        chunks = chunks,
        reindexing = reindex is ReindexState.Running,
    )
}
