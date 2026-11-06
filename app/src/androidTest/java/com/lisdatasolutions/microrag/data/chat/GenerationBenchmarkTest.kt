package com.lisdatasolutions.microrag.data.chat

import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lisdatasolutions.microrag.core.dispatchers.AppDispatchers
import com.lisdatasolutions.microrag.data.ingest.PlatformPdfOpener
import com.lisdatasolutions.microrag.data.retrieval.EngineQueryEmbedder
import com.lisdatasolutions.microrag.data.retrieval.HybridRetriever
import com.lisdatasolutions.microrag.debug.feasibility.RequiresModels
import com.lisdatasolutions.microrag.domain.model.ComputeBackend
import com.lisdatasolutions.microrag.domain.model.ModelCatalog
import com.lisdatasolutions.microrag.domain.model.ModelId
import com.lisdatasolutions.microrag.domain.rag.RagParameters
import com.lisdatasolutions.microrag.domain.retrieval.RetrievalOutcome
import com.lisdatasolutions.microrag.domain.retrieval.RetrievalRequest
import com.lisdatasolutions.microrag.rag.answer.PromptBuilder
import com.lisdatasolutions.microrag.rag.retrieval.ContextSelector
import com.lisdatasolutions.microrag.rag.retrieval.RetrievalGoldenFiles
import com.lisdatasolutions.microrag.rag.runtime.ActivationPrecision
import com.lisdatasolutions.microrag.rag.runtime.EngineCoordinator
import com.lisdatasolutions.microrag.rag.runtime.GenerationConfig
import com.lisdatasolutions.microrag.rag.runtime.GenerationEvent
import com.lisdatasolutions.microrag.rag.runtime.GenerationPrompt
import com.lisdatasolutions.microrag.rag.runtime.TextGenerator
import com.lisdatasolutions.microrag.rag.runtime.litert.LiteRtEngineFactory
import com.lisdatasolutions.microrag.testing.CorpusIndex
import com.lisdatasolutions.microrag.testing.InstalledModels
import com.lisdatasolutions.microrag.testing.TempRagIndex
import com.lisdatasolutions.microrag.testing.bench.BenchReport
import com.lisdatasolutions.microrag.testing.bench.BenchReport.Sample
import com.lisdatasolutions.microrag.testing.bench.DevFiles
import com.lisdatasolutions.microrag.testing.bench.DeviceConditions
import java.io.File
import java.util.UUID
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonPrimitive
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * F14 T023 and T030 (research R5–R8 and R11, contract B2, US3, US4, US7, FR-014, FR-017, FR-020, FR-028): Gemma
 * with the app's prompts of 4, 6 and 8 real blocks of the evaluation corpus, in a temporary index; nothing of the
 * user is read. Per answer the engine's first token (wall), the prefill and decode speeds and the prompt's real
 * tokens (`BenchmarkInfo`, on only in this test) against the app's estimate; the RSS peak per variant. Answers stop
 * at [OUTPUT_TOKENS]: enough to time the decoding. With `f14.alternate`, each question runs the base and the
 * variant in turn, in the same thermal conditions, as the levers are decided (D-090).
 *
 * Arguments: `f14.variant` (base, fp32, e4b, …), `f14.blocks` (4,6,8 for base; 4 otherwise), `f14.questions` (20)
 * and `f14.alternate` (true for the levers).
 */
