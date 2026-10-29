package com.lisdatasolutions.microrag.data.retrieval

import android.os.Build
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.lisdatasolutions.microrag.core.dispatchers.AppDispatchers
import com.lisdatasolutions.microrag.debug.feasibility.Percentiles
import com.lisdatasolutions.microrag.debug.feasibility.RequiresModels
import com.lisdatasolutions.microrag.domain.rag.DocumentStatus
import com.lisdatasolutions.microrag.domain.rag.NewDocument
import com.lisdatasolutions.microrag.domain.retrieval.RetrievalOutcome
import com.lisdatasolutions.microrag.domain.retrieval.RetrievalRequest
import com.lisdatasolutions.microrag.domain.retrieval.RetrievalResult
import com.lisdatasolutions.microrag.rag.retrieval.FollowUpRule
import com.lisdatasolutions.microrag.rag.retrieval.RetrievalGoldenFiles
import com.lisdatasolutions.microrag.rag.runtime.EngineCoordinator
import com.lisdatasolutions.microrag.rag.runtime.litert.LiteRtEngineFactory
import com.lisdatasolutions.microrag.testing.InstalledEmbeddingModel
import com.lisdatasolutions.microrag.testing.TempRagIndex
import com.lisdatasolutions.microrag.testing.TempRagIndex.Companion.chunk
import com.lisdatasolutions.microrag.testing.TempRagIndex.Companion.unitVector
import java.util.Locale
import kotlin.random.Random
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * F10 T040 (research R10, FR-025, SC-003, SC-004): latency of a whole retrieval with the real CPU query
 * engine over 10,000 synthetic chunks, then with 4,300 more that are not searchable (D-042). The questions
 * are the 50 of the parity sample. Logged with `retrieval-benchmark`; the embedding threshold applies on the
 * reference phone only (D-022), and the total is logged only (D-052).
 */
