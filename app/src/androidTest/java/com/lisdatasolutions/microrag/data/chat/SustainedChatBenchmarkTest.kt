package com.lisdatasolutions.microrag.data.chat

import android.os.Build
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lisdatasolutions.microrag.core.dispatchers.AppDispatchers
import com.lisdatasolutions.microrag.data.ingest.PlatformPdfOpener
import com.lisdatasolutions.microrag.data.retrieval.EngineQueryEmbedder
import com.lisdatasolutions.microrag.data.retrieval.HybridRetriever
import com.lisdatasolutions.microrag.debug.feasibility.RequiresModels
import com.lisdatasolutions.microrag.domain.chat.ChatAvailability
import com.lisdatasolutions.microrag.domain.chat.TurnState
import com.lisdatasolutions.microrag.domain.model.ComputeBackend
import com.lisdatasolutions.microrag.domain.model.ModelId
import com.lisdatasolutions.microrag.domain.retrieval.RetrieveContextUseCase
import com.lisdatasolutions.microrag.rag.retrieval.RetrievalGoldenFiles
import com.lisdatasolutions.microrag.rag.runtime.ActivationPrecision
import com.lisdatasolutions.microrag.rag.runtime.EngineCoordinator
import com.lisdatasolutions.microrag.rag.runtime.GenerationConfig
import com.lisdatasolutions.microrag.rag.runtime.SpeculativeMode
import com.lisdatasolutions.microrag.rag.runtime.litert.LiteRtEngineFactory
import com.lisdatasolutions.microrag.testing.bench.BenchCorpus
import com.lisdatasolutions.microrag.testing.InstalledModels
import com.lisdatasolutions.microrag.testing.TempRagIndex
import com.lisdatasolutions.microrag.testing.bench.BenchReport
import com.lisdatasolutions.microrag.testing.bench.BenchReport.Sample
import com.lisdatasolutions.microrag.testing.bench.DeviceConditions
import java.io.File
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonPrimitive
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * F14 T024 (research R5, contract B2 and G9, US3, FR-015, FR-016, SC-004): the real chat session for 20–30 minutes,
 * with whole answers, over the evaluation corpus in a temporary index and `chat.db`; nothing of the user is read.
 * Per question: the first token from `ask()`, the answer's whole time and the breakdown of its log line (embedding,
 * retrieval, prompt and the engine's own wait), with the thermal status and the battery. Without rest the questions
 * follow each other; with rest the phone waits between them. A `SEVERE` thermal status ends the session.
 *
 * Arguments: `f14.minutes` (25), `f14.rest` (seconds, 0), `f14.levers` (prefix, mtp and fp32, comma separated, over the
 * app's configuration) and `f14.label` (the name of the configuration in the report).
 */
@RequiresModels
@RunWith(AndroidJUnit4::class)
class SustainedChatBenchmarkTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val arguments = InstrumentationRegistry.getArguments()
    private val minutes = arguments.getString("f14.minutes")?.toDouble() ?: if (Build.MODEL == REFERENCE_PHONE) 25.0 else 1.0
    private val restSeconds = arguments.getString("f14.rest")?.toLong() ?: 0L
    private val levers = arguments.getString("f14.levers")?.split(",")?.filter { it.isNotBlank() }.orEmpty()
    private val label = arguments.getString("f14.label") ?: levers.joinToString("+").ifEmpty { "base" }
    private val root = File(context.cacheDir, "sustained-benchmark-${UUID.randomUUID()}").apply { mkdirs() }
    private lateinit var temp: TempRagIndex
    private val pdf = PlatformPdfOpener()
    private val models = InstalledModels(context)
    private val questions = RetrievalGoldenFiles.parity(
        instrumentation.context.assets.open(RetrievalGoldenFiles.PARITY).bufferedReader().use { it.readText() },
    ).filter { it.previous == null }

    @After
    fun tearDown() {
        if (::temp.isInitialized) temp.close()
        root.deleteRecursively()
    }

    @Test
    fun aSustainedSession() = runBlocking<Unit> {
        val textEmbedder = listOf(ModelId.EG2_270M, ModelId.EG2_440M).firstOrNull { models.isReady(it) }
        assumeTrue("Gemma or the embedding model is not installed and prepared", textEmbedder != null && models.isReady(ModelId.GEMMA4_E2B))
        checkNotNull(textEmbedder)
        assumeTrue(pdf.isAvailable)
        val dispatchers = AppDispatchers.create()
        val coordinator = EngineCoordinator(com.lisdatasolutions.microrag.testing.CapturingLogger())
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val report = BenchReport(context, "sustained-$label-rest$restSeconds", sqliteVector = "-")
        try {
            val factory = LiteRtEngineFactory(context, dispatchers)
            temp = BenchCorpus.open(context, root, pdf, coordinator, factory, models.pathOf(textEmbedder)!!, textEmbedder)
            val backend = models.preparedBackends.value.getValue(ModelId.GEMMA4_E2B)
            report.model("GEMMA4_E2B", null, backend.name, null)
            report.model(textEmbedder.name, null, "CPU", null)
            val repository = SqliteChatRepository(File(root, "chat.db"), { ChatSchema.load(context.assets) }, Dispatchers.IO, temp.logger)
            val session = DefaultChatSession(
                repository = repository,
                retrieve = RetrieveContextUseCase(
                    HybridRetriever(temp.index, EngineQueryEmbedder(coordinator, factory, models, textEmbedder = { textEmbedder }), temp.logger),
                ),
                engine = EngineAnswerEngine(coordinator, factory, models, textOverrides = ::withLevers),
                availability = { flowOf(ChatAvailability.Ready(cpuMode = backend == ComputeBackend.CPU, partialCoverage = false)) },
                scope = scope,
                logger = temp.logger,
            )
            // Gemma loaded before the session, as the chat does when it opens.
            answer(session, questions.last().question)

            val variant = report.variant("sustained", mapOf("minutes" to "$minutes", "restSeconds" to "$restSeconds", "label" to label))
            report.start = DeviceConditions.snapshot(context)
            DeviceConditions.resetPeak()
            val started = SystemClock.elapsedRealtime()
            val end = started + (minutes * MS_PER_MINUTE).toLong()
            var i = 0
            var aborted = false
            while (SystemClock.elapsedRealtime() < end) {
                val q = questions[i % questions.size]
                val before = DeviceConditions.snapshot(context)
                if (DeviceConditions.isSevere(before.thermal)) {
                    aborted = true
                    break
                }
                val at = (SystemClock.elapsedRealtime() - started) / 1000.0
                val m = answer(session, q.question)
                fun add(phase: String, value: Double?) =
                    value?.let { variant.add(Sample(phase, it, q.id, before.thermal, at = at, battery = before.batteryPercent)) }
                add("ttft", m.ttftMs?.toDouble())
                add("total", m.numbers["total_ms"]?.toDoubleOrNull())
                add("retrieval", m.numbers["retrieval_ms"]?.toDoubleOrNull())
                add("embed", m.numbers["embed_ms"]?.toDoubleOrNull())
                add("prompt", m.numbers["prompt_ms"]?.toDoubleOrNull())
                add("engine-ttft", m.numbers["engine_ttft_ms"]?.toDoubleOrNull())
                add("prompt-tokens-est", m.numbers["prompt_tokens_est"]?.toDoubleOrNull())
                i++
                if (restSeconds > 0) delay(restSeconds * 1000)
            }
            report.end = DeviceConditions.snapshot(context)
            variant.extra["answers"] = JsonPrimitive(i)
            variant.extra["elapsedMinutes"] = JsonPrimitive((SystemClock.elapsedRealtime() - started) / MS_PER_MINUTE.toDouble())
            if (aborted) {
                report.status = BenchReport.STATUS_ABORTED_THERMAL
                report.reason = "thermal ${report.end?.thermal} after $i answers"
            }
            report.write()
        } finally {
            scope.cancel()
            coordinator.releaseAll()
            dispatchers.close()
        }
    }

    /** The levers of `f14.levers` over the app's text configuration (contract G1). */
    private fun withLevers(config: GenerationConfig): GenerationConfig = levers.fold(config) { c, lever ->
        when (lever) {
            "prefix" -> c.copy(prefixCache = true)
            "mtp" -> c.copy(speculative = SpeculativeMode.ADAPTIVE)
            "fp32" -> c.copy(activationPrecision = ActivationPrecision.FLOAT32)
            else -> error("unknown lever $lever")
        }
    }

    private class Measured(val ttftMs: Long?, val numbers: Map<String, String>)

    /** Asks in a new conversation and waits for the whole answer; the first token is timed from `ask()`. */
    private suspend fun answer(session: DefaultChatSession, question: String): Measured = coroutineScope {
        val lines = answerLines().size
        val start = SystemClock.elapsedRealtime()
        val firstToken = async(Dispatchers.Default) {
            session.active.first { it?.text?.isNotEmpty() == true }
            SystemClock.elapsedRealtime() - start
        }
        checkNotNull(session.ask(null, question)) { "the session did not start an answer" }
        withTimeout(ANSWER_TIMEOUT_MS) { while (answerLines().size == lines) delay(POLL_MS) }
        val numbers = answerLines().last().removePrefix(ANSWER_LINE).trim().split(" ")
            .mapNotNull { pair -> pair.split("=", limit = 2).takeIf { it.size == 2 }?.let { it[0] to it[1] } }.toMap()
        val ttft = if (firstToken.isCompleted) firstToken.await() else null.also { firstToken.cancel() }
        withTimeout(ANSWER_TIMEOUT_MS) { session.active.first { it == null || it.state != TurnState.GENERATING } }
        Measured(ttft, numbers)
    }

    private fun answerLines() = temp.logger.lines.toList().filter { it.startsWith(ANSWER_LINE) }

    private companion object {
        const val REFERENCE_PHONE = "A063"
        const val ANSWER_LINE = "chat answer"
        const val ANSWER_TIMEOUT_MS = 180_000L
        const val POLL_MS = 50L
        const val MS_PER_MINUTE = 60_000L
    }
}
