package com.lisdatasolutions.microrag.data.retrieval

import com.google.common.truth.Truth.assertThat
import com.lisdatasolutions.microrag.data.indexing.FakeTextEmbedder
import com.lisdatasolutions.microrag.domain.model.ComputeBackend
import com.lisdatasolutions.microrag.domain.model.ComputeBackend.CPU
import com.lisdatasolutions.microrag.domain.model.ModelFile
import com.lisdatasolutions.microrag.domain.model.ModelId
import com.lisdatasolutions.microrag.domain.rag.ChunkInput
import com.lisdatasolutions.microrag.domain.rag.DocumentStatus
import com.lisdatasolutions.microrag.domain.rag.NewDocument
import com.lisdatasolutions.microrag.domain.rag.ScoredChunk
import com.lisdatasolutions.microrag.domain.retrieval.RetrievalOutcome
import com.lisdatasolutions.microrag.domain.retrieval.RetrievalRequest
import com.lisdatasolutions.microrag.rag.runtime.DocumentEmbedding
import com.lisdatasolutions.microrag.rag.runtime.EngineCoordinator
import com.lisdatasolutions.microrag.rag.runtime.EngineFactory
import com.lisdatasolutions.microrag.rag.runtime.EngineKey
import com.lisdatasolutions.microrag.rag.runtime.GenerationConfig
import com.lisdatasolutions.microrag.rag.runtime.ModelMetadata
import com.lisdatasolutions.microrag.rag.runtime.QueryEmbedding
import com.lisdatasolutions.microrag.rag.runtime.TextEmbedder
import com.lisdatasolutions.microrag.rag.runtime.TextGenerator
import com.lisdatasolutions.microrag.testing.FakeRagIndex
import com.lisdatasolutions.microrag.testing.InMemoryModelFileRepository
import com.lisdatasolutions.microrag.testing.RecordingLogger
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Test

/**
 * F10 T032 (FR-020, rule Q6): during an indexing run on the CPU, a retrieval shares the engine the ingest
 * holds and waits, at most, for the chunk being embedded, never for the end of the run.
 */
class RetrievalDuringIngestTest {

    /** LiteRtTextEmbedder runs every call on the single embeddings thread; so does this engine. */
    private val embeddingsThread = Executors.newSingleThreadExecutor { Thread(it, "test-embeddings") }.asCoroutineDispatcher()
    private val engine = FakeTextEmbedder()
    private val serialEngine = object : TextEmbedder by engine {
        override suspend fun embedDocumentFitting(content: String, header: String): DocumentEmbedding =
            withContext(embeddingsThread) { engine.embedDocumentFitting(content, header) }

        override suspend fun embedQueryFitting(question: String, previous: String?): QueryEmbedding =
            withContext(embeddingsThread) { engine.embedQueryFitting(question, previous) }
    }

    private val factory = object : EngineFactory {
        val created = mutableListOf<ComputeBackend>()

        override fun embedder(file: ModelFile, path: File, backend: ComputeBackend): TextEmbedder = serialEngine.also { created += backend }

        override fun generator(file: ModelFile, path: File, backend: ComputeBackend, config: GenerationConfig): TextGenerator =
            error("unused")

        override fun metadata(path: File): ModelMetadata = error("unused")

        override fun clearCompiledCache() = Unit

        override var benchmarkStatsEnabled: Boolean = false
    }

    private val coordinator = EngineCoordinator(RecordingLogger())
    private val models = InMemoryModelFileRepository(verified = setOf(ModelId.EG2_270M)).apply {
        preparedBackends.value = mapOf(ModelId.EG2_270M to CPU)
    }
    private val index = FakeRagIndex()
    private val retriever = HybridRetriever(index, EngineQueryEmbedder(coordinator, factory, models), RecordingLogger())
    private val cpuKey = EngineKey(ModelId.EG2_270M, CPU)

    @After
    fun tearDown() = embeddingsThread.close()

    @Test
    fun aRetrievalDuringACpuRun_waitsForTheChunkInProgress_notForTheRun() = runBlocking<Unit> {
        val document = index.addDocument(NewDocument(displayName = "Manual.txt", contentHash = "manual"))
        index.insertChunks(document, (0 until 5).map { ChunkInput(it, null, null, "Fragmento $it.", 100, FloatArray(0)) })
        index.updateStatus(document, DocumentStatus.DONE)
        val ids = (0 until 5).map { index.chunkId(document, it) }
        index.vectorResults = { ids.map { ScoredChunk(it, 0.2) } }
        index.textResults = { ids }

        val chunkStarted = CountDownLatch(1)
        val chunkDone = CountDownLatch(1)
        // The first embedding is the ingest's chunk: it stays in progress until chunkDone.
        val blockNext = AtomicBoolean(true)
        engine.beforeEmbedding = {
            if (blockNext.compareAndSet(true, false)) {
                chunkStarted.countDown()
                chunkDone.await(5, TimeUnit.SECONDS)
            }
        }
        val runEnds = CompletableDeferred<Unit>()
        val run = launch(Dispatchers.Default) {
            coordinator.withHeldEngine(cpuKey, { serialEngine.also { factory.created += CPU } }) { held ->
                held.embedDocumentFitting("Fragmento en curso.", "")
                runEnds.await()
            }
        }
        assertThat(chunkStarted.await(5, TimeUnit.SECONDS)).isTrue()

        val retrieval = async(Dispatchers.Default) { retriever.retrieve(RetrievalRequest(QUESTION)) }
        delay(200)
        assertThat(retrieval.isCompleted).isFalse()

        chunkDone.countDown()
        val result = withTimeout(5_000) { retrieval.await() }

        assertThat(result.outcome).isInstanceOf(RetrievalOutcome.Blocks::class.java)
        assertThat(result.timings.coldEngine).isFalse()
        assertThat(run.isActive).isTrue()
        assertThat(factory.created).containsExactly(CPU)
        assertThat(coordinator.loaded.value).containsExactly(cpuKey)

        runEnds.complete(Unit)
        run.join()
    }

    private companion object {
        const val QUESTION = "¿Cuál es el plazo para presentar la declaración trimestral del impuesto sobre el valor añadido?"
    }
}
