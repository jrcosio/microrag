package com.lisdatasolutions.microrag.feature.ingest

import androidx.lifecycle.SavedStateHandle
import com.google.common.truth.Truth.assertThat
import com.lisdatasolutions.microrag.domain.ingest.DocumentFormat
import com.lisdatasolutions.microrag.domain.ingest.ImportDocumentsUseCase
import com.lisdatasolutions.microrag.domain.ingest.ImportQueue
import com.lisdatasolutions.microrag.domain.ingest.IngestError
import com.lisdatasolutions.microrag.domain.ingest.ObserveImportQueueUseCase
import com.lisdatasolutions.microrag.domain.ingest.QueueItem
import com.lisdatasolutions.microrag.domain.ingest.QueueItemState
import com.lisdatasolutions.microrag.domain.ingest.QueueKey
import com.lisdatasolutions.microrag.domain.ingest.RemoveQueueItemUseCase
import com.lisdatasolutions.microrag.domain.ingest.RetryQueueItemUseCase
import com.lisdatasolutions.microrag.domain.ingest.StartIndexingUseCase
import com.lisdatasolutions.microrag.domain.library.ObserveFoldersUseCase
import com.lisdatasolutions.microrag.domain.multimodal.MultimodalStage
import com.lisdatasolutions.microrag.domain.multimodal.ObserveImportModeUseCase
import com.lisdatasolutions.microrag.domain.multimodal.StoredMultimodal
import com.lisdatasolutions.microrag.domain.rag.IndexMode
import com.lisdatasolutions.microrag.testing.FakeImportQueueRepository
import com.lisdatasolutions.microrag.testing.FakeLibraryRepository
import com.lisdatasolutions.microrag.testing.FakeMultimodalStore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

