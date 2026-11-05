package com.lisdatasolutions.microrag.debug.feasibility.visual

import com.lisdatasolutions.microrag.core.dispatchers.AppDispatchers
import com.lisdatasolutions.microrag.debug.feasibility.DeviceInfoSource
import com.lisdatasolutions.microrag.debug.feasibility.MemoryProbe
import com.lisdatasolutions.microrag.debug.feasibility.SuiteFixture
import com.lisdatasolutions.microrag.debug.feasibility.ThermalLevel
import com.lisdatasolutions.microrag.debug.feasibility.ThermalMonitor
import com.lisdatasolutions.microrag.debug.feasibility.ThermalSource
import com.lisdatasolutions.microrag.debug.feasibility.visual.report.VisualReport
import com.lisdatasolutions.microrag.domain.model.ComputeBackend
import com.lisdatasolutions.microrag.domain.model.ModelCatalog
import com.lisdatasolutions.microrag.domain.model.ModelFile
import com.lisdatasolutions.microrag.domain.model.ModelId
import java.io.File
import kotlinx.coroutines.CoroutineDispatcher

/**
 * Fakes for [VisualSuite] tests, on top of F02's [SuiteFixture] (repository, coordinator, text embedders and
 * clock). Time only moves when a fake engine "works"; answers carry a secret marker so tests can check that
 * no answer text reaches the log.
 */
