package com.lisdatasolutions.microrag.feature.home

import com.google.common.truth.Truth.assertThat
import com.lisdatasolutions.microrag.domain.model.ComputeBackend
import com.lisdatasolutions.microrag.domain.model.LocalAiStatus
import com.lisdatasolutions.microrag.domain.model.ModelCatalog
import com.lisdatasolutions.microrag.domain.model.ModelId
import com.lisdatasolutions.microrag.domain.library.ObserveLibrarySummaryUseCase
import com.lisdatasolutions.microrag.domain.library.SummaryResult
import com.lisdatasolutions.microrag.domain.onboarding.ObserveLocalAiUseCase
import com.lisdatasolutions.microrag.domain.rag.LibrarySummary
import com.lisdatasolutions.microrag.domain.onboarding.RefreshLocalAiUseCase
import com.lisdatasolutions.microrag.testing.FakeLibraryRepository
import com.lisdatasolutions.microrag.testing.GatedModelFileRepository
import com.lisdatasolutions.microrag.testing.InMemoryModelFileRepository
import com.lisdatasolutions.microrag.testing.RecordingLogger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

/** F09 T014 (research R1, R5): the chip only after a real check, and again on every return to the foreground. */
@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val logger = RecordingLogger()
    private val required = ModelCatalog.required.map { it.id }.toSet()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private suspend fun models(ready: Boolean = true) = GatedModelFileRepository(
        InMemoryModelFileRepository(verified = if (ready) required else emptySet()).apply {
            if (ready) required.forEach { markPrepared(it, ComputeBackend.GPU) }
        },
    )

    private val library = FakeLibraryRepository()

    private fun viewModel(models: GatedModelFileRepository) =
        HomeViewModel(ObserveLocalAiUseCase(models), RefreshLocalAiUseCase(models), ObserveLibrarySummaryUseCase(library), logger)

    private fun summary(documents: Int, chunks: Int) = SummaryResult.Loaded(LibrarySummary(documents, chunks))

    // US2 (T014)

    @Test
    fun noStatus_untilTheFirstCheckEnds() = runTest(dispatcher) {
        val models = models().apply { gate = CompletableDeferred() }
        val vm = viewModel(models)
        advanceUntilIdle()

        assertThat(vm.state.value.ai).isNull()

        models.gate!!.complete(Unit)
        advanceUntilIdle()

        assertThat(vm.state.value.ai).isEqualTo(LocalAiStatus.READY)
    }

    @Test
    fun theStatus_comesFromTheModels() = runTest(dispatcher) {
        val ready = viewModel(models(ready = true))
        val pending = viewModel(models(ready = false))
        advanceUntilIdle()

        assertThat(ready.state.value.ai).isEqualTo(LocalAiStatus.READY)
        assertThat(pending.state.value.ai).isEqualTo(LocalAiStatus.SETUP_PENDING)
    }

    @Test
    fun onResume_checksAgain_keepingTheLastStatusMeanwhile() = runTest(dispatcher) {
        val models = models()
        val vm = viewModel(models)
        advanceUntilIdle()
        assertThat(vm.state.value.ai).isEqualTo(LocalAiStatus.READY)

        // The embedding model changed while the app was in the background (quickstart §3.3).
        models.gate = CompletableDeferred()
        models.inner.unverifiedOnRefresh = setOf(ModelId.EG2_270M)
        vm.onResume()
        advanceUntilIdle()
        assertThat(vm.state.value.ai).isEqualTo(LocalAiStatus.READY)

        models.gate!!.complete(Unit)
        advanceUntilIdle()

        assertThat(vm.state.value.ai).isEqualTo(LocalAiStatus.SETUP_PENDING)
    }

    @Test
    fun theFirstCheck_isLoggedWithItsTimeOnly() = runTest(dispatcher) {
        viewModel(models())
        advanceUntilIdle()

        assertThat(logger.lines.single { it.contains("home ai=") }).matches(".*home ai=READY in \\d+ ms")
    }

    // US3 (T018)

    @Test
    fun theSummary_startsLoading_thenShowsTheCounts() = runTest(dispatcher) {
        library.summaryResult.value = null
        val vm = viewModel(models())
        advanceUntilIdle()
        assertThat(vm.state.value.summary).isEqualTo(SummaryState.Loading)

        library.summaryResult.value = summary(24, 12_400)
        advanceUntilIdle()

        assertThat(vm.state.value.summary).isEqualTo(SummaryState.Loaded(documents = 24, chunks = 12_400))
    }

    @Test
    fun noDocuments_isTheEmptyState_FR015() = runTest(dispatcher) {
        library.summaryResult.value = summary(0, 0)
        val vm = viewModel(models())
        advanceUntilIdle()

        assertThat(vm.state.value.summary).isEqualTo(SummaryState.Empty)
    }

    @Test
    fun anUnavailableIndex_isAnErrorThatSaysSo() = runTest(dispatcher) {
        library.summaryResult.value = SummaryResult.Unavailable
        val vm = viewModel(models())
        advanceUntilIdle()

        assertThat(vm.state.value.summary).isEqualTo(SummaryState.Error(unavailable = true))
    }

    @Test
    fun aFailure_isAnError_andRetryObservesOnlyTheSummaryAgain_H7() = runTest(dispatcher) {
        library.summaryError = IllegalStateException("disk")
        library.summaryResult.value = summary(3, 0)
        val models = models()
        val vm = viewModel(models)
        advanceUntilIdle()
        assertThat(vm.state.value.summary).isEqualTo(SummaryState.Error(unavailable = false))
        assertThat(vm.state.value.ai).isEqualTo(LocalAiStatus.READY)

        vm.onRetrySummary()
        advanceUntilIdle()

        assertThat(vm.state.value.summary).isEqualTo(SummaryState.Loaded(documents = 3, chunks = 0))
        assertThat(library.summaryObservations).isEqualTo(2)
        assertThat(models.inner.refreshes).hasSize(1)
        assertThat(logger.lines.joinToString("\n")).doesNotContain("disk")
    }

    @Test
    fun indexChanges_updateTheCounts_FR014() = runTest(dispatcher) {
        library.summaryResult.value = summary(1, 0)
        val vm = viewModel(models())
        advanceUntilIdle()

        library.summaryResult.value = summary(1, 430)
        advanceUntilIdle()
        assertThat(vm.state.value.summary).isEqualTo(SummaryState.Loaded(documents = 1, chunks = 430))

        library.summaryResult.value = summary(0, 0)
        advanceUntilIdle()
        assertThat(vm.state.value.summary).isEqualTo(SummaryState.Empty)
    }

    @Test
    fun theFirstSummary_isLoggedWithCountsAndTimeOnly() = runTest(dispatcher) {
        library.summaryResult.value = summary(24, 12_400)
        viewModel(models())
        advanceUntilIdle()

        assertThat(logger.lines.single { it.contains("home summary") }).matches(".*home summary documents=24 chunks=12400 in \\d+ ms")
    }
}
