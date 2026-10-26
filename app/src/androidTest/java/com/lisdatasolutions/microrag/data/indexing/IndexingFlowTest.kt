package com.lisdatasolutions.microrag.data.indexing

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.lisdatasolutions.microrag.data.device.ThermalLevel
import com.lisdatasolutions.microrag.data.ingest.DocumentFileStore
import com.lisdatasolutions.microrag.data.ingest.DocumentImporter
import com.lisdatasolutions.microrag.data.ingest.ImportResult
import com.lisdatasolutions.microrag.data.ingest.IngestRunner
import com.lisdatasolutions.microrag.data.ingest.PlatformPdfOpener
import com.lisdatasolutions.microrag.data.ingest.SafDocumentSource
import com.lisdatasolutions.microrag.domain.ingest.DocumentFormat
import com.lisdatasolutions.microrag.domain.ingest.IngestProgress
import com.lisdatasolutions.microrag.domain.rag.DocumentStatus
import com.lisdatasolutions.microrag.domain.rag.StoredDocument
import com.lisdatasolutions.microrag.rag.ingest.Chunk
import com.lisdatasolutions.microrag.rag.ingest.Chunker
import com.lisdatasolutions.microrag.rag.ingest.DocumentExtractor
import com.lisdatasolutions.microrag.rag.ingest.PdfOpener
import com.lisdatasolutions.microrag.rag.ingest.PdfPages
import com.lisdatasolutions.microrag.rag.runtime.EmbeddingMath
import com.lisdatasolutions.microrag.testing.CapturingLogger
import com.lisdatasolutions.microrag.testing.TempRagIndex
import java.io.File
import java.util.UUID
import kotlin.math.abs
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * F07 T023 on the device: real import, extraction, SQLite-Vector index and run, with a deterministic
 * embedder. Covers SC-002 (interrupted at three points, same chunks), SC-003 (nothing half indexed is
 * found) and the removal and heat paths. The index and the copies are temporary.
 */
