package com.lisdatasolutions.microrag.domain.onboarding

import com.google.common.truth.Truth.assertThat
import com.lisdatasolutions.microrag.data.models.ModelFileRepository
import com.lisdatasolutions.microrag.domain.model.ComputeBackend
import com.lisdatasolutions.microrag.domain.model.LocalAiStatus
import com.lisdatasolutions.microrag.domain.model.ModelCatalog
import com.lisdatasolutions.microrag.domain.model.ModelFileState
import com.lisdatasolutions.microrag.domain.model.ModelId
import com.lisdatasolutions.microrag.domain.model.OnboardingStage
import com.lisdatasolutions.microrag.testing.GatedModelFileRepository
import com.lisdatasolutions.microrag.testing.InMemoryModelFileRepository
import java.io.File
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test

/** F09 T004 (research R1, rules H1–H3): the Home chip from the real model state, never before a check, never hashing. */
class ObserveLocalAiUseCaseTest {

    private val required = ModelCatalog.required.map { it.id }.toSet()

    /** Every required model verified, and [prepared] of them with a recorded backend. */
    private suspend fun repository(verified: Set<ModelId> = required, prepared: Set<ModelId> = required) =
        InMemoryModelFileRepository(verified = verified).apply { prepared.forEach { markPrepared(it, ComputeBackend.GPU) } }

    @Test
    fun nothingIsEmitted_untilTheFirstRefreshEnds_H1() = runTest {
        val repository = GatedModelFileRepository(repository()).apply { gate = CompletableDeferred() }
        val emitted = mutableListOf<LocalAiStatus>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { ObserveLocalAiUseCase(repository)().toList(emitted) }
        advanceUntilIdle()

        assertThat(emitted).isEmpty()

        repository.gate!!.complete(Unit)
        advanceUntilIdle()

        assertThat(emitted).containsExactly(LocalAiStatus.READY)
    }

    @Test
    fun ready_onlyWithEveryRequiredModelVerifiedAndPrepared_H3() = runTest {
        suspend fun statusOf(repository: ModelFileRepository): LocalAiStatus {
            val emitted = mutableListOf<LocalAiStatus>()
            val job = launch { ObserveLocalAiUseCase(repository)().toList(emitted) }
            advanceUntilIdle()
            job.cancel()
            return emitted.single()
        }
        val llm = ModelId.GEMMA4_E2B
        val embeddings = ModelId.EG2_270M

        assertThat(statusOf(repository())).isEqualTo(LocalAiStatus.READY)
        assertThat(statusOf(repository(verified = emptySet(), prepared = emptySet()))).isEqualTo(LocalAiStatus.SETUP_PENDING)
        assertThat(statusOf(repository(verified = setOf(llm), prepared = setOf(llm)))).isEqualTo(LocalAiStatus.SETUP_PENDING)
        assertThat(statusOf(repository(prepared = setOf(embeddings)))).isEqualTo(LocalAiStatus.SETUP_PENDING)
        assertThat(statusOf(repository().apply { unverifiedOnRefresh = setOf(embeddings) })).isEqualTo(LocalAiStatus.SETUP_PENDING)
    }

    @Test
    fun aFileThatChanges_emitsTheNewStatusAfterARefresh() = runTest {
        val repository = repository()
        val emitted = mutableListOf<LocalAiStatus>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { ObserveLocalAiUseCase(repository)().toList(emitted) }
        advanceUntilIdle()

        // The embedding model was touched while the app was in the background (quickstart §3.3).
        repository.unverifiedOnRefresh = setOf(ModelId.EG2_270M)
        RefreshLocalAiUseCase(repository)()
        advanceUntilIdle()

        assertThat(emitted).containsExactly(LocalAiStatus.READY, LocalAiStatus.SETUP_PENDING).inOrder()
        assertThat(repository.refreshes).hasSize(2)
    }

    @Test
    fun theChecks_neverHash_H2() = runTest {
        val repository = repository().apply { unverifiedOnRefresh = setOf(ModelId.EG2_270M) }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { ObserveLocalAiUseCase(repository)().toList(mutableListOf()) }
        RefreshLocalAiUseCase(repository)()
        advanceUntilIdle()

        assertThat(repository.verifyCalls).isEmpty()
        assertThat(repository.adopted).isEmpty()
        assertThat(repository.streamed).isEmpty()
    }

    @Test
    fun allReady_agreesWithTheOnboardingCompletion() = runTest {
        val path = File("/models/x")
        val verified = required.associateWith { ModelFileState.Verified(path) }
        val backends = required.associateWith { ComputeBackend.CPU }
        val cases = listOf(
            verified to backends,
            verified to backends - ModelId.GEMMA4_E2B,
            verified + (ModelId.EG2_270M to ModelFileState.Unverified(path)) to backends,
            verified + (ModelId.GEMMA4_E2B to ModelFileState.Missing) to backends,
            emptyMap<ModelId, ModelFileState>() to emptyMap(),
        )

        for ((states, prepared) in cases) {
            val repository = InMemoryModelFileRepository().apply {
                this.states.value = states
                preparedBackends.value = prepared
            }
            val store = FakeOnboardingStore()
            val completed = CompleteOnboardingUseCase(repository, store)()

            assertThat(completed).isEqualTo(ModelCatalog.allReady(states, prepared))
            assertThat(store.stage.value == OnboardingStage.COMPLETED).isEqualTo(completed)
        }
    }
}
