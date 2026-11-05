package com.lisdatasolutions.microrag.data.chat

import android.content.ComponentCallbacks2
import com.google.common.truth.Truth.assertThat
import com.lisdatasolutions.microrag.domain.chat.ChatAvailability
import com.lisdatasolutions.microrag.domain.chat.ObserveChatAvailabilityUseCase
import com.lisdatasolutions.microrag.domain.chat.TurnState
import com.lisdatasolutions.microrag.domain.chat.TurnUpdate
import com.lisdatasolutions.microrag.domain.chat.WarmUpAnswerEngineUseCase
import com.lisdatasolutions.microrag.domain.indexing.IndexingState
import com.lisdatasolutions.microrag.domain.indexing.ObserveIndexingUseCase
import com.lisdatasolutions.microrag.domain.indexing.PauseReason
import com.lisdatasolutions.microrag.domain.indexing.RunProgress
import com.lisdatasolutions.microrag.domain.model.ComputeBackend
import com.lisdatasolutions.microrag.domain.model.ComputeBackend.GPU
import com.lisdatasolutions.microrag.domain.model.ModelCatalog
import com.lisdatasolutions.microrag.domain.model.ModelFile
import com.lisdatasolutions.microrag.domain.model.ModelId
import com.lisdatasolutions.microrag.domain.onboarding.ObserveLocalAiUseCase
import com.lisdatasolutions.microrag.domain.rag.DocumentStatus
import com.lisdatasolutions.microrag.domain.rag.NewDocument
import com.lisdatasolutions.microrag.domain.retrieval.CandidateScore
import com.lisdatasolutions.microrag.domain.retrieval.ContextBlock
import com.lisdatasolutions.microrag.domain.retrieval.RetrievalOutcome
import com.lisdatasolutions.microrag.domain.retrieval.RetrieveContextUseCase
import com.lisdatasolutions.microrag.rag.runtime.EngineCoordinator
import com.lisdatasolutions.microrag.rag.runtime.EngineFactory
import com.lisdatasolutions.microrag.rag.runtime.EngineMemoryCallbacks
import com.lisdatasolutions.microrag.rag.runtime.EngineKey
import com.lisdatasolutions.microrag.rag.runtime.GenerationConfig
import com.lisdatasolutions.microrag.rag.runtime.GenerationEvent
import com.lisdatasolutions.microrag.rag.runtime.GenerationStats
import com.lisdatasolutions.microrag.rag.runtime.ManagedEngine
import com.lisdatasolutions.microrag.rag.runtime.ModelMetadata
import com.lisdatasolutions.microrag.rag.runtime.TextEmbedder
import com.lisdatasolutions.microrag.rag.runtime.TextGenerator
import com.lisdatasolutions.microrag.testing.FakeContextRetriever
import com.lisdatasolutions.microrag.testing.FakeIndexingRepository
import com.lisdatasolutions.microrag.testing.FakeMultimodalStore
import com.lisdatasolutions.microrag.testing.FakeRagIndex
import com.lisdatasolutions.microrag.testing.InMemoryChatRepository
import com.lisdatasolutions.microrag.testing.InMemoryModelFileRepository
import com.lisdatasolutions.microrag.testing.RecordingLogger
import java.io.File
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Test

