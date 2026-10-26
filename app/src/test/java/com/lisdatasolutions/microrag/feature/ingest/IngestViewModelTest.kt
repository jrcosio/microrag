package com.lisdatasolutions.microrag.feature.ingest

import com.google.common.truth.Truth.assertThat
import com.lisdatasolutions.microrag.domain.ingest.DocumentFormat
import com.lisdatasolutions.microrag.domain.ingest.ImportDocumentsUseCase
import com.lisdatasolutions.microrag.domain.ingest.ImportQueue
import com.lisdatasolutions.microrag.domain.ingest.ObserveImportQueueUseCase
import com.lisdatasolutions.microrag.domain.ingest.QueueItem
import com.lisdatasolutions.microrag.domain.ingest.QueueItemState
import com.lisdatasolutions.microrag.domain.ingest.QueueKey
import com.lisdatasolutions.microrag.domain.ingest.StartIndexingUseCase
import com.lisdatasolutions.microrag.testing.FakeImportQueueRepository
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

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() = IngestViewModel(
        ObserveImportQueueUseCase(repository),
        ImportDocumentsUseCase(repository),
        StartIndexingUseCase(repository),
        backgroundDispatcher = dispatcher,
    )

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
}
