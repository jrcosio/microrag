package com.lisdatasolutions.microrag.debug.feasibility.report

import java.io.File
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

/**
 * Writes `filesDir/reports/feasibility-<yyyyMMdd-HHmmss>.{json,md}` (contracts/report-format.md).
 * Rewriting the same session replaces both files atomically.
 */
class ReportWriter(
    private val dir: File,
    private val ioDispatcher: CoroutineDispatcher,
) : ReportSink {

    override suspend fun write(report: FeasibilityReport): File = withContext(ioDispatcher) {
        dir.mkdirs()
        val base = fileBase(report.createdAt)
        writeAtomically(File(dir, "$base.md"), toMarkdown(report))
        File(dir, "$base.json").also { writeAtomically(it, json.encodeToString(FeasibilityReport.serializer(), report)) }
    }

    /** The most recent Markdown report, if any (survives process death). */
    suspend fun latest(): File? = withContext(ioDispatcher) {
        dir.listFiles { f -> f.name.startsWith(PREFIX) && f.name.endsWith(".md") }?.maxByOrNull { it.name }
    }

    private fun writeAtomically(target: File, text: String) {
        val tmp = File(target.parentFile, target.name + ".tmp")
        tmp.writeText(text)
        if (!tmp.renameTo(target)) {
            target.writeText(text)
            tmp.delete()
        }
    }

    companion object {
        private const val PREFIX = "feasibility-"
        private val nameFormat = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
        val json = Json {
            prettyPrint = true
            encodeDefaults = true
        }

        fun fileBase(createdAt: String): String = PREFIX + OffsetDateTime.parse(createdAt).format(nameFormat)

        fun toMarkdown(r: FeasibilityReport): String = buildString {
            val d = r.device
            appendLine("# Informe de viabilidad on-device (F02)")
            appendLine()
            appendLine("## 1. Sesión")
            appendLine()
            appendLine("| Campo | Valor |")
            appendLine("|---|---|")
            appendLine("| Fecha | ${r.createdAt} |")
            appendLine("| Dispositivo | ${d.manufacturer} ${d.model} · ${d.socManufacturer} ${d.socModel} · ${d.totalRamMb} MB RAM |")
            appendLine("| Android | ${d.androidRelease} (API ${d.sdkInt}) |")
            appendLine("| Versiones | app ${d.appVersion} · LiteRT-LM ${d.liteRtLmVersion} |")
            appendLine("| Térmico inicial | ${d.thermalStatus} (margen ${d.thermalHeadroom?.let { num(it.toDouble(), 2) } ?: "—"}) |")
            appendLine("| Batería | ${d.batteryLevel ?: "—"} % · ${d.batteryTempC?.let { num(it.toDouble(), 1) } ?: "—"} °C · ${if (d.charging) "cargando" else "sin cargar"} |")
            appendLine()

            appendLine("## 2. Modelos")
            appendLine()
            appendLine("| Id | Fichero | MB | SHA-256 | Revisión | Estado |")
            appendLine("|---|---|---:|---|---|---|")
            r.models.forEach {
                appendLine("| ${it.id} | `${it.fileName}` | ${it.sizeBytes / MB} | `${it.sha256.take(12)}…` | `${it.revision.take(8)}` | ${it.state} |")
            }
            appendLine()

            appendLine("## 3. Embeddings")
            appendLine()
            appendLine("| Modelo | Backend | Init frío / caché (ms) | Consulta p50 / p95 (ms) | Documento p50 / p95 (ms) | Dims | Norma | Contexto máx. | RSS pico (MB) | Térmico | Estado |")
            appendLine("|---|---|---:|---:|---:|---|---:|---:|---:|---|---|")
            r.results.filter { it.kind == ScenarioKind.EMBEDDING }.forEach { s ->
                val m = s.embedding
                appendLine(
                    "| ${s.model} | ${s.backend} | ${m?.let { "${it.initColdMs} / ${it.initWarmMs}" } ?: "—"} | " +
                        "${m?.let { "${num(it.queryP50Ms)} / ${num(it.queryP95Ms)}" } ?: "—"} | " +
                        "${m?.let { "${num(it.documentP50Ms)} / ${num(it.documentP95Ms)}" } ?: "—"} | " +
                        "${m?.let { "${it.rawDims} → ${it.outputDims}" } ?: "—"} | ${m?.let { num(it.l2Norm, 6) } ?: "—"} | " +
                        "${m?.declaredMaxContextTokens ?: "—"} | ${s.peakRssMb ?: "—"}${resetMark(s)} | ${thermal(s)} | ${status(s)} |",
                )
            }
            appendLine()

            appendLine("## 4. Compatibilidad CPU↔GPU")
            appendLine()
            appendLine("| Modelo | Textos | Coseno mínimo | Coseno medio | Compatible (≥ 0,99) | Estado |")
            appendLine("|---|---:|---:|---:|---|---|")
            r.results.filter { it.kind == ScenarioKind.COMPATIBILITY }.forEach { s ->
                val c = s.compatibility
                appendLine(
                    "| ${s.model} | ${c?.textCount ?: "—"} | ${c?.let { num(it.cosineMin, 5) } ?: "—"} | " +
                        "${c?.let { num(it.cosineMean, 5) } ?: "—"} | ${c?.let { if (it.compatible) "sí" else "no" } ?: "—"} | ${status(s)} |",
                )
            }
            appendLine()

            appendLine("## 5. Generación")
            appendLine()
            appendLine("| Modelo | Backend | Init frío / caché (ms) | Tokens prompt | TTFT bench / pared (ms) | Prefill (tok/s) | Decode (tok/s) | RSS pico (MB) | Térmico | Plan B | Estado |")
            appendLine("|---|---|---:|---:|---:|---:|---:|---:|---|---|---|")
            r.results.filter { it.kind == ScenarioKind.GENERATION }.forEach { s ->
                val g = s.generation
                appendLine(
                    "| ${s.model} | ${s.backend} | ${g?.let { "${it.initColdMs} / ${it.initWarmMs}" } ?: "—"} | " +
                        "${g?.promptTokens ?: "—"} | ${g?.let { "${it.ttftMs?.let(::num) ?: "—"} / ${it.ttftWallMs}" } ?: "—"} | " +
                        "${g?.prefillTokensPerSecond?.let(::num) ?: "—"} | ${g?.decodeTokensPerSecond?.let(::num) ?: "—"} | " +
                        "${s.peakRssMb ?: "—"}${resetMark(s)} | ${thermal(s)} | ${g?.fallbackReason?.let { "sí: $it" } ?: "no"} | ${status(s)} |",
                )
            }
            appendLine()

            val e = r.exitCriteria
            appendLine("## 6. Criterios de salida de la fase 0")
            appendLine()
            appendLine("- TTFT medido con prompt de ≈ 2.000 tokens: **${if (e.ttftMeasured) "sí" else "no"}**")
            appendLine("- Mejor consulta p95 en CPU: **${e.bestQueryP95CpuMs?.let { "${num(it)} ms" } ?: "—"}**")
            appendLine("- Consulta < 150 ms p95 en CPU: **${if (e.queryUnder150msCpu) "sí" else "no"}**")
            appendLine()

            appendLine("## 7. Muestra de salida")
            appendLine()
            r.results.filter { it.generation != null }.forEach { s ->
                appendLine("### ${s.model} · ${s.backend}")
                appendLine()
                s.generation!!.sampleOutput.lines().forEach { appendLine("> $it") }
                appendLine()
            }
            appendLine("_Notas: «Init frío» borra la caché de kernels de LiteRT-LM; la caché de páginas del sistema no se puede vaciar sin root. " +
                "RSS pico marcado con * no se pudo reiniciar y cubre desde el arranque del proceso._")
        }

        private fun status(s: ScenarioResult): String = when (s.status) {
            ResultStatus.COMPLETED -> "completada"
            else -> listOfNotNull(s.status.name.lowercase(), s.atPhase, s.reason).joinToString(" · ")
        }

        private fun thermal(s: ScenarioResult) = "${s.thermalStart} → ${s.thermalEnd}"

        private fun resetMark(s: ScenarioResult) = if (s.peakRssResetSupported) "" else "*"

        private fun num(value: Double): String = num(value, 1)

        private fun num(value: Double, decimals: Int): String = String.format(Locale.ROOT, "%.${decimals}f", value)

        private const val MB = 1024L * 1024L
    }
}