/**
 * F11 T052 (research R7, SC-011): the chat and an indexing batch never share the GPU. The real coordinator,
 * answer engine, session and availability, with fake engines; the batch makes the coordinator calls of
 * ChunkEmbeddingSession.use: release the generators, hold the embedder, release it at the end.
 * T053 (A11) uses the same pieces: with the app hidden, the answer is interrupted before Gemma is released.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatDuringIndexingTest {

    private val logger = RecordingLogger()
    private val coordinator = EngineCoordinator(logger)
    private val events = mutableListOf<String>()
    private val models = InMemoryModelFileRepository(verified = ModelCatalog.required.map { it.id }.toSet()).apply {
        preparedBackends.value = ModelCatalog.required.associate { it.id to GPU }
    }
    private val answerEngine = EngineAnswerEngine(coordinator, GemmaFactory(), models)
    private val chats = InMemoryChatRepository()
    private val indexing = FakeIndexingRepository()
    private val index = FakeRagIndex()
    private val retriever = FakeContextRetriever(
        RetrievalOutcome.Blocks(
            listOf(ContextBlock(1, "Manual.pdf", null, 1, 0, 0, listOf(101L), "El plazo es de 30 días.", 10)),
            listOf(CandidateScore(101L, 1, 0.1, null, null, null, true)),
        ),
    )
    private val multimodal = FakeMultimodalStore()
    private val availability = ObserveChatAvailabilityUseCase(chats, ObserveLocalAiUseCase(models), answerEngine, ObserveIndexingUseCase(indexing), index, multimodal)
    private val warmUp = WarmUpAnswerEngineUseCase(answerEngine, availability, multimodal, index)
    private val gemma = EngineKey(ModelId.GEMMA4_E2B, GPU)
    private val embedder = EngineKey(ModelId.EG2_270M, GPU)
    private var gemmaTokens = List(5) { "t$it " }
    private var tokenDelayMs = 10L

    /** Gemma streams [gemmaTokens], [tokenDelayMs] apart; its load and close are recorded. */
    private inner class GemmaFactory : EngineFactory {
        override fun generator(file: ModelFile, path: File, backend: ComputeBackend, config: GenerationConfig): TextGenerator {
            events += "gemma load"
            return object : TextGenerator {
                override val model = file.id
                override val backend = backend
                override suspend fun initialize() = Unit
                override fun generate(prompt: String): Flow<GenerationEvent> = flow {
                    gemmaTokens.forEach {
                        delay(tokenDelayMs)
                        emit(GenerationEvent.Token(it))
                    }
                    events += "gemma done"
                    emit(GenerationEvent.Finished(GenerationStats(null, null, 10, 50, null, null, null, 100.0)))
                }

                override fun cancel() = Unit
                override fun close() {
                    events += "gemma close"
                }
            }
        }

        override fun embedder(file: ModelFile, path: File, backend: ComputeBackend): TextEmbedder = error("unused")
        override fun metadata(path: File): ModelMetadata = error("unused")
        override fun clearCompiledCache() = Unit
        override var benchmarkStatsEnabled: Boolean = false
    }

    private fun TestScope.session() = DefaultChatSession(
        repository = chats, retrieve = RetrieveContextUseCase(retriever), engine = answerEngine,
        availability = { availability() }, scope = this, logger = logger, now = { testScheduler.currentTime },
    )

    private fun running(pause: PauseReason? = null) = IndexingState.Active(RunProgress(1, 10, null), null, 0, pause = pause, failed = emptyList())

    /** The batch's engine use, as ChunkEmbeddingSession.use does it; [work] runs with the embedder held. */
    private suspend fun batch(work: suspend () -> Unit) {
        coordinator.releaseGenerators()
        try {
            coordinator.withHeldEngine(embedder, {
                events += "embedder load"
                object : ManagedEngine {
                    override val backend = GPU
                    override fun close() {
                        events += "embedder close"
                    }
                }
            }) { work() }
        } finally {
            withContext(NonCancellable) { coordinator.release(embedder) }
        }
    }

    private suspend fun indexedDocument() {
        val id = index.addDocument(NewDocument(displayName = "Manual.pdf", contentHash = "manual", mimeType = "application/pdf"))
        index.updateStatus(id, DocumentStatus.DONE, null, null, null)
    }

    @Test
    fun withTheBatchRunning_askRetryAndWarmUpLoadNothing() = runTest {
        indexedDocument()
        indexing.state.value = running()
        val failed = chats.insertTurn(chats.createConversation(0), "¿Plazo?", partialCoverage = false, now = 0)
        chats.updateTurn(failed.id, TurnUpdate(state = TurnState.FAILED))
        val session = session()
        assertThat(availability().first()).isEqualTo(ChatAvailability.WaitingForIndexing)

        assertThat(session.ask(null, "¿Plazo?")).isNull()
        assertThat(session.retry(failed.id)).isFalse()
        warmUp()
        advanceUntilIdle()

        assertThat(events).isEmpty()
        assertThat(coordinator.loaded.value).isEmpty()
    }

    @Test
    fun anAnswerInProgress_endsBeforeTheBatchLoadsItsEngine_neverTwoOnTheGpu() = runTest {
        indexedDocument()
        val loadedSets = mutableListOf<Set<EngineKey>>()
        backgroundScope.launch { coordinator.loaded.toList(loadedSets) }
        val session = session()
        session.ask(null, "¿Plazo?")
        // Past the search, two tokens into the answer.
        advanceTimeBy(retriever.delayMs + 25)
        assertThat(coordinator.loaded.value).containsExactly(gemma)
        assertThat(events).containsExactly("gemma load")

        // The batch starts while Gemma answers.
        indexing.state.value = running()
        launch { batch { events += "embedding" } }
        advanceUntilIdle()

        assertThat(chats.turns.values.single().state).isEqualTo(TurnState.COMPLETED)
        assertThat(chats.turns.values.single().answer).isEqualTo("t0 t1 t2 t3 t4 ")
        assertThat(events).containsExactly("gemma load", "gemma done", "gemma close", "embedder load", "embedding", "embedder close").inOrder()
        assertThat(loadedSets.none { keys -> keys.count { it.backend == GPU } > 1 }).isTrue()
    }

    @Test
    fun whenTheBatchPauses_theChatIsReadyAgain_andGemmaLoads() = runTest {
        indexedDocument()
        indexing.state.value = running()
        val session = session()
        // The batch was working; a pause ends its work and releases the embedder.
        batch { events += "embedding" }
        indexing.state.value = running(pause = PauseReason.USER)
        assertThat(availability().first()).isInstanceOf(ChatAvailability.Ready::class.java)

        session.ask(null, "¿Plazo?")
        advanceUntilIdle()

        assertThat(chats.turns.values.single().state).isEqualTo(TurnState.COMPLETED)
        assertThat(events).containsExactly("embedder load", "embedding", "embedder close", "gemma load", "gemma done").inOrder()
        assertThat(coordinator.loaded.value).containsExactly(gemma)
    }

    // T053 (A11): the app hidden or low on memory.

    @Test
    fun theAppHidden_orLowMemory_interruptsTheAnswer_thenReleasesGemma() = runTest {
        indexedDocument()
        gemmaTokens = listOf("Primero [1]. ", "Segundo.")
        tokenDelayMs = 5_000
        listOf(ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN, null).forEach { level ->
            events.clear()
            val session = session()
            val callbacks = EngineMemoryCallbacks(coordinator, this, listOf(session))
            session.ask(null, "¿Plazo?")
            // The first token is in; the second is 5 s away.
            advanceTimeBy(retriever.delayMs + tokenDelayMs + 1)

            if (level != null) {
                callbacks.onTrimMemory(level)
            } else {
                @Suppress("DEPRECATION")
                callbacks.onLowMemory()
            }
            advanceUntilIdle()

            with(chats.turns.values.last()) {
                assertThat(state).isEqualTo(TurnState.INTERRUPTED)
                assertThat(answer).isEqualTo("Primero [1]. ")
                assertThat(sources.map { it.promptNumber }).containsExactly(1)
            }
            assertThat(events).containsExactly("gemma load", "gemma close").inOrder()
            assertThat(coordinator.loaded.value).isEmpty()
        }
    }
}
