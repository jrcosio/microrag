package com.lisdatasolutions.microrag.debug.feasibility

import com.lisdatasolutions.microrag.core.dispatchers.AppDispatchers
import com.lisdatasolutions.microrag.core.logging.AppLogger
import com.lisdatasolutions.microrag.data.models.ModelFileRepository
import com.lisdatasolutions.microrag.debug.feasibility.report.CompatibilityMetrics
import com.lisdatasolutions.microrag.debug.feasibility.report.DeviceSnapshot
import com.lisdatasolutions.microrag.debug.feasibility.report.EmbeddingMetrics
import com.lisdatasolutions.microrag.debug.feasibility.report.FeasibilityReport
import com.lisdatasolutions.microrag.debug.feasibility.report.GenerationMetrics
import com.lisdatasolutions.microrag.debug.feasibility.report.GenerationRun
import com.lisdatasolutions.microrag.debug.feasibility.report.ModelEntry
import com.lisdatasolutions.microrag.debug.feasibility.report.ReportSink
import com.lisdatasolutions.microrag.debug.feasibility.report.ResultStatus
import com.lisdatasolutions.microrag.debug.feasibility.report.ScenarioKind
import com.lisdatasolutions.microrag.debug.feasibility.report.ScenarioResult
import com.lisdatasolutions.microrag.debug.feasibility.report.exitCriteriaOf
import com.lisdatasolutions.microrag.domain.model.ComputeBackend
import com.lisdatasolutions.microrag.domain.model.ModelCatalog
import com.lisdatasolutions.microrag.domain.model.ModelFamily
import com.lisdatasolutions.microrag.domain.model.ModelFile
import com.lisdatasolutions.microrag.domain.model.ModelFileState
import com.lisdatasolutions.microrag.rag.runtime.EmbeddingMath
import com.lisdatasolutions.microrag.rag.runtime.EngineCoordinator
import com.lisdatasolutions.microrag.rag.runtime.EngineFactory
import com.lisdatasolutions.microrag.rag.runtime.EngineKey
import com.lisdatasolutions.microrag.rag.runtime.GenerationConfig
import com.lisdatasolutions.microrag.rag.runtime.GenerationEvent
import com.lisdatasolutions.microrag.rag.runtime.GenerationStats
import com.lisdatasolutions.microrag.rag.runtime.TextGenerator
import com.lisdatasolutions.microrag.rag.runtime.initializeOrClose
import java.io.File
import java.time.OffsetDateTime
import java.time.temporal.ChronoUnit
import kotlin.coroutines.cancellation.CancellationException
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

enum class SuiteBlock { EMBEDDINGS, COMPATIBILITY, GENERATION }

/** Where a scenario is; also stored as `atPhase` when a scenario does not complete. */
enum class SuitePhase {
    VERIFY, METADATA, THERMAL, INIT_COLD, INIT_WARM, QUERY_WARMUP, QUERY, DOCUMENT_WARMUP, DOCUMENT,
    COMPAT_CPU, COMPAT_GPU, GENERATION_WARMUP, GENERATION,
}

/** Real progress: [step] of [total] in [phase] of the scenario named by [label]. */
data class SuiteProgress(val label: String, val phase: SuitePhase, val step: Int, val total: Int)

/**
 * Runs the phase-0 measurements (plan, "Diseño de ejecución de la batería"). Shared by the debug
 * screen and the instrumented benchmark test. Only verified files are ever loaded (FR-005), one
 * engine at a time, and the report is rewritten after every result.
 */
