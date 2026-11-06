package com.lisdatasolutions.microrag.data.chat

import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.os.Build
import android.os.SystemClock
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertWithMessage
import com.lisdatasolutions.microrag.core.dispatchers.AppDispatchers
import com.lisdatasolutions.microrag.data.device.PowerManagerThermalStatusSource
import com.lisdatasolutions.microrag.data.indexing.FakeEmbeddingSessions
import com.lisdatasolutions.microrag.data.indexing.InMemoryIngestRunStore
import com.lisdatasolutions.microrag.data.indexing.PageEmbeddingSession
import com.lisdatasolutions.microrag.data.ingest.AndroidPageRasterizer
import com.lisdatasolutions.microrag.data.ingest.IngestRunner
import com.lisdatasolutions.microrag.data.ingest.PageFileStore
import com.lisdatasolutions.microrag.data.ingest.PlatformPdfOpener
import com.lisdatasolutions.microrag.data.retrieval.EngineQueryEmbedder
import com.lisdatasolutions.microrag.data.retrieval.HybridRetriever
import com.lisdatasolutions.microrag.debug.feasibility.ProcMemoryProbe
import com.lisdatasolutions.microrag.debug.feasibility.RequiresModels
import com.lisdatasolutions.microrag.domain.chat.ChatAvailability
import com.lisdatasolutions.microrag.domain.model.ComputeBackend
import com.lisdatasolutions.microrag.domain.model.ModelId
import com.lisdatasolutions.microrag.domain.multimodal.PageDetail
import com.lisdatasolutions.microrag.domain.rag.IndexMode
import com.lisdatasolutions.microrag.domain.rag.NewDocument
import com.lisdatasolutions.microrag.domain.retrieval.RetrieveContextUseCase
import com.lisdatasolutions.microrag.rag.ingest.DocumentExtractor
import com.lisdatasolutions.microrag.rag.retrieval.RetrievalGoldenFiles
import com.lisdatasolutions.microrag.rag.runtime.EngineCoordinator
import com.lisdatasolutions.microrag.rag.runtime.litert.LiteRtEngineFactory
import com.lisdatasolutions.microrag.testing.CorpusIndex
import com.lisdatasolutions.microrag.testing.InstalledModels
import com.lisdatasolutions.microrag.testing.TempRagIndex
import java.io.File
import java.util.UUID
import kotlin.math.ceil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * F20 T052 (research R19, SC-003, D-078): the first token of the chat with a page, from `ask()` to the first token,
 * for each page detail, and with the mode on but no page (today's Gemma). The text is the evaluation corpus and the
 * pages a generated 12-page PDF of tables, both in a temporary index with the 440M; nothing of the user is read.
 * Each answer is cancelled at its first token: the first token is what is measured. The first answer with a page
 * after those without one also reloads Gemma with vision, and is reported apart. Figures, never a gate: the
 * p50 and p95 per detail and the RSS peak go to `visual-chat-benchmark` and to `files/reports/visual-chat-*.json`.
 * On the reference phone 20 questions per variant; elsewhere 2.
 */
@RequiresModels
@RunWith(AndroidJUnit4::class)
class VisualChatBenchmarkTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val root = File(context.cacheDir, "visual-chat-benchmark-${UUID.randomUUID()}").apply { mkdirs() }
    private val temp = TempRagIndex()
    private val pdf = PlatformPdfOpener()
    private val models = InstalledModels(context)
    private val memory = ProcMemoryProbe()
    private val questions = RetrievalGoldenFiles.parity(
        instrumentation.context.assets.open(RetrievalGoldenFiles.PARITY).bufferedReader().use { it.readText() },
    ).filter { it.previous == null }

    @After
    fun tearDown() {
        temp.close()
        root.deleteRecursively()
    }

    /** [PAGES] A4 pages of a generated table of figures. */
    private fun generatedPdf(file: File): File {
        val document = PdfDocument()
        val paint = Paint().apply { textSize = 12f }
        try {
            repeat(PAGES) { index ->
                val page = document.startPage(PdfDocument.PageInfo.Builder(595, 842, index + 1).create())
                page.canvas.drawText("Tabla ${index + 1}: consumo por trimestre", 40f, 50f, paint)
                repeat(ROWS) { row ->
                    page.canvas.drawText("T${row % 4 + 1} · línea ${row + 1} · ${(index + 1) * 137 + row * 11} kWh · ${(row + 3) * 7} %", 40f, 80f + row * 18f, paint)
                }
                document.finishPage(page)
            }
            file.outputStream().use { document.writeTo(it) }
        } finally {
            document.close()
        }
        return file
    }

    @Test
    fun firstTokenWithAPage_perDetail() = runBlocking<Unit> {
        assumeTrue("Gemma or the 440M is not installed and prepared", models.isReady(ModelId.GEMMA4_E2B) && models.isReady(ModelId.EG2_440M))
        assumeTrue(pdf.isAvailable)
        val perVariant = if (Build.MODEL == REFERENCE_PHONE) PHONE_QUESTIONS else EMULATOR_QUESTIONS
        val measured = questions.take(perVariant)

        val dispatchers = AppDispatchers.create()
        val coordinator = EngineCoordinator(temp.logger)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val factory = LiteRtEngineFactory(context, dispatchers)
            CorpusIndex(temp, root, pdf).build(coordinator, factory, models.pathOf(ModelId.EG2_440M)!!, ModelId.EG2_440M)
            val pages = PageFileStore(File(root, "pages"), dispatchers.io)
            indexPages(coordinator, factory, pages, dispatchers)

            val backend = models.preparedBackends.value.getValue(ModelId.GEMMA4_E2B)
            val ready = ChatAvailability.Ready(cpuMode = backend == ComputeBackend.CPU, partialCoverage = false)
            val repository = SqliteChatRepository(File(root, "chat.db"), { ChatSchema.load(context.assets) }, Dispatchers.IO, temp.logger)
            val retrieve = RetrieveContextUseCase(
                HybridRetriever(temp.index, EngineQueryEmbedder(coordinator, factory, models, textEmbedder = { ModelId.EG2_440M }), temp.logger),
            )
            fun session(detail: PageDetail?) = DefaultChatSession(
                repository = repository, retrieve = retrieve, engine = EngineAnswerEngine(coordinator, factory, models),
                availability = { flowOf(ready) }, scope = scope, logger = temp.logger,
                pageDetail = { detail },
                pageImage = { documentId, page ->
                    temp.index.pageFile(documentId, page)?.let { name -> withContext(Dispatchers.IO) { pages.file(name).readBytes() } }
                },
            )

            memory.resetPeak()
            val variants = linkedMapOf<String, List<Long>>()
            variants["no_page"] = measured.map { firstToken(session(null), it.question) }
            // Gemma without vision is loaded: the first answer with a page reloads it.
            val switchMs = firstToken(session(PageDetail.FAST), measured.first().question)
            PageDetail.entries.forEach { detail ->
                val s = session(detail)
                variants[detail.name.lowercase()] = measured.map { firstToken(s, it.question) }
            }
            val peakMb = memory.peakRssMb()

            val report = buildJsonObject {
                put("model", Build.MODEL)
                put("sdk", Build.VERSION.SDK_INT)
                put("backend", backend.name)
                put("questions", measured.size)
                put("switch_to_vision_ms", switchMs)
                variants.forEach { (name, ttfts) ->
                    put("${name}_ttft_p50_ms", rank(ttfts, 50) ?: -1)
                    put("${name}_ttft_p95_ms", rank(ttfts, 95) ?: -1)
                }
                put("rss_peak_mb", peakMb ?: -1)
            }
            val out = File(context.filesDir, "reports").apply { mkdirs() }.resolve("visual-chat-${System.currentTimeMillis()}.json")
            out.writeText(Json.encodeToString(JsonObject.serializer(), report))
            Log.i(TAG, "visual-chat-benchmark $report")

            // SC-007: no question ever reaches the log; with pages, the answers did carry a page.
            val texts = measured.map { it.question }
            temp.logger.lines.toList().forEach { line -> texts.forEach { assertWithMessage("log line").that(line).doesNotContain(it) } }
            val withPage = temp.logger.lines.toList().count { it.startsWith(ANSWER_LINE) && " page=1 " in it }
            assertWithMessage("answers with a page").that(withPage).isAtLeast(measured.size * PageDetail.entries.size)
        } finally {
            scope.cancel()
            coordinator.releaseAll()
            dispatchers.close()
        }
    }

    /**
     * F14 T039 (research R10, contract B2, FR-025): with `-e f14.mode reload`, the first answer with a page after one
     * without, with and without loading Gemma with vision beforehand, as the chat would on a change of scope (the time
     * a person takes to type is [TYPING_MS]); with `-e f14.mode sustained`, [SUSTAINED_MINUTES] of whole answers with
     * Equilibrado. Figures to `files/reports/f14-visual-*.json`. Skipped without the argument.
     */
    @Test
    fun f14ReloadAndSustained() = runBlocking<Unit> {
        val mode = InstrumentationRegistry.getArguments().getString("f14.mode")
        assumeTrue("only with -e f14.mode reload|sustained", mode == "reload" || mode == "sustained")
        assumeTrue("Gemma or the 440M is not installed and prepared", models.isReady(ModelId.GEMMA4_E2B) && models.isReady(ModelId.EG2_440M))
        assumeTrue(pdf.isAvailable)
        val dispatchers = AppDispatchers.create()
        val coordinator = EngineCoordinator(temp.logger)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val report = com.lisdatasolutions.microrag.testing.bench.BenchReport(context, "visual-$mode", sqliteVector = "-")
        try {
            val factory = LiteRtEngineFactory(context, dispatchers)
            CorpusIndex(temp, root, pdf).build(coordinator, factory, models.pathOf(ModelId.EG2_440M)!!, ModelId.EG2_440M)
            val pages = PageFileStore(File(root, "pages"), dispatchers.io)
            indexPages(coordinator, factory, pages, dispatchers)
            val backend = models.preparedBackends.value.getValue(ModelId.GEMMA4_E2B)
            val ready = ChatAvailability.Ready(cpuMode = backend == ComputeBackend.CPU, partialCoverage = false)
            val repository = SqliteChatRepository(File(root, "chat.db"), { ChatSchema.load(context.assets) }, Dispatchers.IO, temp.logger)
            val engine = EngineAnswerEngine(coordinator, factory, models)
            val retrieve = RetrieveContextUseCase(
                HybridRetriever(temp.index, EngineQueryEmbedder(coordinator, factory, models, textEmbedder = { ModelId.EG2_440M }), temp.logger),
            )
            fun session(detail: PageDetail?) = DefaultChatSession(
                repository = repository, retrieve = retrieve, engine = engine,
                availability = { flowOf(ready) }, scope = scope, logger = temp.logger,
                pageDetail = { detail },
                pageImage = { documentId, page ->
                    temp.index.pageFile(documentId, page)?.let { name -> withContext(Dispatchers.IO) { pages.file(name).readBytes() } }
                },
            )
            val measured = questions.take(if (Build.MODEL == REFERENCE_PHONE) PHONE_QUESTIONS else EMULATOR_QUESTIONS)
            report.start = com.lisdatasolutions.microrag.testing.bench.DeviceConditions.snapshot(context)
            com.lisdatasolutions.microrag.testing.bench.DeviceConditions.resetPeak()
            if (mode == "reload") {
                val cold = report.variant("reload-without-prewarm")
                val warm = report.variant("reload-with-prewarm")
                measured.take(RELOADS).forEach { q ->
                    firstToken(session(null), q.question)
                    cold.add(com.lisdatasolutions.microrag.testing.bench.BenchReport.Sample("ttft", firstToken(session(PageDetail.BALANCED), q.question).toDouble(), q.id))
                    firstToken(session(null), q.question)
                    // What the chat does on a change of scope with pages: load Gemma with vision before the question.
                    val prewarm = scope.launch { engine.warmUp(visual = true) }
                    delay(TYPING_MS)
                    prewarm.join()
                    warm.add(com.lisdatasolutions.microrag.testing.bench.BenchReport.Sample("ttft", firstToken(session(PageDetail.BALANCED), q.question).toDouble(), q.id))
                }
                warm.extra["typingMs"] = JsonPrimitive(TYPING_MS)
            } else {
                val sustained = report.variant("sustained-balanced", mapOf("minutes" to "$SUSTAINED_MINUTES"))
                val s = session(PageDetail.BALANCED)
                val started = SystemClock.elapsedRealtime()
                var i = 0
                while (SystemClock.elapsedRealtime() - started < SUSTAINED_MINUTES * 60_000L) {
                    val conditions = com.lisdatasolutions.microrag.testing.bench.DeviceConditions.snapshot(context)
                    if (com.lisdatasolutions.microrag.testing.bench.DeviceConditions.isSevere(conditions.thermal)) {
                        report.status = com.lisdatasolutions.microrag.testing.bench.BenchReport.STATUS_ABORTED_THERMAL
                        break
                    }
                    val q = measured[i % measured.size]
                    val at = (SystemClock.elapsedRealtime() - started) / 1000.0
                    sustained.add(com.lisdatasolutions.microrag.testing.bench.BenchReport.Sample("ttft", wholeAnswer(s, q.question).toDouble(), q.id, conditions.thermal, at = at, battery = conditions.batteryPercent))
                    i++
                }
                sustained.extra["answers"] = JsonPrimitive(i)
            }
            report.end = com.lisdatasolutions.microrag.testing.bench.DeviceConditions.snapshot(context)
            report.write()
        } finally {
            scope.cancel()
            coordinator.releaseAll()
            dispatchers.close()
        }
    }

    /** From `ask()` to the first token of a whole answer, then its end awaited. */
    private suspend fun wholeAnswer(session: DefaultChatSession, question: String): Long {
        val lines = answerLines().size
        val start = SystemClock.elapsedRealtime()
        checkNotNull(session.ask(null, question)) { "the session did not start an answer" }
        val ttft = withTimeout(ANSWER_TIMEOUT_MS) {
            session.active.first { it?.text?.isNotEmpty() == true }
            SystemClock.elapsedRealtime() - start
        }
        withTimeout(ANSWER_TIMEOUT_MS) { while (answerLines().size == lines) delay(POLL_MS) }
        return ttft
    }

    /** The generated PDF in Visual, with the real 440M, as F19's page phase indexes it. */
    private suspend fun indexPages(coordinator: EngineCoordinator, factory: LiteRtEngineFactory, pages: PageFileStore, dispatchers: AppDispatchers) {
        val file = generatedPdf(File(root, "tablas.pdf"))
        val id = temp.index.addDocument(
            NewDocument(displayName = "tablas.pdf", contentHash = "visual-chat-benchmark", mimeType = "application/pdf", pageCount = PAGES, localPath = file.path, indexMode = IndexMode.VISUAL),
        )
        IngestRunner(
            temp.index, DocumentExtractor(pdf), FakeEmbeddingSessions(), InMemoryIngestRunStore(), PowerManagerThermalStatusSource(context),
            { File(context.cacheDir.path).usableSpace }, dispatchers.io, temp.logger,
            pages = IngestRunner.PagePhase(
                sessions = PageEmbeddingSession(coordinator, factory, { models.pathOf(ModelId.EG2_440M) }, temp.logger),
                rasterizer = AndroidPageRasterizer(),
                files = pages,
                enabled = { true },
            ),
        ).process(listOf(id)) { }
        assertWithMessage("pages indexed").that(temp.index.storedPages(id)).hasSize(PAGES)
    }

    /** From `ask()` to the first token, then the answer is cancelled and its end awaited. */
    private suspend fun firstToken(session: DefaultChatSession, question: String): Long {
        val lines = answerLines().size
        val start = SystemClock.elapsedRealtime()
        checkNotNull(session.ask(null, question)) { "the session did not start an answer" }
        val ttft = withTimeout(ANSWER_TIMEOUT_MS) {
            session.active.first { it?.text?.isNotEmpty() == true }
            SystemClock.elapsedRealtime() - start
        }
        session.cancel()
        withTimeout(ANSWER_TIMEOUT_MS) { while (answerLines().size == lines) delay(POLL_MS) }
        return ttft
    }

    private fun answerLines(): List<String> = temp.logger.lines.toList().filter { it.startsWith(ANSWER_LINE) }

    /** Nearest rank, as ChatBenchmarkTest; null when there is nothing to rank. */
    private fun rank(values: List<Long>, p: Int): Long? {
        if (values.isEmpty()) return null
        val sorted = values.sorted()
        return sorted[(ceil(p / 100.0 * sorted.size).toInt() - 1).coerceIn(0, sorted.lastIndex)]
    }

    private companion object {
        const val TAG = "MicroRAG-F20"
        const val REFERENCE_PHONE = "A063"
        const val PHONE_QUESTIONS = 20
        const val EMULATOR_QUESTIONS = 2
        const val PAGES = 12
        const val ROWS = 36
        const val ANSWER_LINE = "chat answer"
        const val ANSWER_TIMEOUT_MS = 180_000L
        const val POLL_MS = 20L
        const val RELOADS = 5
        const val TYPING_MS = 5_000L
        const val SUSTAINED_MINUTES = 25
    }
}
