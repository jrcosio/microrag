package com.lisdatasolutions.microrag.data.chat

import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lisdatasolutions.microrag.core.dispatchers.AppDispatchers
import com.lisdatasolutions.microrag.data.ingest.PlatformPdfOpener
import com.lisdatasolutions.microrag.data.retrieval.EngineQueryEmbedder
import com.lisdatasolutions.microrag.data.retrieval.HybridRetriever
import com.lisdatasolutions.microrag.domain.model.ComputeBackend
import com.lisdatasolutions.microrag.debug.feasibility.RequiresModels
import com.lisdatasolutions.microrag.domain.model.ModelCatalog
import com.lisdatasolutions.microrag.domain.model.ModelId
import com.lisdatasolutions.microrag.domain.rag.RagParameters
import com.lisdatasolutions.microrag.domain.retrieval.RetrievalOutcome
import com.lisdatasolutions.microrag.domain.retrieval.RetrievalRequest
import com.lisdatasolutions.microrag.rag.answer.PromptBuilder
import com.lisdatasolutions.microrag.rag.retrieval.RetrievalGoldenFiles
import com.lisdatasolutions.microrag.rag.runtime.EngineCoordinator
import com.lisdatasolutions.microrag.rag.runtime.GenerationConfig
import com.lisdatasolutions.microrag.rag.runtime.GenerationEvent
import com.lisdatasolutions.microrag.rag.runtime.GenerationPrompt
import com.lisdatasolutions.microrag.rag.runtime.SpeculativeMode
import com.lisdatasolutions.microrag.rag.runtime.TextGenerator
import com.lisdatasolutions.microrag.rag.runtime.litert.LiteRtEngineFactory
import com.lisdatasolutions.microrag.testing.bench.BenchCorpus
import com.lisdatasolutions.microrag.testing.InstalledModels
import com.lisdatasolutions.microrag.testing.TempRagIndex
import com.lisdatasolutions.microrag.testing.bench.BenchReport
import com.lisdatasolutions.microrag.testing.bench.DeviceConditions
import java.io.File
import java.util.UUID
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * F14 T027 (research R6, contract G3): with greedy sampling, the answer to a prompt whose rules were prefilled once and
 * cloned must be the answer to the whole prompt in one message, as on the desktop (the same tokens). One engine
 * answers both in turn, whole, over the evaluation corpus; nothing of the user is read. Differences are reported, not
 * failed: a split prefill on the GPU may diverge in a token, and then the answers that change go to the quality rule.
 * The report keeps how many answers differ, never their text: per question only where the two answers part (the
 * characters they share from the start) and their lengths, which tell a different prompt (they part at once) from the
 * rounding of a split prefill on the GPU (they part late). `-e f14.backend cpu` runs the same check on the CPU, and
 * `-e f14.questions` sets how many questions.
 *
 * `-e f14.compare` picks what is set against the whole prompt: `prefix` (the default), `mtp` (the same prompt with
 * speculative decoding, FR-017: the greedy answers of the goldens must not change; the two engines one after the
 * other, constitution IV) or `repeat` (the whole prompt twice on one engine: the control of how far greedy repeats).
 */
