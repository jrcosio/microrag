package com.lisdatasolutions.microrag.data.ingest

import com.google.common.truth.Truth.assertThat
import com.lisdatasolutions.microrag.data.device.ThermalLevel
import com.lisdatasolutions.microrag.data.indexing.ChunkEmbedder
import com.lisdatasolutions.microrag.data.indexing.EmbeddingSessions
import com.lisdatasolutions.microrag.data.indexing.FakeEmbeddingSessions
import com.lisdatasolutions.microrag.data.indexing.FakeThermalStatusSource
import com.lisdatasolutions.microrag.data.indexing.InMemoryIngestRunStore
import com.lisdatasolutions.microrag.data.indexing.PageEmbedder
import com.lisdatasolutions.microrag.data.indexing.PageEmbeddingSessions
import com.lisdatasolutions.microrag.data.indexing.RunPauseCause
import com.lisdatasolutions.microrag.domain.ingest.ImportLimits
import com.lisdatasolutions.microrag.domain.ingest.IngestError
import com.lisdatasolutions.microrag.domain.ingest.IngestProgress
import com.lisdatasolutions.microrag.domain.rag.DocumentStatus
import com.lisdatasolutions.microrag.domain.rag.IndexMode
import com.lisdatasolutions.microrag.domain.rag.NewDocument
import com.lisdatasolutions.microrag.rag.ingest.DocumentExtractor
import com.lisdatasolutions.microrag.rag.ingest.EncodedImage
import com.lisdatasolutions.microrag.rag.ingest.ImageFormat
import com.lisdatasolutions.microrag.rag.ingest.PageRasterizer
import com.lisdatasolutions.microrag.rag.runtime.EmbeddingMath
import com.lisdatasolutions.microrag.testing.FakePdfOpener
import com.lisdatasolutions.microrag.testing.FakeRagIndex
import com.lisdatasolutions.microrag.testing.RecordingLogger
import java.io.File
import java.io.IOException
import java.nio.file.Files
import kotlin.math.sqrt
import kotlin.random.Random
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Test

/** F19 T027 and T035 (contract I2, research R9 and R10): the page phase of the run. */
class IngestRunnerPagesTest {

    private val dir: File = Files.createTempDirectory("documents").toFile()
    private val pagesDir = File(dir, "pages")
    private val index = FakeRagIndex()
    private val logger = RecordingLogger()
    private val runStore = InMemoryIngestRunStore()
    private val thermal = FakeThermalStatusSource()
    private val text = TrackingTextSessions()
    private val rasterizer = FakeRasterizer()
    private val images = FakePageSessions()
    private val files = PageFileStore(pagesDir, Dispatchers.IO)
    private var modeOn = true

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    /** The text sessions of F07, counting the ones open now. */
    private inner class TrackingTextSessions : EmbeddingSessions {
        val inner = FakeEmbeddingSessions()

        @Volatile
        var open = 0

        override suspend fun modelReady() = inner.modelReady()

        override suspend fun <R> use(block: suspend (ChunkEmbedder) -> R): R {
            assertThat(images.open).isEqualTo(0)
            open++
            return try {
                inner.use(block)
            } finally {
                open--
            }
        }
    }

    /** The 440M's image engine: a raw 768D vector seeded by the image, and never beside the text engine. */
    private inner class FakePageSessions : PageEmbeddingSessions {
        @Volatile
        var open = 0
        var opened = 0
        var ready = true
        val embedded = mutableListOf<String>()
        var beforeEmbedding: () -> Unit = {}

        override suspend fun modelReady() = ready

        override suspend fun <R> use(block: suspend (PageEmbedder) -> R): R {
            assertThat(text.open).isEqualTo(0)
            open++
            opened++
            return try {
                block { bytes ->
                    beforeEmbedding()
                    embedded += String(bytes)
                    val random = Random(String(bytes).hashCode())
                    FloatArray(RAW_DIMENSIONS) { random.nextFloat() * 2f - 1f }
                }
            } finally {
                open--
            }
        }
    }

    private class FakeRasterizer : PageRasterizer {
        var pages = 3
        val rendered = mutableListOf<Int>()

        override fun pageCount(pdf: File) = pages

        override fun renderPage(pdf: File, page: Int, longSidePx: Int, format: ImageFormat, quality: Int): EncodedImage {
            check(longSidePx == 768 && format == ImageFormat.JPEG && quality == 85)
            rendered += page
            return EncodedImage("${pdf.name}-page-$page".toByteArray(), 543, 768)
        }