class FeasibilitySuite(
    private val coordinator: EngineCoordinator,
    private val factory: EngineFactory,
    private val repository: ModelFileRepository,
    private val textsSource: FeasibilityTextsSource,
    private val thermal: ThermalMonitor,
    private val memory: MemoryProbe,
    private val device: DeviceInfoSource,
    private val reportSink: ReportSink,
    private val dispatchers: AppDispatchers,
    private val logger: AppLogger,
    private val nanoTime: () -> Long = System::nanoTime,
    private val now: () -> String = { OffsetDateTime.now().truncatedTo(ChronoUnit.SECONDS).toString() },
    private val catalog: List<ModelFile> = ModelCatalog.all,
) {

    private val runLock = Mutex()
    private val _progress = MutableStateFlow<SuiteProgress?>(null)
    private val _results = MutableStateFlow<List<ScenarioResult>>(emptyList())
    private val _lastReport = MutableStateFlow<File?>(null)

    val progress: StateFlow<SuiteProgress?> = _progress.asStateFlow()
    val results: StateFlow<List<ScenarioResult>> = _results.asStateFlow()
    val lastReport: StateFlow<File?> = _lastReport.asStateFlow()

    /**
     * Runs [blocks] in order. [confirmHot] is asked when the device is SEVERE or worse after the
     * cool-down wait (FR-015); declining cancels that scenario. Cancelling the caller stops the
     * session, records the scenario in progress as cancelled and still writes the report.
     */
    suspend fun run(
        blocks: Set<SuiteBlock>,
        confirmHot: suspend (ThermalLevel) -> Boolean,
    ): FeasibilityReport = runLock.withLock {
        withContext(dispatchers.default) {
            _results.value = emptyList()
            val session = Session(createdAt = now(), device = device.snapshot())
            factory.benchmarkStatsEnabled = true
            try {
                revalidate()
                val texts = textsSource.load()
                if (SuiteBlock.EMBEDDINGS in blocks) {
                    verified(ModelFamily.EMBEDDING).forEach { file ->
                        ComputeBackend.entries.forEach { backend ->
                            session.record(embedding(session, file, backend, texts, confirmHot))
                        }
                    }
                }
                if (SuiteBlock.COMPATIBILITY in blocks) {
                    verified(ModelFamily.EMBEDDING).forEach { file ->
                        session.record(compatibility(session, file, texts, confirmHot))
                    }
                }
                if (SuiteBlock.GENERATION in blocks) {
                    verified(ModelFamily.LLM).forEach { file ->
                        val gpu = generation(session, file, ComputeBackend.GPU, texts, confirmHot, fallbackReason = null)
                        session.record(gpu)
                        if (gpu.status == ResultStatus.FAILED || gpu.status == ResultStatus.NOT_AVAILABLE) {
                            val reason = gpu.reason ?: gpu.status.name
                            session.record(generation(session, file, ComputeBackend.CPU, texts, confirmHot, reason))
                        }
                    }
                }
            } finally {
                withContext(NonCancellable) {
                    coordinator.releaseAll()
                    factory.benchmarkStatsEnabled = false
                    _progress.value = null
                    session.writeReport()
                }
            }
            session.report()
        }
    }

    private inner class Session(val createdAt: String, val device: DeviceSnapshot) {

        suspend fun record(result: ScenarioResult) {
            _results.update { it + result }
            logger.i(TAG) { "result ${result.kind} ${result.model} ${result.backend}: ${result.status}" }
            writeReport()
        }

        suspend fun writeReport() {
            _lastReport.value = reportSink.write(report())
        }

        fun report(): FeasibilityReport {
            val states = repository.states.value
            return FeasibilityReport(
                createdAt = createdAt,
                device = device,
                models = catalog.map {
                    ModelEntry(it.id, it.fileName, it.sizeBytes, it.sha256, it.revision, stateName(states[it.id]))
                },
                results = _results.value,
                exitCriteria = exitCriteriaOf(_results.value),
            )
        }
    }

    /** FR-005: every file is hashed again before any measurement of this session. */
    private suspend fun revalidate() {
        repository.refresh()
        val present = catalog.filter {
            val state = repository.states.value[it.id]
            state is ModelFileState.Unverified || state is ModelFileState.Verified
        }
        present.forEachIndexed { index, file ->
            _progress.value = SuiteProgress(file.displayName, SuitePhase.VERIFY, index + 1, present.size)
            repository.verify(file.id)
        }
    }

    private fun verified(family: ModelFamily) =
        catalog.filter { it.family == family && repository.pathOf(it.id) != null }

    /** One scenario: its timing, thermal and memory context and how it ends. */
    private inner class ScenarioRun(
        val kind: ScenarioKind,
        val file: ModelFile,
        val backend: ComputeBackend?,
        val protocol: MeasurementProtocol,
    ) {
        private val startedAt = now()
        private val start = nanoTime()
        private val thermalStart = thermal.level()
        private val charging = device.isCharging()
        var phase = SuitePhase.METADATA
        var resetSupported = false
        val label = listOfNotNull(file.displayName, backend?.name).joinToString(" · ")

        fun progress(phase: SuitePhase, step: Int = 0, total: Int = 0) {
            this.phase = phase
            _progress.value = SuiteProgress(label, phase, step, total)
        }

        fun finish(
            status: ResultStatus,
            reason: String? = null,
            embedding: EmbeddingMetrics? = null,
            compatibility: CompatibilityMetrics? = null,
            generation: GenerationMetrics? = null,
        ) = ScenarioResult(
            kind = kind,
            model = file.id,
            backend = backend,
            status = status,
            reason = reason,
            atPhase = phase.name.takeIf { status != ResultStatus.COMPLETED },
            protocol = protocol,
            startedAt = startedAt,
            durationMs = (nanoTime() - start) / NANOS_PER_MILLI,
            thermalStart = thermalStart,
            thermalEnd = thermal.level(),
            charging = charging,
            peakRssMb = memory.peakRssMb(),
            peakRssResetSupported = resetSupported,
            embedding = embedding,
            compatibility = compatibility,
            generation = generation,
        )

        /** Waits for a cool device; false when the user declines measuring while SEVERE or worse. */
        suspend fun coolDown(confirmHot: suspend (ThermalLevel) -> Boolean): Boolean {
            progress(SuitePhase.THERMAL)
            val level = thermal.awaitCool()
            return !level.needsConfirmation() || confirmHot(level)
        }
    }

    /**
     * Runs [body] for [run]: a cancellation records the scenario as cancelled before propagating,
     * any other error ends it (GPU problems as not available, as the spec requires).
     */
    private suspend fun scenario(
        session: Session,
        run: ScenarioRun,
        body: suspend ScenarioRun.() -> ScenarioResult,
    ): ScenarioResult = try {
        run.body()
    } catch (e: CancellationException) {
        withContext(NonCancellable) { session.record(run.finish(ResultStatus.CANCELLED)) }
        throw e
    } catch (e: Throwable) {
        logger.w(TAG, e) { "scenario ${run.label} failed in ${run.phase}" }
        val status = if (run.backend == ComputeBackend.GPU) ResultStatus.NOT_AVAILABLE else ResultStatus.FAILED
        run.finish(status, reason = e.describe())
    } finally {
        withContext(NonCancellable) { coordinator.releaseAll() }
    }

    private suspend fun embedding(
        session: Session,
        file: ModelFile,
        backend: ComputeBackend,
        texts: FeasibilityTexts,
        confirmHot: suspend (ThermalLevel) -> Boolean,
    ): ScenarioResult = scenario(session, ScenarioRun(ScenarioKind.EMBEDDING, file, backend, MeasurementProtocol.EMBEDDING)) {
        val path = repository.pathOf(file.id) ?: return@scenario finish(ResultStatus.NOT_AVAILABLE, "model not verified")
        progress(SuitePhase.METADATA)
        val metadata = withContext(dispatchers.io) { factory.metadata(path) }
        if (backend !in metadata.supportedBackends) {
            return@scenario finish(ResultStatus.NOT_AVAILABLE, "backend not declared by the model")
        }
        if (!coolDown(confirmHot)) return@scenario finish(ResultStatus.CANCELLED, "thermal")
        coordinator.releaseAll()
        resetSupported = memory.resetPeak()
        val key = EngineKey(file.id, backend)
        val create = suspend { factory.embedder(file, path, backend).initializeOrClose { it.initialize() } }
        val (coldMs, warmMs) = initTimes(this, key, create)

        coordinator.withEngine(key, create) { embedder ->
            withContext(dispatchers.embeddings) {
                val queries = measureLoop(this@scenario, SuitePhase.QUERY_WARMUP, SuitePhase.QUERY, protocol) { i ->
                    embedder.embedQuery(texts.queries[i % texts.queries.size])
                }
                val documents = measureLoop(this@scenario, SuitePhase.DOCUMENT_WARMUP, SuitePhase.DOCUMENT, protocol) {
                    embedder.embedDocument(texts.measuredDocument, texts.documentHeader)
                }
                val vector = embedder.embedQuery(texts.queries.first())
                val norm = EmbeddingMath.l2Norm(vector)
                if (vector.size != EmbeddingMath.DIMENSIONS || kotlin.math.abs(norm - 1.0) > NORM_TOLERANCE) {
                    return@withContext finish(ResultStatus.FAILED, "vector check failed: dims=${vector.size} norm=$norm")
                }
                finish(
                    ResultStatus.COMPLETED,
                    embedding = EmbeddingMetrics(
                        initColdMs = coldMs,
                        initWarmMs = warmMs,
                        queryP50Ms = Percentiles.of(queries, P50),
                        queryP95Ms = Percentiles.of(queries, P95),
                        documentP50Ms = Percentiles.of(documents, P50),
                        documentP95Ms = Percentiles.of(documents, P95),
                        rawDims = embedder.rawDimensions ?: vector.size,
                        outputDims = vector.size,
                        l2Norm = norm,
                        declaredMaxContextTokens = metadata.maxContextTokens,
                        supportedBackends = metadata.supportedBackends,
                    ),
                )
            }
        }
    }

    private suspend fun compatibility(
        session: Session,
        file: ModelFile,
        texts: FeasibilityTexts,
        confirmHot: suspend (ThermalLevel) -> Boolean,
    ): ScenarioResult = scenario(session, ScenarioRun(ScenarioKind.COMPATIBILITY, file, null, MeasurementProtocol.COMPATIBILITY)) {
        val path = repository.pathOf(file.id) ?: return@scenario finish(ResultStatus.NOT_AVAILABLE, "model not verified")
        val incomplete = _results.value.firstOrNull {
            it.kind == ScenarioKind.EMBEDDING && it.model == file.id && it.status != ResultStatus.COMPLETED
        }
        if (incomplete != null) {
            return@scenario finish(ResultStatus.NOT_AVAILABLE, "${incomplete.backend} embedding did not complete")
        }
        progress(SuitePhase.METADATA)
        val metadata = withContext(dispatchers.io) { factory.metadata(path) }
        if (!metadata.supportedBackends.containsAll(ComputeBackend.entries)) {
            return@scenario finish(ResultStatus.NOT_AVAILABLE, "model does not declare both backends")
        }
        if (!coolDown(confirmHot)) return@scenario finish(ResultStatus.CANCELLED, "thermal")
        coordinator.releaseAll()
        resetSupported = memory.resetPeak()
        val cpu = vectors(this, file, path, ComputeBackend.CPU, SuitePhase.COMPAT_CPU, texts)
        coordinator.releaseAll()
        val gpu = try {
            vectors(this, file, path, ComputeBackend.GPU, SuitePhase.COMPAT_GPU, texts)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            return@scenario finish(ResultStatus.NOT_AVAILABLE, "GPU: ${e.describe()}")
        }
        val cosines = cpu.zip(gpu) { a, b -> EmbeddingMath.cosine(a, b) }
        val min = cosines.min()
        finish(
            ResultStatus.COMPLETED,
            compatibility = CompatibilityMetrics(
                textCount = cosines.size,
                cosineMin = min,
                cosineMean = cosines.average(),
                compatible = min >= MeasurementProtocol.COMPATIBILITY_MIN_COSINE,
            ),
        )
    }

    private suspend fun vectors(
        run: ScenarioRun,
        file: ModelFile,
        path: File,
        backend: ComputeBackend,
        phase: SuitePhase,
        texts: FeasibilityTexts,
    ): List<FloatArray> {
        run.progress(phase)
        val total = texts.queries.size + texts.documents.size
        val create = suspend { factory.embedder(file, path, backend).initializeOrClose { it.initialize() } }
        return coordinator.withEngine(EngineKey(file.id, backend), create) { embedder ->
            // The same 20 texts, with their own prefixes, on each backend (FR-009b).
            texts.queries.mapIndexed { i, query ->
                run.progress(phase, i + 1, total)
                embedder.embedQuery(query)
            } + texts.documents.mapIndexed { i, document ->
                run.progress(phase, texts.queries.size + i + 1, total)
                embedder.embedDocument(document, texts.documentHeader)
            }
        }
    }

    private suspend fun generation(
        session: Session,
        file: ModelFile,
        backend: ComputeBackend,
        texts: FeasibilityTexts,
        confirmHot: suspend (ThermalLevel) -> Boolean,
        fallbackReason: String?,
    ): ScenarioResult = scenario(session, ScenarioRun(ScenarioKind.GENERATION, file, backend, MeasurementProtocol.GENERATION)) {
        val path = repository.pathOf(file.id) ?: return@scenario finish(ResultStatus.NOT_AVAILABLE, "model not verified")
        progress(SuitePhase.METADATA)
        val metadata = withContext(dispatchers.io) { factory.metadata(path) }
        if (backend !in metadata.supportedBackends) {
            return@scenario finish(ResultStatus.NOT_AVAILABLE, "backend not declared by the model")
        }
        if (!coolDown(confirmHot)) return@scenario finish(ResultStatus.CANCELLED, "thermal")
        coordinator.releaseAll()
        resetSupported = memory.resetPeak()
        val config = GenerationConfig(
            maxNumTokens = MeasurementProtocol.GENERATION_MAX_NUM_TOKENS,
            maxOutputTokens = checkNotNull(protocol.maxOutputTokens),
            greedy = true,
        )
        val key = EngineKey(file.id, backend)
        val create = suspend { factory.generator(file, path, backend, config).initializeOrClose { it.initialize() } }
        val (coldMs, warmMs) = initTimes(this, key, create)

        coordinator.withEngine(key, create) { generator ->
            repeat(protocol.warmup) { i ->
                progress(SuitePhase.GENERATION_WARMUP, i + 1, protocol.warmup)
                generator.runOnce(texts.prompt)
            }
            val outputs = List(protocol.measured) { i ->
                progress(SuitePhase.GENERATION, i + 1, protocol.measured)
                generator.runOnce(texts.prompt)
            }
            val runs = outputs.map { (_, stats) -> stats.toRun() }
            finish(
                ResultStatus.COMPLETED,
                generation = GenerationMetrics(
                    initColdMs = coldMs,
                    initWarmMs = warmMs,
                    benchmarkInitS = outputs.first().second.initSeconds,
                    promptTokens = runs.mapNotNull { it.promptTokens }.medianOrNull()?.toInt(),
                    ttftMs = runs.mapNotNull { it.ttftMs }.medianOrNull(),
                    ttftWallMs = Percentiles.of(runs.map { it.ttftWallMs.toDouble() }, P50).toLong(),
                    prefillTokensPerSecond = runs.mapNotNull { it.prefillTokensPerSecond }.medianOrNull(),
                    decodeTokensPerSecond = runs.mapNotNull { it.decodeTokensPerSecond }.medianOrNull(),
                    decodeTokens = runs.mapNotNull { it.decodeTokens }.medianOrNull()?.toInt(),
                    runs = runs,
                    sampleOutput = firstWords(outputs.last().first, SAMPLE_WORDS),
                    fallbackFromGpu = fallbackReason != null,
                    fallbackReason = fallbackReason,
                ),
            )
        }
    }

    /** Cold init (compiled-kernel cache cleared) and cached init, each on a freshly created engine. */
    private suspend fun <T : com.lisdatasolutions.microrag.rag.runtime.ManagedEngine> initTimes(
        run: ScenarioRun,
        key: EngineKey,
        create: suspend () -> T,
    ): Pair<Long, Long> {
        run.progress(SuitePhase.INIT_COLD)
        factory.clearCompiledCache()
        val cold = timedMs { coordinator.withEngine(key, create) { } }
        coordinator.release(key)
        run.progress(SuitePhase.INIT_WARM)
        val warm = timedMs { coordinator.withEngine(key, create) { } }
        return cold to warm
    }

    private suspend fun measureLoop(
        run: ScenarioRun,
        warmupPhase: SuitePhase,
        phase: SuitePhase,
        protocol: MeasurementProtocol,
        call: suspend (Int) -> Unit,
    ): List<Double> {
        repeat(protocol.warmup) { i ->
            run.progress(warmupPhase, i + 1, protocol.warmup)
            call(i)
        }
        return List(protocol.measured) { i ->
            run.progress(phase, i + 1, protocol.measured)
            currentCoroutineContext().ensureActive()
            val start = nanoTime()
            call(i)
            (nanoTime() - start) / NANOS_PER_MILLI_D
        }
    }

    private inline fun timedMs(block: () -> Unit): Long {
        val start = nanoTime()
        block()
        return (nanoTime() - start) / NANOS_PER_MILLI
    }

    private suspend fun TextGenerator.runOnce(prompt: String): Pair<String, GenerationStats> {
        val text = StringBuilder()
        var stats: GenerationStats? = null
        generate(prompt).collect { event ->
            when (event) {
                is GenerationEvent.Token -> text.append(event.textDelta)
                is GenerationEvent.Finished -> stats = event.stats
            }
        }
        return text.toString() to checkNotNull(stats) { "generation ended without stats" }
    }

    private fun GenerationStats.toRun() = GenerationRun(
        promptTokens = prefillTokens,
        ttftMs = ttftSeconds?.times(MILLIS_PER_SECOND),
        ttftWallMs = ttftWallMs,
        prefillTokensPerSecond = prefillTokensPerSecond,
        decodeTokens = decodeTokens,
        decodeTokensPerSecond = decodeTokensPerSecond,
        totalWallMs = totalWallMs,
    )

    private companion object {
        const val TAG = "MicroRAG"
        const val P50 = 50
        const val P95 = 95
        const val NORM_TOLERANCE = 1e-5
        const val SAMPLE_WORDS = 400
        const val NANOS_PER_MILLI = 1_000_000L
        const val NANOS_PER_MILLI_D = 1_000_000.0
        const val MILLIS_PER_SECOND = 1000.0
    }
}

private fun List<Number>.medianOrNull(): Double? =
    takeIf { it.isNotEmpty() }?.let { values -> Percentiles.of(values.map { it.toDouble() }, 50) }

/** The first [count] words of [text], keeping its original line breaks. */
internal fun firstWords(text: String, count: Int): String {
    val cut = Regex("\\S+").findAll(text).elementAtOrNull(count)?.range?.first ?: return text.trim()
    return text.substring(0, cut).trimEnd() + "…"
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
