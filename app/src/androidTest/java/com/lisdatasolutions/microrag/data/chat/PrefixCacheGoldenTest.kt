package com.lisdatasolutions.microrag.data.chat

import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lisdatasolutions.microrag.core.dispatchers.AppDispatchers
import com.lisdatasolutions.microrag.data.ingest.PlatformPdfOpener
import com.lisdatasolutions.microrag.data.retrieval.EngineQueryEmbedder
import com.lisdatasolutions.microrag.data.retrieval.HybridRetriever
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
import com.lisdatasolutions.microrag.rag.runtime.TextGenerator
import com.lisdatasolutions.microrag.rag.runtime.litert.LiteRtEngineFactory
import com.lisdatasolutions.microrag.testing.CorpusIndex
import com.lisdatasolutions.microrag.testing.InstalledModels
import com.lisdatasolutions.microrag.testing.TempRagIndex
import com.lisdatasolutions.microrag.testing.bench.BenchReport
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
 * F14 T027 (research R6, contract G3): with greedy sampling, the answer to a prompt whose rules were prefilled once and
 * cloned must be the answer to the whole prompt in one message, as on the desktop (the same tokens). One engine
 * answers both in turn, whole, over the evaluation corpus; nothing of the user is read. Differences are reported, not
 * failed: a split prefill on the GPU may diverge in a token, and then the answers that change go to the quality rule.
 * The report keeps how many answers differ, never their text.
 */
@RequiresModels
@RunWith(AndroidJUnit4::class)
class PrefixCacheGoldenTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val root = File(context.cacheDir, "prefix-golden-${UUID.randomUUID()}").apply { mkdirs() }
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
    fun thePrefilledRules_giveTheSameGreedyAnswers() = runBlocking<Unit> {
        val textEmbedder = listOf(ModelId.EG2_270M, ModelId.EG2_440M).firstOrNull { models.isReady(it) }
        assumeTrue("Gemma or the embedding model is not installed and prepared", textEmbedder != null && models.isReady(ModelId.GEMMA4_E2B))
        checkNotNull(textEmbedder)
        assumeTrue(pdf.isAvailable)
        val dispatchers = AppDispatchers.create()
        val coordinator = EngineCoordinator(temp.logger)
        val factory = LiteRtEngineFactory(context, dispatchers)
        val report = BenchReport(context, "prefix-golden", sqliteVector = "-")
        report.start = DeviceConditions.snapshot(context)
        try {
            CorpusIndex(temp, root, pdf).build(coordinator, factory, models.pathOf(textEmbedder)!!, textEmbedder)
            val retriever = HybridRetriever(temp.index, EngineQueryEmbedder(coordinator, factory, models, textEmbedder = { textEmbedder }), temp.logger)
            val measured = questions.take(if (Build.MODEL == REFERENCE_PHONE) QUESTIONS else 2)
            val prompts = measured.map { q ->
                val blocks = (retriever.retrieve(RetrievalRequest(q.question)).outcome as? RetrievalOutcome.Blocks)?.blocks.orEmpty()
                PromptBuilder.buildSplit(q.question, blocks.map { PromptBuilder.Block(it.documentName, it.headingPath, it.page, it.text) }, null)
            }
            coordinator.releaseAll()

            val backend = models.preparedBackends.value.getValue(ModelId.GEMMA4_E2B)
            val config = GenerationConfig(RagParameters.LLM_MAX_NUM_TOKENS, RagParameters.MAX_OUTPUT_TOKENS, greedy = true, prefixCache = true)
            val generator = factory.generator(ModelCatalog.byId(ModelId.GEMMA4_E2B), models.pathOf(ModelId.GEMMA4_E2B)!!, backend, config)
            val variant = report.variant("prefix-vs-whole", mapOf("questions" to "${measured.size}", "backend" to backend.name))
            var different = 0
            try {
                generator.initialize()
                prompts.forEach { prompt ->
                    val whole = text(generator, GenerationPrompt.Text(prompt.whole))
                    val split = text(generator, GenerationPrompt.Split(prompt.prefix, prompt.rest))
                    if (whole != split) different++
                }
            } finally {
                generator.close()
            }
            variant.extra["identical"] = JsonPrimitive(measured.size - different)
            variant.extra["different"] = JsonPrimitive(different)
            report.end = DeviceConditions.snapshot(context)
            report.write()
        } finally {
            coordinator.releaseAll()
            dispatchers.close()
        }
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
