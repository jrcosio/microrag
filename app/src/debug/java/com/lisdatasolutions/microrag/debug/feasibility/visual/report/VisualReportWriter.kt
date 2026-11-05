package com.lisdatasolutions.microrag.debug.feasibility.visual.report

import com.lisdatasolutions.microrag.debug.feasibility.report.ReportWriter
import java.io.File
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/**
 * Writes `filesDir/reports/visual-<yyyyMMdd-HHmmss>.{json,md}` (data-model §2.2), like F02's [ReportWriter]:
 * rewriting the same session replaces both files atomically. The Markdown has no answer text and no vector.
 */
class VisualReportWriter(
    private val dir: File,
    private val ioDispatcher: CoroutineDispatcher,
) : VisualReportSink {

    override suspend fun write(report: VisualReport): File = withContext(ioDispatcher) {
        dir.mkdirs()
        val base = fileBase(report.createdAt)
        writeAtomically(File(dir, "$base.md"), toMarkdown(report))
        File(dir, "$base.json").also { writeAtomically(it, ReportWriter.json.encodeToString(VisualReport.serializer(), report)) }
    }

    /** The most recent Markdown report, if any. */
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
        private const val PREFIX = "visual-"
        private val nameFormat = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")

        fun fileBase(createdAt: String): String = PREFIX + OffsetDateTime.parse(createdAt).format(nameFormat)

        fun toMarkdown(r: VisualReport): String = buildString {
            val d = r.device
            appendLine("# Informe de viabilidad visual (F18)")
            appendLine()
            appendLine("## 1. Sesión")
            appendLine()
            appendLine("| Campo | Valor |")
            appendLine("|---|---|")
            appendLine("| Fecha | ${r.createdAt} |")
            appendLine("| Dispositivo | ${d.manufacturer} ${d.model} · ${d.socManufacturer} ${d.socModel} · ${d.totalRamMb} MB RAM |")
            appendLine("| Android | ${d.androidRelease} (API ${d.sdkInt}) |")
            appendLine("| Versiones | app ${d.appVersion} · LiteRT-LM ${d.liteRtLmVersion} |")
            appendLine("| Conjunto del dispositivo | ${if (r.deviceSet.present) "${r.deviceSet.verified} ficheros verificados" else "ausente"}" +
                "${if (r.deviceSet.rejected.isEmpty()) "" else " · rechazados: ${r.deviceSet.rejected.joinToString()}"} |")
            appendLine()

            appendLine("## 2. Modelos")
            appendLine()
            appendLine("| Id | Fichero | SHA-256 | Estado |")
            appendLine("|---|---|---|---|")
            r.models.forEach { appendLine("| ${it.id} | `${it.fileName}` | `${it.sha256.take(12)}…` | ${it.state} |") }
            appendLine()
            listOf("Gemma" to r.gemmaInfo, "440M" to r.embedderInfo).forEach { (name, info) ->
                if (info != null) {
                    appendLine("- **$name:** imágenes ${if (info.vision) "sí" else "no"}, presupuesto máximo ${info.maxVisionTokenBudget}, " +
                        "firmas ${info.visionSignatures.joinToString("/").ifEmpty { "—" }}, texto en ${info.textBackends.joinToString("/")}, " +
                        "visión en ${info.visionBackends.joinToString("/").ifEmpty { "—" }}.")
                }
            }
            appendLine()

            section("3. Gemma con páginas", VisualKind.GEMMA_PAGES, r,
                "| Etapa | Combinación | Muestras | Primer token p50 / p95 (ms) | Total p50 (ms) | Tokens del prefill | Prefill (tok/s) | Decodificación (tok/s) | RSS pico (MB) | Térmico | Estado |",
                "|---|---|---:|---:|---:|---:|---:|---:|---:|---|---|") { s, m ->
                "| ${s.params.stage ?: "—"} | ${s.params.combination ?: "—"} · visión ${s.params.visionBackend ?: "—"} | ${m?.samples ?: "—"} | " +
                    "${pair(m?.p50Ms, m?.p95Ms)} | ${m?.totalP50Ms?.let(::num) ?: "—"} | ${m?.prefillTokens ?: "—"} | " +
                    "${m?.prefillTokensPerSecond?.let(::num) ?: "—"} | ${m?.decodeTokensPerSecond?.let(::num) ?: "—"} | ${rss(s)} | ${thermal(s)} | ${status(s)} |"
            }
            section("4. Lectura de páginas", VisualKind.READING, r,
                "| Preguntas | Primer token p50 / p95 (ms) | Estado |", "|---:|---:|---|") { s, m ->
                "| ${s.answers?.size ?: 0} | ${pair(m?.p50Ms, m?.p95Ms)} | ${status(s)} |"
            }
            appendLine("_Las respuestas van solo en el JSON; las juzga el escritorio (`run_eval.py visual-parity`)._")
            appendLine()
            section("5. Embeddings de texto", VisualKind.EMBED_TEXT, r,
                "| Modelo | Backend | Carga frío / caché (ms) | Consulta p50 / p95 (ms) | Fragmento p50 / p95 (ms) | RSS pico (MB) | Térmico | Estado |",
                "|---|---|---:|---:|---:|---:|---|---|") { s, m ->
                "| ${s.model} | ${s.backend} | ${pairLong(m?.initColdMs, m?.initWarmMs)} | ${pair(m?.p50Ms, m?.p95Ms)} | " +
                    "${pair(m?.documentP50Ms, m?.documentP95Ms)} | ${rss(s)} | ${thermal(s)} | ${status(s)} |"
            }
            section("6. Embeddings de imagen", VisualKind.EMBED_IMAGE, r,
                "| Backend | Tokens por imagen | Carga frío / caché (ms) | Página p50 / p95 (ms) | Dimensión y norma | RSS pico (MB) | Térmico | Estado |",
                "|---|---:|---:|---:|---|---:|---|---|") { s, m ->
                "| ${s.backend} | ${s.params.tokensPerImage ?: "—"} | ${pairLong(m?.initColdMs, m?.initWarmMs)} | ${pair(m?.p50Ms, m?.p95Ms)} | " +
                    "${m?.dimensions?.joinToString { "${it.dims}: ${num(it.norm, 6)}" } ?: "—"} | ${rss(s)} | ${thermal(s)} | ${status(s)} |"
            }
            section("7. Paridad con el escritorio", VisualKind.PARITY, r,
                "| Tokens por imagen | Vectores | Estado |", "|---:|---:|---|") { s, _ ->
                "| ${s.params.tokensPerImage ?: "—"} | ${s.vectors?.size ?: 0} | ${status(s)} |"
            }
            appendLine("_Los vectores van solo en el JSON; los cosenos los calcula el escritorio._")
            appendLine()
            section("8. Conversión de páginas", VisualKind.RASTER, r,
                "| Documento | px | Formato | Página p50 / p95 (ms) | Peso p50 (KB) | Tamaño | RSS pico (MB) | Térmico | Estado |",
                "|---|---:|---|---:|---:|---|---:|---|---|") { s, m ->
                "| ${s.params.document ?: "—"} | ${s.params.px ?: "—"} | ${s.params.format ?: "—"} | ${pair(m?.p50Ms, m?.p95Ms)} | " +
                    "${m?.bytesP50?.let { it / KB } ?: "—"} | ${size(m)} | ${rss(s)} | ${thermal(s)} | ${status(s)} |"
            }
            section("9. Decodificación de imágenes", VisualKind.IMAGE_DECODE, r,
                "| Imagen | px | Formato | p50 / p95 (ms) | Peso p50 (KB) | Tamaño | RSS pico (MB) | Estado |",
                "|---|---:|---|---:|---:|---|---:|---|") { s, m ->
                "| ${s.params.document ?: "—"} | ${s.params.px ?: "—"} | ${s.params.format ?: "—"} | ${pair(m?.p50Ms, m?.p95Ms)} | " +
                    "${m?.bytesP50?.let { it / KB } ?: "—"} | ${size(m)} | ${rss(s)} | ${status(s)} |"
            }
            section("10. Proyección de un PDF de 200 páginas", VisualKind.PROJECTION, r,
                "| Páginas medidas | Por página p50 / p95 (ms) | Proyección a 200 p50 / p95 (s) | Térmico inicio → mitad → fin | Estado |",
                "|---:|---:|---:|---|---|") { s, m ->
                "| ${m?.samples ?: "—"} | ${pair(m?.p50Ms, m?.p95Ms)} | ${pair(m?.projectedP50S, m?.projectedP95S)} | " +
                    "${s.thermalStart} → ${m?.thermalMiddle ?: "—"} → ${s.thermalEnd} | ${status(s)} |"
            }
            appendLine("_La proyección multiplica el tiempo por página medido; no es una ingesta real (R14)._")
        }

        private fun StringBuilder.section(
            title: String,
            kind: VisualKind,
            r: VisualReport,
            header: String,
            separator: String,
            row: (VisualScenario, VisualMetrics?) -> String,
        ) {
            appendLine("## $title")
            appendLine()
            val rows = r.scenarios.filter { it.kind == kind }
            if (rows.isEmpty()) {
                appendLine("Sin medidas.")
            } else {
                appendLine(header)
                appendLine(separator)
                rows.forEach { appendLine(row(it, it.metrics)) }
            }
            appendLine()
        }

        private fun status(s: VisualScenario): String = when (s.status) {
            VisualStatus.COMPLETED -> "completada"
            else -> listOfNotNull(s.status.name.lowercase(), s.atPhase, s.reason).joinToString(" · ")
        }

        private fun thermal(s: VisualScenario) = "${s.thermalStart} → ${s.thermalEnd}${if (s.charging) " · cargando" else ""}"

        private fun rss(s: VisualScenario) = s.peakRssMb?.let { "$it${if (s.peakRssResetSupported) "" else "*"}" } ?: "—"

        private fun size(m: VisualMetrics?) = if (m?.widthPx != null && m.heightPx != null) "${m.widthPx}×${m.heightPx}" else "—"

        private fun pair(a: Double?, b: Double?) = if (a == null && b == null) "—" else "${a?.let(::num) ?: "—"} / ${b?.let(::num) ?: "—"}"

        private fun pairLong(a: Long?, b: Long?) = if (a == null && b == null) "—" else "${a ?: "—"} / ${b ?: "—"}"

        private fun num(value: Double): String = num(value, 1)

        private fun num(value: Double, decimals: Int): String = String.format(Locale.ROOT, "%.${decimals}f", value)

        private const val KB = 1024L
    }
}