        override fun scaleImage(image: File, longSidePx: Int, format: ImageFormat, quality: Int): EncodedImage {
            if (image.readText().startsWith("broken")) throw IOException("cannot decode")
            return EncodedImage("${image.name}-image".toByteArray(), 768, 512)
        }
    }

    private fun runner(freeBytes: () -> Long = { Long.MAX_VALUE }) = IngestRunner(
        index, DocumentExtractor(FakePdfOpener()), text, runStore, thermal, freeBytes, Dispatchers.IO, logger,
        clock = System::currentTimeMillis, progressIntervalMs = 10,
        pages = IngestRunner.PagePhase(images, rasterizer, files, enabled = { modeOn }),
    )

    private suspend fun pdf(name: String, mode: IndexMode, vararg pageTexts: String): Long {
        val file = File(dir, name).apply { writeBytes(FakePdfOpener.pdf(*pageTexts)) }
        return index.addDocument(
            NewDocument(
                displayName = name, contentHash = "hash-$name", mimeType = "application/pdf", pageCount = pageTexts.size,
                localPath = file.path, indexMode = mode,
            ),
        )
    }

    private suspend fun image(name: String, content: String = "jpg"): Long {
        val file = File(dir, name).apply { writeText(content) }
        return index.addDocument(
            NewDocument(displayName = name, contentHash = "hash-$name", mimeType = "image/jpeg", pageCount = 1, localPath = file.path, indexMode = IndexMode.VISUAL),
        )
    }

    private val manual = arrayOf(
        "Cambio de aceite cada 500 horas de uso del motor.",
        "Revisión de los filtros del aire y del combustible.",
        "Tabla de presiones de los neumáticos delanteros.",
    )

    @Test
    fun visual_storesEveryPage_withoutChunks_andEndsDone() = runBlocking<Unit> {
        val id = pdf("plano.pdf", IndexMode.VISUAL, *manual)

        runner().process(listOf(id)) { }

        val document = index.docs.getValue(id)
        assertThat(document.status).isEqualTo(DocumentStatus.DONE)
        assertThat(document.chunkCount).isEqualTo(0)
        assertThat(index.chunks[id].orEmpty()).isEmpty()
        assertThat(index.pages.getValue(id).keys).containsExactly(1, 2, 3).inOrder()
        assertThat(text.inner.embedder.embedded).isEmpty()
        assertThat(images.opened).isEqualTo(1)
    }

    @Test
    fun both_storesTheTextThenThePages_andIsDoneOnlyWithBoth() = runBlocking<Unit> {
        val id = pdf("manual.pdf", IndexMode.BOTH, *manual)
        val statusAtFirstPage = mutableListOf<DocumentStatus>()
        images.beforeEmbedding = { if (statusAtFirstPage.isEmpty()) statusAtFirstPage += index.docs.getValue(id).status }

        runner().process(listOf(id)) { }

        // While its pages are embedded, it is not searchable yet.
        assertThat(statusAtFirstPage).containsExactly(DocumentStatus.EMBEDDING)
        val document = index.docs.getValue(id)
        assertThat(document.status).isEqualTo(DocumentStatus.DONE)
        assertThat(document.chunkCount).isGreaterThan(0)
        assertThat(index.chunks.getValue(id)).hasSize(document.chunkCount!!)
        assertThat(index.pages.getValue(id)).hasSize(3)
    }

    @Test
    fun text_isExactlyAsBefore() = runBlocking<Unit> {
        val id = pdf("texto.pdf", IndexMode.TEXT, *manual)

        runner().process(listOf(id)) { }

        assertThat(index.docs.getValue(id).status).isEqualTo(DocumentStatus.DONE)
        assertThat(index.pages[id].orEmpty()).isEmpty()
        assertThat(images.opened).isEqualTo(0)
        assertThat(rasterizer.rendered).isEmpty()
        assertThat(pagesDir.exists()).isFalse()
    }

