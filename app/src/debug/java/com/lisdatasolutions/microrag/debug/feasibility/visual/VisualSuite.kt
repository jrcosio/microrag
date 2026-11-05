package com.lisdatasolutions.microrag.debug.feasibility.visual

import com.lisdatasolutions.microrag.core.dispatchers.AppDispatchers
import com.lisdatasolutions.microrag.core.logging.AppLogger
import com.lisdatasolutions.microrag.data.models.ModelFileRepository
import com.lisdatasolutions.microrag.debug.feasibility.DeviceInfoSource
import com.lisdatasolutions.microrag.debug.feasibility.FeasibilityTexts
import com.lisdatasolutions.microrag.debug.feasibility.FeasibilityTextsSource
import com.lisdatasolutions.microrag.debug.feasibility.MeasurementProtocol
import com.lisdatasolutions.microrag.debug.feasibility.MemoryProbe
import com.lisdatasolutions.microrag.debug.feasibility.Percentiles
import com.lisdatasolutions.microrag.debug.feasibility.ThermalLevel
import com.lisdatasolutions.microrag.debug.feasibility.ThermalMonitor
import com.lisdatasolutions.microrag.debug.feasibility.needsConfirmation
import com.lisdatasolutions.microrag.debug.feasibility.report.DeviceSnapshot
import com.lisdatasolutions.microrag.debug.feasibility.report.ModelEntry
import com.lisdatasolutions.microrag.debug.feasibility.visual.report.AnswerEntry
import com.lisdatasolutions.microrag.debug.feasibility.visual.report.DeviceSetSummary
import com.lisdatasolutions.microrag.debug.feasibility.visual.report.DimensionCheck
import com.lisdatasolutions.microrag.debug.feasibility.visual.report.VectorEntry
import com.lisdatasolutions.microrag.debug.feasibility.visual.report.VisualKind
import com.lisdatasolutions.microrag.debug.feasibility.visual.report.VisualMetrics
import com.lisdatasolutions.microrag.debug.feasibility.visual.report.VisualParams
import com.lisdatasolutions.microrag.debug.feasibility.visual.report.VisualReport
import com.lisdatasolutions.microrag.debug.feasibility.visual.report.VisualReportSink
import com.lisdatasolutions.microrag.debug.feasibility.visual.report.VisualScenario
import com.lisdatasolutions.microrag.debug.feasibility.visual.report.VisualStatus
import com.lisdatasolutions.microrag.domain.model.ComputeBackend
import com.lisdatasolutions.microrag.domain.model.ModelCatalog
import com.lisdatasolutions.microrag.domain.model.ModelFile
import com.lisdatasolutions.microrag.domain.model.ModelFileState
import com.lisdatasolutions.microrag.domain.model.ModelId
import com.lisdatasolutions.microrag.rag.ingest.EncodedImage
import com.lisdatasolutions.microrag.rag.ingest.ImageFormat
import com.lisdatasolutions.microrag.rag.ingest.PageRasterizer
import com.lisdatasolutions.microrag.rag.runtime.EmbeddingMath
import com.lisdatasolutions.microrag.rag.runtime.EngineCoordinator
import com.lisdatasolutions.microrag.rag.runtime.EngineFactory
import com.lisdatasolutions.microrag.rag.runtime.EngineKey
import com.lisdatasolutions.microrag.rag.runtime.EngineKind
import com.lisdatasolutions.microrag.rag.runtime.GenerationConfig
import com.lisdatasolutions.microrag.rag.runtime.ManagedEngine
import com.lisdatasolutions.microrag.rag.runtime.initializeOrClose
import java.io.File
import java.time.OffsetDateTime
import java.time.temporal.ChronoUnit
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.abs
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** The blocks of the visual diagnostic, run in this order (contracts/visual-diagnostic.md §2). */
enum class VisualBlock { GEMMA_INFO, EMBED_TEXT, EMBED_IMAGE, PARITY, RASTER, IMAGE_DECODE, PROJECTION, GEMMA_PAGES, READING }

/** Real progress: [step] of [total] of the scenario named by [label]. */
data class VisualProgress(val label: String, val step: Int, val total: Int)

/**
 * Runs the F18 measurements (research R4–R6, R13, R14). Like F02's suite: only verified files are loaded,
 * one engine configuration per scenario between two releaseAll() calls (finding M6), and the report is
 * rewritten after every result. Answers and vectors go only to the report, never to the log (FR-018).
 */
