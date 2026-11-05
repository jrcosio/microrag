package com.lisdatasolutions.microrag.data.library

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.lisdatasolutions.microrag.data.indexing.DefaultIndexingRepository
import com.lisdatasolutions.microrag.data.indexing.FakeEmbeddingSessions
import com.lisdatasolutions.microrag.data.indexing.FakeThermalStatusSource
import com.lisdatasolutions.microrag.data.indexing.InMemoryIngestRunStore
import com.lisdatasolutions.microrag.data.indexing.PageEmbedder
import com.lisdatasolutions.microrag.data.indexing.PageEmbeddingSessions
import com.lisdatasolutions.microrag.data.ingest.DocumentFileStore
import com.lisdatasolutions.microrag.data.ingest.DocumentFiles
import com.lisdatasolutions.microrag.data.ingest.IngestRunner
import com.lisdatasolutions.microrag.data.ingest.IngestScheduler
import com.lisdatasolutions.microrag.data.ingest.IngestWork
import com.lisdatasolutions.microrag.data.ingest.PageFileStore
import com.lisdatasolutions.microrag.data.ingest.PlatformPdfOpener
import com.lisdatasolutions.microrag.domain.ingest.IngestError
import com.lisdatasolutions.microrag.domain.ingest.IngestProgress
import com.lisdatasolutions.microrag.domain.rag.DocumentStatus
import com.lisdatasolutions.microrag.domain.rag.IndexMode
import com.lisdatasolutions.microrag.domain.rag.NewDocument
import com.lisdatasolutions.microrag.domain.rag.PageInput
import com.lisdatasolutions.microrag.rag.ingest.DocumentExtractor
import com.lisdatasolutions.microrag.rag.ingest.EncodedImage
import com.lisdatasolutions.microrag.rag.ingest.ImageFormat
import com.lisdatasolutions.microrag.rag.ingest.PageRasterizer
import com.lisdatasolutions.microrag.testing.TempRagIndex
import com.lisdatasolutions.microrag.testing.TempRagIndex.Companion.unitVector
import java.io.File
import java.util.UUID
import kotlin.random.Random
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith

/**
 * F19 T060 (SC-005, research R11, analyze L8 and M6): on a real index and real files, deleting, removing from the
 * index, reindexing in another mode, discarding a failed reindex and the startup sweep leave no image without its
 * row and no row without its image.
 */
@RunWith(AndroidJUnit4::class)
class PageFilesCleanupTest {

    private val temp = TempRagIndex()
    private val index = temp.index
    private val root = File(TempRagIndex.context.cacheDir, "page-files-${UUID.randomUUID()}").apply { mkdirs() }
    private val pagesDir = File(root, "pages")
    private val pages = PageFileStore(pagesDir, Dispatchers.IO)
    private val documents = DocumentFileStore(File(root, "documents"))
    private val runStore = InMemoryIngestRunStore()

    private val library = DefaultLibraryRepository(
        index = index,
        files = DocumentFiles(documents, index, Dispatchers.IO, temp.logger),
        runStore = runStore,
        indexing = DefaultIndexingRepository(index, StubScheduler(), runStore, FakeThermalStatusSource(), Dispatchers.IO, temp.logger),
        io = Dispatchers.IO,
        logger = temp.logger,
        pageFiles = pages,
    )

    @After
    fun tearDown() {
        temp.close()
        root.deleteRecursively()
    }

    /** A done document with its private copy and [count] stored pages with their images. */
    private suspend fun visual(name: String, count: Int): Long {
        val copy = File(root, "documents/$name").apply { parentFile!!.mkdirs(); writeText("Texto de $name para el troceado.") }
        val id = index.addDocument(
            NewDocument(displayName = name, contentHash = "hash-$name", mimeType = "text/plain", pageCount = count, localPath = copy.path, indexMode = IndexMode.VISUAL),
        )
        storePages(id, count, "hash-$name")
        index.updateStatus(id, DocumentStatus.DONE, chunkCount = 0)
        return id
    }

    private suspend fun storePages(id: Long, count: Int, hash: String) = (1..count).forEach { page ->
        val name = PageFileStore.nameOf(hash, page)
        pages.write(name, byteArrayOf(page.toByte())) { index.insertPage(id, PageInput(page, name, 10, 10, 1, unitVector(id.toInt() * 100 + page))) }
    }