    @Test
    fun theVectors_are256DAndNormalized_andTheFilesAreRenamedIntoPlace() = runBlocking<Unit> {
        val id = pdf("plano.pdf", IndexMode.VISUAL, *manual)

        runner().process(listOf(id)) { }

        val stored = index.pages.getValue(id).values
        stored.forEach { page ->
            assertThat(page.embedding.size).isEqualTo(EmbeddingMath.DIMENSIONS)
            assertThat(sqrt(page.embedding.sumOf { (it * it).toDouble() })).isWithin(1e-4).of(1.0)
            assertThat(page.fileName).isEqualTo("hash-plano_pdf-p${page.page}.jpg")
            assertThat(File(pagesDir, page.fileName).readText()).isEqualTo("plano.pdf-page-${page.page}")
            assertThat(page.widthPx to page.heightPx).isEqualTo(543 to 768)
        }
        assertThat(pagesDir.list()!!.toList()).containsExactlyElementsIn(stored.map { it.fileName })
    }

    @Test
    fun theProgress_isInPages_withTheirRate() = runBlocking<Unit> {
        val id = pdf("plano.pdf", IndexMode.VISUAL, *manual)
        val progress = mutableListOf<IngestProgress>()

        runner().process(listOf(id)) { progress += it }

        val pages = progress.filter { it.stage == IngestProgress.Stage.PAGES }
        assertThat(pages).isNotEmpty()
        assertThat(pages.first().pagesDone).isEqualTo(0)
        assertThat(pages.all { it.pagesTotal == 3 }).isTrue()
        assertThat(progress.last { it.stage == IngestProgress.Stage.CLOSING }.pagesDone).isEqualTo(3)
    }

    @Test
    fun aStoppedDocument_resumesFromItsStoredPages() = runBlocking<Unit> {
        val id = pdf("plano.pdf", IndexMode.VISUAL, *manual)
        var embedded = 0
        images.beforeEmbedding = { if (++embedded == 2) throw kotlinx.coroutines.CancellationException("killed") }
        runCatching { runner().process(listOf(id)) { } }
        assertThat(index.pages.getValue(id).keys).containsExactly(1)

        images.beforeEmbedding = {}
        rasterizer.rendered.clear()
        runner().process(listOf(id)) { }

        assertThat(rasterizer.rendered).containsExactly(2, 3).inOrder()
        assertThat(index.docs.getValue(id).status).isEqualTo(DocumentStatus.DONE)
    }

    @Test
    fun withTheModeOff_documentsWithPagesWait_andTheTextOnesGoOn() = runBlocking<Unit> {
        modeOn = false
        val visual = pdf("plano.pdf", IndexMode.VISUAL, *manual)
        val both = pdf("manual.pdf", IndexMode.BOTH, *manual)
        val plain = pdf("texto.pdf", IndexMode.TEXT, *manual)
        runStore.edit { it.adding(listOf(visual, both, plain)) }

        runner().process(listOf(visual, both, plain)) { }

        assertThat(index.docs.getValue(plain).status).isEqualTo(DocumentStatus.DONE)
        assertThat(index.docs.getValue(visual).status).isEqualTo(DocumentStatus.PENDING)
        assertThat(index.docs.getValue(both).status).isEqualTo(DocumentStatus.PENDING)
        // They stay in the run, so turning the mode on finishes them.
        assertThat(runStore.run.first().documentIds).containsAtLeast(visual, both)

        modeOn = true
        runner().process(emptyList()) { }
        assertThat(index.docs.getValue(visual).status).isEqualTo(DocumentStatus.DONE)
        assertThat(index.docs.getValue(both).status).isEqualTo(DocumentStatus.DONE)
        assertThat(runStore.run.first().documentIds).isEmpty()
    }

    @Test
    fun aHotPhone_waitsBetweenPages() = runBlocking<Unit> {
        val id = pdf("plano.pdf", IndexMode.VISUAL, *manual)
        var embedded = 0
        images.beforeEmbedding = {
            if (++embedded == 1) thermal.level.value = ThermalLevel.SEVERE_OR_WORSE
        }
        val progress = mutableListOf<IngestProgress>()
        val cooled = Thread {
            Thread.sleep(300)
            thermal.level.value = ThermalLevel.NORMAL
        }.apply { start() }

        runner().process(listOf(id)) { progress += it }
        cooled.join()

        assertThat(progress.any { it.stage == IngestProgress.Stage.PAGES && it.waiting == IngestProgress.WaitReason.THERMAL }).isTrue()
        assertThat(index.docs.getValue(id).status).isEqualTo(DocumentStatus.DONE)
        // The engine was released while it cooled down, then opened again.
        assertThat(images.opened).isEqualTo(2)
    }