@RunWith(AndroidJUnit4::class)
class IndexingFlowTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val root = File(context.cacheDir, "indexing-flow-${UUID.randomUUID()}")
    private val sources = File(root, "picked").apply { mkdirs() }
    private val temp = TempRagIndex()
    private val logger = CapturingLogger()
    private val files = DocumentFileStore(File(root, "documents"))
    private val pdf = PlatformPdfOpener()
    private val source = SafDocumentSource(context.contentResolver)
    private val importer = DocumentImporter(source, files, pdf, temp.index, { Long.MAX_VALUE / 2 }, logger)
    private val runStore = InMemoryIngestRunStore()
    private val thermal = FakeThermalStatusSource()

    @After
    fun tearDown() {
        temp.close()
        root.deleteRecursively()
    }

    private fun runner(sessions: EmbeddingSessions, opener: PdfOpener = pdf) = IngestRunner(
        temp.index, DocumentExtractor(opener), sessions, runStore, thermal, { Long.MAX_VALUE }, Dispatchers.IO, logger,
    )

    private fun picked(name: String, bytes: ByteArray? = null): String {
        val file = File(sources, name)
        if (bytes != null) file.writeBytes(bytes) else instrumentation.context.assets.open("pdf/$name").use { input -> file.outputStream().use(input::copyTo) }
        return Uri.fromFile(file).toString()
    }

    private fun import(uri: String): Long = runBlocking { (importer.import(uri, source.describe(uri)) as ImportResult.Imported).documentId }

    private fun document(id: Long): StoredDocument = runBlocking { temp.index.document(id)!! }

    /** The chunks the app's chunker makes of a document's private copy. */
    private fun chunking(id: Long): List<Chunk> {
        val doc = document(id)
        val out = ArrayList<Chunk>()
        val session = Chunker().Session()
        DocumentExtractor(pdf).extract(File(doc.localPath!!), DocumentFormat.fromMime(doc.mimeType)!!) { session.add(it, out::add) }
        session.finish(out::add)
        return out
    }

    private fun assertIndexedLikeTheChunking(id: Long) {
        val expected = chunking(id)
        val doc = document(id)
        assertWithMessage("document $id").that(doc.status).isEqualTo(DocumentStatus.DONE)
        assertWithMessage("document $id").that(doc.chunkCount).isEqualTo(expected.size)
        val stored = runBlocking { temp.index.storedChunkContents(id) }
        assertWithMessage("document $id ordinals").that(stored.keys.toList()).isEqualTo(expected.indices.toList())
        assertWithMessage("document $id contents").that(stored.values.toList()).isEqualTo(expected.map { it.content })
        val vectors = temp.withRaw { db ->
            db.query("SELECT embedding FROM chunks WHERE document_id = ?", id) { it.getBlob(0) }
        }
        vectors.forEach { blob ->
            val vector = java.nio.ByteBuffer.wrap(blob).order(java.nio.ByteOrder.LITTLE_ENDIAN).asFloatBuffer().let { b -> FloatArray(b.remaining()).also(b::get) }
            assertThat(vector.size).isEqualTo(EmbeddingMath.DIMENSIONS)
            assertThat(abs(EmbeddingMath.l2Norm(vector) - 1.0)).isLessThan(1e-3)
        }
    }

    @Test
    fun textMarkdownAndPdf_endIndexedLikeTheirChunking() = runBlocking<Unit> {
        val txt = import(picked("notas.txt", "Primera nota.\r\n\r\nSegunda nota con más texto.".toByteArray()))
        val md = import(picked("guia.md", "# Instalación\nCopia el archivo.\n## Requisitos\nAndroid 12.".toByteArray()))
        val ids = mutableListOf(txt, md)
        if (pdf.isAvailable) ids += import(picked("text_3_pages.pdf"))

        runner(FakeEmbeddingSessions()).process(ids) { }

        ids.forEach(::assertIndexedLikeTheChunking)
    }

    @Test
    fun nothingHalfIndexedIsEverFound() = runBlocking<Unit> {
        assumeTrue(pdf.isAvailable)
        val done = import(picked("guia.md", "# Guía\nEl manual de la bomba.".toByteArray()))
        runner(FakeEmbeddingSessions()).process(listOf(done)) { }
        val long = import(picked("text_200_pages.pdf"))
        val sessions = FakeEmbeddingSessions()
        val seen = mutableListOf<Long>()
        var calls = 0
        sessions.embedder.beforeEmbedding = {
            if (++calls % 50 == 0) runBlocking {
                val hits = temp.index.vectorSearch(FakeTextEmbedder.vectorOf("consulta $calls"), k = 50).map { it.id }
                seen += temp.index.loadChunks(hits).values.map { it.documentId }
            }
        }

        runner(sessions).process(listOf(long)) { }

        assertThat(calls).isGreaterThan(100)
        assertThat(seen).isNotEmpty()
        assertWithMessage("a search during indexing returned the document being indexed").that(seen).doesNotContain(long)
        assertIndexedLikeTheChunking(long)
    }

    @Test
    fun interruptedAtThreePoints_endsWithTheSameChunks() = runBlocking<Unit> {
        assumeTrue(pdf.isAvailable)
        val long = import(picked("text_200_pages.pdf"))

        // 1. While chunking (phase 1): page 20 is held until the work is cancelled.
        val reachedPage = CompletableDeferred<Unit>()
        val slow = object : PdfOpener {
            override val isAvailable = true
            override fun open(file: File): PdfPages {
                val pages = pdf.open(file)
                return object : PdfPages by pages {
                    override fun pageText(index: Int): String {
                        if (index == 20 && !reachedPage.isCompleted) {
                            reachedPage.complete(Unit)
                            Thread.sleep(500)
                        }
                        return pages.pageText(index)
                    }
                }
            }
        }
        val chunking = launch(Dispatchers.Default) { runner(FakeEmbeddingSessions(), slow).process(listOf(long)) { } }
        withTimeout(60_000) { reachedPage.await() }
        chunking.cancelAndJoin()
        assertThat(document(long).status).isEqualTo(DocumentStatus.PENDING)

        // 2. In the middle of the embeddings.
        val midway = FakeEmbeddingSessions()
        var calls = 0
        lateinit var embedding: Job
        midway.embedder.beforeEmbedding = { if (++calls == 150) embedding.cancel() }
        embedding = launch(Dispatchers.Default) { runner(midway).process(listOf(long)) { } }
        withTimeout(120_000) { embedding.join() }
        assertThat(document(long).status).isEqualTo(DocumentStatus.EMBEDDING)
        val stored = temp.index.storedChunkCounts(listOf(long)).getValue(long)
        assertThat(stored).isAtLeast(149)
        assertThat(stored).isAtMost(150)

        // 3. At closing: every chunk is stored but the document is not done yet.
        lateinit var closing: Job
        closing = launch(Dispatchers.Default) {
            runner(FakeEmbeddingSessions()).process(listOf(long)) { if (it.stage == IngestProgress.Stage.CLOSING) closing.cancel() }
        }
        withTimeout(120_000) { closing.join() }
        assertThat(document(long).status).isEqualTo(DocumentStatus.EMBEDDING)
        assertThat(temp.index.storedChunkCounts(listOf(long)).getValue(long)).isEqualTo(430)

        val last = FakeEmbeddingSessions()
        runner(last).process(listOf(long)) { }
        assertThat(last.embedder.embedded).isEmpty()
        assertIndexedLikeTheChunking(long)
        assertThat(document(long).chunkCount).isEqualTo(430)
    }

    @Test
    fun removingADocumentMidway_dropsItsChunksAndTheNextGoesOn() = runBlocking<Unit> {
        assumeTrue(pdf.isAvailable)
        val removed = import(picked("text_200_pages.pdf"))
        val next = import(picked("guia.md", "# Guía\nTexto de la guía.".toByteArray()))
        val sessions = FakeEmbeddingSessions()
        var calls = 0
        sessions.embedder.beforeEmbedding = { if (++calls == 100) runBlocking { temp.index.deleteDocument(removed) } }

        runner(sessions).process(listOf(removed, next)) { }

        assertThat(runBlocking { temp.index.document(removed) }).isNull()
        assertThat(temp.withRaw { db -> db.queryOne("SELECT count(*) FROM chunks WHERE document_id = ?", removed) { it.getLong(0) } }).isEqualTo(0)
        assertIndexedLikeTheChunking(next)
    }

    @Test
    fun severeHeat_pausesWithTheEngineReleasedAndGoesOnWhenCooler() = runBlocking<Unit> {
        assumeTrue(pdf.isAvailable)
        val long = import(picked("text_200_pages.pdf"))
        val sessions = FakeEmbeddingSessions()
        var calls = 0
        sessions.embedder.beforeEmbedding = { if (++calls == 80) thermal.level.value = ThermalLevel.SEVERE_OR_WORSE }
        val waited = CompletableDeferred<Unit>()

        val job = launch(Dispatchers.Default) {
            runner(sessions).process(listOf(long)) { if (it.waiting == IngestProgress.WaitReason.THERMAL) waited.complete(Unit) }
        }
        withTimeout(60_000) { waited.await() }
        assertThat(document(long).status).isEqualTo(DocumentStatus.EMBEDDING)
        thermal.level.value = ThermalLevel.MODERATE
        withTimeout(120_000) { job.join() }

        assertThat(sessions.opened).isEqualTo(2)
        assertIndexedLikeTheChunking(long)
    }
}
