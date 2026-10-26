package com.lisdatasolutions.microrag.feature.indexing

import com.google.common.truth.Truth.assertThat
import com.lisdatasolutions.microrag.domain.indexing.FailedDocument
import com.lisdatasolutions.microrag.domain.indexing.IndexingRepository
import com.lisdatasolutions.microrag.domain.indexing.IndexingState
import com.lisdatasolutions.microrag.domain.indexing.ObserveIndexingUseCase
import com.lisdatasolutions.microrag.domain.indexing.PauseIndexingUseCase
import com.lisdatasolutions.microrag.domain.indexing.PauseReason
import com.lisdatasolutions.microrag.domain.indexing.ResumeIndexingUseCase
import com.lisdatasolutions.microrag.domain.indexing.RetryIndexingUseCase
import com.lisdatasolutions.microrag.domain.indexing.RunProgress
import com.lisdatasolutions.microrag.domain.ingest.IngestError
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

/** F07 T030: the screen state mirrors the run, and its actions go to the use cases, one at a time. */
@OptIn(ExperimentalCoroutinesApi::class)
class IndexViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    private class FakeRepository : IndexingRepository {
        override val state = MutableStateFlow<IndexingState>(IndexingState.Loading)
        val calls = mutableListOf<String>()
        var gate: CompletableDeferred<Unit>? = null

        override suspend fun start(): Int = 0.also { calls += "start" }

        override suspend fun pause() {
            calls += "pause"
            gate?.await()
        }

        override suspend fun resume() {
            calls += "resume"
        }

        override suspend fun retry(documentId: Long) {
            calls += "retry $documentId"
        }

        override suspend fun reconcile() {
            calls += "reconcile"
        }
    }

    private val repository = FakeRepository()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() = IndexViewModel(
        ObserveIndexingUseCase(repository), PauseIndexingUseCase(repository), ResumeIndexingUseCase(repository),
        RetryIndexingUseCase(repository), backgroundDispatcher = dispatcher,
    )

    private fun active(pause: PauseReason? = null) =
        IndexingState.Active(RunProgress(10, 100, null), current = null, queuedDocuments = 1, pause = pause, failed = emptyList())

    @Test
    fun stateAndAction_followTheRun() = runTest(dispatcher) {
        val viewModel = viewModel()
        repository.state.value = active()
        advanceUntilIdle()
        assertThat(viewModel.state.value.indexing).isEqualTo(active())
        assertThat(viewModel.state.value.action).isEqualTo(IndexAction.Pause)

        repository.state.value = active(PauseReason.USER)
        advanceUntilIdle()
        assertThat(viewModel.state.value.action).isEqualTo(IndexAction.Resume)

        repository.state.value = active(PauseReason.THERMAL)
        advanceUntilIdle()
        assertThat(viewModel.state.value.action).isEqualTo(IndexAction.Pause)

        repository.state.value = active(PauseReason.SYSTEM_LIMIT)
        advanceUntilIdle()
        assertThat(viewModel.state.value.action).isEqualTo(IndexAction.Pause)

        repository.state.value = active(PauseReason.NO_SPACE)
        advanceUntilIdle()
        assertThat(viewModel.state.value.action).isEqualTo(IndexAction.Resume)

        repository.state.value = active(PauseReason.MODEL_MISSING)
        advanceUntilIdle()
        assertThat(viewModel.state.value.action).isNull()

        repository.state.value = IndexingState.Idle(listOf(FailedDocument(4, "a.pdf", IngestError.EMBEDDING_FAILED)))
        advanceUntilIdle()
        assertThat(viewModel.state.value.action).isEqualTo(IndexAction.Retry(4))

        repository.state.value = IndexingState.Idle(listOf(FailedDocument(4, "a.pdf", IngestError.NO_TEXT)))
        advanceUntilIdle()
        assertThat(viewModel.state.value.action).isNull()
    }

    @Test
    fun actions_reachTheUseCasesOneAtATime() = runTest(dispatcher) {
        val viewModel = viewModel()
        val gate = CompletableDeferred<Unit>().also { repository.gate = it }

        viewModel.onPause()
        advanceUntilIdle()
        assertThat(viewModel.state.value.busy).isTrue()
        viewModel.onResume()
        advanceUntilIdle()
        assertThat(repository.calls).containsExactly("pause")

        gate.complete(Unit)
        advanceUntilIdle()
        viewModel.onResume()
        viewModel.onRetry(9)
        advanceUntilIdle()
        assertThat(repository.calls).containsExactly("pause", "resume").inOrder()
        viewModel.onRetry(9)
        advanceUntilIdle()
        assertThat(repository.calls).containsExactly("pause", "resume", "retry 9").inOrder()
    }

    @Test
    fun prepareModels_opensTheSetup() = runTest(dispatcher) {
        val viewModel = viewModel()

        viewModel.onPrepareModels()

        assertThat(viewModel.indexEvents.first()).isEqualTo(IndexEvent.OpenSetup)
    }
}