    @Test
    fun withoutRoomForAPage_theRunPausesForSpace() = runBlocking<Unit> {
        val id = pdf("plano.pdf", IndexMode.VISUAL, *manual)

        runner(freeBytes = { ImportLimits.SPACE_MARGIN_BYTES + IngestRunner.PAGE_SPACE_BYTES - 1 }).process(listOf(id)) { }

        assertThat(runStore.run.first().pauseCause).isEqualTo(RunPauseCause.NO_SPACE)
        assertThat(index.pages[id].orEmpty()).isEmpty()
        assertThat(index.docs.getValue(id).status).isEqualTo(DocumentStatus.EMBEDDING)
    }

    @Test
    fun aFullIndex_keepsNoImageWithoutItsRow() = runBlocking<Unit> {
        val id = pdf("plano.pdf", IndexMode.VISUAL, *manual)
        index.failNextPageWithFullDisk = true

        runner().process(listOf(id)) { }

        assertThat(runStore.run.first().pauseCause).isEqualTo(RunPauseCause.NO_SPACE)
        assertThat(pagesDir.list().orEmpty()).isEmpty()
    }

    @Test
    fun withoutThe440M_theRunPausesForTheModel() = runBlocking<Unit> {
        images.ready = false
        val id = pdf("plano.pdf", IndexMode.VISUAL, *manual)

        runner().process(listOf(id)) { }

        assertThat(runStore.run.first().pauseCause).isEqualTo(RunPauseCause.MODEL_MISSING)
        assertThat(index.docs.getValue(id).status).isEqualTo(DocumentStatus.PARSED)
    }

    // T035 (research R10, FR-011).

    @Test
    fun aPdfWithoutText_inBoth_endsWithOnlyItsPages() = runBlocking<Unit> {
        val id = pdf("escaneado.pdf", IndexMode.BOTH, "", "", "")

        runner().process(listOf(id)) { }

        val document = index.docs.getValue(id)
        assertThat(document.status).isEqualTo(DocumentStatus.DONE)
        assertThat(document.error).isNull()
        assertThat(document.chunkCount).isEqualTo(0)
        assertThat(index.pages.getValue(id)).hasSize(3)
    }

    @Test
    fun aPdfWithoutText_inText_isStillNoText() = runBlocking<Unit> {
        val id = pdf("escaneado.pdf", IndexMode.TEXT, "", "", "")

        runner().process(listOf(id)) { }

        assertThat(index.docs.getValue(id).status).isEqualTo(DocumentStatus.ERROR)
        assertThat(index.docs.getValue(id).error).isEqualTo(IngestError.NO_TEXT.code)
    }

    @Test
    fun anImage_isOnePage_andAnUnreadableOneFails() = runBlocking<Unit> {
        val photo = image("foto.jpg")
        val broken = image("rota.jpg", content = "broken")

        runner().process(listOf(photo, broken)) { }

        assertThat(index.docs.getValue(photo).status).isEqualTo(DocumentStatus.DONE)
        assertThat(index.pages.getValue(photo).keys).containsExactly(1)
        assertThat(images.embedded).contains("foto.jpg-image")
        assertThat(index.docs.getValue(broken).status).isEqualTo(DocumentStatus.ERROR)
        assertThat(index.docs.getValue(broken).error).isEqualTo(IngestError.IMAGE_UNREADABLE.code)
    }

    @Test
    fun aShadowInOtherMode_replacesTheOriginalsPages_andTheirFilesGo() = runBlocking<Unit> {
        val original = pdf("plano.pdf", IndexMode.VISUAL, *manual)
        runner().process(listOf(original)) { }
        val oldFiles = index.pages.getValue(original).values.map { it.fileName }
        val shadow = index.addReindexShadow(original)
        index.setIndexMode(shadow, IndexMode.BOTH)

        runner().process(listOf(shadow)) { }

        assertThat(index.docs.containsKey(shadow)).isFalse()
        val published = index.docs.getValue(original)
        assertThat(published.indexMode).isEqualTo(IndexMode.BOTH)
        assertThat(published.chunkCount).isGreaterThan(0)
        assertThat(index.pages.getValue(original)).hasSize(3)
        oldFiles.forEach { assertThat(File(pagesDir, it).exists()).isFalse() }
        index.pages.getValue(original).values.forEach { assertThat(File(pagesDir, it.fileName).exists()).isTrue() }
    }

    private companion object {
        const val RAW_DIMENSIONS = 768
    }
}
