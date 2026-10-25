package com.lisdatasolutions.microrag.debug.feasibility.report

import com.lisdatasolutions.microrag.debug.feasibility.MeasurementProtocol
import com.lisdatasolutions.microrag.debug.feasibility.ThermalLevel
import com.lisdatasolutions.microrag.domain.model.ComputeBackend
import com.lisdatasolutions.microrag.domain.model.ModelId
import kotlinx.serialization.Serializable

/** Feasibility report, schema 1 (contracts/report-format.md). Only measured values, never examples. */
@Serializable
data class FeasibilityReport(
    val schemaVersion: Int = SCHEMA_VERSION,
    val createdAt: String,
    val device: DeviceSnapshot,
    val models: List<ModelEntry>,
    val results: List<ScenarioResult>,
    val exitCriteria: ExitCriteria,
) {
    companion object {
        const val SCHEMA_VERSION = 1
    }
}

@Serializable
data class DeviceSnapshot(
    val manufacturer: String,
    val model: String,
    val socManufacturer: String,
    val socModel: String,
    val androidRelease: String,
    val sdkInt: Int,
    val totalRamMb: Long,
    val thermalStatus: ThermalLevel,
    val thermalHeadroom: Float?,
    val charging: Boolean,
    val batteryLevel: Int?,
    val batteryTempC: Float?,
    val appVersion: String,
    val liteRtLmVersion: String,
)

@Serializable
data class ModelEntry(
    val id: ModelId,
    val fileName: String,
    val sizeBytes: Long,
    val sha256: String,
    val revision: String,
    val state: String,
)

enum class ScenarioKind { EMBEDDING, COMPATIBILITY, GENERATION }

enum class ResultStatus { COMPLETED, CANCELLED, FAILED, NOT_AVAILABLE }

@Serializable
data class ScenarioResult(
    val kind: ScenarioKind,
    val model: ModelId,
    /** Null for COMPATIBILITY, which compares CPU against GPU. */
    val backend: ComputeBackend?,
    val status: ResultStatus,
    val reason: String? = null,
    val atPhase: String? = null,
    val protocol: MeasurementProtocol,
    val startedAt: String,
    val durationMs: Long,
    val thermalStart: ThermalLevel,
    val thermalEnd: ThermalLevel,
    val charging: Boolean,
    val peakRssMb: Long?,
    val peakRssResetSupported: Boolean,
    val embedding: EmbeddingMetrics? = null,
    val compatibility: CompatibilityMetrics? = null,
    val generation: GenerationMetrics? = null,
)

@Serializable
data class EmbeddingMetrics(
    val initColdMs: Long,
    val initWarmMs: Long,
    val queryP50Ms: Double,
    val queryP95Ms: Double,
    val documentP50Ms: Double,
    val documentP95Ms: Double,
    val rawDims: Int,
    val outputDims: Int,
    val l2Norm: Double,
    val declaredMaxContextTokens: Int?,
    val supportedBackends: List<ComputeBackend>,
)

@Serializable
data class CompatibilityMetrics(
    val textCount: Int,
    val cosineMin: Double,
    val cosineMean: Double,
    val compatible: Boolean,
)

/** One measured generation; [GenerationMetrics] reports the median of the measured runs. */
@Serializable
data class GenerationRun(
    val promptTokens: Int?,
    val ttftMs: Double?,
    val ttftWallMs: Long,
    val prefillTokensPerSecond: Double?,
    val decodeTokens: Int?,
    val decodeTokensPerSecond: Double?,
    val totalWallMs: Long,
)

@Serializable
data class GenerationMetrics(
    val initColdMs: Long,
    val initWarmMs: Long,
    val benchmarkInitS: Double?,
    val promptTokens: Int?,
    val ttftMs: Double?,
    val ttftWallMs: Long,
    val prefillTokensPerSecond: Double?,
    val decodeTokensPerSecond: Double?,
    val decodeTokens: Int?,
    val runs: List<GenerationRun>,
    val sampleOutput: String,
    val fallbackFromGpu: Boolean,
    val fallbackReason: String? = null,
)

@Serializable
data class ExitCriteria(
    val ttftMeasured: Boolean,
    val bestQueryP95CpuMs: Double?,
    val queryUnder150msCpu: Boolean,
)

/** Phase-0 exit criteria (FR-017): TTFT measured and a CPU query embedding under 150 ms p95. */
fun exitCriteriaOf(results: List<ScenarioResult>): ExitCriteria {
    val bestQueryP95Cpu = results
        .filter { it.kind == ScenarioKind.EMBEDDING && it.backend == ComputeBackend.CPU && it.status == ResultStatus.COMPLETED }
        .mapNotNull { it.embedding?.queryP95Ms }
        .minOrNull()
    val ttftMeasured = results.any { it.kind == ScenarioKind.GENERATION && it.status == ResultStatus.COMPLETED && it.generation != null }
    return ExitCriteria(
        ttftMeasured = ttftMeasured,
        bestQueryP95CpuMs = bestQueryP95Cpu,
        queryUnder150msCpu = bestQueryP95Cpu != null && bestQueryP95Cpu < MeasurementProtocol.QUERY_P95_BUDGET_MS,
    )
}

/** Where each report version is written; the suite rewrites it after every result. */
fun interface ReportSink {
    suspend fun write(report: FeasibilityReport): java.io.File
}