/** F06 US1 (T021): the screen state comes from the queue; picks go to the use case. */
@OptIn(ExperimentalCoroutinesApi::class)
class IngestViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val repository = FakeImportQueueRepository()
    private val library = FakeLibraryRepository()
    private var saved = SavedStateHandle()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    /** F19: the mode's copy, as Ajustes leaves it. */
    private val mode = FakeMultimodalStore()

    private fun viewModel() = IngestViewModel(
        ObserveImportQueueUseCase(repository),
        ImportDocumentsUseCase(repository),
        StartIndexingUseCase(repository),
        RetryQueueItemUseCase(repository),
        RemoveQueueItemUseCase(repository),
        ObserveFoldersUseCase(library),
        saved,
        observeMode = ObserveImportModeUseCase(mode),
        backgroundDispatcher = dispatcher,
    )

    // F19 T029 (contracts/multimodal-ui.md U8, contract I1).

    @Test
    fun withTheModeOff_thereIsNoModeRow_andEverythingIsText() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        assertThat(vm.state.value.multimodalOn).isFalse()
        vm.onChooseMode()
        advanceUntilIdle()
        assertThat(vm.state.value.choosingMode).isFalse()
        vm.onFilesPicked(listOf("content://a"))
        assertThat(repository.modes).containsExactly(IndexMode.TEXT)
    }

    @Test
    fun withTheModeOn_theSelectionStartsAsAjustes_andGoesWithThePick() = runTest(dispatcher) {
        mode.state.value = StoredMultimodal(MultimodalStage.ON, defaultMode = IndexMode.VISUAL)
        val vm = viewModel()
        advanceUntilIdle()

        assertThat(vm.state.value.multimodalOn).isTrue()
        assertThat(vm.state.value.indexMode).isEqualTo(IndexMode.VISUAL)
        vm.onFilesPicked(listOf("content://a"))
        assertThat(repository.modes).containsExactly(IndexMode.VISUAL)
    }

    @Test
    fun theSheet_changesTheSelection_whichSurvivesARotation() = runTest(dispatcher) {
        mode.state.value = StoredMultimodal(MultimodalStage.ON)
        val vm = viewModel()
        advanceUntilIdle()
        assertThat(vm.state.value.indexMode).isEqualTo(IndexMode.BOTH)

        vm.onChooseMode()
        advanceUntilIdle()
        assertThat(vm.state.value.choosingMode).isTrue()
        vm.onModeSelect(IndexMode.TEXT)
        advanceUntilIdle()
        assertThat(vm.state.value.choosingMode).isFalse()
        assertThat(vm.state.value.indexMode).isEqualTo(IndexMode.TEXT)

        val rotated = viewModel()
        advanceUntilIdle()
        assertThat(rotated.state.value.indexMode).isEqualTo(IndexMode.TEXT)
        rotated.onFilesPicked(listOf("content://a"))
        assertThat(repository.modes).containsExactly(IndexMode.TEXT)

        // Turning the mode off sends everything in Text again.
        mode.state.value = StoredMultimodal(MultimodalStage.OFF)
        advanceUntilIdle()
        rotated.onFilesPicked(listOf("content://b"))
        assertThat(repository.modes).containsExactly(IndexMode.TEXT, IndexMode.TEXT).inOrder()
    }

    private fun ready(id: Long, name: String = "doc$id.pdf") =
        QueueItem(QueueKey.Document(id), name, 2_400_000, DocumentFormat.PDF, QueueItemState.Ready)

    @Test
    fun pickedFilesGoToTheImportAndACancelledPickerDoesNothing() = runTest(dispatcher) {
        val vm = viewModel()

        vm.onFilesPicked(listOf("content://a", "content://b"))
        vm.onFilesPicked(emptyList())

        assertThat(repository.imported).containsExactly(listOf("content://a", "content://b"))
    }

    @Test
    fun stateFollowsTheQueue() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        assertThat(vm.state.value.loading).isFalse()
        assertThat(vm.state.value.startEnabled).isFalse()

        repository.state.value = ImportQueue(listOf(ready(1), ready(2)), readyCount = 2, pdfSupported = true, indexAvailable = true)
        advanceUntilIdle()

        assertThat(vm.state.value.items.map { it.key }).containsExactly(QueueKey.Document(1), QueueKey.Document(2)).inOrder()
        assertThat(vm.state.value.startEnabled).isTrue()
        assertThat(vm.state.value.pickEnabled).isTrue()
    }

    @Test
    fun anUnavailableLibraryDisablesPickingAndStarting() = runTest(dispatcher) {
        val vm = viewModel()
        repository.state.value = ImportQueue(listOf(ready(1)), readyCount = 1, pdfSupported = false, indexAvailable = false)
        advanceUntilIdle()

        assertThat(vm.state.value.pickEnabled).isFalse()
        assertThat(vm.state.value.startEnabled).isFalse()
        assertThat(vm.state.value.queue.pdfSupported).isFalse()
    }

    @Test
    fun nothingReadyButARunToGoOn_enablesStarting() = runTest(dispatcher) {
        // F07 T055: chunked or half-indexed documents without work, or a paused run (index-ui §Ingesta).
        val vm = viewModel()
        val paused = QueueItem(QueueKey.Document(1), "a.txt", 10, DocumentFormat.TXT, QueueItemState.Paused(64, 430))
        repository.state.value = ImportQueue(listOf(paused), readyCount = 0, pdfSupported = true, indexAvailable = true, canStart = true)
        advanceUntilIdle()

        assertThat(vm.state.value.startEnabled).isTrue()
    }

    @Test
    fun startingTwiceEnqueuesOnceAndOpensTheIndexingScreen() = runTest(dispatcher) {
        val vm = viewModel()
        repository.state.value = ImportQueue(listOf(ready(1)), readyCount = 1, pdfSupported = true, indexAvailable = true)
        val events = mutableListOf<IngestEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.ingestEvents.toList(events) }
        val gate = CompletableDeferred<Unit>().also { repository.startGate = it }
        advanceUntilIdle()

        vm.onStartIndexing()
        vm.onStartIndexing()
        advanceUntilIdle()
        assertThat(vm.state.value.starting).isTrue()
        assertThat(vm.state.value.startEnabled).isFalse()

        gate.complete(Unit)
        advanceUntilIdle()

        assertThat(repository.starts).isEqualTo(1)
        assertThat(events).containsExactly(IngestEvent.OpenIndex)
        assertThat(vm.state.value.starting).isFalse()
    }

    @Test
    fun nothingEnqueuedStaysOnTheScreen() = runTest(dispatcher) {
        val vm = viewModel()
        repository.startResult = 0
        val events = mutableListOf<IngestEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.ingestEvents.toList(events) }

        vm.onStartIndexing()
        advanceUntilIdle()

        assertThat(events).isEmpty()
        assertThat(vm.state.first().starting).isFalse()
    }

    // F06 US2 (T033): detail sheet, retry and removal with confirmation.

    private fun failedAttempt() = QueueItem(QueueKey.Attempt("a1"), "roto.pdf", 1_000, DocumentFormat.PDF,
        QueueItemState.ImportFailed(IngestError.READ_FAILED))

    private fun failedDocument() = QueueItem(QueueKey.Document(7), "escaneado.pdf", 5_000, DocumentFormat.PDF,
        QueueItemState.Failed(IngestError.NO_TEXT))

    @Test
    fun theSheetOpensForTheTappedRowAndClosesWhenItLeavesTheQueue() = runTest(dispatcher) {
        val vm = viewModel()
        repository.state.value = ImportQueue(listOf(failedAttempt(), failedDocument()), 0, pdfSupported = true, indexAvailable = true)
        advanceUntilIdle()

        vm.onRowClick(QueueKey.Document(7))
        advanceUntilIdle()
        assertThat(vm.state.value.selectedItem?.displayName).isEqualTo("escaneado.pdf")
        assertThat(vm.state.value.selectedItem?.canRetry).isFalse()

        repository.state.value = ImportQueue(listOf(failedAttempt()), 0, pdfSupported = true, indexAvailable = true)
        advanceUntilIdle()
        assertThat(vm.state.value.selectedItem).isNull()
    }

    @Test
    fun retryClosesTheSheetAndCallsTheUseCase() = runTest(dispatcher) {
        val vm = viewModel()
        repository.state.value = ImportQueue(listOf(failedAttempt()), 0, pdfSupported = true, indexAvailable = true)
        advanceUntilIdle()
        vm.onRowClick(QueueKey.Attempt("a1"))

        vm.onRetry(QueueKey.Attempt("a1"))
        advanceUntilIdle()

        assertThat(repository.retried).containsExactly(QueueKey.Attempt("a1"))
        assertThat(vm.state.value.selected).isNull()
    }

    @Test
    fun removingADocumentAsksForConfirmationButAnAttemptDoesNot() = runTest(dispatcher) {
        val vm = viewModel()
        repository.state.value = ImportQueue(listOf(failedAttempt(), failedDocument()), 0, pdfSupported = true, indexAvailable = true)
        advanceUntilIdle()

        vm.onRemove(QueueKey.Attempt("a1"))
        advanceUntilIdle()
        assertThat(repository.removed).containsExactly(QueueKey.Attempt("a1"))

        vm.onRemove(QueueKey.Document(7))
        advanceUntilIdle()
        assertThat(repository.removed).containsExactly(QueueKey.Attempt("a1"))
        assertThat(vm.state.value.removalItem?.displayName).isEqualTo("escaneado.pdf")

        vm.onDismissRemoval()
        advanceUntilIdle()
        assertThat(vm.state.value.removalItem).isNull()

        vm.onRemove(QueueKey.Document(7))
        vm.onConfirmRemoval()
        advanceUntilIdle()
        assertThat(repository.removed).containsExactly(QueueKey.Attempt("a1"), QueueKey.Document(7)).inOrder()
        assertThat(vm.state.value.removalItem).isNull()
    }

    // F17 T025 (K6, M3): the destination folder.

    private fun withFolders() {
        library.folderRows.value = listOf(
            com.lisdatasolutions.microrag.domain.rag.Folder(1, "Legal", 10, 0),
            com.lisdatasolutions.microrag.domain.rag.Folder(2, "Motores", 20, 0),
        )
    }

    @Test
    fun theDestinationStartsAsTheRoutes_andGoesWithEachPick() = runTest(dispatcher) {
        withFolders()
        saved = SavedStateHandle(mapOf("folderId" to 1L))
        val vm = viewModel()
        advanceUntilIdle()
        assertThat(vm.state.value.destination).isEqualTo(1)
        assertThat(vm.state.value.destinationName).isEqualTo("Legal")

        vm.onFilesPicked(listOf("content://a"))
        vm.onChooseDestination()
        advanceUntilIdle()
        assertThat(vm.state.value.choosing).isEqualTo(DestinationChoice(selected = 1))
        vm.onDestinationSelect(null)
        advanceUntilIdle()
        vm.onConfirmDestination()
        advanceUntilIdle()
        vm.onFilesPicked(listOf("content://b"))

        assertThat(vm.state.value.choosing).isNull()
        assertThat(vm.state.value.destination).isNull()
        assertThat(repository.destinations).containsExactly(1L, null).inOrder()
    }

    @Test
    fun withoutARouteFolderItIsGeneral_andTheChoiceSurvivesRecreation() = runTest(dispatcher) {
        withFolders()
        val vm = viewModel()
        advanceUntilIdle()
        assertThat(vm.state.value.destination).isNull()

        vm.onChooseDestination()
        vm.onDestinationSelect(2)
        advanceUntilIdle()
        val restored = viewModel()
        advanceUntilIdle()

        assertThat(restored.state.value.choosing).isEqualTo(DestinationChoice(selected = 2))
        restored.onConfirmDestination()
        advanceUntilIdle()
        assertThat(restored.state.value.destination).isEqualTo(2)
    }

    @Test
    fun aFolderCreatedFromTheSheetIsTheDestinationAtOnce() = runTest(dispatcher) {
        withFolders()
        val vm = viewModel()
        advanceUntilIdle()
        vm.onChooseDestination()
        advanceUntilIdle()

        library.folderRows.value += com.lisdatasolutions.microrag.domain.rag.Folder(3, "Contratos", 30, 0)
        vm.onFolderCreated(3)
        advanceUntilIdle()

        assertThat(vm.state.value.destination).isEqualTo(3)
        assertThat(vm.state.value.choosing).isNull()
    }

    /** M3: a destination that is deleted goes back to «General», and the screen says so. */
    @Test
    fun aDeletedDestinationIsGeneralAgain() = runTest(dispatcher) {
        withFolders()
        saved = SavedStateHandle(mapOf("folderId" to 1L))
        val vm = viewModel()
        advanceUntilIdle()

        library.folderRows.value = library.folderRows.value.filterNot { it.id == 1L }
        advanceUntilIdle()

        assertThat(vm.state.value.destination).isNull()
        assertThat(vm.state.value.destinationMissing).isTrue()
        vm.onFilesPicked(listOf("content://a"))
        assertThat(repository.destinations).containsExactly(null)
    }
}
