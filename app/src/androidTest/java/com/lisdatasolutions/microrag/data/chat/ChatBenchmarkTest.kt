package com.lisdatasolutions.microrag.data.chat

import android.os.Build
import android.os.SystemClock
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertWithMessage
import com.lisdatasolutions.microrag.core.dispatchers.AppDispatchers
import com.lisdatasolutions.microrag.data.ingest.PlatformPdfOpener
import com.lisdatasolutions.microrag.data.retrieval.EngineQueryEmbedder
import com.lisdatasolutions.microrag.data.retrieval.HybridRetriever
import com.lisdatasolutions.microrag.debug.feasibility.ProcMemoryProbe
import com.lisdatasolutions.microrag.debug.feasibility.RequiresModels
import com.lisdatasolutions.microrag.domain.chat.ChatAvailability
import com.lisdatasolutions.microrag.domain.chat.ChatRepository
import com.lisdatasolutions.microrag.domain.chat.TurnState
import com.lisdatasolutions.microrag.domain.model.ComputeBackend
import com.lisdatasolutions.microrag.domain.model.ModelId
import com.lisdatasolutions.microrag.domain.retrieval.RetrieveContextUseCase
import com.lisdatasolutions.microrag.rag.answer.PromptBuilder
import com.lisdatasolutions.microrag.rag.retrieval.ParityQuestion
import com.lisdatasolutions.microrag.rag.retrieval.RetrievalGoldenFiles
import com.lisdatasolutions.microrag.rag.runtime.EngineCoordinator
import com.lisdatasolutions.microrag.rag.runtime.litert.LiteRtEngineFactory
import com.lisdatasolutions.microrag.testing.CorpusIndex
import com.lisdatasolutions.microrag.testing.InstalledModels
import com.lisdatasolutions.microrag.testing.TempRagIndex
import java.io.File
import java.util.Locale
import java.util.UUID
import kotlin.math.ceil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * F11 T062 (research R15, SC-004): the first token of the real chat session, from `ask()` to the first
 * token, over the evaluation corpus in a temporary index and a temporary `chat.db`; the user's library
 * and conversations are never read. Each of the 50 questions of the parity sample goes in a new
 * conversation; a follow-up first asks its previous question, unmeasured. Gemma is loaded beforehand, and
 * the first question is also measured apart with Gemma unloaded. Logged as `chat-benchmark`, numbers only
 * (data-model §9), plus one `chat-benchmark-q` line per measured question. The generation speed is an
 * estimate: the engine gives its own only with the diagnostics flag, which the app never sets. On the
 * reference phone the p95 is compared with 5 s and reported (`sc004=`), without failing: the phone measured
 * it over, and D-057 keeps it as a documented exception for F14. Elsewhere, 3 questions.
 */
