package com.lisdatasolutions.microrag.debug.feasibility

import com.lisdatasolutions.microrag.core.dispatchers.AppDispatchers
import com.lisdatasolutions.microrag.core.logging.DefaultAppLogger
import com.lisdatasolutions.microrag.core.logging.LogSink
import com.lisdatasolutions.microrag.debug.feasibility.report.DeviceSnapshot
import com.lisdatasolutions.microrag.debug.feasibility.report.FeasibilityReport
import com.lisdatasolutions.microrag.domain.model.ComputeBackend
import com.lisdatasolutions.microrag.domain.model.ModelFile
import com.lisdatasolutions.microrag.domain.model.ModelId
import com.lisdatasolutions.microrag.rag.runtime.EngineCoordinator
import com.lisdatasolutions.microrag.rag.runtime.EngineFactory
import com.lisdatasolutions.microrag.rag.runtime.GenerationConfig
import com.lisdatasolutions.microrag.rag.runtime.GenerationEvent
import com.lisdatasolutions.microrag.rag.runtime.GenerationStats
import com.lisdatasolutions.microrag.rag.runtime.ModelMetadata
import com.lisdatasolutions.microrag.rag.runtime.TextEmbedder
import com.lisdatasolutions.microrag.rag.runtime.TextGenerator
import java.io.File
import kotlin.math.sqrt
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/** Fake runtime and device for [FeasibilitySuite] tests; time only moves when engines "work". */
class SuiteFixture(
    dispatcher: CoroutineDispatcher,
    present: Set<ModelId> = emptySet(),
    corrupt: Set<ModelId> = emptySet(),
    val repository: FakeModelFileRepository = FakeModelFileRepository(present, corrupt),
) {
    var clock = 0L
    val events = mutableListOf<String>()
    val logs = mutableListOf<String>()
    val reports = mutableListOf<FeasibilityReport>()

    /** Backends each model declares (all by default). */
    val declared = mutableMapOf<ModelId, List<ComputeBackend>>()
    val failInit = mutableSetOf<Pair<ModelId, ComputeBackend>>()
    val failGenerate = mutableSetOf<Pair<ModelId, ComputeBackend>>()
    var embeddingDims = 256
    var gpuPerturbation = 0f
    var queryLatencyMs: (Int) -> Long = { 10 }
    var onQuery: suspend (Int) -> Unit = {}
    var onGenerate: suspend () -> Unit = {}
    var thermalLevel = ThermalLevel.NONE

    private val logger = DefaultAppLogger(isEnabled = true, sink = LogSink { _, _, message, _ -> logs += message })
    val coordinator = EngineCoordinator(logger)

    val texts = FeasibilityTexts(
        documentHeader = "Informe › Sección",
        queries = List(10) { "consulta secreta $it" },
        documents = List(10) { "documento secreto $it" },
        measuredDocument = "fragmento medido",
        prompt = "PROMPT-SECRETO",
    )

    val factory = object : EngineFactory {
        override fun embedder(file: ModelFile, path: File, backend: ComputeBackend): TextEmbedder =
            FakeEmbedder(file.id, backend)

        override fun generator(file: ModelFile, path: File, backend: ComputeBackend, config: GenerationConfig): TextGenerator =
            FakeGenerator(file.id, backend, config)

        override fun metadata(path: File): ModelMetadata {
            events += "metadata"
            val id = ModelId.entries.first { com.lisdatasolutions.microrag.domain.model.ModelCatalog.byId(it).fileName == path.name }
            return ModelMetadata(8192, declared[id] ?: ComputeBackend.entries)
        }

        override fun clearCompiledCache() {
            events += "clear"
        }

        override var benchmarkStatsEnabled = false
    }

    fun suite(dispatcher: CoroutineDispatcher) = FeasibilitySuite(
        coordinator = coordinator,
        factory = factory,
        repository = repository,
        textsSource = { texts },
        thermal = ThermalMonitor(object : ThermalSource {
            override fun level() = thermalLevel
            override fun headroom() = 0.3f
        }),
        memory = object : MemoryProbe {
            override fun resetPeak() = true
            override fun peakRssMb() = 512L
        },
        device = object : DeviceInfoSource {
            override fun snapshot() = DEVICE
            override fun isCharging() = true
        },
        reportSink = { report -> reports += report; File("/reports/feasibility.json") },
        dispatchers = AppDispatchers(dispatcher, dispatcher, dispatcher, dispatcher, dispatcher, dispatcher),
        logger = logger,
        nanoTime = { clock },
        now = { "2026-10-25T23:00:00+01:00" },
    )

    val suite = suite(dispatcher)

    private fun advance(ms: Long) {
        clock += ms * 1_000_000
    }

    inner class FakeEmbedder(override val model: ModelId, override val backend: ComputeBackend) : TextEmbedder {
        private var queries = 0
        override var rawDimensions: Int? = null

        override suspend fun initialize() {
            events += "init $model $backend"
            advance(100)
            if (model to backend in failInit) error("delegate unavailable")
        }

        override suspend fun embedQuery(text: String): FloatArray {
            events += "query"
            queries++
            onQuery(queries)
            advance(queryLatencyMs(queries))
            return vector(text)
        }

        override suspend fun embedDocument(content: String, header: String): FloatArray {
            events += "document"
            advance(40)
            return vector(header + content)
        }

        private fun vector(text: String): FloatArray {
            rawDimensions = 768
            val v = FloatArray(embeddingDims) { i -> ((text.hashCode() + i * 31) % 17).toFloat() + 1f }
            if (backend == ComputeBackend.GPU) v[0] += gpuPerturbation * v[0] * 20
            val n = sqrt(v.sumOf { it.toDouble() * it }).toFloat()
            return FloatArray(v.size) { v[it] / n }
        }

        override fun close() {
            events += "close $model $backend"
        }
    }

    inner class FakeGenerator(
        override val model: ModelId,
        override val backend: ComputeBackend,
        private val config: GenerationConfig,
    ) : TextGenerator {
        var cancelled = false

        override suspend fun initialize() {
            events += "init $model $backend"
            advance(2000)
            if (model to backend in failInit) error("GPU init failed")
        }

        override fun generate(prompt: String): Flow<GenerationEvent> = flow {
            events += "generate $model $backend ${config.maxOutputTokens} ${config.activationPrecision}"
            if (model to backend in failGenerate) error("GPU generation failed")
            onGenerate()
            advance(800)
            emit(GenerationEvent.Token("RESPUESTA-SECRETA "))
            emit(GenerationEvent.Token("con cita [1]."))
            emit(
                GenerationEvent.Finished(
                    GenerationStats(
                        initSeconds = 2.0,
                        ttftSeconds = 0.8,
                        ttftWallMs = 820,
                        totalWallMs = 4000,
                        prefillTokens = 2010,
                        prefillTokensPerSecond = 2500.0,
                        decodeTokens = 128,
                        decodeTokensPerSecond = 40.0,
                    ),
                ),
            )
        }

        override fun cancel() {
            cancelled = true
        }

        override fun close() {
            events += "close $model $backend"
        }
    }

    companion object {
        val DEVICE = DeviceSnapshot(
            manufacturer = "Nothing", model = "A063", socManufacturer = "QTI", socModel = "SM7325",
            androidRelease = "15", sdkInt = 35, totalRamMb = 11236, thermalStatus = ThermalLevel.NONE,
            thermalHeadroom = 0.3f, charging = true, batteryLevel = 80, batteryTempC = 30f,
            appVersion = "0.1.0", liteRtLmVersion = "0.18.1",
        )
    }
}
