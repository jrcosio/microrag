package com.lisdatasolutions.microrag.data.ingest

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.lisdatasolutions.microrag.domain.ingest.IngestError
import com.lisdatasolutions.microrag.domain.rag.DocumentStatus
import com.lisdatasolutions.microrag.rag.ingest.DocumentExtractor
import com.lisdatasolutions.microrag.rag.ingest.PdfOpener
import com.lisdatasolutions.microrag.rag.ingest.PdfPages
import com.lisdatasolutions.microrag.testing.CapturingLogger
import com.lisdatasolutions.microrag.testing.TempRagIndex
import java.io.File
import java.util.UUID
import com.lisdatasolutions.microrag.domain.ingest.IngestProgress
import com.lisdatasolutions.microrag.domain.ingest.QueueItemState
import com.lisdatasolutions.microrag.domain.ingest.QueueKey
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * SC-003 on the device: the fixtures go through the real import (copy, hash, system PDF reader,
 * SQLite) and the real extraction and chunking, each ending in its state (F06 T056). The index and the
 * copies are temporary; the app's own index is never touched.
 */
@RunWith(AndroidJUnit4::class)
class IngestFlowTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val root = File(context.cacheDir, "ingest-flow-${UUID.randomUUID()}")
    private val sources = File(root, "picked").apply { mkdirs() }
    private val temp = TempRagIndex()
    private val logger = CapturingLogger()
    private val files = DocumentFileStore(File(root, "documents"))
    private val pdf = PlatformPdfOpener()
    private val source = SafDocumentSource(context.contentResolver)
    private val importer = DocumentImporter(source, files, pdf, temp.index, { Long.MAX_VALUE / 2 }, logger)
    private val runner = IngestRunner(temp.index, DocumentExtractor(pdf), Dispatchers.IO, logger)

    @After
    fun tearDown() {
        temp.close()
        root.deleteRecursively()
    }

    private fun picked(name: String, bytes: ByteArray? = null): String {
        val file = File(sources, name)
        if (bytes != null) file.writeBytes(bytes) else instrumentation.context.assets.open("pdf/$name").use { input -> file.outputStream().use(input::copyTo) }
        return Uri.fromFile(file).toString()
    }

    private fun import(uri: String): ImportResult = runBlocking { importer.import(uri, source.describe(uri)) }

    private fun id(result: ImportResult): Long = (result as ImportResult.Imported).documentId

    @Test
    fun everyFixtureEndsInItsState() = runBlocking<Unit> {
        val txtUri = picked("notas.txt", "Primera nota.\r\n\r\nSegunda nota con más texto.".toByteArray())
        val mdUri = picked("guia.md", "# Instalación\nCopia el archivo.\n## Requisitos\nAndroid 12.".toByteArray())
        val txt = id(import(txtUri))
        val md = id(import(mdUri))
        File(Uri.parse(txtUri).path!!).delete() // US1-3: the private copy is what gets processed
        val processed = mutableListOf(txt, md)

        if (pdf.isAvailable) {
            val text = id(import(picked("text_3_pages.pdf")))
            val scanned = id(import(picked("scanned.pdf")))
            val partial = id(import(picked("partially_scanned.pdf")))
            val restricted = id(import(picked("restricted.pdf")))
            val long = id(import(picked("text_200_pages.pdf")))
            assertThat(import(picked("encrypted.pdf"))).isEqualTo(ImportResult.Rejected(IngestError.ENCRYPTED))
            assertThat(import(picked("truncated.pdf"))).isEqualTo(ImportResult.Rejected(IngestError.DAMAGED))
            assertThat(import(picked("fake.pdf"))).isEqualTo(ImportResult.Rejected(IngestError.DAMAGED))
            assertThat(import(picked("copia de text_3_pages.pdf", File(sources, "text_3_pages.pdf").readBytes())))
                .isEqualTo(ImportResult.Rejected(IngestError.DUPLICATE))
            processed += listOf(text, scanned, partial, restricted, long)

            runner.process(processed) { }

            val index = temp.index
            assertThat(index.document(text)!!.status).isEqualTo(DocumentStatus.PARSED)
            assertThat(index.document(text)!!.skippedPages).isEqualTo(0)
            assertThat(index.document(scanned)!!.error).isEqualTo(IngestError.NO_TEXT.code)
            assertThat(index.document(partial)!!.skippedPages).isEqualTo(1)
            assertThat(index.document(restricted)!!.status).isEqualTo(DocumentStatus.PARSED)
            assertThat(index.document(long)!!.pageCount).isEqualTo(200)
            assertThat(index.document(long)!!.chunkCount!!).isGreaterThan(100)
        } else {
            assertThat(import(picked("text_3_pages.pdf"))).isEqualTo(ImportResult.Rejected(IngestError.PDF_UNAVAILABLE))
            runner.process(processed) { }
        }

        for (id in listOf(txt, md)) {
            val document = temp.index.document(id)!!
            assertWithMessage("document $id").that(document.status).isEqualTo(DocumentStatus.PARSED)
            assertWithMessage("document $id").that(document.chunkCount).isEqualTo(if (id == txt) 1 else 2)
        }
        assertThat(files.cleanOrphans(temp.index.documents(DocumentStatus.entries.toSet()).mapNotNull { it.localPath }.toSet())).isEqualTo(0)
    }

    // F06 US4 (T057): interruptions never leave partial copies or double results (SC-006).

    @Test
    fun interruptedProcessingResumesWithTheSameResult() = runBlocking<Unit> {
        assumeTrue(pdf.isAvailable)
        val long = id(import(picked("text_200_pages.pdf")))
        val reachedPage = CompletableDeferred<Unit>()
        // Holds page 20 for a moment so the cancellation always lands in the middle of the document.
        val slowAtPage20 = object : PdfOpener {
            override val isAvailable = true
            override fun open(file: File): PdfPages {
                val pages = pdf.open(file)
                return object : PdfPages by pages {
                    override fun pageText(index: Int): String {
                        if (index == 20) {
                            reachedPage.complete(Unit)
                            Thread.sleep(500)
                        }
                        return pages.pageText(index)
                    }
                }
            }
        }
        val interrupted = IngestRunner(temp.index, DocumentExtractor(slowAtPage20), Dispatchers.IO, logger)

        val job = launch(Dispatchers.Default) { interrupted.process(listOf(long)) { } }
        withTimeout(60_000) { reachedPage.await() }
        job.cancelAndJoin()
        assertThat(temp.index.document(long)!!.status).isEqualTo(DocumentStatus.PENDING)

        runner.process(listOf(long)) { }
        val resumed = temp.index.document(long)!!
        assertThat(resumed.status).isEqualTo(DocumentStatus.PARSED)
        temp.index.updateStatus(long, DocumentStatus.PENDING)
        runner.process(listOf(long)) { }
        assertThat(temp.index.document(long)!!.chunkCount).isEqualTo(resumed.chunkCount)
    }

    @Test
    fun aNewRepositoryRebuildsTheSameQueueFromTheIndex() = runBlocking<Unit> {
        val first = id(import(picked("a.txt", "Uno.".toByteArray())))
        val second = id(import(picked("b.md", "# B\ndos".toByteArray())))
        runner.process(listOf(second)) { }
        val idle = object : IngestScheduler {
            override val activeDocumentIds = MutableStateFlow(emptySet<Long>())
            override val progress = MutableStateFlow<IngestProgress?>(null)
            override suspend fun enqueue(documentIds: List<Long>) = Unit
        }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val restarted = DefaultImportQueueRepository(importer, source, temp.index, files, idle, pdf, scope, Dispatchers.IO, logger)

        val queue = restarted.queue.first()
        scope.cancel()

        assertThat(queue.items.map { it.key }).containsExactly(QueueKey.Document(first), QueueKey.Document(second)).inOrder()
        assertThat(queue.items.map { it.state }).containsExactly(QueueItemState.Ready, QueueItemState.Prepared(1, null)).inOrder()
    }

    @Test
    fun aCancelledCopyLeavesNothingBehind() = runBlocking<Unit> {
        val big = picked("grande.txt", ByteArray(20_000_000) { ('a' + it % 26).code.toByte() })
        val copying = CompletableDeferred<Unit>()

        val job = launch(Dispatchers.IO) {
            importer.import(big, source.describe(big)) { copied, _ -> if (copied > 0) copying.complete(Unit) }
        }
        withTimeout(30_000) { copying.await() }
        job.cancelAndJoin()

        val documentsDir = File(root, "documents")
        assertThat(documentsDir.list().orEmpty().filter { it.endsWith(".tmp") }).isEmpty()
        assertThat(temp.index.documents(DocumentStatus.entries.toSet())).isEmpty()
        assertThat(files.cleanOrphans(emptySet())).isEqualTo(0)
    }
}