@RequiresModels
@RunWith(AndroidJUnit4::class)
class RetrievalBenchmarkTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val temp = TempRagIndex()
    private val models = InstalledEmbeddingModel(context)
    private val questions = RetrievalGoldenFiles.parity(
        instrumentation.context.assets.open(RetrievalGoldenFiles.PARITY).bufferedReader().use { it.readText() },
    )

    @After
    fun tearDown() = temp.close()

    @Test
    fun hybridRetrievalLatency() = runBlocking<Unit> {
        assumeTrue("the embedding model is not installed", models.isInstalled)
        val random = Random(SEED)
        // The questions' own words are in the vocabulary, so the lexical search finds and ranks chunks.
        val vocabulary = (WORDS + questions.flatMap { q -> q.question.lowercase().split(Regex("[^\\p{L}\\p{N}]+")) })
            .filter { it.length >= 4 }.distinct()
        repeat(DOCUMENTS) { d ->
            val doc = temp.index.addDocument(NewDocument(displayName = "Documento $d.txt", contentHash = "retrieval-bench-$d"))
            (0 until CHUNKS_PER_DOCUMENT).chunked(BATCH).forEach { ordinals ->
                temp.index.insertChunks(doc, ordinals.map { o -> chunk(o, content = sentence(random, vocabulary), vector = unitVector(d * 1_000 + o)) })
            }
            temp.index.updateStatus(doc, DocumentStatus.DONE, chunkCount = CHUNKS_PER_DOCUMENT)
        }

        val dispatchers = AppDispatchers.create()
        val coordinator = EngineCoordinator(temp.logger)
        try {
            val embedder = EngineQueryEmbedder(coordinator, LiteRtEngineFactory(context, dispatchers), models)
            val retriever = HybridRetriever(temp.index, embedder, temp.logger)

            val cold = retriever.retrieve(request(0))
            assertThat(cold.timings.coldEngine).isTrue()
            val warm = rounds(retriever)
            log("hidden=0", cold, warm)
            // The same embeddings with nothing else running: how much the overlapped lexical search costs the embedding.
            val alone = (0 until ROUNDS * questions.size).map { i ->
                val started = System.nanoTime()
                val r = request(i)
                embedder.embed(r.question, r.previousQuestion?.takeIf { FollowUpRule.searchText(r.question, it) != r.question })
                (System.nanoTime() - started) / 1e6
            }
            Log.i(TAG, "retrieval-benchmark embedding alone n=${alone.size} p50=%.1f p95=%.1f".format(Locale.ROOT, Percentiles.of(alone, 50), Percentiles.of(alone, 95)))

            // F07 research R7: a 2,000-page PDF half indexed leaves 4,300 chunks stored and not searchable.
            val halfIndexed = temp.index.addDocument(NewDocument(displayName = "A medias.pdf", contentHash = "retrieval-bench-hidden"))
            temp.index.updateStatus(halfIndexed, DocumentStatus.EMBEDDING, chunkCount = HIDDEN_CHUNKS)
            (0 until HIDDEN_CHUNKS).chunked(BATCH).forEach { ordinals ->
                temp.index.insertChunks(halfIndexed, ordinals.map { o -> chunk(o, content = sentence(random, vocabulary), vector = unitVector(HIDDEN_SEED + o)) })
            }
            val hidden = rounds(retriever)
            log("hidden=$HIDDEN_CHUNKS", null, hidden)

            (warm + hidden).forEach { assertThat(it.outcome).isInstanceOf(RetrievalOutcome.Blocks::class.java) }
            assertWithMessage("the engine stays loaded between retrievals").that(warm.none { it.timings.coldEngine }).isTrue()
            // D-052: the total (202 ms against 200 on the A063) is logged, not asserted; F14 takes it up with INT8.
            if (Build.MODEL == REFERENCE_MODEL) {
                assertWithMessage("embedding p95 (SC-003)").that(p95(warm) { it.timings.embeddingMs }).isLessThan(SC_003_EMBEDDING_MS)
            }
        } finally {
            coordinator.releaseAll()
            dispatchers.close()
        }
    }

    private fun request(index: Int) = questions[index % questions.size].let { RetrievalRequest(it.question, it.previous) }

    private suspend fun rounds(retriever: HybridRetriever): List<RetrievalResult> =
        (0 until ROUNDS * questions.size).map { retriever.retrieve(request(it)) }

    private fun log(label: String, cold: RetrievalResult?, results: List<RetrievalResult>) {
        val steps = listOf<Pair<String, (RetrievalResult) -> Double>>(
            "embed" to { it.timings.embeddingMs }, "vector" to { it.timings.vectorMs }, "text" to { it.timings.textMs },
            "select" to { it.timings.selectionMs }, "total" to { it.timings.totalMs },
        ).joinToString(" ") { (name, value) ->
            "%s_p50=%.1f %s_p95=%.1f".format(Locale.ROOT, name, Percentiles.of(results.map(value), 50), name, Percentiles.of(results.map(value), 95))
        }
        val first = cold?.let { " cold_total_ms=%.1f cold_embed_ms=%.1f".format(Locale.ROOT, it.timings.totalMs, it.timings.embeddingMs) }.orEmpty()
        Log.i(TAG, "retrieval-benchmark model=${Build.MODEL} sdk=${Build.VERSION.SDK_INT} chunks=${DOCUMENTS * CHUNKS_PER_DOCUMENT} $label n=${results.size}$first $steps")
    }

    private fun p95(results: List<RetrievalResult>, value: (RetrievalResult) -> Double) = Percentiles.of(results.map(value), 95)

    private fun sentence(random: Random, vocabulary: List<String>) = List(WORDS_PER_CHUNK) { vocabulary[random.nextInt(vocabulary.size)] }.joinToString(" ")

    private companion object {
        const val TAG = "MicroRAG"
        const val REFERENCE_MODEL = "A063"
        const val SEED = 2026
        const val DOCUMENTS = 100
        const val CHUNKS_PER_DOCUMENT = 100
        const val BATCH = 64
        const val WORDS_PER_CHUNK = 120
        const val ROUNDS = 2
        const val HIDDEN_CHUNKS = 4_300
        const val HIDDEN_SEED = 500_000
        const val SC_003_EMBEDDING_MS = 150.0

        val WORDS = """
            motor bomba válvula filtro presión caudal temperatura sensor tubería junta aceite revisión
            mantenimiento avería técnico manual instrucción seguridad operario guantes gafas protección
            cliente contrato factura pago plazo reembolso garantía devolución pedido envío proveedor
            empleado vacaciones turno jornada descanso nómina convenio horario permiso
            servidor red firmware versión actualización configuración usuario contraseña cuenta acceso
        """.trimIndent().split(Regex("\\s+")).filter { it.isNotBlank() }
    }
}
