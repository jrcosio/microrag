package com.lisdatasolutions.microrag.feature.document

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.lisdatasolutions.microrag.domain.indexing.IndexingRepository
import com.lisdatasolutions.microrag.domain.indexing.IndexingState
import com.lisdatasolutions.microrag.domain.indexing.RetryIndexingUseCase
import com.lisdatasolutions.microrag.domain.ingest.DocumentFormat
import com.lisdatasolutions.microrag.domain.library.DeleteDocumentUseCase
import com.lisdatasolutions.microrag.domain.library.DetailResult
import com.lisdatasolutions.microrag.domain.library.DiscardReindexUseCase
import com.lisdatasolutions.microrag.domain.library.DocumentDetail
import com.lisdatasolutions.microrag.domain.library.DocumentDetailState
import com.lisdatasolutions.microrag.domain.library.DocumentPreview
import com.lisdatasolutions.microrag.domain.library.LoadPreviewUseCase
import com.lisdatasolutions.microrag.domain.library.ObserveDocumentUseCase
import com.lisdatasolutions.microrag.domain.library.ReindexDocumentUseCase
import com.lisdatasolutions.microrag.domain.library.ReindexState
import com.lisdatasolutions.microrag.domain.library.RemoveFromIndexUseCase
import com.lisdatasolutions.microrag.domain.library.SetIncludedUseCase
import com.lisdatasolutions.microrag.testing.FakeLibraryRepository
import com.lisdatasolutions.microrag.domain.ingest.IngestError
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** F08 T029: the detail follows the document, loads its preview when it changes and leaves once if it disappears (R8). */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class)
class DocumentViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val repository = FakeLibraryRepository()
    val retried = mutableListOf<Long>()
    private val indexing = object : IndexingRepository {
        override val state = MutableStateFlow<IndexingState>(IndexingState.Idle(emptyList()))
        override suspend fun start() = 0
        override suspend fun pause() = Unit
        override suspend fun resume() = Unit
        override suspend fun retry(documentId: Long) {
            retried += documentId
        }
        override suspend fun reconcile() = Unit
        override suspend fun add(documentId: Long) = Unit
    }

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(documentId: Long = 7, chunkId: Long? = null) = DocumentViewModel(
        SavedStateHandle(mapOf("documentId" to documentId, "chunkId" to chunkId)),
        ObserveDocumentUseCase(repository),
        LoadPreviewUseCase(repository),
        SetIncludedUseCase(repository),
        DeleteDocumentUseCase(repository),
        RemoveFromIndexUseCase(repository),
        ReindexDocumentUseCase(repository),
        DiscardReindexUseCase(repository),
        RetryIndexingUseCase(indexing),
        dispatcher,
    )

    private fun detail(
        state: DocumentDetailState = DocumentDetailState.Indexed,
        chunks: Int? = 860,
        reindex: ReindexState? = null,
        included: Boolean = true,
    ) = DocumentDetail(
        id = 7, displayName = "Manual de procesos.pdf", format = DocumentFormat.PDF, state = state, included = included,
        pages = 48, chunks = chunks, sizeBytes = 2_400_000, importedAtMs = 0, canReindex = true, copyMissing = false, reindex = reindex,
    )

    private fun show(detail: DetailResult) {
        repository.details.getOrPut(7) { MutableStateFlow(null) }.value = detail
    }

    private val citation = DocumentPreview(12, "Antes de cerrar el proceso, se", "debe registrar la validación final", "en el sistema documental.", true, false)

    @Test
    fun loadsTheDetailAndThePreviewOfTheCitedChunk() = runTest(dispatcher) {
        show(DetailResult.Loaded(detail()))
        repository.previews[7L to 3L] = citation
        val vm = viewModel(chunkId = 3)
        assertThat(vm.state.value.content).isEqualTo(DocumentContent.Loading)

        advanceUntilIdle()

        assertThat(vm.state.value.content).isEqualTo(DocumentContent.Loaded(detail()))
        assertThat(vm.state.value.preview).isEqualTo(PreviewContent.Shown(citation))
        assertThat(repository.previewRequests).containsExactly(7L to 3L)
    }

    @Test
    fun aDocumentNotIndexed_hasNoPreview_untilItIs() = runTest(dispatcher) {
        show(DetailResult.Loaded(detail(DocumentDetailState.Pending, chunks = null)))
        val first = DocumentPreview(1, null, "Primer fragmento.", null, false, false)
        repository.previews[7L to null] = first
        val vm = viewModel()
        advanceUntilIdle()
        assertThat(vm.state.value.preview).isEqualTo(PreviewContent.None)
        assertThat(repository.previewRequests).isEmpty()

        show(DetailResult.Loaded(detail()))
        advanceUntilIdle()

        assertThat(vm.state.value.preview).isEqualTo(PreviewContent.Shown(first))
    }

    @Test
    fun aPublishedReindex_loadsThePreviewAgain() = runTest(dispatcher) {
        show(DetailResult.Loaded(detail(reindex = ReindexState.Running(9, 10, 430, null))))
        val vm = viewModel()
        advanceUntilIdle()
        show(DetailResult.Loaded(detail(reindex = ReindexState.Running(9, 74, 430, null))))
        advanceUntilIdle()
        assertThat(repository.previewRequests).hasSize(1)

        show(DetailResult.Loaded(detail(chunks = 430)))
        advanceUntilIdle()

        assertThat(repository.previewRequests).hasSize(2)
        assertThat(vm.state.value.content).isEqualTo(DocumentContent.Loaded(detail(chunks = 430)))
    }

    @Test
    fun aMissingDocument_leavesOnce() = runTest(dispatcher) {
        show(DetailResult.NotFound)
        val vm = viewModel()
        val events = mutableListOf<DocumentEvent>()
        backgroundScope.launch(dispatcher) { vm.events.toList(events) }
        advanceUntilIdle()

        show(DetailResult.Loaded(detail()))
        show(DetailResult.NotFound)
        advanceUntilIdle()

        assertThat(events).containsExactly(DocumentEvent.Gone)
    }

    @Test
    fun anUnavailableIndex_isUnavailable_notGone_D11() = runTest(dispatcher) {
        show(DetailResult.Unavailable)
        val vm = viewModel()
        val events = mutableListOf<DocumentEvent>()
        backgroundScope.launch(dispatcher) { vm.events.toList(events) }
        advanceUntilIdle()

        assertThat(vm.state.value.content).isEqualTo(DocumentContent.Unavailable)
        assertThat(events).isEmpty()
    }

    // US4 (T042)

    @Test
    fun delete_asksFirst_thenShowsDeleting_andLeavesAsDeleted_D8() = runTest(dispatcher) {
        show(DetailResult.Loaded(detail()))
        val gate = CompletableDeferred<Unit>()
        repository.deleteGate = gate
        val vm = viewModel()
        val events = mutableListOf<DocumentEvent>()
        backgroundScope.launch(dispatcher) { vm.events.toList(events) }
        advanceUntilIdle()

        vm.onDelete()
        advanceUntilIdle()
        assertThat(vm.state.value.dialog).isEqualTo(DocumentDialog.DELETE)
        assertThat(repository.calls).isEmpty()
        vm.onConfirmDialog()
        advanceUntilIdle()

        assertThat(vm.state.value.dialog).isNull()
        assertThat(vm.state.value.deleting).isTrue()
        assertThat(repository.calls).containsExactly("delete 7")
        gate.complete(Unit)
        advanceUntilIdle()
        assertThat(events).containsExactly(DocumentEvent.Deleted)
    }

    @Test
    fun aDismissedDialog_doesNothing() = runTest(dispatcher) {
        show(DetailResult.Loaded(detail()))
        val vm = viewModel()
        advanceUntilIdle()

        vm.onRemoveFromIndex()
        advanceUntilIdle()
        assertThat(vm.state.value.dialog).isEqualTo(DocumentDialog.REMOVE_FROM_INDEX)
        vm.onDismissDialog()
        advanceUntilIdle()

        assertThat(vm.state.value.dialog).isNull()
        assertThat(repository.calls).isEmpty()
    }

    @Test
    fun removeFromIndex_isConfirmed_andAnnounced_A7() = runTest(dispatcher) {
        show(DetailResult.Loaded(detail()))
        val vm = viewModel()
        advanceUntilIdle()

        vm.onRemoveFromIndex()
        vm.onConfirmDialog()
        advanceUntilIdle()

        assertThat(repository.calls).containsExactly("remove 7")
        assertThat(vm.state.value.announcement).isEqualTo(DocumentAnnouncement.REMOVED_FROM_INDEX)
    }

    @Test
    fun reindex_runsOnce_evenWithADoubleTap_andIsAnnounced() = runTest(dispatcher) {
        show(DetailResult.Loaded(detail()))
        val vm = viewModel()
        advanceUntilIdle()

        vm.onReindex()
        vm.onReindex()
        advanceUntilIdle()

        assertThat(repository.calls).containsExactly("reindex 7")
        assertThat(vm.state.value.announcement).isEqualTo(DocumentAnnouncement.REINDEXING)
        assertThat(vm.state.value.busy).isFalse()
    }

    @Test
    fun retry_retriesTheDocument_D3_orItsFailedReindex_D6() = runTest(dispatcher) {
        show(DetailResult.Loaded(detail(DocumentDetailState.Failed(IngestError.EMBEDDING_FAILED), chunks = null)))
        val vm = viewModel()
        advanceUntilIdle()
        vm.onRetry()
        advanceUntilIdle()

        show(DetailResult.Loaded(detail(reindex = ReindexState.Failed(9, IngestError.INCOMPLETE))))
        advanceUntilIdle()
        vm.onRetry()
        advanceUntilIdle()

        assertThat(retried).containsExactly(7L, 9L).inOrder()
    }

    @Test
    fun discard_dropsTheFailedReindex_D6() = runTest(dispatcher) {
        show(DetailResult.Loaded(detail(reindex = ReindexState.Failed(9, IngestError.INCOMPLETE))))
        val vm = viewModel()
        advanceUntilIdle()

        vm.onDiscardReindex()
        advanceUntilIdle()

        assertThat(repository.calls).containsExactly("discard 7")
    }

    @Test
    fun theSwitch_savesTheInclusion() = runTest(dispatcher) {
        repository.documents.value = emptyList()
        show(DetailResult.Loaded(detail()))
        val vm = viewModel()
        advanceUntilIdle()

        vm.onIncludedChange(false)
        advanceUntilIdle()

        assertThat(repository.calls).containsExactly("include 7 false")
        assertThat(vm.state.value.included).isFalse()
        assertThat(vm.state.value.announcement).isEqualTo(DocumentAnnouncement.EXCLUDED)
    }
}
