package com.lisdatasolutions.microrag.data.indexing

import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.lisdatasolutions.microrag.core.dispatchers.AppDispatchers
import com.lisdatasolutions.microrag.data.ingest.DocumentFileStore
import com.lisdatasolutions.microrag.data.ingest.DocumentImporter
import com.lisdatasolutions.microrag.data.ingest.ImportResult
import com.lisdatasolutions.microrag.data.ingest.IngestRunner
import com.lisdatasolutions.microrag.data.ingest.PlatformPdfOpener
import com.lisdatasolutions.microrag.data.ingest.SafDocumentSource
import com.lisdatasolutions.microrag.debug.feasibility.RequiresModels
import com.lisdatasolutions.microrag.domain.ingest.IngestProgress
import com.lisdatasolutions.microrag.domain.model.ComputeBackend
import com.lisdatasolutions.microrag.domain.model.ModelCatalog
import com.lisdatasolutions.microrag.domain.model.ModelFamily
import com.lisdatasolutions.microrag.domain.model.ModelId
import com.lisdatasolutions.microrag.domain.rag.DocumentStatus
import com.lisdatasolutions.microrag.rag.ingest.DocumentExtractor
import com.lisdatasolutions.microrag.rag.runtime.EngineCoordinator
import com.lisdatasolutions.microrag.rag.runtime.EngineKey
import com.lisdatasolutions.microrag.rag.runtime.GenerationConfig
import com.lisdatasolutions.microrag.rag.runtime.initializeOrClose
import com.lisdatasolutions.microrag.rag.runtime.litert.LiteRtEngineFactory
import com.lisdatasolutions.microrag.testing.CapturingLogger
import com.lisdatasolutions.microrag.testing.TempRagIndex
import java.io.File
import java.util.UUID
import java.util.concurrent.CopyOnWriteArraySet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * F07 T063 (research R13, FR-040, SC-001, SC-007): text_200_pages.pdf indexed by the real runner,
 * ChunkEmbeddingSession and LiteRT-LM on a temporary index, on the GPU and on the CPU. When Gemma is
 * installed it is loaded first, as after a chat, and the engines are sampled at every progress: the
 * LLM is never loaded during the run. Logged with `indexing-benchmark`; SC-001 (≤ 5 min on the GPU)
 * applies on the reference phone only (D-022).
 */