class VisualFixture(
    dispatcher: CoroutineDispatcher,
    dir: File,
    present: Set<ModelId> = setOf(ModelId.GEMMA4_E2B, ModelId.EG2_440M, ModelId.EG2_270M),
    questions: Int = 1,
) {
    val base = SuiteFixture(dispatcher, present)
    /** One chronological stream with F02's fakes, so the order of loads and closes can be checked. */
    val events: MutableList<String> = base.events
    val reports = mutableListOf<VisualReport>()

    var gemmaInfo = VisionModelInfo(true, true, 280, listOf(70, 140, 280), ComputeBackend.entries, listOf(ComputeBackend.CPU))
    var embedderInfo = VisionModelInfo(true, true, 140, listOf(70, 140), ComputeBackend.entries, listOf(ComputeBackend.CPU))
    var thermalLevel: ThermalLevel
        get() = base.thermalLevel
        set(value) {
            base.thermalLevel = value
        }
    val failInit = mutableSetOf<String>()
    var onGenerate: suspend (Int) -> Unit = {}
    private var generations = 0

    val deviceSet: DeviceSet = buildDeviceSet(dir, questions)

    val visionFactory = object : VisionEngineFactory {
        override fun generator(file: ModelFile, path: File, backend: ComputeBackend, visionBackend: ComputeBackend, maxNumImages: Int) =
            FakeVisionGenerator(file.id, backend, visionBackend)

        override fun imageEmbedder(file: ModelFile, path: File, backend: ComputeBackend, visionTokensPerImage: Int) =
            FakeImageEmbedder(file.id, backend, visionTokensPerImage)

        override fun modelInfo(path: File): VisionModelInfo =
            if (path.name == ModelCatalog.byId(ModelId.GEMMA4_E2B).fileName) gemmaInfo else embedderInfo
    }

    val rasterizer = object : PageRasterizer {
        override fun pageCount(pdf: File) = 3

        override fun renderPage(pdf: File, page: Int, longSidePx: Int, format: ImageFormat, quality: Int): EncodedImage {
            events += "render ${pdf.name} $page $longSidePx $format"
            advance(20)
            return EncodedImage(ByteArray(longSidePx / 8), longSidePx * 7 / 10, longSidePx)
        }

        override fun scaleImage(image: File, longSidePx: Int, format: ImageFormat, quality: Int): EncodedImage {
            events += "scale ${image.name} $longSidePx"
            advance(15)
            return EncodedImage(ByteArray(longSidePx / 10), longSidePx, longSidePx * 3 / 4)
        }
    }

    fun suite(dispatcher: CoroutineDispatcher) = VisualSuite(
        coordinator = base.coordinator,
        textFactory = base.factory,
        visionFactory = visionFactory,
        rasterizer = rasterizer,
        repository = base.repository,
        deviceSetSource = { deviceSet },
        textsSource = { base.texts },
        thermal = ThermalMonitor(object : ThermalSource {
            override fun level() = base.thermalLevel
            override fun headroom() = 0.3f
        }),
        memory = object : MemoryProbe {
            override fun resetPeak() = true
            override fun peakRssMb() = 900L
        },
        device = object : DeviceInfoSource {
            override fun snapshot() = SuiteFixture.DEVICE
            override fun isCharging() = false
        },
        reportSink = { report -> reports += report; File("/reports/visual.json") },
        dispatchers = AppDispatchers(dispatcher, dispatcher, dispatcher, dispatcher, dispatcher, dispatcher),
        logger = com.lisdatasolutions.microrag.core.logging.DefaultAppLogger(
            isEnabled = true,
            sink = com.lisdatasolutions.microrag.core.logging.LogSink { _, _, message, _ -> base.logs += message },
        ),
        nanoTime = { base.clock },
        now = { "2026-11-05T10:00:00+01:00" },
    )

    val suite = suite(dispatcher)

    private fun advance(ms: Long) {
        base.clock += ms * 1_000_000
    }

    inner class FakeVisionGenerator(
        override val model: ModelId,
        override val backend: ComputeBackend,
        override val visionBackend: ComputeBackend,
    ) : VisionGenerator {
        override suspend fun initialize() {
            events += "vinit $model $backend vision=$visionBackend"
            advance(3000)
            if ("gemma vision=$visionBackend" in failInit) error("vision encoder unavailable")
        }

        override suspend fun generate(parts: List<VisionPart>, visualTokenBudget: Int?, maxOutputTokens: Int): VisionGeneration {
            val images = parts.count { it is VisionPart.Image }
            events += "vgen $visualTokenBudget $images"
            generations++
            onGenerate(generations)
            val first = 4000L + images * (visualTokenBudget ?: 280) * 3L
            advance(first + 500)
            return VisionGeneration("RESPUESTA-SECRETA $generations", first, first + 500, 1700 + images * (visualTokenBudget ?: 280), 450.0, 9.0)
        }

        override fun close() {
            events += "vclose $model $backend vision=$visionBackend"
        }
    }

    inner class FakeImageEmbedder(
        override val model: ModelId,
        override val backend: ComputeBackend,
        override val visionTokensPerImage: Int,
    ) : ImageEmbedder {
        override suspend fun initialize() {
            events += "iinit $model $backend $visionTokensPerImage"
            advance(400)
            if ("embedder $backend" in failInit) error("delegate unavailable")
        }

        override suspend fun embedImage(bytes: ByteArray): FloatArray {
            events += "iembed $visionTokensPerImage"
            advance(if (visionTokensPerImage == 70) 900 else 1600)
            return FloatArray(768) { i -> ((bytes.size + i * 31) % 17).toFloat() + 1f }
        }

        override fun close() {
            events += "iclose $model $backend $visionTokensPerImage"
        }
    }

    private fun buildDeviceSet(dir: File, questions: Int): DeviceSet {
        fun write(path: String, bytes: ByteArray) = File(dir, path).apply { parentFile.mkdirs(); writeBytes(bytes) }
        val images = (1..4).map { "images/i$it.png" to write("images/i$it.png", ByteArray(100 + it)) }
        val pdfs = listOf(
            DeviceSetPdf("pdfs/text.pdf", write("pdfs/text.pdf", byteArrayOf(1)), "text", listOf(1, 2)),
            DeviceSetPdf("pdfs/scan.pdf", write("pdfs/scan.pdf", byteArrayOf(2)), "scanned", listOf(1)),
        )
        val decode = listOf("decode/big.jpg" to write("decode/big.jpg", ByteArray(500)), "decode/alpha.png" to write("decode/alpha.png", ByteArray(50)))
        val pages = listOf("ctx/p1.png", "ctx/p2.png", "ctx/p3.png", "ctx/t1a.png", "ctx/t1b.png", "ctx/t2a.png", "ctx/t2b.png", "ctx/t3a.png", "ctx/t3b.png")
            .associateWith { write(it, ByteArray(64)) }
        fun layout(withText: Boolean, count: Int, tiles: Boolean): List<RawPart> = buildList {
            if (withText) add(RawPart(text = "[1] Bloque de texto"))
            (1..count).forEach { n ->
                add(RawPart(text = "[${n + 1}] Documento, página $n"))
                if (tiles) {
                    add(RawPart(image = "ctx/t${n}a.png"))
                    add(RawPart(image = "ctx/t${n}b.png"))
                } else {
                    add(RawPart(image = "ctx/p$n.png"))
                }
            }
            add(RawPart(text = "¿Pregunta?"))
        }
        val layouts = buildMap {
            put(PagesCombination.BASELINE_KEY, layout(withText = true, count = 0, tiles = false))
            listOf(false, true).forEach { withText ->
                (1..3).forEach { n ->
                    listOf(false, true).forEach { tiles ->
                        val family = if (withText) "mezcla" else "paginas"
                        put("$family-p$n-t${if (tiles) 1 else 0}", layout(withText, n, tiles))
                    }
                }
            }
        }
        val verified = images.toMap() + pdfs.associate { it.path to it.file } + decode.toMap() + pages
        return DeviceSet(
            present = true,
            verified = verified,
            shaOf = verified.keys.associateWith { "sha-$it" },
            rejected = emptyList(),
            parityPx = 1024,
            chosenTokens = 140,
            images = images,
            pdfs = pdfs,
            decode = decode,
            reading = listOf(ReadingItem("r01", layout(withText = false, count = 1, tiles = false)), ReadingItem("r02", layout(false, 2, false))),
            contexts = List(questions) { ContextItem("c%02d".format(it + 1), layouts) },
        )
    }
}
