package com.lisdatasolutions.microrag.data.retrieval

import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.lisdatasolutions.microrag.core.dispatchers.AppDispatchers
import com.lisdatasolutions.microrag.data.indexing.ChunkEmbeddingSession
import com.lisdatasolutions.microrag.data.indexing.EmbeddingProbes
import com.lisdatasolutions.microrag.data.indexing.FakeThermalStatusSource
import com.lisdatasolutions.microrag.data.indexing.InMemoryIngestRunStore
import com.lisdatasolutions.microrag.data.ingest.DocumentFileStore
import com.lisdatasolutions.microrag.data.ingest.DocumentImporter
import com.lisdatasolutions.microrag.data.ingest.ImportResult
import com.lisdatasolutions.microrag.data.ingest.IngestRunner
import com.lisdatasolutions.microrag.data.ingest.PlatformPdfOpener
import com.lisdatasolutions.microrag.data.ingest.SafDocumentSource
import com.lisdatasolutions.microrag.debug.feasibility.Percentiles
import com.lisdatasolutions.microrag.debug.feasibility.RequiresModels
import com.lisdatasolutions.microrag.domain.ingest.IngestProgress
import com.lisdatasolutions.microrag.domain.model.ComputeBackend
import com.lisdatasolutions.microrag.domain.model.ModelCatalog
import com.lisdatasolutions.microrag.domain.model.ModelId
import com.lisdatasolutions.microrag.domain.rag.DocumentStatus
import com.lisdatasolutions.microrag.domain.rag.NewDocument
import com.lisdatasolutions.microrag.domain.retrieval.RetrievalOutcome
import com.lisdatasolutions.microrag.domain.retrieval.RetrievalRequest
import com.lisdatasolutions.microrag.rag.ingest.DocumentExtractor
import com.lisdatasolutions.microrag.rag.retrieval.RetrievalGoldenFiles
import com.lisdatasolutions.microrag.rag.runtime.EngineCoordinator
import com.lisdatasolutions.microrag.rag.runtime.litert.LiteRtEngineFactory
import com.lisdatasolutions.microrag.testing.InstalledEmbeddingModel
import com.lisdatasolutions.microrag.testing.TempRagIndex
import com.lisdatasolutions.microrag.testing.TempRagIndex.Companion.chunk
import com.lisdatasolutions.microrag.testing.TempRagIndex.Companion.unitVector
import java.io.File
import java.util.Locale
import java.util.UUID
import java.util.concurrent.CopyOnWriteArraySet
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * F10 T041 (research R10, FR-020, SC-007): while the real runner indexes text_200_pages.pdf, a retrieval
 * every 500 ms over 1,000 published chunks. Each one must end before the run does: none waits for the run.
 * Logged with `retrieval-during-indexing`; the 1 s p95 applies to a GPU run on the reference phone only.
 */