    private fun images(): Set<String> = pagesDir.list().orEmpty().toSet()

    /** Every image has its row and every row its image. */
    private suspend fun assertConsistent() {
        assertThat(images()).isEqualTo(index.allPageFiles())
    }

    @Test
    fun deleting_leavesNoImage() = runBlocking<Unit> {
        index.open()
        val id = visual("plano.txt", 3)
        visual("otro.txt", 2)

        library.delete(id)

        assertThat(images()).hasSize(2)
        assertConsistent()
    }

    @Test
    fun removingFromTheIndex_leavesNoImage() = runBlocking<Unit> {
        index.open()
        val id = visual("plano.txt", 3)

        library.removeFromIndex(id)

        assertThat(images()).isEmpty()
        assertConsistent()
        assertThat(index.document(id)!!.status).isEqualTo(DocumentStatus.OUT_OF_INDEX)
    }

    @Test
    fun discardingAFailedReindex_takesOnlyTheShadowsImages() = runBlocking<Unit> {
        index.open()
        val id = visual("plano.txt", 2)
        val before = images()
        val shadow = index.addReindexShadow(id)
        storePages(shadow, 2, "hash-plano.txt#reindex")
        index.updateStatus(shadow, DocumentStatus.ERROR, error = IngestError.EMBEDDING_FAILED.code)

        library.discardReindex(id)

        assertThat(images()).isEqualTo(before)
        assertConsistent()
    }

    @Test
    fun reindexingInBoth_replacesTheImages_andKeepsNoOldOne() = runBlocking<Unit> {
        index.open()
        val id = visual("plano.txt", 2)
        val old = images()

        library.reindex(id, IndexMode.BOTH)
        val shadow = checkNotNull(index.shadowOf(id)).id
        IngestRunner(
            index, DocumentExtractor(PlatformPdfOpener()), FakeEmbeddingSessions(), runStore, FakeThermalStatusSource(), { Long.MAX_VALUE },
            Dispatchers.IO, temp.logger, pages = IngestRunner.PagePhase(StubImages(), StubRasterizer(), pages, enabled = { true }),
        ).process(listOf(shadow)) { }

        val published = index.document(id)!!
        assertThat(published.indexMode).isEqualTo(IndexMode.BOTH)
        assertThat(published.chunkCount).isGreaterThan(0)
        assertThat(index.storedPages(id)).containsExactly(1, 2)
        assertThat(images().intersect(old)).isEmpty()
        assertConsistent()
    }

    @Test
    fun theSweep_takesImagesNoRowNames_andLeftoversOfAWrite() = runBlocking<Unit> {
        index.open()
        visual("plano.txt", 2)
        File(pagesDir, "huerfana-p1.jpg").writeText("x")
        File(pagesDir, "a-medias-p1.jpg.part").writeText("x")

        assertThat(pages.sweep { index.allPageFiles() }).isEqualTo(2)

        assertConsistent()
    }

    private class StubImages : PageEmbeddingSessions {
        override suspend fun modelReady() = true

        override suspend fun <R> use(block: suspend (PageEmbedder) -> R): R = block { bytes ->
            val random = Random(bytes.contentHashCode())
            FloatArray(768) { random.nextFloat() * 2f - 1f }
        }
    }

    private class StubRasterizer : PageRasterizer {
        override fun pageCount(pdf: File) = 2

        override fun renderPage(pdf: File, page: Int, longSidePx: Int, format: ImageFormat, quality: Int) =
            EncodedImage("${pdf.name}-$page".toByteArray(), 543, 768)

        override fun scaleImage(image: File, longSidePx: Int, format: ImageFormat, quality: Int) = renderPage(image, 1, longSidePx, format, quality)
    }

    private class StubScheduler : IngestScheduler {
        override val activeDocumentIds = MutableStateFlow<Set<Long>>(emptySet())
        override val progress = MutableStateFlow<IngestProgress?>(null)
        override val work = MutableStateFlow(IngestWork.NONE)
        override suspend fun enqueue(documentIds: List<Long>) = Unit
        override suspend fun replace(documentIds: List<Long>) = Unit
        override suspend fun cancel() = Unit
    }
}
