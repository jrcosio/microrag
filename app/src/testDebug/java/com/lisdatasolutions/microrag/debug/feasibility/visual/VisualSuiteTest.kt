package com.lisdatasolutions.microrag.debug.feasibility.visual

import com.google.common.truth.Truth.assertThat
import com.lisdatasolutions.microrag.debug.feasibility.ThermalLevel
import com.lisdatasolutions.microrag.debug.feasibility.visual.report.VisualKind
import com.lisdatasolutions.microrag.debug.feasibility.visual.report.VisualStatus
import com.lisdatasolutions.microrag.domain.model.ComputeBackend
import com.lisdatasolutions.microrag.domain.model.ModelId
import kotlin.math.abs
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** F18 T011, T038, T041, T043, T046 and T048: the visual suite with fake engines (contracts/visual-diagnostic.md §2). */
@OptIn(ExperimentalCoroutinesApi::class)
class VisualSuiteTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val proceed: suspend (ThermalLevel) -> Boolean = { true }

    private fun TestScope.fixture(
        present: Set<ModelId> = setOf(ModelId.GEMMA4_E2B, ModelId.EG2_440M, ModelId.EG2_270M),
        questions: Int = 1,
    ) = VisualFixture(StandardTestDispatcher(testScheduler), tmp.newFolder(), present, questions)

    // Core (T011) ------------------------------------------------------------------------------------------

    @Test
    fun everyScenarioStartsAndEndsWithNoEngineLoaded() = runTest {
        val f = fixture()

        f.suite.run(setOf(VisualBlock.EMBED_TEXT, VisualBlock.EMBED_IMAGE), proceed)

        assertThat(f.base.coordinator.loaded.value).isEmpty()
        // The 440M's text and image engines share a key: each scenario closes its own before the next one loads.
        assertThat(f.events.lastIndexOf("close EG2_440M CPU")).isLessThan(f.events.indexOf("iinit EG2_440M CPU 70"))
        assertThat(f.events.indexOf("close EG2_440M CPU")).isAtLeast(0)
    }

    @Test
    fun theReportIsRewrittenAfterEveryResult() = runTest {
        val f = fixture()

        f.suite.run(setOf(VisualBlock.GEMMA_INFO, VisualBlock.RASTER), proceed)

        val results = f.suite.results.value
        assertThat(f.reports.size).isEqualTo(results.size + 1)
        assertThat(f.reports.last().gemmaInfo).isEqualTo(f.gemmaInfo)
        assertThat(f.reports.last().embedderInfo).isEqualTo(f.embedderInfo)
    }

    @Test
    fun aHotDeviceAsksFirstAndDecliningCancelsWithTheReason() = runTest {
        val f = fixture()
        f.thermalLevel = ThermalLevel.SEVERE

        f.suite.run(setOf(VisualBlock.EMBED_TEXT), confirmHot = { false })

        assertThat(f.suite.results.value.map { it.status to it.reason })
            .containsExactly(VisualStatus.CANCELLED to "thermal", VisualStatus.CANCELLED to "thermal")
    }

    @Test
    fun aGpuFailureIsNotAvailableAndACpuFailureIsFailed() = runTest {
        val f = fixture()
        f.embedderInfo = f.embedderInfo.copy(visionBackends = ComputeBackend.entries)
        f.failInit += "embedder GPU"
        f.failInit += "embedder CPU"

        f.suite.run(setOf(VisualBlock.EMBED_IMAGE), proceed)

        val byBackend = f.suite.results.value.groupBy({ it.backend }, { it.status })
        assertThat(byBackend[ComputeBackend.GPU]).containsExactly(VisualStatus.NOT_AVAILABLE, VisualStatus.NOT_AVAILABLE)
        assertThat(byBackend[ComputeBackend.CPU]).containsExactly(VisualStatus.FAILED, VisualStatus.FAILED)
        assertThat(f.base.coordinator.loaded.value).isEmpty()
    }

    @Test
    fun blocksWithoutTheirFilesDoNotRunAndSayWhy() = runTest {
        val f = fixture(present = emptySet())

        f.suite.run(setOf(VisualBlock.EMBED_TEXT, VisualBlock.GEMMA_PAGES, VisualBlock.PARITY), proceed)

        assertThat(f.suite.results.value.map { it.status }.distinct()).containsExactly(VisualStatus.NOT_AVAILABLE)
        assertThat(f.suite.results.value.map { it.reason }.distinct()).containsExactly("model not verified")
        assertThat(f.events.none { it.contains("init") }).isTrue()
    }

    @Test
    fun cancellingRecordsTheScenarioAndReleases() = runTest {
        val f = fixture()
        val reached = CompletableDeferred<Unit>()
        f.onGenerate = { call -> if (call == 3) { reached.complete(Unit); CompletableDeferred<Unit>().await() } }

        val job = launch { f.suite.run(setOf(VisualBlock.GEMMA_PAGES), proceed) }
        reached.await()
        job.cancel()
        advanceUntilIdle()

        assertThat(f.suite.results.value.last().status).isEqualTo(VisualStatus.CANCELLED)
        assertThat(f.base.coordinator.loaded.value).isEmpty()
        assertThat(f.reports).isNotEmpty()
    }

    // Gemma (T038) ------------------------------------------------------------------------------------------

    @Test
    fun stageAWalksTheWholeMatrixWithOneEngine() = runTest {
        val f = fixture()

        f.suite.run(setOf(VisualBlock.GEMMA_PAGES), proceed)

        val pages = f.suite.results.value.filter { it.params.stage == "A" && it.params.visionBackend == ComputeBackend.CPU }
        assertThat(pages.map { it.params.combination }).containsExactlyElementsIn(VisualProtocol.stageA().map { it.key })
        assertThat(pages.map { it.status }.distinct()).containsExactly(VisualStatus.COMPLETED)
        assertThat(pages.first { it.params.combination == "mezcla-p2-b140-t0" }.metrics!!.samples).isEqualTo(3)
        // 1 warm-up and 3 measured answers per combination, all on the engine loaded for the batch.
        assertThat(f.events.count { it.startsWith("vgen") }).isEqualTo(25 * 4)
        assertThat(f.events.count { it == "vinit GEMMA4_E2B GPU vision=CPU" }).isEqualTo(3) // cold and warm load, then the batch
        val loads = f.suite.results.value.filter { it.params.stage == "load" }
        assertThat(loads.map { it.params.combination }).containsExactly("carga-vision", "carga-texto").inOrder()
        assertThat(loads.all { it.metrics?.initColdMs != null }).isTrue()
    }

    @Test
    fun theMixAndTilesSendTheirLayoutInOrder() = runTest {
        val f = fixture()

        f.suite.run(setOf(VisualBlock.GEMMA_PAGES), proceed)

        // mezcla-p3-b280-t1: three pages split in two tiles: 6 images.
        assertThat(f.events).contains("vgen 280 6")
        assertThat(f.events).contains("vgen null 0") // the text-only base
    }

    @Test
    fun aBudgetOverTheFileMaximumIsNotSupportedAndLoadsNothing() = runTest {
        val f = fixture()
        f.gemmaInfo = f.gemmaInfo.copy(maxVisionTokenBudget = 140, visionSignatures = listOf(70, 140))

        f.suite.run(setOf(VisualBlock.GEMMA_PAGES), proceed)

        val over = f.suite.results.value.filter {
            it.params.combination?.contains("-b280-") == true && it.params.stage == "A" && it.params.visionBackend == ComputeBackend.CPU
        }
        assertThat(over).isNotEmpty()
        assertThat(over.map { it.status }.distinct()).containsExactly(VisualStatus.NOT_SUPPORTED)
        assertThat(f.events.none { it.startsWith("vgen 280") }).isTrue()
    }

    @Test
    fun visionOnTheGpuIsRecordedAsNotAvailableWhenTheFileDoesNotDeclareIt() = runTest {
        val f = fixture()

        f.suite.run(setOf(VisualBlock.GEMMA_PAGES), proceed)

        val gpu = f.suite.results.value.single { it.params.visionBackend == ComputeBackend.GPU }
        assertThat(gpu.status).isEqualTo(VisualStatus.NOT_AVAILABLE)
        assertThat(f.events.none { it.contains("vision=GPU") }).isTrue()
    }

    @Test
    fun declaredVisionOnTheGpuIsTriedOnceAndItsFailureIsNotAvailable() = runTest {
        val f = fixture()
        f.gemmaInfo = f.gemmaInfo.copy(visionBackends = ComputeBackend.entries)
        f.failInit += "gemma vision=GPU"

        f.suite.run(setOf(VisualBlock.GEMMA_PAGES), proceed)

        val gpu = f.suite.results.value.single { it.params.visionBackend == ComputeBackend.GPU }
        assertThat(gpu.status).isEqualTo(VisualStatus.NOT_AVAILABLE)
        assertThat(f.events.count { it == "vinit GEMMA4_E2B GPU vision=GPU" }).isEqualTo(1)
    }

    @Test
    fun stageBAsksTheTwentyQuestionsOfEachCandidate() = runTest {
        val f = fixture(questions = 20)

        f.suite.run(setOf(VisualBlock.GEMMA_PAGES), proceed, candidates = listOf("mezcla-p1-b280-t0", "mezcla-p2-b140-t0"))

        val results = f.suite.results.value
        assertThat(results.map { it.params.combination }).containsExactly("mezcla-p1-b280-t0", "mezcla-p2-b140-t0").inOrder()
        results.forEach { result ->
            assertThat(result.params.stage).isEqualTo("B")
            assertThat(result.metrics!!.samples).isEqualTo(20)
            assertThat(result.answers!!.map { it.id }).containsExactlyElementsIn((1..20).map { "c%02d".format(it) })
        }
        assertThat(f.events.count { it.startsWith("vgen") }).isEqualTo(2 * 21)
    }

    @Test
    fun stageBWithFewerThanTwentyQuestionsIsNotAvailable() = runTest {
        val f = fixture(questions = 5)

        f.suite.run(setOf(VisualBlock.GEMMA_PAGES), proceed, candidates = listOf("mezcla-p1-b280-t0"))

        assertThat(f.suite.results.value.single().status).isEqualTo(VisualStatus.NOT_AVAILABLE)
    }

    @Test
    fun answersGoToTheReportAndNeverToTheLog() = runTest {
        val f = fixture()

        f.suite.run(setOf(VisualBlock.GEMMA_PAGES, VisualBlock.READING), proceed)

        val reading = f.suite.results.value.single { it.kind == VisualKind.READING }
        assertThat(reading.answers!!.map { it.id }).containsExactly("r01", "r02").inOrder()
        assertThat(reading.answers!!.first().text).startsWith("RESPUESTA-SECRETA")
        assertThat(f.base.logs.none { it.contains("RESPUESTA-SECRETA") || it.contains("Pregunta") }).isTrue()
    }

    // 440M text (T041) --------------------------------------------------------------------------------------

    @Test
    fun embedTextMeasuresBothVariantsWithTheirLoads() = runTest {
        val f = fixture()

        f.suite.run(setOf(VisualBlock.EMBED_TEXT), proceed)

        val results = f.suite.results.value
        assertThat(results.map { it.model }).containsExactly(ModelId.EG2_270M, ModelId.EG2_440M).inOrder()
        results.forEach { result ->
            val m = result.metrics!!
            assertThat(result.status).isEqualTo(VisualStatus.COMPLETED)
            assertThat(m.samples).isEqualTo(30)
            assertThat(m.initColdMs).isNotNull()
            assertThat(m.initWarmMs).isNotNull()
            assertThat(m.p95Ms).isNotNull()
            assertThat(m.documentP95Ms).isNotNull()
            assertThat(result.peakRssMb).isEqualTo(900)
        }
    }

    // Images (T043) -----------------------------------------------------------------------------------------

    @Test
    fun embedImageChecksBothDimensionsAndSkipsAnUndeclaredGpu() = runTest {
        val f = fixture()

        f.suite.run(setOf(VisualBlock.EMBED_IMAGE), proceed)

        val cpu = f.suite.results.value.filter { it.backend == ComputeBackend.CPU }
        assertThat(cpu.map { it.params.tokensPerImage }).containsExactly(70, 140).inOrder()
        cpu.forEach { result ->
            assertThat(result.status).isEqualTo(VisualStatus.COMPLETED)
            val dims = result.metrics!!.dimensions!!
            assertThat(dims.map { it.dims }).containsExactly(768, 256, 512).inOrder()
            assertThat(dims.drop(1).all { abs(it.norm - 1.0) < 1e-5 }).isTrue()
        }
        val gpu = f.suite.results.value.filter { it.backend == ComputeBackend.GPU }
        assertThat(gpu.map { it.status }.distinct()).containsExactly(VisualStatus.NOT_AVAILABLE)
        assertThat(f.events.none { it.startsWith("iinit EG2_440M GPU") }).isTrue()
    }

    @Test
    fun parityStoresTheRawVectorsOfImagesAndRenderedPages() = runTest {
        val f = fixture()

        f.suite.run(setOf(VisualBlock.PARITY), proceed)

        val results = f.suite.results.value
        assertThat(results.map { it.params.tokensPerImage }).containsExactly(70, 140).inOrder()
        val vectors = results.first().vectors!!
        assertThat(vectors).hasSize(4 + 3) // 4 images and the 3 pages listed for parity
        assertThat(vectors.first().sha256).isEqualTo("sha-images/i1.png")
        assertThat(vectors.last().id).isEqualTo("pdf:pdfs/scan.pdf:1:1024")
        assertThat(vectors.all { it.values.size == 768 }).isTrue()
        assertThat(f.events).contains("render scan.pdf 1 1024 PNG")
    }

    // Conversion (T046) -------------------------------------------------------------------------------------

    @Test
    fun rasterCoversEveryPdfResolutionAndFormat() = runTest {
        val f = fixture()

        f.suite.run(setOf(VisualBlock.RASTER), proceed)

        val results = f.suite.results.value
        assertThat(results).hasSize(2 * 3 * 2)
        assertThat(results.map { it.status }.distinct()).containsExactly(VisualStatus.COMPLETED)
        val first = results.first()
        assertThat(first.params.document).isEqualTo("pdfs/text.pdf")
        assertThat(first.metrics!!.samples).isEqualTo(30)
        assertThat(first.metrics!!.bytesP50).isEqualTo(768L / 8)
        assertThat(first.metrics!!.heightPx).isEqualTo(768)
    }

    @Test
    fun imageDecodeCoversEveryImageAndResolution() = runTest {
        val f = fixture()

        f.suite.run(setOf(VisualBlock.IMAGE_DECODE), proceed)

        val results = f.suite.results.value
        assertThat(results.map { it.params.document to it.params.px }).containsExactly(
            "decode/big.jpg" to 768, "decode/big.jpg" to 1024, "decode/big.jpg" to 1536,
            "decode/alpha.png" to 768, "decode/alpha.png" to 1024, "decode/alpha.png" to 1536,
        ).inOrder()
    }

    @Test
    fun projectionMeasuresFiftyPagesAndMarksTheProjection() = runTest {
        val f = fixture()

        f.suite.run(setOf(VisualBlock.PROJECTION), proceed)

        val m = f.suite.results.value.single().metrics!!
        assertThat(m.samples).isEqualTo(50)
        assertThat(m.projectedPages).isEqualTo(200)
        assertThat(m.projectedP50S).isWithin(1e-6).of(m.p50Ms!! * 200 / 1000)
        assertThat(m.thermalMiddle).isEqualTo(ThermalLevel.NONE)
        assertThat(f.suite.results.value.single().params.tokensPerImage).isEqualTo(140)
    }

    // Resources (T048) --------------------------------------------------------------------------------------

    @Test
    fun theImageEmbedderIsClosedBeforeGemmaLoads() = runTest {
        val f = fixture()

        f.suite.run(setOf(VisualBlock.EMBED_IMAGE, VisualBlock.GEMMA_PAGES), proceed)

        val lastEmbedderClose = f.events.indexOfLast { it.startsWith("iclose") }
        val firstGemmaInit = f.events.indexOfFirst { it.startsWith("vinit") }
        assertThat(lastEmbedderClose).isLessThan(firstGemmaInit)
        assertThat(f.base.coordinator.loaded.value).isEmpty()
    }
}
