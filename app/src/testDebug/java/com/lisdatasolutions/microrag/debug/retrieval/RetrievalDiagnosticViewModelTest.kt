package com.lisdatasolutions.microrag.debug.retrieval

import com.google.common.truth.Truth.assertThat
import com.lisdatasolutions.microrag.debug.retrieval.RetrievalDiagnosticUiState.Status
import com.lisdatasolutions.microrag.domain.rag.IndexCompatibility
import com.lisdatasolutions.microrag.domain.retrieval.ContextRetriever
import com.lisdatasolutions.microrag.domain.retrieval.RetrievalOutcome
import com.lisdatasolutions.microrag.domain.retrieval.RetrievalRequest
import com.lisdatasolutions.microrag.domain.retrieval.RetrievalResult
import com.lisdatasolutions.microrag.domain.retrieval.RetrievalTimings
import com.lisdatasolutions.microrag.domain.retrieval.RetrieveContextUseCase
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

/** F10 T035 (contracts/retrieval-diagnostic.md): Idle → Searching → Done, one search at a time. */
@OptIn(ExperimentalCoroutinesApi::class)
class RetrievalDiagnosticViewModelTest {

    private val main = StandardTestDispatcher()
    private val requests = mutableListOf<RetrievalRequest>()
    private var gate = CompletableDeferred<Unit>().apply { complete(Unit) }
    private var outcome: RetrievalOutcome = RetrievalOutcome.NoResults

    private val retriever = object : ContextRetriever {
        override suspend fun retrieve(request: RetrievalRequest): RetrievalResult {
            requests += request
            gate.await()
            return RetrievalResult(request.question, false, 0, RetrievalTimings(), outcome)
        }
    }
    private val viewModel by lazy { RetrievalDiagnosticViewModel(RetrieveContextUseCase(retriever)) }

    @Before
    fun setUp() = Dispatchers.setMain(main)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun aSearchGoesFromIdleToSearchingToDone() = runTest(main) {
        gate = CompletableDeferred()
        assertThat(viewModel.state.value.status).isEqualTo(Status.Idle)

        viewModel.onQuestionChange("¿Cada cuánto se revisa la bomba?")
        viewModel.onSearch()
        runCurrent()
        assertThat(viewModel.state.value.status).isEqualTo(Status.Searching)
        assertThat(viewModel.state.value.canSearch).isFalse()

        gate.complete(Unit)
        advanceUntilIdle()
        val done = viewModel.state.value.status as Status.Done
        assertThat(done.result.searchText).isEqualTo("¿Cada cuánto se revisa la bomba?")
        assertThat(viewModel.state.value.canSearch).isTrue()
    }

    @Test
    fun aSecondPressWhileSearching_isIgnored() = runTest(main) {
        gate = CompletableDeferred()
        viewModel.onQuestionChange("pregunta")
        viewModel.onSearch()
        runCurrent()
        viewModel.onSearch()
        viewModel.onSearch()
        gate.complete(Unit)
        advanceUntilIdle()

        assertThat(requests).hasSize(1)
    }

    @Test
    fun aBlankPreviousQuestionIsPassedAsNone() = runTest(main) {
        viewModel.onQuestionChange("¿Y el aceite?")
        viewModel.onPreviousChange("   ")
        viewModel.onSearch()
        advanceUntilIdle()
        viewModel.onPreviousChange("¿Cada cuánto se revisa la bomba?")
        viewModel.onSearch()
        advanceUntilIdle()

        assertThat(requests).containsExactly(
            RetrievalRequest("¿Y el aceite?", null),
            RetrievalRequest("¿Y el aceite?", "¿Cada cuánto se revisa la bomba?"),
        ).inOrder()
    }

    @Test
    fun everyOutcomeEndsInDone() = runTest(main) {
        val outcomes = listOf(
            RetrievalOutcome.Blocks(emptyList(), emptyList()), RetrievalOutcome.NoResults, RetrievalOutcome.EmptyQuestion,
            RetrievalOutcome.IndexUnavailable(IndexCompatibility.Unreadable("x")), RetrievalOutcome.ModelsNotReady,
            RetrievalOutcome.EmbeddingFailed,
        )
        outcomes.forEach { expected ->
            outcome = expected
            viewModel.onSearch()
            advanceUntilIdle()
            assertThat((viewModel.state.value.status as Status.Done).result.outcome).isEqualTo(expected)
        }
    }

    @Test
    fun theViewModelOnlyDependsOnTheUseCase_soItCannotLog() {
        val parameters = RetrievalDiagnosticViewModel::class.java.constructors.single().parameterTypes.toList()
        assertThat(parameters).containsExactly(RetrieveContextUseCase::class.java)
    }
}