@RequiresModels
@RunWith(AndroidJUnit4::class)
class GenerationBenchmarkTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val arguments = InstrumentationRegistry.getArguments()
    private val variant = arguments.getString("f14.variant") ?: "base"
    private val blockCounts = arguments.getString("f14.blocks")?.split(",")?.map { it.toInt() } ?: if (variant == "base") listOf(4, 6, 8) else listOf(4)
    private val questionCount = arguments.getString("f14.questions")?.toInt() ?: if (Build.MODEL == REFERENCE_PHONE) 20 else 2
    private val alternate = arguments.getString("f14.alternate") == "true"
    private val root = File(context.cacheDir, "generation-benchmark-${UUID.randomUUID()}").apply { mkdirs() }
    private val temp = TempRagIndex()
    private val pdf = PlatformPdfOpener()
    private val models = InstalledModels(context)
    private val questions = RetrievalGoldenFiles.parity(
        instrumentation.context.assets.open(RetrievalGoldenFiles.PARITY).bufferedReader().use { it.readText() },
    ).filter { it.previous == null }

    @After
    fun tearDown() {
        temp.close()
        root.deleteRecursively()
    }

    @Test
    fun firstTokenPrefillAndDecode() = runBlocking<Unit> {
        val textEmbedder = listOf(ModelId.EG2_270M, ModelId.EG2_440M).firstOrNull { models.isReady(it) }
        assumeTrue("Gemma or the embedding model is not installed and prepared", textEmbedder != null && models.isReady(ModelId.GEMMA4_E2B))
        checkNotNull(textEmbedder)
        assumeTrue(pdf.isAvailable)
        val dispatchers = AppDispatchers.create()
        val coordinator = EngineCoordinator(temp.logger)
        val factory = LiteRtEngineFactory(context, dispatchers)
        val report = BenchReport(context, "generation-$variant", sqliteVector = "-")
        try {
            CorpusIndex(temp, root, pdf).build(coordinator, factory, models.pathOf(textEmbedder)!!, textEmbedder)
            val embedder = EngineQueryEmbedder(coordinator, factory, models, textEmbedder = { textEmbedder })
            val measured = questions.take(questionCount)
            val prompts = blockCounts.associateWith { blocks -> prompts(HybridRetriever(temp.index, embedder, temp.logger, selector = selector(blocks)), measured) }
            coordinator.releaseAll()

            val backend = models.preparedBackends.value.getValue(ModelId.GEMMA4_E2B)
            report.start = DeviceConditions.snapshot(context)
            factory.benchmarkStatsEnabled = true
            if (alternate) {
                val baseGenerator = generator(factory, "base", backend)
                val leverGenerator = generator(factory, variant, backend)
                val base = report.variant("base-alternating", mapOf("blocks" to "4"))
                val lever = report.variant("$variant-alternating", mapOf("blocks" to "4"))
                try {
                    measured.forEachIndexed { i, q ->
                        // One engine on the GPU at a time (constitution IV): each is initialized for its answer.
                        answer(baseGenerator, prompts.getValue(4)[i], base, q.id)
                        baseGenerator.close()
                        answer(leverGenerator, prompts.getValue(4)[i], lever, q.id)
                        leverGenerator.close()
                    }
                } finally {
                    baseGenerator.close()
                    leverGenerator.close()
                }
            } else {
                val generator = generator(factory, variant, backend)
                try {
                    DeviceConditions.resetPeak()
                    val initStarted = System.nanoTime()
                    generator.initialize()
                    val initMs = (System.nanoTime() - initStarted) / 1e6
                    blockCounts.forEach { blocks ->
                        val v = report.variant("$variant-$blocks", mapOf("blocks" to "$blocks", "backend" to backend.name))
                        v.extra["initMs"] = JsonPrimitive(initMs)
                        measured.forEachIndexed { i, q -> answer(generator, prompts.getValue(blocks)[i], v, q.id) }
                        v.extra["rssPeakMb"] = JsonPrimitive(DeviceConditions.snapshot(context).rssPeakMb ?: -1)
                    }
                } finally {
                    generator.close()
                }
            }
            report.end = DeviceConditions.snapshot(context)
            report.write()
        } catch (e: NotAvailable) {
            report.status = e.status
            report.reason = e.message
            report.end = DeviceConditions.snapshot(context)
            report.write()
        } finally {
            factory.benchmarkStatsEnabled = false
            coordinator.releaseAll()
            dispatchers.close()
        }
    }

    private class NotAvailable(val status: String, message: String) : Exception(message)

    /** D-034's selector with [blocks] blocks and a budget that lets them in (F05 measured 6 and 8 the same way). */
    private fun selector(blocks: Int) = if (blocks == RagParameters.MAX_BLOCKS) ContextSelector() else ContextSelector(maxBlocks = blocks, budgetTokens = blocks * TOKENS_PER_BLOCK)

    /** The app's v4 prompt for each question, with the blocks the retrieval picked. */
    private suspend fun prompts(retriever: HybridRetriever, measured: List<com.lisdatasolutions.microrag.rag.retrieval.ParityQuestion>): List<String> =
        measured.map { q ->
            val outcome = retriever.retrieve(RetrievalRequest(q.question)).outcome
            val blocks = (outcome as? RetrievalOutcome.Blocks)?.blocks.orEmpty()
            PromptBuilder.build(q.question, blocks.map { PromptBuilder.Block(it.documentName, it.headingPath, it.page, it.text) }, previous = null)
        }

    /** The engine each variant measures; E4B comes from `dev-models`, verified here and never installed (D-090). */
    private fun generator(factory: LiteRtEngineFactory, name: String, backend: ComputeBackend): TextGenerator {
        val gemma = ModelCatalog.byId(ModelId.GEMMA4_E2B)
        val config = GenerationConfig(maxNumTokens = RagParameters.LLM_MAX_NUM_TOKENS, maxOutputTokens = OUTPUT_TOKENS, greedy = true)
        return when (name) {
            "base" -> factory.generator(gemma, models.pathOf(ModelId.GEMMA4_E2B)!!, backend, config)
            "fp32" -> factory.generator(gemma, models.pathOf(ModelId.GEMMA4_E2B)!!, backend, config.copy(activationPrecision = ActivationPrecision.FLOAT32))
            "e4b" -> {
                val file = DevFiles.verified(context, DevFiles.GEMMA4_E4B)
                    ?: throw NotAvailable(BenchReport.STATUS_NOT_AVAILABLE, "E4B is not in dev-models or its SHA-256 differs")
                // The engine is labelled as E2B for its logs only; the report names it.
                factory.generator(gemma, file, backend, config)
            }
            else -> throw NotAvailable(BenchReport.STATUS_NOT_AVAILABLE, "unknown variant $name")
        }
    }

    /** One answer: the first token, the engine's numbers and the real tokens against the estimate. */
    private suspend fun answer(generator: TextGenerator, prompt: String, variant: BenchReport.Variant, questionId: String) {
        try {
            generator.initialize()
        } catch (e: Exception) {
            // A model that does not fit fails here (out of memory or the GPU's allocation); the report says so.
            throw NotAvailable(BenchReport.STATUS_DOES_NOT_FIT, "initialize: ${e.javaClass.simpleName}: ${e.message}")
        }
        val thermal = DeviceConditions.thermalNow(context)
        var stats: com.lisdatasolutions.microrag.rag.runtime.GenerationStats? = null
        generator.generate(GenerationPrompt.Text(prompt)).collect { event -> if (event is GenerationEvent.Finished) stats = event.stats }
        val s = checkNotNull(stats)
        val estimated = PromptBuilder.estimatedTokens(prompt)
        variant.add(Sample("ttft", s.ttftWallMs.toDouble(), questionId, thermal, s.prefillTokens, estimated))
        variant.add(Sample("total", s.totalWallMs.toDouble(), null, thermal))
        s.prefillTokensPerSecond?.let { variant.add(Sample("prefill-tok-s", it, null, thermal)) }
        s.decodeTokensPerSecond?.let { variant.add(Sample("decode-tok-s", it, null, thermal)) }
    }

    private companion object {
        const val REFERENCE_PHONE = "A063"
        const val OUTPUT_TOKENS = 128
        const val TOKENS_PER_BLOCK = 450
    }
}