@RequiresModels
@RunWith(AndroidJUnit4::class)
class PrefixCacheGoldenTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val root = File(context.cacheDir, "prefix-golden-${UUID.randomUUID()}").apply { mkdirs() }
    private lateinit var temp: TempRagIndex
    private val pdf = PlatformPdfOpener()
    private val models = InstalledModels(context)
    private val arguments = InstrumentationRegistry.getArguments()
    private val forcedBackend = arguments.getString("f14.backend")?.let { ComputeBackend.valueOf(it.uppercase()) }
    private val questionCount = arguments.getString("f14.questions")?.toInt()
    private val compare = arguments.getString("f14.compare") ?: "prefix"
    private val questions = RetrievalGoldenFiles.parity(
        instrumentation.context.assets.open(RetrievalGoldenFiles.PARITY).bufferedReader().use { it.readText() },
    ).filter { it.previous == null }

    @After
    fun tearDown() {
        if (::temp.isInitialized) temp.close()
        root.deleteRecursively()
    }

    @Test
    fun thePrefilledRules_giveTheSameGreedyAnswers() = runBlocking<Unit> {
        val textEmbedder = listOf(ModelId.EG2_270M, ModelId.EG2_440M).firstOrNull { models.isReady(it) }
        assumeTrue("Gemma or the embedding model is not installed and prepared", textEmbedder != null && models.isReady(ModelId.GEMMA4_E2B))
        checkNotNull(textEmbedder)
        assumeTrue(pdf.isAvailable)
        val dispatchers = AppDispatchers.create()
        val coordinator = EngineCoordinator(com.lisdatasolutions.microrag.testing.CapturingLogger())
        val factory = LiteRtEngineFactory(context, dispatchers)
        val report = BenchReport(context, "$compare-golden", sqliteVector = "-")
        report.start = DeviceConditions.snapshot(context)
        try {
            temp = BenchCorpus.open(context, root, pdf, coordinator, factory, models.pathOf(textEmbedder)!!, textEmbedder)
            val retriever = HybridRetriever(temp.index, EngineQueryEmbedder(coordinator, factory, models, textEmbedder = { textEmbedder }), temp.logger)
            val measured = questions.take(questionCount ?: if (Build.MODEL == REFERENCE_PHONE) QUESTIONS else 2)
            val prompts = measured.map { q ->
                val blocks = (retriever.retrieve(RetrievalRequest(q.question)).outcome as? RetrievalOutcome.Blocks)?.blocks.orEmpty()
                PromptBuilder.buildSplit(q.question, blocks.map { PromptBuilder.Block(it.documentName, it.headingPath, it.page, it.text) }, null)
            }
            coordinator.releaseAll()

            val backend = forcedBackend ?: models.preparedBackends.value.getValue(ModelId.GEMMA4_E2B)
            val config = GenerationConfig(RagParameters.LLM_MAX_NUM_TOKENS, RagParameters.MAX_OUTPUT_TOKENS, greedy = true)
            val generator = { c: GenerationConfig -> factory.generator(ModelCatalog.byId(ModelId.GEMMA4_E2B), models.pathOf(ModelId.GEMMA4_E2B)!!, backend, c) }
            val variant = report.variant("$compare-vs-whole", mapOf("questions" to "${measured.size}", "backend" to backend.name))
            val answers = when (compare) {
                "prefix" -> inTurn(generator(config.copy(prefixCache = true)), prompts) { GenerationPrompt.Split(it.prefix, it.rest) }
                "repeat" -> inTurn(generator(config), prompts) { GenerationPrompt.Text(it.whole) }
                "mtp" -> all(generator(config), prompts).zip(all(generator(config.copy(speculative = SpeculativeMode.ADAPTIVE)), prompts))
                else -> error("f14.compare: prefix, mtp or repeat")
            }
            val pairs = answers.map { (whole, other) -> Triple(whole.commonPrefixWith(other).length, whole.length, other.length) }
            val different = pairs.count { (shared, whole, split) -> shared < whole || whole != split }
            variant.extra["identical"] = JsonPrimitive(measured.size - different)
            variant.extra["different"] = JsonPrimitive(different)
            // Numbers only: the characters both answers share from the start, and the length of each.
            variant.extra["sharedChars"] = buildJsonArray { pairs.forEach { add(JsonPrimitive(it.first)) } }
            variant.extra["wholeChars"] = buildJsonArray { pairs.forEach { add(JsonPrimitive(it.second)) } }
            variant.extra["splitChars"] = buildJsonArray { pairs.forEach { add(JsonPrimitive(it.third)) } }
            report.end = DeviceConditions.snapshot(context)
            report.write()
        } finally {
            coordinator.releaseAll()
            dispatchers.close()
        }
    }

    /** One engine answers each prompt whole and then as [other] says, in turn. */
    private suspend fun inTurn(generator: TextGenerator, prompts: List<PromptBuilder.SplitPrompt>, other: (PromptBuilder.SplitPrompt) -> GenerationPrompt) =
        try {
            generator.initialize()
            prompts.map { text(generator, GenerationPrompt.Text(it.whole)) to text(generator, other(it)) }
        } finally {
            generator.close()
        }

    /** One engine answers every prompt whole and is closed before the next is loaded (constitution IV). */
    private suspend fun all(generator: TextGenerator, prompts: List<PromptBuilder.SplitPrompt>) =
        try {
            generator.initialize()
            prompts.map { text(generator, GenerationPrompt.Text(it.whole)) }
        } finally {
            generator.close()
        }

    private suspend fun text(generator: TextGenerator, prompt: GenerationPrompt): String {
        val out = StringBuilder()
        generator.generate(prompt).collect { event -> if (event is GenerationEvent.Token) out.append(event.textDelta) }
        return out.toString()
    }

    private companion object {
        const val REFERENCE_PHONE = "A063"
        const val QUESTIONS = 20
    }
}
