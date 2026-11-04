package com.lisdatasolutions.microrag.domain.chat

import com.google.common.truth.Truth.assertThat
import com.lisdatasolutions.microrag.domain.indexing.IndexingState
import com.lisdatasolutions.microrag.domain.indexing.ObserveIndexingUseCase
import com.lisdatasolutions.microrag.domain.indexing.PauseReason
import com.lisdatasolutions.microrag.domain.indexing.RunProgress
import com.lisdatasolutions.microrag.domain.model.ComputeBackend
import com.lisdatasolutions.microrag.domain.model.ModelCatalog
import com.lisdatasolutions.microrag.domain.onboarding.ObserveLocalAiUseCase
import com.lisdatasolutions.microrag.domain.rag.DocumentStatus
import com.lisdatasolutions.microrag.domain.rag.IndexCompatibility
import com.lisdatasolutions.microrag.domain.rag.NewDocument
import com.lisdatasolutions.microrag.testing.FakeAnswerEngine
import com.lisdatasolutions.microrag.testing.FakeIndexingRepository
import com.lisdatasolutions.microrag.testing.FakeRagIndex
import com.lisdatasolutions.microrag.testing.InMemoryChatRepository
import com.lisdatasolutions.microrag.testing.InMemoryModelFileRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test

/** F11 T016 (research R11): the chat's availability, in precedence order, from the real state. */
class ObserveChatAvailabilityUseCaseTest {

    private val chats = InMemoryChatRepository()
    private val models = InMemoryModelFileRepository(verified = ModelCatalog.required.map { it.id }.toSet()).apply {
        preparedBackends.value = ModelCatalog.required.associate { it.id to ComputeBackend.GPU }
    }
    private val engine = FakeAnswerEngine()
    private val indexing = FakeIndexingRepository()
    private val index = FakeRagIndex()

    private val observe = ObserveChatAvailabilityUseCase(chats, ObserveLocalAiUseCase(models), engine, ObserveIndexingUseCase(indexing), index)

    private suspend fun doc(name: String, status: DocumentStatus, included: Boolean = true): Long {
        val id = index.addDocument(NewDocument(displayName = name, contentHash = name, mimeType = "text/plain"))
        index.updateStatus(id, status, null, null, null)
        if (!included) index.setIncluded(id, false)
        return id
    }

    private suspend fun current(): ChatAvailability = observe().first()

    private fun running() = IndexingState.Active(RunProgress(1, 10, null), null, 0, pause = null, failed = emptyList())

    @Test
    fun ready_withSearchableDocuments() = runTest {
        doc("Hecho.txt", DocumentStatus.DONE)
        assertThat(current()).isEqualTo(ChatAvailability.Ready(cpuMode = false, partialCoverage = false))
    }

    @Test
    fun precedence_storageFirst_thenModels_index_library_batch_nothingReady() = runTest {
        doc("Hecho.txt", DocumentStatus.DONE)
        indexing.state.value = running()
        index.setCompatibility(IndexCompatibility.Unreadable("test"))
        models.preparedBackends.value = emptyMap()
        chats.storage = ChatStorageState.UNAVAILABLE
        assertThat(current()).isEqualTo(ChatAvailability.StorageUnavailable)

        chats.storage = ChatStorageState.OK
        assertThat(current()).isEqualTo(ChatAvailability.ModelsNotReady)

        models.preparedBackends.value = ModelCatalog.required.associate { it.id to ComputeBackend.GPU }
        assertThat(current()).isEqualTo(ChatAvailability.IndexUnavailable)

        index.setCompatibility(IndexCompatibility.Compatible)
        assertThat(current()).isEqualTo(ChatAvailability.WaitingForIndexing)

        indexing.state.value = IndexingState.Idle(emptyList())
        assertThat(current()).isEqualTo(ChatAvailability.Ready(cpuMode = false, partialCoverage = false))
    }

    @Test
    fun anEmptyLibrary_andOneWithoutSearchableDocuments() = runTest {
        assertThat(current()).isEqualTo(ChatAvailability.EmptyLibrary)

        doc("Pendiente.txt", DocumentStatus.PENDING)
        doc("Excluido.txt", DocumentStatus.DONE, included = false)
        assertThat(current()).isEqualTo(ChatAvailability.NothingReady)
    }

    @Test
    fun aPausedBatch_letsTheChatAnswerWithPartialCoverage() = runTest {
        doc("Hecho.txt", DocumentStatus.DONE)
        doc("A medias.txt", DocumentStatus.EMBEDDING)
        indexing.state.value = running().copy(pause = PauseReason.USER)

        assertThat(current()).isEqualTo(ChatAvailability.Ready(cpuMode = false, partialCoverage = true))
    }