@RequiresModels
@RunWith(AndroidJUnit4::class)
class IndexingBenchmarkTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val root = File(context.cacheDir, "indexing-bench-${UUID.randomUUID()}").apply { mkdirs() }
    private val temp = TempRagIndex()
    private val logger = CapturingLogger()
    private val pdf = PlatformPdfOpener()
    private val embedding = ModelCatalog.byId(ModelId.EG2_270M)
    private val llm = ModelCatalog.byId(ModelId.GEMMA4_E2B)

    @After
    fun tearDown() {
        temp.close()
        root.deleteRecursively()
    }

    @Test
    fun twoHundredPagesOnTheGpu() = index(ComputeBackend.GPU)

    @Test
    fun twoHundredPagesOnTheCpu() = index(ComputeBackend.CPU)

    private fun index(backend: ComputeBackend) = runBlocking<Unit> {
        val model = File(context.noBackupFilesDir, "models/${embedding.fileName}")
        assumeTrue("${embedding.fileName} is not installed", model.isFile)
        assumeTrue("the emulator's GPU init blocks in native code", backend == ComputeBackend.CPU || !EmbeddingProbes.isEmulator)
        assumeTrue(pdf.isAvailable)
        val id = importTwoHundredPages()

        val dispatchers = AppDispatchers.create()
        val coordinator = EngineCoordinator(logger)
        try {
            val factory = LiteRtEngineFactory(context, dispatchers)
            val llmLoaded = preloadTheLlm(coordinator, factory)
            // The backend this device would use, stamped as the GPU check does (research R4).
            val runs = InMemoryIngestRunStore().apply { setGpuCheck("${embedding.sha256}|$APP_VERSION|${backend.name.lowercase()}") }
            val session = ChunkEmbeddingSession(coordinator, factory, { model }, runs, APP_VERSION, logger)
            val runner = IngestRunner(temp.index, DocumentExtractor(pdf), session, runs, FakeThermalStatusSource(), { Long.MAX_VALUE }, Dispatchers.IO, logger)

            // SC-007: every engine set seen while the run goes on.
            val seen = CopyOnWriteArraySet<Set<EngineKey>>()
            val watcher = launch(Dispatchers.Default) { coordinator.loaded.collect { seen += it } }
            val backends = CopyOnWriteArraySet<ComputeBackend>()
            var embeddingStarted = 0L
            val started = SystemClock.elapsedRealtime()
            runner.process(listOf(id)) { progress ->
                seen += coordinator.loaded.value
                progress.backend?.let(backends::add)
                if (embeddingStarted == 0L && progress.stage == IngestProgress.Stage.EMBEDDING) embeddingStarted = SystemClock.elapsedRealtime()
            }
            val ended = SystemClock.elapsedRealtime()
            watcher.cancel()

            val document = temp.index.document(id)!!
            val chunks = document.chunkCount ?: 0
            val embeddingSeconds = (ended - embeddingStarted) / 1000.0
            val llmSeen = seen.drop(if (llmLoaded) 1 else 0).any { keys -> keys.any { ModelCatalog.byId(it.model).family == ModelFamily.LLM } }
            Log.i(
                TAG,
                "indexing-benchmark model=${Build.MODEL} sdk=${Build.VERSION.SDK_INT} backend=$backend used=${backends.joinToString("+")} " +
                    "pages=${document.pageCount} chunks=$chunks total_ms=${ended - started} embedding_ms=${ended - embeddingStarted} " +
                    "rate=${"%.2f".format(chunks / embeddingSeconds)} llm_preloaded=$llmLoaded llm_during_run=$llmSeen",
            )
            assertThat(document.status).isEqualTo(DocumentStatus.DONE)
            assertThat(temp.index.storedChunkCounts(listOf(id))[id]).isEqualTo(chunks)
            assertWithMessage("backend reported by the run").that(backends).containsExactly(backend)
            assertWithMessage("an LLM engine was loaded during the run (SC-007)").that(llmSeen).isFalse()
            if (Build.MODEL == REFERENCE_MODEL && backend == ComputeBackend.GPU) assertThat(ended - started).isAtMost(SC_001_MS)
        } finally {
            coordinator.releaseAll()
            dispatchers.close()
        }
    }

    private suspend fun importTwoHundredPages(): Long {
        val file = File(root, "text_200_pages.pdf").apply {
            instrumentation.context.assets.open("pdf/text_200_pages.pdf").use { input -> outputStream().use(input::copyTo) }
        }
        val source = SafDocumentSource(context.contentResolver)
        val importer = DocumentImporter(source, DocumentFileStore(File(root, "documents")), pdf, temp.index, { Long.MAX_VALUE / 2 }, logger)
        val uri = Uri.fromFile(file).toString()
        return (importer.import(uri, source.describe(uri)) as ImportResult.Imported).documentId
    }

    /** Gemma on the GPU, left loaded as after a chat; false when it is not installed. */
    private suspend fun preloadTheLlm(coordinator: EngineCoordinator, factory: LiteRtEngineFactory): Boolean {
        val path = File(context.noBackupFilesDir, "models/${llm.fileName}")
        if (!path.isFile || EmbeddingProbes.isEmulator) return false
        val key = EngineKey(llm.id, ComputeBackend.GPU)
        coordinator.withEngine(key, {
            factory.generator(llm, path, ComputeBackend.GPU, GenerationConfig(maxNumTokens = 4096, maxOutputTokens = 8, greedy = true))
                .initializeOrClose { it.initialize() }
        }) { }
        assertThat(coordinator.loaded.value).contains(key)
        return true
    }

    private companion object {
        const val TAG = "MicroRAG"
        const val REFERENCE_MODEL = "A063"
        const val APP_VERSION = 1L
        const val SC_001_MS = 5 * 60_000L
    }
}
