package com.lisdatasolutions.microrag.feature.document

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.lisdatasolutions.microrag.domain.indexing.IndexingRepository
import com.lisdatasolutions.microrag.domain.indexing.IndexingState
import com.lisdatasolutions.microrag.domain.indexing.RetryIndexingUseCase
import com.lisdatasolutions.microrag.domain.ingest.DocumentFormat
import com.lisdatasolutions.microrag.domain.ingest.IngestError
import com.lisdatasolutions.microrag.domain.library.DeleteDocumentUseCase
import com.lisdatasolutions.microrag.domain.library.DetailResult
import com.lisdatasolutions.microrag.domain.library.DiscardReindexUseCase
import com.lisdatasolutions.microrag.domain.library.DocumentDetail
import com.lisdatasolutions.microrag.domain.library.DocumentDetailState
import com.lisdatasolutions.microrag.domain.library.DocumentPreview
import com.lisdatasolutions.microrag.domain.library.LoadPreviewUseCase
import com.lisdatasolutions.microrag.domain.library.MoveDocumentUseCase
import com.lisdatasolutions.microrag.domain.library.ObserveDocumentUseCase
import com.lisdatasolutions.microrag.domain.library.ObserveFoldersUseCase
import com.lisdatasolutions.microrag.domain.library.PageExistsUseCase
import com.lisdatasolutions.microrag.domain.library.ReindexDocumentUseCase
import com.lisdatasolutions.microrag.domain.library.ReindexState
import com.lisdatasolutions.microrag.domain.library.RemoveFromIndexUseCase
import com.lisdatasolutions.microrag.domain.library.SetIncludedUseCase
import com.lisdatasolutions.microrag.domain.multimodal.MultimodalStage
import com.lisdatasolutions.microrag.domain.multimodal.ObserveImportModeUseCase
import com.lisdatasolutions.microrag.domain.multimodal.StoredMultimodal
import com.lisdatasolutions.microrag.domain.rag.IndexMode
import com.lisdatasolutions.microrag.domain.rag.NewDocument
import com.lisdatasolutions.microrag.domain.rag.PageInput
import com.lisdatasolutions.microrag.testing.FakeLibraryRepository
import com.lisdatasolutions.microrag.testing.FakeMultimodalStore
import com.lisdatasolutions.microrag.testing.FakeRagIndex
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

    /** F19: the mode's copy, as Ajustes leaves it. */
    private val mode = FakeMultimodalStore()

    /** F20: the pages the index still stores, for [PageExistsUseCase]. */
    private val pagesIndex = FakeRagIndex()

    private fun viewModel(documentId: Long = 7, chunkId: Long? = null, page: Int? = null) = DocumentViewModel(
        SavedStateHandle(mapOf("documentId" to documentId, "chunkId" to chunkId, "page" to page)),
        ObserveDocumentUseCase(repository),
        LoadPreviewUseCase(repository),
        SetIncludedUseCase(repository),
        DeleteDocumentUseCase(repository),
        RemoveFromIndexUseCase(repository),
        ReindexDocumentUseCase(repository),
        DiscardReindexUseCase(repository),
        RetryIndexingUseCase(indexing),
        ObserveFoldersUseCase(repository),
        MoveDocumentUseCase(repository),
        dispatcher,
        ObserveImportModeUseCase(mode),
        PageExistsUseCase(pagesIndex),
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

    // F17 T024 (K5, wireframe §4): «Mover a carpeta».

    private fun withFolders() {
        repository.folderRows.value = listOf(
            com.lisdatasolutions.microrag.domain.rag.Folder(1, "Legal", 10, 0),
            com.lisdatasolutions.microrag.domain.rag.Folder(2, "Motores", 20, 0),
        )
        repository.documents.value = listOf(com.lisdatasolutions.microrag.testing.libraryDocument(7, "Manual de procesos.pdf", folderId = 1))
        show(DetailResult.Loaded(detail().copy(folderId = 1, folderName = "Legal")))
    }

    @Test
    fun theSheetStartsOnTheCurrentFolder_andMovingSaysWhere() = runTest(dispatcher) {
        withFolders()
        val vm = viewModel()
        advanceUntilIdle()

        vm.onMoveToFolder()
        advanceUntilIdle()
        assertThat(vm.state.value.move).isEqualTo(MoveSheet(selected = 1))

        vm.onMoveSelect(2)
        advanceUntilIdle()
        vm.onConfirmMove()
        advanceUntilIdle()

        assertThat(repository.calls).containsExactly("move 7 2")
        assertThat(vm.state.value.move).isNull()
        assertThat(vm.state.value.moveMessage).isEqualTo(MoveMessage.Moved("Motores"))
        vm.onMoveMessageShown()
        advanceUntilIdle()
        assertThat(vm.state.value.moveMessage).isNull()
    }

    @Test
    fun movingToGeneral_andToTheSameFolder() = runTest(dispatcher) {
        withFolders()
        val vm = viewModel()
        advanceUntilIdle()

        vm.onMoveToFolder()
        advanceUntilIdle()
        vm.onConfirmMove()
        advanceUntilIdle()
        // The same folder: nothing to write.
        assertThat(repository.calls).isEmpty()

        vm.onMoveToFolder()
        vm.onMoveSelect(null)
        advanceUntilIdle()
        vm.onConfirmMove()
        advanceUntilIdle()
        assertThat(repository.calls).containsExactly("move 7 null")
        assertThat(vm.state.value.moveMessage).isEqualTo(MoveMessage.Moved(null))
    }

    @Test
    fun aFailedMoveSaysSo() = runTest(dispatcher) {
        withFolders()
        repository.failMove = true
        val vm = viewModel()
        advanceUntilIdle()

        vm.onMoveToFolder()
        vm.onMoveSelect(2)
        advanceUntilIdle()
        vm.onConfirmMove()
        advanceUntilIdle()

        assertThat(vm.state.value.moveMessage).isEqualTo(MoveMessage.Failed)
    }

    /** Wireframe §4: «Nueva carpeta…» moves the document into the folder it creates. */
    @Test
    fun aFolderCreatedFromTheSheetTakesTheDocument() = runTest(dispatcher) {
        withFolders()
        val vm = viewModel()
        advanceUntilIdle()
        vm.onMoveToFolder()
        advanceUntilIdle()

        repository.folderRows.value += com.lisdatasolutions.microrag.domain.rag.Folder(3, "Contratos", 30, 0)
        advanceUntilIdle()
        vm.onFolderCreated(3)
        advanceUntilIdle()

        assertThat(repository.calls).containsExactly("move 7 3")
        assertThat(vm.state.value.moveMessage).isEqualTo(MoveMessage.Moved("Contratos"))
    }


    // F19 T038 (contracts U13–U14).

    @Test
    fun withTheModeOn_reindexAsksForTheMode_andUsesTheChosenOne() = runTest(dispatcher) {
        mode.state.value = StoredMultimodal(MultimodalStage.ON)
        show(DetailResult.Loaded(detail()))
        val vm = viewModel()
        advanceUntilIdle()

        vm.onReindex()
        advanceUntilIdle()
        assertThat(vm.state.value.choosingReindexMode).isTrue()
        assertThat(repository.calls).isEmpty()

        vm.onReindexModeSelect(IndexMode.BOTH)
        advanceUntilIdle()
        assertThat(vm.state.value.choosingReindexMode).isFalse()
        assertThat(repository.calls).containsExactly("reindex 7 BOTH")
    }

    @Test
    fun withTheModeOff_aDocumentWithPages_isReindexedInText() = runTest(dispatcher) {
        show(DetailResult.Loaded(detail().copy(indexMode = IndexMode.VISUAL, pagesStored = 48)))
        val vm = viewModel()
        advanceUntilIdle()

        vm.onReindex()
        advanceUntilIdle()

        assertThat(vm.state.value.choosingReindexMode).isFalse()
        assertThat(repository.calls).containsExactly("reindex 7 TEXT")
        assertThat(vm.state.value.pagesNeedMultimodal).isTrue()
    }

    @Test
    fun aPdfWithoutText_canBeReindexedAsPages_onlyWithTheModeOn() = runTest(dispatcher) {
        show(DetailResult.Loaded(detail(DocumentDetailState.Failed(IngestError.NO_TEXT), chunks = null)))
        val vm = viewModel()
        advanceUntilIdle()
        assertThat(vm.state.value.canReindexAsPages).isFalse()

        mode.state.value = StoredMultimodal(MultimodalStage.ON)
        advanceUntilIdle()
        assertThat(vm.state.value.canReindexAsPages).isTrue()
        vm.onReindexAsPages()
        advanceUntilIdle()
        assertThat(repository.calls).containsExactly("reindex 7 VISUAL")
    }

    // F20 T036 (contract U9, research R10, R15).

    private suspend fun storePage(page: Int) {
        while (pagesIndex.docs.size < 7) pagesIndex.addDocument(NewDocument(displayName = "d", contentHash = "d${pagesIndex.docs.size}"))
        pagesIndex.insertPage(7, PageInput(page, "7-$page.jpg", 10, 10, 1, FloatArray(256)))
    }

    @Test
    fun aCitedPage_isThePreview_whileItIsStored() = runTest(dispatcher) {
        show(DetailResult.Loaded(detail()))
        storePage(4)

        val vm = viewModel(page = 4)
        advanceUntilIdle()

        assertThat(vm.state.value.preview).isEqualTo(PreviewContent.Page(7, 4, "Manual de procesos.pdf", image = false))
        assertThat(vm.state.value.citedPageMissing).isFalse()
        assertThat(repository.previewRequests).isEmpty()
    }

    @Test
    fun aCitedPageNoLongerStored_showsTheUsualPreview_withItsNotice() = runTest(dispatcher) {
        show(DetailResult.Loaded(detail()))
        val first = DocumentPreview(1, null, "Primer fragmento.", null, false, false)
        repository.previews[7L to null] = first

        val vm = viewModel(page = 4)
        advanceUntilIdle()

        assertThat(vm.state.value.preview).isEqualTo(PreviewContent.Shown(first))
        assertThat(vm.state.value.citedPageMissing).isTrue()
    }
}
