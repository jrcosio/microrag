package com.lisdatasolutions.microrag.debug.feasibility.visual.report

import com.lisdatasolutions.microrag.debug.feasibility.ThermalLevel
import com.lisdatasolutions.microrag.debug.feasibility.report.DeviceSnapshot
import com.lisdatasolutions.microrag.debug.feasibility.report.ModelEntry
import com.lisdatasolutions.microrag.rag.ingest.ImageFormat
import com.lisdatasolutions.microrag.debug.feasibility.visual.VisionModelInfo
import com.lisdatasolutions.microrag.domain.model.ComputeBackend
import com.lisdatasolutions.microrag.domain.model.ModelId
import java.io.File
import kotlinx.serialization.Serializable

/**
 * Visual feasibility report, schema 1 (F18 data-model §2.2). Only measured values. Answers and vectors go
 * only to the JSON, in private storage; the Markdown and logcat never carry them (FR-018).
 */
@Serializable
data class VisualReport(
    val schemaVersion: Int = SCHEMA_VERSION,
    val createdAt: String,
    val device: DeviceSnapshot,
    val models: List<ModelEntry>,
    val deviceSet: DeviceSetSummary,
    val gemmaInfo: VisionModelInfo? = null,
    val embedderInfo: VisionModelInfo? = null,
    val scenarios: List<VisualScenario>,
) {
    companion object {
        const val SCHEMA_VERSION = 1
    }
}

@Serializable
data class DeviceSetSummary(
    val present: Boolean,
    val verified: Int,
    val rejected: List<String>,
    val parityPx: Int? = null,
)

enum class VisualKind { GEMMA_INFO, GEMMA_PAGES, READING, EMBED_TEXT, EMBED_IMAGE, PARITY, RASTER, IMAGE_DECODE, PROJECTION }

enum class VisualStatus { COMPLETED, CANCELLED, NOT_SUPPORTED, NOT_AVAILABLE, FAILED }

@Serializable
data class VisualParams(
    /** GEMMA_PAGES: "A" (matrix) or "B" (candidates with the 20 questions). */
    val stage: String? = null,
    /** GEMMA_PAGES: the combination key, as on the desktop (`mezcla-p1-b280-t0`). */
    val combination: String? = null,
    val visionBackend: ComputeBackend? = null,
    val tokensPerImage: Int? = null,
    val px: Int? = null,
    val format: ImageFormat? = null,
    /** The device-set file the scenario used. */
    val document: String? = null,
)

@Serializable
data class DimensionCheck(val dims: Int, val norm: Double)

@Serializable
data class VisualMetrics(
    val initColdMs: Long? = null,
    val initWarmMs: Long? = null,
    val samples: Int? = null,
    /** The scenario's measure: first token (GEMMA_PAGES), embedding, render or decode time. */
    val p50Ms: Double? = null,
    val p95Ms: Double? = null,
    /** EMBED_TEXT: the ~350-token chunk. */
    val documentP50Ms: Double? = null,
    val documentP95Ms: Double? = null,
    val totalP50Ms: Double? = null,
    val prefillTokens: Int? = null,
    val prefillTokensPerSecond: Double? = null,
    val decodeTokensPerSecond: Double? = null,
    val bytesP50: Long? = null,
    val widthPx: Int? = null,
    val heightPx: Int? = null,
    val dimensions: List<DimensionCheck>? = null,
    /** PROJECTION: per-page time × 200 pages, marked as a projection (R14). */
    val projectedPages: Int? = null,
    val projectedP50S: Double? = null,
    val projectedP95S: Double? = null,
    val thermalMiddle: ThermalLevel? = null,
)

@Serializable
data class AnswerEntry(val id: String, val text: String, val firstTokenMs: Long, val prefillTokens: Int?)

@Serializable
data class VectorEntry(val id: String, val sha256: String?, val tokensPerImage: Int, val values: List<Float>)

@Serializable
data class VisualScenario(
    val kind: VisualKind,
    val model: ModelId? = null,
    val backend: ComputeBackend? = null,
    val params: VisualParams = VisualParams(),
    val status: VisualStatus,
    val reason: String? = null,
    val atPhase: String? = null,
    val startedAt: String,
    val durationMs: Long,
    val thermalStart: ThermalLevel,
    val thermalEnd: ThermalLevel,
    val charging: Boolean,
    val peakRssMb: Long?,
    val peakRssResetSupported: Boolean,
    val metrics: VisualMetrics? = null,
    val answers: List<AnswerEntry>? = null,
    val vectors: List<VectorEntry>? = null,
)

/** Where each report version is written; the suite rewrites it after every result. */
fun interface VisualReportSink {
    suspend fun write(report: VisualReport): File
}
