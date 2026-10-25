package com.lisdatasolutions.microrag.debug.feasibility.report

import com.google.common.truth.Truth.assertThat
import com.lisdatasolutions.microrag.debug.feasibility.MeasurementProtocol
import com.lisdatasolutions.microrag.debug.feasibility.SuiteFixture
import com.lisdatasolutions.microrag.debug.feasibility.ThermalLevel
import com.lisdatasolutions.microrag.domain.model.ComputeBackend
import com.lisdatasolutions.microrag.domain.model.ModelId
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ReportWriterTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun result(
        kind: ScenarioKind,
        model: ModelId,
        backend: ComputeBackend?,
        status: ResultStatus = ResultStatus.COMPLETED,
        queryP95: Double = 40.0,
        reason: String? = null,
    ) = ScenarioResult(
        kind = kind, model = model, backend = backend, status = status, reason = reason,
        atPhase = if (status == ResultStatus.COMPLETED) null else "INIT_COLD",
        protocol = MeasurementProtocol.EMBEDDING, startedAt = "2026-10-25T23:00:00+01:00", durationMs = 1000,
        thermalStart = ThermalLevel.NONE, thermalEnd = ThermalLevel.LIGHT, charging = true, peakRssMb = 400,
        peakRssResetSupported = true,
        embedding = if (kind == ScenarioKind.EMBEDDING && status == ResultStatus.COMPLETED) {
            EmbeddingMetrics(900, 300, queryP95 - 5, queryP95, 90.0, 99.0, 768, 256, 1.0, 8192, ComputeBackend.entries)
        } else {
            null
        },
        compatibility = if (kind == ScenarioKind.COMPATIBILITY) CompatibilityMetrics(20, 0.995, 0.998, true) else null,
        generation = if (kind == ScenarioKind.GENERATION && status == ResultStatus.COMPLETED) {
            GenerationMetrics(
                initColdMs = 9000, initWarmMs = 3000, benchmarkInitS = 3.0, promptTokens = 2010, ttftMs = 1500.0,
                ttftWallMs = 1550, prefillTokensPerSecond = 1300.0, decodeTokensPerSecond = 18.0, decodeTokens = 128,
                runs = emptyList(), sampleOutput = "Se mide el TTFT [6].\nY la consulta [3].", fallbackFromGpu = false,
            )
        } else {
            null
        },
    )

    private val results = listOf(
        result(ScenarioKind.EMBEDDING, ModelId.EG2_740M, ComputeBackend.CPU, queryP95 = 180.0),
        result(ScenarioKind.EMBEDDING, ModelId.EG2_270M, ComputeBackend.CPU, queryP95 = 45.0),
        result(ScenarioKind.EMBEDDING, ModelId.EG2_270M, ComputeBackend.GPU, ResultStatus.NOT_AVAILABLE, reason = "delegate unavailable"),
        result(ScenarioKind.COMPATIBILITY, ModelId.EG2_270M, null),
        result(ScenarioKind.GENERATION, ModelId.GEMMA4_E2B_GPU, ComputeBackend.GPU),
        result(ScenarioKind.GENERATION, ModelId.GEMMA4_E2B, ComputeBackend.GPU, ResultStatus.CANCELLED),
    )

    private val report = FeasibilityReport(
        createdAt = "2026-10-25T23:10:05+01:00",
        device = SuiteFixture.DEVICE,
        models = listOf(ModelEntry(ModelId.EG2_270M, "embeddinggemma-2-text-270m.litertlm", 164_626_432, "2d079e".padEnd(64, '0'), "9be6e8".padEnd(40, '0'), "VERIFIED")),
        results = results,
        exitCriteria = exitCriteriaOf(results),
    )

    @Test
    fun exitCriteria_useTheBestCompletedCpuQueryAndAnyCompletedGeneration() {
        assertThat(report.exitCriteria).isEqualTo(ExitCriteria(ttftMeasured = true, bestQueryP95CpuMs = 45.0, queryUnder150msCpu = true))
        assertThat(exitCriteriaOf(results.take(1))).isEqualTo(ExitCriteria(false, 180.0, false))
        assertThat(exitCriteriaOf(emptyList())).isEqualTo(ExitCriteria(false, null, false))
    }

    @Test
    fun json_roundTrips() = runTest {
        val writer = ReportWriter(tmp.root, StandardTestDispatcher(testScheduler))

        val file = writer.write(report)

        assertThat(file.name).isEqualTo("feasibility-20261025-231005.json")
        val decoded = ReportWriter.json.decodeFromString(FeasibilityReport.serializer(), file.readText())
        assertThat(decoded).isEqualTo(report)
        assertThat(decoded.schemaVersion).isEqualTo(1)
    }

    @Test
    fun rewritingTheSameSession_replacesItsFiles() = runTest {
        val writer = ReportWriter(tmp.root, StandardTestDispatcher(testScheduler))

        writer.write(report.copy(results = results.take(1)))
        writer.write(report)

        assertThat(tmp.root.list()!!.toList()).containsExactly("feasibility-20261025-231005.json", "feasibility-20261025-231005.md")
        assertThat(writer.latest()!!.name).isEqualTo("feasibility-20261025-231005.md")
    }

    @Test
    fun markdown_hasTheSevenContractSectionsAndShowsReasons() {
        val md = ReportWriter.toMarkdown(report)

        (1..7).forEach { assertThat(md).contains("## $it. ") }
        assertThat(md).contains("| EG2_270M | CPU | 900 / 300 | 40.0 / 45.0 | 90.0 / 99.0 | 768 → 256 |")
        assertThat(md).contains("not_available · INIT_COLD · delegate unavailable")
        assertThat(md).contains("cancelled · INIT_COLD")
        assertThat(md).contains("| EG2_270M | 20 | 0.99500 | 0.99800 | sí | completada |")
        assertThat(md).contains("Consulta < 150 ms p95 en CPU: **sí**")
        assertThat(md).contains("> Se mide el TTFT [6].\n> Y la consulta [3].")
        assertThat(md).contains("A063")
        assertThat(md).contains("| GEMMA4_E2B_GPU | GPU | por defecto | 9000 / 3000 | 2010 |")
    }
}
