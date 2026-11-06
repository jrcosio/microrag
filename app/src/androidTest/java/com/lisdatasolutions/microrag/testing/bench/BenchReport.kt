package com.lisdatasolutions.microrag.testing.bench

import android.content.Context
import android.os.Build
import android.util.Log
import com.lisdatasolutions.microrag.debug.feasibility.Percentiles
import com.lisdatasolutions.microrag.rag.runtime.RuntimeInfo
import java.io.File
import java.time.Instant
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * F14 (research R4, contract B1, data-model §4): one bench's report, written to `files/reports/f14-<bench>-<ms>.json`
 * for `run_eval.py device-report`, with a numeric summary in the log. Percentiles are nearest-rank, as F02's.
 */
class BenchReport(
    private val context: Context,
    val bench: String,
    var sqliteVector: String,
) {
    /** One measured sample: [phase] is what was timed (embed, vector, fts, hybrid, ttft…), [ms] how long it took. */
    data class Sample(
        val phase: String,
        val ms: Double,
        val question: String? = null,
        val thermal: String? = null,
        val tokens: Int? = null,
        val estimated: Int? = null,
        /** Seconds since the bench started, for the 5-minute stretches of a sustained session. */
        val at: Double? = null,
        /** Battery level when the sample was taken, for a sustained session. */
        val battery: Int? = null,
    )

    inner class Variant(val name: String, val parameters: Map<String, String>) {
        val samples = mutableListOf<Sample>()
        val extra = linkedMapOf<String, JsonElement>()

        fun add(sample: Sample) {
            samples += sample
        }

        /** p50, p95 and p99 per phase, in ms. */
        fun summary(): Map<String, Map<String, Double>> = samples.groupBy { it.phase }.mapValues { (_, list) ->
            val values = list.map { it.ms }
            mapOf("p50" to Percentiles.of(values, 50), "p95" to Percentiles.of(values, 95), "p99" to Percentiles.of(values, 99),
                "n" to values.size.toDouble())
        }
    }

    private val started = Instant.now()
    private val variants = mutableListOf<Variant>()
    private val models = mutableListOf<JsonObject>()
    var start: DeviceConditions? = null
    var end: DeviceConditions? = null
    var status: String = STATUS_COMPLETED
    var reason: String? = null

    fun variant(name: String, parameters: Map<String, String> = emptyMap()): Variant = Variant(name, parameters).also { variants += it }

    fun model(id: String, sha256: String?, backend: String?, precision: String?) {
        models += buildJsonObject {
            put("id", id)
            sha256?.let { put("sha256", it) }
            backend?.let { put("backend", it) }
            precision?.let { put("precision", it) }
        }
    }

    fun toJson(): JsonObject = buildJsonObject {
        put("bench", bench)
        put("started", started.toString())
        put("device", "${Build.MANUFACTURER} ${Build.MODEL}")
        put("android", Build.VERSION.SDK_INT)
        put("runtimes", buildJsonObject {
            put("litertlm", RuntimeInfo.LITERT_LM_VERSION)
            put("sqliteVector", sqliteVector)
        })
        put("models", buildJsonArray { models.forEach { add(it) } })
        put("conditions", buildJsonObject {
            start?.let { put("start", it.toJson()) }
            end?.let { put("end", it.toJson()) }
        })
        put("variants", buildJsonArray {
            variants.forEach { v ->
                add(buildJsonObject {
                    put("name", v.name)
                    put("parameters", buildJsonObject { v.parameters.forEach { (k, value) -> put(k, value) } })
                    put("samples", buildJsonArray {
                        v.samples.forEachIndexed { i, s ->
                            add(buildJsonObject {
                                put("i", i)
                                put("phase", s.phase)
                                put("ms", s.ms)
                                s.question?.let { put("q", it) }
                                s.thermal?.let { put("thermal", it) }
                                s.tokens?.let { put("tokens", it) }
                                s.estimated?.let { put("estimated", it) }
                                s.at?.let { put("t", it) }
                                s.battery?.let { put("battery", it) }
                            })
                        }
                    })
                    put("summary", buildJsonObject {
                        v.summary().forEach { (phase, figures) ->
                            put(phase, buildJsonObject { figures.forEach { (k, value) -> put(k, value) } })
                        }
                    })
                    put("extra", JsonObject(v.extra))
                })
            }
        })
        put("status", status)
        reason?.let { put("reason", it) }
    }

    /** Writes the JSON and logs the summary (numbers only); returns the file. */
    fun write(): File {
        val out = File(context.filesDir, REPORTS).apply { mkdirs() }.resolve("$PREFIX$bench-${System.currentTimeMillis()}.json")
        out.writeText(Json.encodeToString(JsonObject.serializer(), toJson()))
        variants.forEach { v -> Log.i(TAG, "f14 $bench ${v.name} ${v.summary()} ${v.extra}") }
        Log.i(TAG, "f14 $bench status=$status ${reason ?: ""} -> ${out.name}")
        return out
    }

    companion object {
        const val REPORTS = "reports"
        const val PREFIX = "f14-"
        const val STATUS_COMPLETED = "completed"
        const val STATUS_NOT_AVAILABLE = "not-available"
        const val STATUS_DOES_NOT_FIT = "does-not-fit"
        const val STATUS_NO_SPACE = "no-space"
        const val STATUS_ABORTED_THERMAL = "aborted-thermal"
        private const val TAG = "MicroRAG"
    }
}