class VisualSuite(
    private val coordinator: EngineCoordinator,
    private val textFactory: EngineFactory,
    private val visionFactory: VisionEngineFactory,
    private val rasterizer: PageRasterizer,
    private val repository: ModelFileRepository,
    private val deviceSetSource: () -> DeviceSet,
    private val textsSource: FeasibilityTextsSource,
    private val thermal: ThermalMonitor,
    private val memory: MemoryProbe,
    private val device: DeviceInfoSource,
    private val reportSink: VisualReportSink,
    private val dispatchers: AppDispatchers,
    private val logger: AppLogger,
    private val nanoTime: () -> Long = System::nanoTime,
    private val now: () -> String = { OffsetDateTime.now().truncatedTo(ChronoUnit.SECONDS).toString() },
    private val catalog: List<ModelFile> = ModelCatalog.all,
) {

    private val runLock = Mutex()
    private val _progress = MutableStateFlow<VisualProgress?>(null)
    private val _results = MutableStateFlow<List<VisualScenario>>(emptyList())
    private val _lastReport = MutableStateFlow<File?>(null)

    val progress: StateFlow<VisualProgress?> = _progress.asStateFlow()
    val results: StateFlow<List<VisualScenario>> = _results.asStateFlow()
    val lastReport: StateFlow<File?> = _lastReport.asStateFlow()

    /**
     * Runs [blocks] in [VisualBlock] order. With [candidates] (desktop keys such as `mezcla-p1-b280-t0`),
     * GEMMA_PAGES runs stage B on them instead of the stage-A matrix (research R4).
     */
    suspend fun run(
        blocks: Set<VisualBlock>,
        confirmHot: suspend (ThermalLevel) -> Boolean,
        candidates: List<String> = emptyList(),
    ): VisualReport = runLock.withLock {
        withContext(dispatchers.default) {
            _results.value = emptyList()
            val session = Session(createdAt = now(), device = device.snapshot(), deviceSet = deviceSetSource())
            textFactory.benchmarkStatsEnabled = true
            try {
                revalidate()
                VisualBlock.entries.filter { it in blocks }.forEach { block ->
                    when (block) {
                        VisualBlock.GEMMA_INFO -> gemmaInfo(session)
                        VisualBlock.EMBED_TEXT -> listOf(ModelId.EG2_270M, ModelId.EG2_440M).forEach { embedText(session, it, confirmHot) }
                        VisualBlock.EMBED_IMAGE -> VisualProtocol.EMBEDDER_TOKENS.forEach { tokens ->
                            ComputeBackend.entries.forEach { embedImage(session, tokens, it, confirmHot) }
                        }
                        VisualBlock.PARITY -> VisualProtocol.EMBEDDER_TOKENS.forEach { parity(session, it, confirmHot) }
                        VisualBlock.RASTER -> raster(session, confirmHot)
                        VisualBlock.IMAGE_DECODE -> imageDecode(session, confirmHot)
                        VisualBlock.PROJECTION -> projection(session, confirmHot)
                        VisualBlock.GEMMA_PAGES -> gemmaPages(session, candidates, confirmHot)
                        VisualBlock.READING -> reading(session, confirmHot)
                    }
                }
            } finally {
                withContext(NonCancellable) {
                    coordinator.releaseAll()
                    textFactory.benchmarkStatsEnabled = false
                    _progress.value = null
                    session.writeReport()
                }
            }
            session.report()
        }
    }

    private inner class Session(val createdAt: String, val device: DeviceSnapshot, val deviceSet: DeviceSet) {
        var gemmaInfo: VisionModelInfo? = null
        var embedderInfo: VisionModelInfo? = null

        suspend fun record(result: VisualScenario) {
            _results.update { it + result }
            logger.i(TAG) { "visual ${result.kind} ${result.model ?: "-"} ${result.backend ?: "-"} ${result.params.combination ?: ""}: ${result.status}" }
            writeReport()
        }

        suspend fun writeReport() {
            _lastReport.value = reportSink.write(report())
        }

        fun report(): VisualReport {
            val states = repository.states.value
            return VisualReport(
                createdAt = createdAt,
                device = device,
                models = catalog.map { ModelEntry(it.id, it.fileName, it.sizeBytes, it.sha256, it.revision, stateName(states[it.id])) },
                deviceSet = DeviceSetSummary(deviceSet.present, deviceSet.verified.size, deviceSet.rejected, deviceSet.parityPx),
                gemmaInfo = gemmaInfo,
                embedderInfo = embedderInfo,
                scenarios = _results.value,
            )
        }
    }

    /** FR-002: every present file is hashed again before any measurement of this session. */
    private suspend fun revalidate() {
        repository.refresh()
        catalog.filter {
            val state = repository.states.value[it.id]
            state is ModelFileState.Unverified || state is ModelFileState.Verified
        }.forEach { repository.verify(it.id) }
    }

    private fun file(id: ModelId) = catalog.first { it.id == id }

    /** One scenario: its timing, thermal and memory context and how it ends. */
    private inner class ScenarioRun(
        val kind: VisualKind,
        val model: ModelId?,
        val backend: ComputeBackend?,
        val params: VisualParams = VisualParams(),
    ) {
        private val startedAt = now()
        private val start = nanoTime()
        private val thermalStart = thermal.level()
        private val charging = device.isCharging()
        private val resetSupported = memory.resetPeak()
        var phase = "START"
        val label = listOfNotNull(kind.name, model?.name, backend?.name, params.combination, params.document, params.px?.toString(),
            params.tokensPerImage?.let { "$it tokens" }).joinToString(" · ")

        fun progress(phase: String, step: Int = 0, total: Int = 0) {
            this.phase = phase
            _progress.value = VisualProgress("$label · $phase", step, total)
        }

        fun finish(
            status: VisualStatus,
            reason: String? = null,
            metrics: VisualMetrics? = null,
            answers: List<AnswerEntry>? = null,
            vectors: List<VectorEntry>? = null,
        ) = VisualScenario(
            kind = kind, model = model, backend = backend, params = params, status = status, reason = reason,
            atPhase = phase.takeIf { status != VisualStatus.COMPLETED },
            startedAt = startedAt, durationMs = (nanoTime() - start) / NANOS_PER_MILLI,
            thermalStart = thermalStart, thermalEnd = thermal.level(), charging = charging,
            peakRssMb = memory.peakRssMb(), peakRssResetSupported = resetSupported,
            metrics = metrics, answers = answers, vectors = vectors,
        )

        /** Waits for a cool device; false when the user declines measuring while SEVERE or worse. */
        suspend fun coolDown(confirmHot: suspend (ThermalLevel) -> Boolean): Boolean {
            progress("THERMAL")
            val level = thermal.awaitCool()
            return !level.needsConfirmation() || confirmHot(level)
        }
    }

    /**
     * Runs and records [body] for [run]: a cancellation records the scenario as cancelled before propagating;
     * any other error ends it, GPU problems as not available. Engines are released before and after (M6).
     */
    private suspend fun scenario(
        session: Session,
        run: ScenarioRun,
        release: Boolean = true,
        body: suspend ScenarioRun.() -> VisualScenario,
    ): VisualScenario {
        if (release) coordinator.releaseAll()
        val result = try {
            run.body()
        } catch (e: CancellationException) {
            withContext(NonCancellable) {
                session.record(run.finish(VisualStatus.CANCELLED))
                if (release) coordinator.releaseAll()
            }
            throw e
        } catch (e: Throwable) {
            logger.w(TAG, e) { "visual scenario ${run.label} failed in ${run.phase}" }
            val status = if (run.backend == ComputeBackend.GPU) VisualStatus.NOT_AVAILABLE else VisualStatus.FAILED
            run.finish(status, reason = e.describe())
        } finally {
            if (release) withContext(NonCancellable) { coordinator.releaseAll() }
        }
        session.record(result)
        return result
    }

    private suspend fun skip(session: Session, run: ScenarioRun, status: VisualStatus, reason: String) {
        session.record(run.finish(status, reason))
    }

    // GEMMA_INFO -----------------------------------------------------------------------------------------

    private suspend fun gemmaInfo(session: Session) {
        listOf(ModelId.GEMMA4_E2B, ModelId.EG2_440M).forEach { id ->
            val run = ScenarioRun(VisualKind.GEMMA_INFO, id, null)
            scenario(session, run) {
                val path = repository.pathOf(id) ?: return@scenario finish(VisualStatus.NOT_AVAILABLE, "model not verified")
                val info = withContext(dispatchers.io) { visionFactory.modelInfo(path) }
                if (id == ModelId.GEMMA4_E2B) session.gemmaInfo = info else session.embedderInfo = info
                finish(VisualStatus.COMPLETED)
            }
        }
    }

    private suspend fun infoOf(session: Session, id: ModelId, path: File): VisionModelInfo {
        val known = if (id == ModelId.GEMMA4_E2B) session.gemmaInfo else session.embedderInfo
        return known ?: withContext(dispatchers.io) { visionFactory.modelInfo(path) }.also {
            if (id == ModelId.GEMMA4_E2B) session.gemmaInfo = it else session.embedderInfo = it
        }
    }

    // EMBED_TEXT -----------------------------------------------------------------------------------------

    private suspend fun embedText(session: Session, id: ModelId, confirmHot: suspend (ThermalLevel) -> Boolean) {
        val run = ScenarioRun(VisualKind.EMBED_TEXT, id, ComputeBackend.CPU)
        val path = repository.pathOf(id) ?: return skip(session, run, VisualStatus.NOT_AVAILABLE, "model not verified")
        val texts = textsSource.load()
        scenario(session, run) {
            if (!coolDown(confirmHot)) return@scenario finish(VisualStatus.CANCELLED, "thermal")
            val key = EngineKey(id, ComputeBackend.CPU)
            val create = suspend { textFactory.embedder(file(id), path, ComputeBackend.CPU).initializeOrClose { it.initialize() } }
            val (cold, warm) = initTimes(this, key, create)
            coordinator.withEngine(key, create) { embedder ->
                withContext(dispatchers.embeddings) {
                    val protocol = VisualProtocol.EMBEDDING
                    val queries = measureLoop(this@scenario, "QUERY", protocol) { i -> embedder.embedQuery(texts.queries[i % texts.queries.size]) }
                    val documents = measureLoop(this@scenario, "DOCUMENT", protocol) { embedder.embedDocument(texts.measuredDocument, texts.documentHeader) }
                    val vector = embedder.embedQuery(texts.queries.first())
                    finish(
                        VisualStatus.COMPLETED,
                        metrics = VisualMetrics(
                            initColdMs = cold, initWarmMs = warm, samples = queries.size,
                            p50Ms = p(queries, P50), p95Ms = p(queries, P95),
                            documentP50Ms = p(documents, P50), documentP95Ms = p(documents, P95),
                            dimensions = listOf(DimensionCheck(vector.size, EmbeddingMath.l2Norm(vector))),
                        ),
                    )
                }
            }
        }
    }

    // EMBED_IMAGE and PARITY ------------------------------------------------------------------------------

    private suspend fun embedImage(session: Session, tokens: Int, backend: ComputeBackend, confirmHot: suspend (ThermalLevel) -> Boolean) {
        val run = ScenarioRun(VisualKind.EMBED_IMAGE, ModelId.EG2_440M, backend, VisualParams(tokensPerImage = tokens))
        val path = repository.pathOf(ModelId.EG2_440M) ?: return skip(session, run, VisualStatus.NOT_AVAILABLE, "model not verified")
        val images = session.deviceSet.images
        if (images.isEmpty()) return skip(session, run, VisualStatus.NOT_AVAILABLE, "device set has no images")
        val info = infoOf(session, ModelId.EG2_440M, path)
        if (backend !in info.visionBackends) return skip(session, run, VisualStatus.NOT_AVAILABLE, "vision backend not declared by the model")
        if (info.visionSignatures.isNotEmpty() && tokens !in info.visionSignatures) {
            return skip(session, run, VisualStatus.NOT_SUPPORTED, "the model offers ${info.visionSignatures} tokens per image")
        }
        scenario(session, run) {
            if (!coolDown(confirmHot)) return@scenario finish(VisualStatus.CANCELLED, "thermal")
            val key = EngineKey(ModelId.EG2_440M, backend, EngineKind.IMAGE_EMBEDDER)
            val create = suspend { visionFactory.imageEmbedder(file(ModelId.EG2_440M), path, backend, tokens).initializeOrClose { it.initialize() } }
            val (cold, warm) = initTimes(this, key, create)
            val bytes = withContext(dispatchers.io) { images.map { it.second.readBytes() } }
            coordinator.withEngine(key, create) { embedder ->
                withContext(dispatchers.embeddings) {
                    val times = measureLoop(this@scenario, "IMAGE", VisualProtocol.EMBEDDING) { i -> embedder.embedImage(bytes[i % bytes.size]) }
                    val raw = embedder.embedImage(bytes.first())
                    val checks = VisualProtocol.PAGE_DIMENSIONS.map { dims ->
                        DimensionCheck(dims, EmbeddingMath.l2Norm(EmbeddingMath.truncateAndNormalize(raw, dims)))
                    }
                    if (raw.size < VisualProtocol.PAGE_DIMENSIONS.max() || checks.any { abs(it.norm - 1.0) > NORM_TOLERANCE }) {
                        return@withContext finish(VisualStatus.FAILED, "vector check failed: raw=${raw.size} $checks")
                    }
                    finish(
                        VisualStatus.COMPLETED,
                        metrics = VisualMetrics(initColdMs = cold, initWarmMs = warm, samples = times.size, p50Ms = p(times, P50),
                            p95Ms = p(times, P95), dimensions = listOf(DimensionCheck(raw.size, EmbeddingMath.l2Norm(raw))) + checks),
                    )
                }
            }
        }
    }

    private suspend fun parity(session: Session, tokens: Int, confirmHot: suspend (ThermalLevel) -> Boolean) {
        val set = session.deviceSet
        val run = ScenarioRun(VisualKind.PARITY, ModelId.EG2_440M, ComputeBackend.CPU, VisualParams(tokensPerImage = tokens, px = set.parityPx))
        val path = repository.pathOf(ModelId.EG2_440M) ?: return skip(session, run, VisualStatus.NOT_AVAILABLE, "model not verified")
        if (set.images.isEmpty() && set.pdfs.none { it.pages.isNotEmpty() }) {
            return skip(session, run, VisualStatus.NOT_AVAILABLE, "device set has no parity files")
        }
        scenario(session, run) {
            if (!coolDown(confirmHot)) return@scenario finish(VisualStatus.CANCELLED, "thermal")
            val key = EngineKey(ModelId.EG2_440M, ComputeBackend.CPU, EngineKind.IMAGE_EMBEDDER)
            val create = suspend {
                visionFactory.imageEmbedder(file(ModelId.EG2_440M), path, ComputeBackend.CPU, tokens).initializeOrClose { it.initialize() }
            }
            val inputs = withContext(dispatchers.io) {
                set.images.map { (name, image) -> Triple(name, set.shaOf[name], image.readBytes()) } +
                    set.pdfs.flatMap { pdf ->
                        pdf.pages.map { page ->
                            val px = checkNotNull(set.parityPx) { "device set has no parity_px" }
                            // JPEG, as the desktop encodes the same page for its vector (research R13).
                            val rendered = rasterizer.renderPage(pdf.file, page, px, ImageFormat.JPEG, VisualProtocol.QUALITY)
                            Triple("pdf:${pdf.path}:$page:$px", null, rendered.bytes)
                        }
                    }
            }
            coordinator.withEngine(key, create) { embedder ->
                withContext(dispatchers.embeddings) {
                    val vectors = inputs.mapIndexed { i, (id, sha, bytes) ->
                        progress("VECTORS", i + 1, inputs.size)
                        currentCoroutineContext().ensureActive()
                        VectorEntry(id, sha, tokens, embedder.embedImage(bytes).toList())
                    }
                    finish(VisualStatus.COMPLETED, metrics = VisualMetrics(samples = vectors.size), vectors = vectors)
                }
            }
        }
    }

    // RASTER, IMAGE_DECODE and PROJECTION -----------------------------------------------------------------

    private suspend fun raster(session: Session, confirmHot: suspend (ThermalLevel) -> Boolean) {
        val pdfs = session.deviceSet.pdfs
        if (pdfs.isEmpty()) return skip(session, ScenarioRun(VisualKind.RASTER, null, null), VisualStatus.NOT_AVAILABLE, "device set has no PDF")
        pdfs.forEach { pdf ->
            VisualProtocol.RESOLUTIONS.forEach { px ->
                VisualProtocol.RASTER_FORMATS.forEach { format ->
                    val run = ScenarioRun(VisualKind.RASTER, null, null, VisualParams(px = px, format = format, document = pdf.path))
                    scenario(session, run) {
                        if (!coolDown(confirmHot)) return@scenario finish(VisualStatus.CANCELLED, "thermal")
                        withContext(dispatchers.io) {
                            val pages = rasterizer.pageCount(pdf.file).coerceAtMost(RASTER_PAGES)
                            var last: EncodedImage? = null
                            val sizes = mutableListOf<Double>()
                            val times = measureLoop(this@scenario, "RENDER", VisualProtocol.EMBEDDING) { i ->
                                last = rasterizer.renderPage(pdf.file, i % pages + 1, px, format, VisualProtocol.QUALITY)
                                    .also { sizes += it.bytes.size.toDouble() }
                            }
                            val image = checkNotNull(last)
                            finish(VisualStatus.COMPLETED, metrics = VisualMetrics(samples = times.size, p50Ms = p(times, P50),
                                p95Ms = p(times, P95), bytesP50 = p(sizes, P50).toLong(), widthPx = image.widthPx, heightPx = image.heightPx))
                        }
                    }
                }
            }
        }
    }

    private suspend fun imageDecode(session: Session, confirmHot: suspend (ThermalLevel) -> Boolean) {
        val images = session.deviceSet.decode
        if (images.isEmpty()) {
            return skip(session, ScenarioRun(VisualKind.IMAGE_DECODE, null, null), VisualStatus.NOT_AVAILABLE, "device set has no decode images")
        }
        images.forEach { (name, image) ->
            VisualProtocol.RESOLUTIONS.forEach { px ->
                val run = ScenarioRun(VisualKind.IMAGE_DECODE, null, null, VisualParams(px = px, format = ImageFormat.JPEG, document = name))
                scenario(session, run) {
                    if (!coolDown(confirmHot)) return@scenario finish(VisualStatus.CANCELLED, "thermal")
                    withContext(dispatchers.io) {
                        var last: EncodedImage? = null
                        val sizes = mutableListOf<Double>()
                        val times = measureLoop(this@scenario, "DECODE", VisualProtocol.EMBEDDING) {
                            last = rasterizer.scaleImage(image, px, ImageFormat.JPEG, VisualProtocol.QUALITY).also { sizes += it.bytes.size.toDouble() }
                        }
                        val scaled = checkNotNull(last)
                        finish(VisualStatus.COMPLETED, metrics = VisualMetrics(samples = times.size, p50Ms = p(times, P50), p95Ms = p(times, P95),
                            bytesP50 = p(sizes, P50).toLong(), widthPx = scaled.widthPx, heightPx = scaled.heightPx))
                    }
                }
            }
        }
    }

    private suspend fun projection(session: Session, confirmHot: suspend (ThermalLevel) -> Boolean) {
        val set = session.deviceSet
        val tokens = set.chosenTokens ?: VisualProtocol.EMBEDDER_TOKENS.last()
        val px = set.parityPx ?: VisualProtocol.RESOLUTIONS[1]
        // JPEG: LiteRT-LM does not decode WebP input images (research R6).
        val run = ScenarioRun(VisualKind.PROJECTION, ModelId.EG2_440M, ComputeBackend.CPU,
            VisualParams(tokensPerImage = tokens, px = px, format = ImageFormat.JPEG))
        val path = repository.pathOf(ModelId.EG2_440M) ?: return skip(session, run, VisualStatus.NOT_AVAILABLE, "model not verified")
        val pdfs = set.pdfs.filter { it.role == "text" || it.role == "scanned" }.ifEmpty { set.pdfs }
        if (pdfs.isEmpty()) return skip(session, run, VisualStatus.NOT_AVAILABLE, "device set has no PDF")
        scenario(session, run) {
            if (!coolDown(confirmHot)) return@scenario finish(VisualStatus.CANCELLED, "thermal")
            val key = EngineKey(ModelId.EG2_440M, ComputeBackend.CPU, EngineKind.IMAGE_EMBEDDER)
            val create = suspend {
                visionFactory.imageEmbedder(file(ModelId.EG2_440M), path, ComputeBackend.CPU, tokens).initializeOrClose { it.initialize() }
            }
            val pages = withContext(dispatchers.io) { pdfs.flatMap { pdf -> (1..rasterizer.pageCount(pdf.file)).map { pdf.file to it } } }
            coordinator.withEngine(key, create) { embedder ->
                var middle: ThermalLevel? = null
                val times = List(VisualProtocol.PROJECTION_PAGES) { i ->
                    progress("PAGES", i + 1, VisualProtocol.PROJECTION_PAGES)
                    currentCoroutineContext().ensureActive()
                    if (i == VisualProtocol.PROJECTION_PAGES / 2) middle = thermal.level()
                    val (pdf, page) = pages[i % pages.size]
                    val start = nanoTime()
                    val image = withContext(dispatchers.io) { rasterizer.renderPage(pdf, page, px, ImageFormat.JPEG, VisualProtocol.QUALITY) }
                    withContext(dispatchers.embeddings) { embedder.embedImage(image.bytes) }
                    (nanoTime() - start) / NANOS_PER_MILLI_D
                }
                val target = VisualProtocol.PROJECTION_TARGET
                finish(VisualStatus.COMPLETED, metrics = VisualMetrics(samples = times.size, p50Ms = p(times, P50), p95Ms = p(times, P95),
                    projectedPages = target, projectedP50S = p(times, P50) * target / MILLIS_PER_SECOND,
                    projectedP95S = p(times, P95) * target / MILLIS_PER_SECOND, thermalMiddle = middle))
            }
        }
    }

    // GEMMA_PAGES and READING ------------------------------------------------------------------------------

    private suspend fun gemmaPages(session: Session, candidates: List<String>, confirmHot: suspend (ThermalLevel) -> Boolean) {
        val gemma = ModelId.GEMMA4_E2B
        val path = repository.pathOf(gemma)
            ?: return skip(session, ScenarioRun(VisualKind.GEMMA_PAGES, gemma, ComputeBackend.GPU), VisualStatus.NOT_AVAILABLE, "model not verified")
        val contexts = session.deviceSet.contexts
        if (contexts.isEmpty()) {
            return skip(session, ScenarioRun(VisualKind.GEMMA_PAGES, gemma, ComputeBackend.GPU), VisualStatus.NOT_AVAILABLE, "device set has no contexts")
        }
        val info = infoOf(session, gemma, path)
        if (candidates.isEmpty()) {
            gemmaLoad(session, path, info, confirmHot)
            visionOnGpu(session, path, info, contexts.first(), confirmHot)
            gemmaBatch(session, path, info, VisualProtocol.stageA(), stage = "A", confirmHot) { combination, generator ->
                stageA(combination, generator, session.deviceSet, contexts.first())
            }
        } else {
            gemmaBatch(session, path, info, candidates.map(PagesCombination::parse), stage = "B", confirmHot) { combination, generator ->
                stageB(combination, generator, session.deviceSet, contexts)
            }
        }
    }

    /** FR-005: cold and cached load with the vision encoder, against the same file without it. */
    private suspend fun gemmaLoad(session: Session, path: File, info: VisionModelInfo, confirmHot: suspend (ThermalLevel) -> Boolean) {
        val gemma = ModelId.GEMMA4_E2B
        listOf(true, false).forEach { vision ->
            val label = if (vision) "carga-vision" else "carga-texto"
            val run = ScenarioRun(VisualKind.GEMMA_PAGES, gemma, ComputeBackend.GPU,
                VisualParams(stage = "load", combination = label, visionBackend = ComputeBackend.CPU.takeIf { vision }))
            if (vision && !info.vision) return@forEach skip(session, run, VisualStatus.NOT_SUPPORTED, "the file declares no vision")
            scenario(session, run) {
                if (!coolDown(confirmHot)) return@scenario finish(VisualStatus.CANCELLED, "thermal")
                val key = EngineKey(gemma, ComputeBackend.GPU)
                val (cold, warm) = if (vision) {
                    initTimes(this, key) { visionGenerator(path, ComputeBackend.CPU) }
                } else {
                    initTimes(this, key) {
                        textFactory.generator(file(gemma), path, ComputeBackend.GPU, textConfig()).initializeOrClose { it.initialize() }
                    }
                }
                finish(VisualStatus.COMPLETED, metrics = VisualMetrics(initColdMs = cold, initWarmMs = warm))
            }
        }
    }

    /** FR-007: the vision encoder on the GPU only when the file declares it; never a second GPU engine. */
    private suspend fun visionOnGpu(
        session: Session,
        path: File,
        info: VisionModelInfo,
        context: ContextItem,
        confirmHot: suspend (ThermalLevel) -> Boolean,
    ) {
        val combination = PagesCombination(1, VisualProtocol.TILE_BUDGET, tiles = false, withText = false)
        val run = ScenarioRun(VisualKind.GEMMA_PAGES, ModelId.GEMMA4_E2B, ComputeBackend.GPU,
            VisualParams(stage = "A", combination = combination.key, visionBackend = ComputeBackend.GPU))
        if (ComputeBackend.GPU !in info.visionBackends) {
            return skip(session, run, VisualStatus.NOT_AVAILABLE, "vision backend GPU not declared by the model")
        }
        scenario(session, run) {
            if (!coolDown(confirmHot)) return@scenario finish(VisualStatus.CANCELLED, "thermal")
            val key = EngineKey(ModelId.GEMMA4_E2B, ComputeBackend.GPU)
            coordinator.withEngine(key, { visionGenerator(path, ComputeBackend.GPU) }) { generator ->
                stageA(combination, generator, session.deviceSet, context)
            }
        }
    }

    /**
     * One Gemma engine with the vision encoder for a whole batch of combinations: the budget is per call, so
     * the configuration does not change and the engine is loaded once; each combination is its own scenario.
     */
    private suspend fun gemmaBatch(
        session: Session,
        path: File,
        info: VisionModelInfo,
        combinations: List<PagesCombination>,
        stage: String,
        confirmHot: suspend (ThermalLevel) -> Boolean,
        measure: suspend ScenarioRun.(PagesCombination, VisionGenerator) -> VisualScenario,
    ) {
        val gemma = ModelId.GEMMA4_E2B
        coordinator.releaseAll()
        val key = EngineKey(gemma, ComputeBackend.GPU)
        try {
            combinations.forEach { combination ->
                val run = ScenarioRun(VisualKind.GEMMA_PAGES, gemma, ComputeBackend.GPU,
                    VisualParams(stage = stage, combination = combination.key, visionBackend = ComputeBackend.CPU))
                val unsupported = unsupported(info, combination)
                if (unsupported != null) return@forEach skip(session, run, VisualStatus.NOT_SUPPORTED, unsupported)
                scenario(session, run, release = false) {
                    if (!coolDown(confirmHot)) return@scenario finish(VisualStatus.CANCELLED, "thermal")
                    coordinator.withEngine(key, { visionGenerator(path, ComputeBackend.CPU) }) { generator -> measure(combination, generator) }
                }
            }
        } finally {
            withContext(NonCancellable) { coordinator.releaseAll() }
        }
    }

    private fun unsupported(info: VisionModelInfo, combination: PagesCombination): String? = when {
        combination.pages == 0 -> null
        !info.vision -> "the file declares no vision"
        combination.budget != null && combination.budget > info.maxVisionTokenBudget ->
            "budget ${combination.budget} over the file's maximum ${info.maxVisionTokenBudget}"
        combination.budget != null && info.visionSignatures.isNotEmpty() && combination.budget !in info.visionSignatures ->
            "the file offers ${info.visionSignatures} visual tokens"
        combination.images > VisualProtocol.MAX_NUM_IMAGES -> "more than ${VisualProtocol.MAX_NUM_IMAGES} images"
        else -> null
    }

    /** Stage A: one prompt, 1 warm-up and 3 measured answers (research R4). */
    private suspend fun ScenarioRun.stageA(
        combination: PagesCombination,
        generator: VisionGenerator,
        set: DeviceSet,
        context: ContextItem,
    ): VisualScenario {
        val raw = context.layouts[combination.layout] ?: return finish(VisualStatus.NOT_AVAILABLE, "layout ${combination.layout} missing")
        val parts = withContext(dispatchers.io) { set.parts(raw) } ?: return finish(VisualStatus.NOT_AVAILABLE, "an image of the layout is not verified")
        val protocol = VisualProtocol.GENERATION
        repeat(protocol.warmup) { i ->
            progress("WARMUP", i + 1, protocol.warmup)
            generator.generate(parts, combination.budget, checkNotNull(protocol.maxOutputTokens))
        }
        val runs = List(protocol.measured) { i ->
            progress("ANSWER", i + 1, protocol.measured)
            currentCoroutineContext().ensureActive()
            generator.generate(parts, combination.budget, checkNotNull(protocol.maxOutputTokens))
        }
        return finish(VisualStatus.COMPLETED, metrics = generationMetrics(runs),
            answers = listOf(AnswerEntry(context.id, runs.first().text, runs.first().firstTokenMs, runs.first().prefillTokens)))
    }

    /** Stage B: 1 warm-up, then the 20 questions once each; their p95 decides (D-075). */
    private suspend fun ScenarioRun.stageB(
        combination: PagesCombination,
        generator: VisionGenerator,
        set: DeviceSet,
        contexts: List<ContextItem>,
    ): VisualScenario {
        val prepared = withContext(dispatchers.io) {
            contexts.take(VisualProtocol.STAGE_B_QUESTIONS).mapNotNull { context ->
                context.layouts[combination.layout]?.let(set::parts)?.let { context.id to it }
            }
        }
        if (prepared.size < VisualProtocol.STAGE_B_QUESTIONS) {
            return finish(VisualStatus.NOT_AVAILABLE, "only ${prepared.size} of ${VisualProtocol.STAGE_B_QUESTIONS} questions have layout ${combination.layout}")
        }
        val maxOutput = checkNotNull(VisualProtocol.GENERATION.maxOutputTokens)
        progress("WARMUP", 1, 1)
        generator.generate(prepared.first().second, combination.budget, maxOutput)
        val runs = prepared.mapIndexed { i, (id, parts) ->
            progress("QUESTION", i + 1, prepared.size)
            currentCoroutineContext().ensureActive()
            id to generator.generate(parts, combination.budget, maxOutput)
        }
        return finish(VisualStatus.COMPLETED, metrics = generationMetrics(runs.map { it.second }),
            answers = runs.map { (id, run) -> AnswerEntry(id, run.text, run.firstTokenMs, run.prefillTokens) })
    }

    private suspend fun reading(session: Session, confirmHot: suspend (ThermalLevel) -> Boolean) {
        val gemma = ModelId.GEMMA4_E2B
        val run = ScenarioRun(VisualKind.READING, gemma, ComputeBackend.GPU,
            VisualParams(combination = "lectura-b${VisualProtocol.TILE_BUDGET}", visionBackend = ComputeBackend.CPU))
        val path = repository.pathOf(gemma) ?: return skip(session, run, VisualStatus.NOT_AVAILABLE, "model not verified")
        val items = session.deviceSet.reading
        if (items.isEmpty()) return skip(session, run, VisualStatus.NOT_AVAILABLE, "device set has no reading pages")
        if (!infoOf(session, gemma, path).vision) return skip(session, run, VisualStatus.NOT_SUPPORTED, "the file declares no vision")
        scenario(session, run) {
            if (!coolDown(confirmHot)) return@scenario finish(VisualStatus.CANCELLED, "thermal")
            val prepared = withContext(dispatchers.io) { items.mapNotNull { item -> session.deviceSet.parts(item.parts)?.let { item.id to it } } }
            coordinator.withEngine(EngineKey(gemma, ComputeBackend.GPU), { visionGenerator(path, ComputeBackend.CPU) }) { generator ->
                val maxOutput = checkNotNull(VisualProtocol.GENERATION.maxOutputTokens)
                val runs = prepared.mapIndexed { i, (id, parts) ->
                    progress("PAGE", i + 1, prepared.size)
                    currentCoroutineContext().ensureActive()
                    id to generator.generate(parts, VisualProtocol.TILE_BUDGET, maxOutput)
                }
                finish(VisualStatus.COMPLETED, metrics = generationMetrics(runs.map { it.second }),
                    answers = runs.map { (id, r) -> AnswerEntry(id, r.text, r.firstTokenMs, r.prefillTokens) })
            }
        }
    }

    private suspend fun visionGenerator(path: File, visionBackend: ComputeBackend): VisionGenerator =
        visionFactory.generator(file(ModelId.GEMMA4_E2B), path, ComputeBackend.GPU, visionBackend, VisualProtocol.MAX_NUM_IMAGES)
            .initializeOrClose { it.initialize() }

    private fun textConfig() = GenerationConfig(
        maxNumTokens = MeasurementProtocol.GENERATION_MAX_NUM_TOKENS,
        maxOutputTokens = checkNotNull(VisualProtocol.GENERATION.maxOutputTokens),
        greedy = true,
    )

    private fun generationMetrics(runs: List<VisionGeneration>): VisualMetrics {
        val first = runs.map { it.firstTokenMs.toDouble() }
        return VisualMetrics(
            samples = runs.size,
            p50Ms = p(first, P50),
            p95Ms = p(first, P95),
            totalP50Ms = p(runs.map { it.totalMs.toDouble() }, P50),
            prefillTokens = runs.mapNotNull { it.prefillTokens }.takeIf { it.isNotEmpty() }?.let { p(it.map(Int::toDouble), P50).toInt() },
            prefillTokensPerSecond = runs.mapNotNull { it.prefillTokensPerSecond }.takeIf { it.isNotEmpty() }?.let { p(it, P50) },
            decodeTokensPerSecond = runs.mapNotNull { it.decodeTokensPerSecond }.takeIf { it.isNotEmpty() }?.let { p(it, P50) },
        )
    }

    // Helpers ------------------------------------------------------------------------------------------------

    /** Cold load (compiled-kernel cache cleared) and cached load, each on a freshly created engine. */
    private suspend fun <T : ManagedEngine> initTimes(run: ScenarioRun, key: EngineKey, create: suspend () -> T): Pair<Long, Long> {
        run.progress("INIT_COLD")
        textFactory.clearCompiledCache()
        val cold = timedMs { coordinator.withEngine(key, create) { } }
        coordinator.release(key)
        run.progress("INIT_WARM")
        val warm = timedMs { coordinator.withEngine(key, create) { } }
        return cold to warm
    }

    private suspend fun measureLoop(
        run: ScenarioRun,
        phase: String,
        protocol: MeasurementProtocol,
        call: suspend (Int) -> Unit,
    ): List<Double> {
        repeat(protocol.warmup) { i ->
            run.progress("${phase}_WARMUP", i + 1, protocol.warmup)
            call(i)
        }
        return List(protocol.measured) { i ->
            run.progress(phase, i + 1, protocol.measured)
            currentCoroutineContext().ensureActive()
            val start = nanoTime()
            call(protocol.warmup + i)
            (nanoTime() - start) / NANOS_PER_MILLI_D
        }
    }

    private inline fun timedMs(block: () -> Unit): Long {
        val start = nanoTime()
        block()
        return (nanoTime() - start) / NANOS_PER_MILLI
    }

    private fun p(values: List<Double>, percentile: Int) = Percentiles.of(values, percentile)

    private companion object {
        const val TAG = "MicroRAG"
        const val P50 = 50
        const val P95 = 95
        const val NORM_TOLERANCE = 1e-5
        const val RASTER_PAGES = 10
        const val NANOS_PER_MILLI = 1_000_000L
        const val NANOS_PER_MILLI_D = 1_000_000.0
        const val MILLIS_PER_SECOND = 1000.0
    }
}

private fun stateName(state: ModelFileState?): String = when (state) {
    null, ModelFileState.Missing -> "MISSING"
    is ModelFileState.Unverified -> "UNVERIFIED"
    is ModelFileState.Verifying -> "VERIFYING"
    is ModelFileState.Verified -> "VERIFIED"
    is ModelFileState.Mismatch -> "MISMATCH"
    is ModelFileState.Failed -> "FAILED"
}

private fun Throwable.describe(): String = "${this::class.simpleName}: ${message.orEmpty()}".take(300)