    @Test
    fun errorsAndDocumentsOutOfTheIndex_areNotPartialCoverage() = runTest {
        doc("Hecho.txt", DocumentStatus.DONE)
        doc("Con error.txt", DocumentStatus.ERROR)
        doc("Fuera.txt", DocumentStatus.OUT_OF_INDEX)
        doc("Pendiente excluido.txt", DocumentStatus.PENDING, included = false)

        assertThat(current()).isEqualTo(ChatAvailability.Ready(cpuMode = false, partialCoverage = false))
    }

    @Test
    fun gemmaPreparedOnCpu_isCpuMode() = runTest {
        doc("Hecho.txt", DocumentStatus.DONE)
        engine.backend.value = ComputeBackend.CPU
        assertThat(current()).isEqualTo(ChatAvailability.Ready(cpuMode = true, partialCoverage = false))
    }

    @Test
    fun nothingIsEmitted_beforeTheIndexingStateIsKnown() = runTest(UnconfinedTestDispatcher()) {
        doc("Hecho.txt", DocumentStatus.DONE)
        indexing.state.value = IndexingState.Loading
        val seen = mutableListOf<ChatAvailability>()
        val job = launch { observe().toList(seen) }
        runCurrent()
        assertThat(seen).isEmpty()

        indexing.state.value = IndexingState.Idle(emptyList())
        runCurrent()
        assertThat(seen).containsExactly(ChatAvailability.Ready(cpuMode = false, partialCoverage = false))
        job.cancel()
    }

    @Test
    fun theAvailabilityFollowsTheLibraryAndTheBatch() = runTest(UnconfinedTestDispatcher()) {
        val seen = collect()
        assertThat(seen.last()).isEqualTo(ChatAvailability.EmptyLibrary)

        val id = doc("Pendiente.txt", DocumentStatus.PENDING)
        indexing.state.value = running()
        assertThat(seen.last()).isEqualTo(ChatAvailability.WaitingForIndexing)

        index.updateStatus(id, DocumentStatus.DONE, null, null, null)
        indexing.state.value = IndexingState.Idle(emptyList())
        assertThat(seen.last()).isEqualTo(ChatAvailability.Ready(cpuMode = false, partialCoverage = false))
    }

    @Test
    fun aRetry_checksTheIndexAgain() = runTest(UnconfinedTestDispatcher()) {
        doc("Hecho.txt", DocumentStatus.DONE)
        index.setCompatibility(IndexCompatibility.Unreadable("test"))
        val retries = MutableStateFlow(0)
        val seen = mutableListOf<ChatAvailability>()
        val job = launch { observe(retries).toList(seen) }
        runCurrent()
        assertThat(seen.last()).isEqualTo(ChatAvailability.IndexUnavailable)
        val opens = index.calls.count { it == "open" }

        index.setCompatibility(IndexCompatibility.Compatible)
        retries.value++
        runCurrent()

        assertThat(index.calls.count { it == "open" }).isGreaterThan(opens)
        assertThat(seen.last()).isEqualTo(ChatAvailability.Ready(cpuMode = false, partialCoverage = false))
        job.cancel()
    }

    private fun TestScope.collect(): List<ChatAvailability> {
        val seen = mutableListOf<ChatAvailability>()
        backgroundScope.launch { observe().toList(seen) }
        runCurrent()
        return seen
    }

    // F17 T044 (research R8, analyze M5): within a scope.

    private val legal = com.lisdatasolutions.microrag.domain.rag.SearchScope.Only(setOf(1L), general = false, documentIds = emptySet())

    private suspend fun inScope(): ChatAvailability = observe(scope = legal).first()

    @Test
    fun aScopeWithNothingReadyIsNothingInScope_andWithItReady() = runTest {
        doc("Manual.txt", DocumentStatus.DONE)
        val folder = checkNotNull(index.createFolder("Legal", "legal"))
        val pending = doc("Contrato.txt", DocumentStatus.PENDING)
        index.file(pending, folder)
        assertThat(inScope()).isEqualTo(ChatAvailability.NothingInScope)
        // Without a scope, the same library is ready.
        assertThat(current()).isEqualTo(ChatAvailability.Ready(cpuMode = false, partialCoverage = true))

        val done = doc("Garantía.txt", DocumentStatus.DONE)
        index.file(done, folder)

        // The partial coverage is the scope's: its pending contract.
        assertThat(inScope()).isEqualTo(ChatAvailability.Ready(cpuMode = false, partialCoverage = true))
    }

    /** M5: the global states come first, whatever the scope. */
    @Test
    fun theGlobalStatesComeBeforeTheScope() = runTest {
        assertThat(inScope()).isEqualTo(ChatAvailability.EmptyLibrary)

        doc("Manual.txt", DocumentStatus.DONE)
        indexing.state.value = running()
        assertThat(inScope()).isEqualTo(ChatAvailability.WaitingForIndexing)

        indexing.state.value = IndexingState.Idle(emptyList())
        assertThat(inScope()).isEqualTo(ChatAvailability.NothingInScope)
    }
}