@RequiresModels
@RunWith(AndroidJUnit4::class)
class ChatBenchmarkTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val root = File(context.cacheDir, "chat-benchmark-${UUID.randomUUID()}").apply { mkdirs() }
    private val temp = TempRagIndex()
    private val pdf = PlatformPdfOpener()
    private val models = InstalledModels(context)
    private val memory = ProcMemoryProbe()
    private val questions = RetrievalGoldenFiles.parity(
        instrumentation.context.assets.open(RetrievalGoldenFiles.PARITY).bufferedReader().use { it.readText() },
    )

    @After
    fun tearDown() {
        temp.close()
        root.deleteRecursively()
    }

    /**
     * One measured answer: the first token from `ask()` (null for a refusal without blocks), the session's
     * numbers and the estimated tokens of the answer.
     */
    private class Measure(val ttftMs: Long?, val numbers: Map<String, String>, val answerTokensEst: Int) {
        /** Estimated answer tokens over the time after the session's first token. */
        val decodeTokensPerSecondEst: Double? = numbers["ttft_ms"]?.toLongOrNull()?.let { ttft ->
            val decodeMs = (numbers["total_ms"]?.toLongOrNull() ?: return@let null) - ttft
            if (decodeMs > 0) answerTokensEst * 1000.0 / decodeMs else null
        }
    }

    @Test
    fun firstTokenOfTheChat() = runBlocking<Unit> {
        // F19 (analyze H5): the text embedder in use, the 440M once the multimodal mode deleted the 270M.
        val textEmbedder = listOf(ModelId.EG2_270M, ModelId.EG2_440M).firstOrNull { models.isReady(it) }
        assumeTrue("Gemma or the embedding model is not installed and prepared", textEmbedder != null && models.isReady(ModelId.GEMMA4_E2B))
        checkNotNull(textEmbedder)
        assumeTrue(pdf.isAvailable)
        val onReferencePhone = Build.MODEL == REFERENCE_PHONE
        val measured = if (onReferencePhone) questions else questions.take(EMULATOR_QUESTIONS)

        val dispatchers = AppDispatchers.create()
        val coordinator = EngineCoordinator(temp.logger)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val factory = LiteRtEngineFactory(context, dispatchers)
            CorpusIndex(temp, root, pdf).build(coordinator, factory, models.pathOf(textEmbedder)!!, textEmbedder)

            val backend = models.preparedBackends.value.getValue(ModelId.GEMMA4_E2B)
            val ready = ChatAvailability.Ready(cpuMode = backend == ComputeBackend.CPU, partialCoverage = false)
            val engine = EngineAnswerEngine(coordinator, factory, models)
            val repository = SqliteChatRepository(File(root, "chat.db"), { ChatSchema.load(context.assets) }, Dispatchers.IO, temp.logger)
            val session = DefaultChatSession(
                repository = repository,
                retrieve = RetrieveContextUseCase(
                    HybridRetriever(temp.index, EngineQueryEmbedder(coordinator, factory, models, textEmbedder = { textEmbedder }), temp.logger),
                ),
                engine = engine,
                availability = { flowOf(ready) },
                scope = scope,
                logger = temp.logger,
            )

            memory.resetPeak()
            // Gemma unloaded: the indexing released its engine and nothing loaded Gemma yet.
            val cold = answer(session, repository, null, measured.first { it.previous == null }.question)
            val results = measured.mapIndexed { i, question ->
                val conversation = question.previous?.let { previous -> answer(session, repository, null, previous).conversationId }
                answer(session, repository, conversation, question.question).measure.also { m ->
                    Log.i(
                        TAG,
                        "chat-benchmark-q i=$i follow_up=${question.previous != null} ttft_ms=${shown(m.ttftMs)} " +
                            "session_ttft_ms=${m.numbers["ttft_ms"]} retrieval_ms=${m.numbers["retrieval_ms"]} " +
                            "prompt_tokens_est=${m.numbers["prompt_tokens_est"]} blocks=${m.numbers["blocks"]} " +
                            "total_ms=${m.numbers["total_ms"]} answer_tokens_est=${m.answerTokensEst} " +
                            "decode_tok_s_est=${shown(m.decodeTokensPerSecondEst)} refusal=${m.numbers["refusal"]} cited=${m.numbers["cited"]}",
                    )
                }
            }
            val peakMb = memory.peakRssMb()

            val ttfts = results.mapNotNull { it.ttftMs }
            val numbers = results.map { it.numbers }
            val refusals = numbers.count { it["refusal"] == "true" }
            val uncited = numbers.count { it["refusal"] == "false" && it["cited"] == "0" }
            val sc004 = (rank(ttfts, 95) ?: Long.MAX_VALUE) <= MAX_TTFT_P95_MS
            Log.i(
                TAG,
                "chat-benchmark model=${Build.MODEL} sdk=${Build.VERSION.SDK_INT} backend=$backend n=${results.size} " +
                    "cold_ttft_ms=${shown(cold.measure.ttftMs)} ttft_p50=${shown(rank(ttfts, 50))} ttft_p95=${shown(rank(ttfts, 95))} " +
                    "prompt_tokens_p50=${shown(rank(numbers.longs("prompt_tokens_est"), 50))} " +
                    "prompt_tokens_p95=${shown(rank(numbers.longs("prompt_tokens_est"), 95))} " +
                    "decode_tok_s_est_p50=${shown(rank(results.mapNotNull { it.decodeTokensPerSecondEst }, 50))} " +
                    "total_p50=${shown(rank(numbers.longs("total_ms"), 50))} total_p95=${shown(rank(numbers.longs("total_ms"), 95))} " +
                    "rss_peak_mb=${shown(peakMb)} refusals=$refusals uncited=$uncited" +
                    (if (onReferencePhone) " sc004=${if (sc004) "met" else "exceeded"}" else ""),
            )

            // SC-010: no question, previous question or answer text ever reaches the log.
            val texts = measured.flatMap { listOfNotNull(it.question, it.previous) }
            temp.logger.lines.toList().forEach { line -> texts.forEach { assertWithMessage("log line").that(line).doesNotContain(it) } }
            assertWithMessage("every answer completed").that(numbers.map { it["outcome"] }.toSet()).containsExactly("completed")
        } finally {
            scope.cancel()
            coordinator.releaseAll()
            dispatchers.close()
        }
    }

    private class Answered(val conversationId: Long, val measure: Measure)

    /** Asks and waits for the end of the answer; the first token is timed from the call to `ask()`. */
    private suspend fun answer(session: DefaultChatSession, repository: ChatRepository, conversationId: Long?, question: String): Answered = coroutineScope {
        val lines = answerLines().size
        val start = SystemClock.elapsedRealtime()
        // Off the polling thread, so the first token is seen as soon as it arrives.
        val firstToken = async(Dispatchers.Default) {
            session.active.first { it?.text?.isNotEmpty() == true }
            SystemClock.elapsedRealtime() - start
        }
        val id = checkNotNull(session.ask(conversationId, question)) { "the session did not start an answer" }
        withTimeout(ANSWER_TIMEOUT_MS) {
            while (answerLines().size == lines) delay(POLL_MS)
        }
        val numbers = answerLines().last().removePrefix(ANSWER_LINE).trim().split(" ")
            .mapNotNull { pair -> pair.split("=", limit = 2).takeIf { it.size == 2 }?.let { it[0] to it[1] } }.toMap()
        val ttft = if (firstToken.isCompleted) {
            firstToken.await()
        } else {
            // A refusal without blocks never calls the model.
            firstToken.cancel()
            null
        }
        // The saved text, once the turn is saved as completed.
        val answer = withTimeout(ANSWER_TIMEOUT_MS) {
            repository.conversation(id).first { turns -> turns.lastOrNull()?.state == TurnState.COMPLETED }.last().answer
        }
        Answered(id, Measure(ttft, numbers, if (answer.isEmpty()) 0 else PromptBuilder.estimatedTokens(answer)))
    }

    /** The session's answer lines, from a copy: it keeps writing the log from its own threads. */
    private fun answerLines(): List<String> = temp.logger.lines.toList().filter { it.startsWith(ANSWER_LINE) }

    private fun List<Map<String, String>>.longs(key: String) = mapNotNull { it[key]?.toLongOrNull() }

    /** Nearest rank, as the F02 suite; null when there is nothing to rank. */
    private fun <T : Comparable<T>> rank(values: List<T>, p: Int): T? {
        if (values.isEmpty()) return null
        val sorted = values.sorted()
        return sorted[(ceil(p / 100.0 * sorted.size).toInt() - 1).coerceIn(0, sorted.lastIndex)]
    }

    private fun shown(value: Any?): String = when (value) {
        null -> "-"
        is Double -> String.format(Locale.ROOT, "%.1f", value)
        else -> value.toString()
    }

    private companion object {
        const val TAG = "MicroRAG"
        const val REFERENCE_PHONE = "A063"
        const val EMULATOR_QUESTIONS = 3
        const val MAX_TTFT_P95_MS = 5_000L
        const val ANSWER_LINE = "chat answer"
        const val ANSWER_TIMEOUT_MS = 180_000L
        const val POLL_MS = 20L
    }
}
