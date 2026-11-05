package com.lisdatasolutions.microrag.debug.feasibility.visual

import com.google.common.truth.Truth.assertThat
import com.lisdatasolutions.microrag.debug.feasibility.SuiteFixture
import com.lisdatasolutions.microrag.debug.feasibility.ThermalLevel
import com.lisdatasolutions.microrag.debug.feasibility.visual.report.AnswerEntry
import com.lisdatasolutions.microrag.debug.feasibility.visual.report.DeviceSetSummary
import com.lisdatasolutions.microrag.debug.feasibility.visual.report.VectorEntry
import com.lisdatasolutions.microrag.debug.feasibility.visual.report.VisualKind
import com.lisdatasolutions.microrag.debug.feasibility.visual.report.VisualMetrics
import com.lisdatasolutions.microrag.debug.feasibility.visual.report.VisualParams
import com.lisdatasolutions.microrag.debug.feasibility.visual.report.VisualReport
import com.lisdatasolutions.microrag.debug.feasibility.visual.report.VisualReportWriter
import com.lisdatasolutions.microrag.debug.feasibility.visual.report.VisualScenario
import com.lisdatasolutions.microrag.debug.feasibility.visual.report.VisualStatus
import com.lisdatasolutions.microrag.domain.model.ComputeBackend
import com.lisdatasolutions.microrag.domain.model.ModelId
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** F18 T007: schema 1, atomic files, and no answer text or vector outside the JSON (FR-018). */
class VisualReportTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun scenario(kind: VisualKind, charging: Boolean = true, answers: List<AnswerEntry>? = null, vectors: List<VectorEntry>? = null) =
        VisualScenario(
            kind = kind,
            model = ModelId.GEMMA4_E2B,
            backend = ComputeBackend.GPU,
            params = VisualParams(stage = "A", combination = "mezcla-p1-b280-t0", visionBackend = ComputeBackend.CPU),
            status = VisualStatus.COMPLETED,
            startedAt = "2026-11-05T10:00:00+01:00",
            durationMs = 1000,
            thermalStart = ThermalLevel.NONE,
            thermalEnd = ThermalLevel.LIGHT,
            charging = charging,
            peakRssMb = 3200,
            peakRssResetSupported = true,
            metrics = VisualMetrics(samples = 3, p50Ms = 5100.0, p95Ms = 5400.0, prefillTokens = 2010),
            answers = answers,
            vectors = vectors,
        )

    private fun report(vararg scenarios: VisualScenario) = VisualReport(
        createdAt = "2026-11-05T10:00:00+01:00",
        device = SuiteFixture.DEVICE,
        models = emptyList(),
        deviceSet = DeviceSetSummary(present = true, verified = 30, rejected = listOf("images/bad.png")),
        scenarios = scenarios.toList(),
    )

    @Test
    fun answersAndVectorsGoOnlyToTheJson() = runTest {
        val writer = VisualReportWriter(tmp.root, StandardTestDispatcher(testScheduler))
        val json = writer.write(
            report(
                scenario(VisualKind.GEMMA_PAGES, answers = listOf(AnswerEntry("c01", "RESPUESTA-SECRETA", 5100, 2010))),
                scenario(VisualKind.READING, answers = listOf(AnswerEntry("r01", "LECTURA-SECRETA", 900, 400))),
                scenario(VisualKind.PARITY, vectors = listOf(VectorEntry("images/a.png", "abc", 70, listOf(0.123456f, 0.5f)))),
            ),
        )
        val markdown = tmp.root.resolve(json.name.replace(".json", ".md")).readText()
        val text = json.readText()

        assertThat(json.name).isEqualTo("visual-20261105-100000.json")
        assertThat(text).contains("RESPUESTA-SECRETA")
        assertThat(text).contains("LECTURA-SECRETA")
        assertThat(text).contains("0.123456")
        assertThat(text).contains("\"schemaVersion\": 1")
        assertThat(markdown).doesNotContain("RESPUESTA-SECRETA")
        assertThat(markdown).doesNotContain("LECTURA-SECRETA")
        assertThat(markdown).doesNotContain("0.123456")
        assertThat(markdown).contains("mezcla-p1-b280-t0")
        assertThat(markdown).contains("rechazados: images/bad.png")
    }

    @Test
    fun everyScenarioCarriesItsChargingState() = runTest {
        val writer = VisualReportWriter(tmp.root, StandardTestDispatcher(testScheduler))
        val json = writer.write(report(scenario(VisualKind.RASTER, charging = false), scenario(VisualKind.RASTER, charging = true)))

        val text = json.readText()
        assertThat(Regex("\"charging\": false").findAll(text).count()).isEqualTo(1)
        assertThat(Regex("\"charging\": true").findAll(text).count()).isEqualTo(2) // the scenario and the device
    }

    @Test
    fun rewritingTheSessionReplacesBothFiles() = runTest {
        val writer = VisualReportWriter(tmp.root, StandardTestDispatcher(testScheduler))
        writer.write(report(scenario(VisualKind.RASTER)))
        writer.write(report(scenario(VisualKind.RASTER), scenario(VisualKind.IMAGE_DECODE)))

        val names = tmp.root.list()!!.sorted()
        assertThat(names).containsExactly("visual-20261105-100000.json", "visual-20261105-100000.md")
        assertThat(tmp.root.resolve(names[0]).readText()).contains("IMAGE_DECODE")
        assertThat(writer.latest()?.name).isEqualTo("visual-20261105-100000.md")
    }
}