@RequiresModels
@RunWith(AndroidJUnit4::class)
class RetrievalDuringIndexingTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val root = File(context.cacheDir, "retrieval-indexing-${UUID.randomUUID()}").apply { mkdirs() }
    private val temp = TempRagIndex()
    private val pdf = PlatformPdfOpener()
    private val models = InstalledEmbeddingModel(context)
    private val embedding = ModelCatalog.byId(ModelId.EG2_270M)
    private val questions = RetrievalGoldenFiles.parity(
        instrumentation.context.assets.open(RetrievalGoldenFiles.PARITY).bufferedReader().use { it.readText() },
    )

    @After
    fun tearDown() {
        temp.close()
        root.deleteRecursively()
    }

    @Test
    fun duringAGpuRun() {
        assumeTrue("the emulator's GPU init blocks in native code", !EmbeddingProbes.isEmulator)
        measure(ComputeBackend.GPU)
    }

    /** The CPU run shares its engine with the queries (research R5): informative, no threshold. */
    @Test
    fun duringACpuRun() = measure(ComputeBackend.CPU)

    private fun measure(backend: ComputeBackend) = runBlocking<Unit> {
        assumeTrue("the embedding model is not installed", models.isInstalled)
        assumeTrue(pdf.isAvailable)
        publishSyntheticChunks()
        val id = importTwoHundredPages()

        val dispatchers = AppDispatchers.create()
        val coordinator = EngineCoordinator(temp.logger)
        try {
            val factory = LiteRtEngineFactory(context, dispatchers)
            val runs = InMemoryIngestRunStore().apply { setGpuCheck("${embedding.sha256}|$APP_VERSION|${backend.name.lowercase()}") }
            // A CPU run as on a phone that failed the GPU check, or on the emulator.
            val session = ChunkEmbeddingSession(coordinator, factory, { models.file }, runs, APP_VERSION, temp.logger, isEmulator = backend == ComputeBackend.CPU)
            val runner = IngestRunner(temp.index, DocumentExtractor(pdf), session, runs, FakeThermalStatusSource(), { Long.MAX_VALUE }, Dispatchers.IO, temp.logger)
            val retriever = HybridRetriever(temp.index, EngineQueryEmbedder(coordinator, factory, models), temp.logger)

            val embeddingStarted = CompletableDeferred<Unit>()
            val backends = CopyOnWriteArraySet<ComputeBackend>()
            var runEnded = 0L
            val run = launch(Dispatchers.Default) {
                runner.process(listOf(id)) { progress ->
                    progress.backend?.let(backends::add)
                    if (progress.stage == IngestProgress.Stage.EMBEDDING) embeddingStarted.complete(Unit)
                }
            }
            run.invokeOnCompletion { runEnded = SystemClock.elapsedRealtime() }
            assertThat(withTimeoutOrNull(START_TIMEOUT_MS) { embeddingStarted.await() }).isNotNull()

            val totals = mutableListOf<Double>()
            val endedAt = mutableListOf<Long>()
            var question = 0
            while (run.isActive && totals.size < MAX_RETRIEVALS) {
                val result = retriever.retrieve(questions[question++ % questions.size].let { RetrievalRequest(it.question, it.previous) })
                assertThat(result.outcome).isInstanceOf(RetrievalOutcome.Blocks::class.java)
                totals += result.timings.totalMs
                endedAt += SystemClock.elapsedRealtime()
                delay(INTERVAL_MS)
            }
            val stillRunning = run.isActive
            run.cancelAndJoin()
            Log.i(
                TAG,
                "retrieval-during-indexing model=${Build.MODEL} sdk=${Build.VERSION.SDK_INT} backend=$backend used=${backends.joinToString("+")} " +
                    "retrievals=${totals.size} run_cut=$stillRunning p50=%.1f p95=%.1f max=%.1f".format(
                        Locale.ROOT, Percentiles.of(totals, 50), Percentiles.of(totals, 95), totals.max(),
                    ),
            )
            assertWithMessage("backend reported by the run").that(backends).containsExactly(backend)
            assertWithMessage("every retrieval ended before the run").that(endedAt.all { it <= runEnded }).isTrue()
            assertThat(totals.size).isAtLeast(MIN_RETRIEVALS)
            if (Build.MODEL == REFERENCE_MODEL && backend == ComputeBackend.GPU) {
                assertWithMessage("p95 during a GPU run (SC-007)").that(Percentiles.of(totals, 95)).isAtMost(SC_007_MS)
            }
        } finally {
            coordinator.releaseAll()
            dispatchers.close()
        }
    }

    private suspend fun publishSyntheticChunks() {
        repeat(SYNTHETIC_DOCUMENTS) { d ->
            val doc = temp.index.addDocument(NewDocument(displayName = "Documento $d.txt", contentHash = "during-indexing-$d"))
            val words = questions.flatMap { it.question.split(' ') }
            temp.index.insertChunks(doc, (0 until CHUNKS_PER_DOCUMENT).map { o ->
                chunk(o, content = List(60) { words[(d * 31 + o * 7 + it) % words.size] }.joinToString(" "), vector = unitVector(d * 1_000 + o))
            })
            temp.index.updateStatus(doc, DocumentStatus.DONE, chunkCount = CHUNKS_PER_DOCUMENT)
        }
    }

    private suspend fun importTwoHundredPages(): Long {
        val file = File(root, "text_200_pages.pdf").apply {
            instrumentation.context.assets.open("pdf/text_200_pages.pdf").use { input -> outputStream().use(input::copyTo) }
        }
        val source = SafDocumentSource(context.contentResolver)
        val importer = DocumentImporter(source, DocumentFileStore(File(root, "documents")), pdf, temp.index, { Long.MAX_VALUE / 2 }, temp.logger)
        val uri = Uri.fromFile(file).toString()
        return (importer.import(uri, source.describe(uri)) as ImportResult.Imported).documentId
    }

    private companion object {
        const val TAG = "MicroRAG"
        const val REFERENCE_MODEL = "A063"
        const val APP_VERSION = 1L
        const val SYNTHETIC_DOCUMENTS = 10
        const val CHUNKS_PER_DOCUMENT = 100
        const val INTERVAL_MS = 500L
        const val MAX_RETRIEVALS = 40
        const val MIN_RETRIEVALS = 5
        const val START_TIMEOUT_MS = 120_000L
        const val SC_007_MS = 1_000.0
    }
}
