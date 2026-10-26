package com.lisdatasolutions.microrag.data.ingest

import com.google.common.truth.Truth.assertThat
import com.lisdatasolutions.microrag.domain.ingest.IngestError
import com.lisdatasolutions.microrag.domain.ingest.IngestProgress
import com.lisdatasolutions.microrag.domain.rag.DocumentStatus
import com.lisdatasolutions.microrag.domain.rag.NewDocument
import com.lisdatasolutions.microrag.rag.ingest.Chunker
import com.lisdatasolutions.microrag.rag.ingest.DocumentExtractor
import com.lisdatasolutions.microrag.rag.ingest.PdfOpener
import com.lisdatasolutions.microrag.rag.ingest.PdfPages
import com.lisdatasolutions.microrag.rag.ingest.RawBlock
import com.lisdatasolutions.microrag.testing.FakePdfOpener
import com.lisdatasolutions.microrag.testing.FakeRagIndex
import com.lisdatasolutions.microrag.testing.RecordingLogger
import java.io.File
import java.io.IOException
import java.nio.file.Files
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test

/** F06 T045: one processing request, document by document (research R7, contracts I3–I6, I10). */
@OptIn(ExperimentalCoroutinesApi::class)
class IngestRunnerTest {

    private val dir: File = Files.createTempDirectory("documents").toFile()
    private val index = FakeRagIndex()
    private val logger = RecordingLogger()
    private var pdf: PdfOpener = FakePdfOpener()

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    private suspend fun document(name: String, mime: String, bytes: ByteArray, pages: Int? = null): Long {
        val file = File(dir, name).apply { writeBytes(bytes) }
        return index.addDocument(
            NewDocument(displayName = name, contentHash = name, mimeType = mime, sizeBytes = bytes.size.toLong(), pageCount = pages, localPath = file.path),
        )
    }

    private fun runner(dispatcher: kotlinx.coroutines.CoroutineDispatcher) =
        IngestRunner(index, DocumentExtractor(pdf), dispatcher, logger, clock = { 0L }, progressIntervalMs = 10)

    private fun expectedChunks(text: String) = Chunker().chunk(listOf(RawBlock(emptyList(), text, null))).size

    @Test
    fun pendingDocumentsBecomeParsedWithTheirChunks() = runTest {
        val text = (1..60).joinToString("\n\n") { "Párrafo $it con texto suficiente para llenar varios fragmentos del documento." }
        val txt = document("notas.txt", "text/plain", text.toByteArray())
        val md = document("guia.md", "text/markdown", "# Uno\ncuerpo\n## Dos\nmás".toByteArray())
        val progress = mutableListOf<IngestProgress>()

        runner(UnconfinedTestDispatcher(testScheduler)).process(listOf(txt, md)) { progress += it }

        assertThat(index.docs.getValue(txt).status).isEqualTo(DocumentStatus.PARSED)
        assertThat(index.docs.getValue(txt).chunkCount).isEqualTo(expectedChunks(text))
        assertThat(index.docs.getValue(txt).skippedPages).isNull()
        assertThat(index.docs.getValue(md).chunkCount).isEqualTo(2)
        assertThat(progress.map { it.documentId to it.stage }).containsAtLeast(
            txt to IngestProgress.Stage.EXTRACTING, txt to IngestProgress.Stage.CHUNKING,
            md to IngestProgress.Stage.EXTRACTING, md to IngestProgress.Stage.CHUNKING,
        ).inOrder()
    }

    @Test
    fun pdfPagesWithoutTextAreSkippedAndCounted() = runTest {
        val id = document("manual.pdf", "application/pdf", FakePdfOpener.pdf("Uno con texto.", "", "7", "Cuatro."), pages = 4)

        runner(UnconfinedTestDispatcher(testScheduler)).process(listOf(id)) { }

        val stored = index.docs.getValue(id)
        assertThat(stored.status).isEqualTo(DocumentStatus.PARSED)
        assertThat(stored.skippedPages).isEqualTo(2)
        assertThat(stored.chunkCount).isEqualTo(1)
    }

    @Test
    fun failuresAreRecordedPerDocumentAndTheNextOneGoesOn() = runTest {
        val scanned = document("escaneado.pdf", "application/pdf", FakePdfOpener.pdf("", " "), pages = 2)
        val locked = document("cifrado.pdf", "application/pdf", FakePdfOpener.pdf("x", marker = FakePdfOpener.ENCRYPTED))
        val damaged = document("roto.pdf", "application/pdf", FakePdfOpener.pdf("x", marker = FakePdfOpener.DAMAGED))
        val empty = document("vacío.txt", "text/plain", " \n\t".toByteArray())
        val missing = document("perdido.txt", "text/plain", "texto".toByteArray()).also { File(dir, "perdido.txt").delete() }
        val good = document("bueno.txt", "text/plain", "Texto bueno.".toByteArray())

        runner(UnconfinedTestDispatcher(testScheduler)).process(listOf(scanned, locked, damaged, empty, missing, good)) { }

        assertThat(index.docs.getValue(scanned).error).isEqualTo(IngestError.NO_TEXT.code)
        assertThat(index.docs.getValue(locked).error).isEqualTo(IngestError.ENCRYPTED.code)
        assertThat(index.docs.getValue(damaged).error).isEqualTo(IngestError.DAMAGED.code)
        assertThat(index.docs.getValue(empty).error).isEqualTo(IngestError.NO_TEXT.code)
        assertThat(index.docs.getValue(missing).error).isEqualTo(IngestError.EXTRACTION_FAILED.code)
        listOf(scanned, locked, damaged, empty, missing).forEach { assertThat(index.docs.getValue(it).status).isEqualTo(DocumentStatus.ERROR) }
        assertThat(index.docs.getValue(good).status).isEqualTo(DocumentStatus.PARSED)
    }

    @Test
    fun anIoErrorWhileReadingIsAnExtractionFailure() = runTest {
        pdf = object : PdfOpener {
            override val isAvailable = true
            override fun open(file: File): PdfPages = object : PdfPages {
                override val pageCount = 3
                override fun pageText(index: Int): String = if (index == 1) throw IOException("bad page") else "Texto."
                override fun close() = Unit
            }
        }
        val id = document("a.pdf", "application/pdf", FakePdfOpener.pdf("x"))

        runner(UnconfinedTestDispatcher(testScheduler)).process(listOf(id)) { }

        assertThat(index.docs.getValue(id).error).isEqualTo(IngestError.EXTRACTION_FAILED.code)
    }

    @Test
    fun removedOrNotPendingDocumentsAreSkipped() = runTest {
        val parsed = document("ya.txt", "text/plain", "uno".toByteArray())
        index.updateStatus(parsed, DocumentStatus.PARSED, chunkCount = 9)
        val removed = document("quitado.txt", "text/plain", "dos".toByteArray())
        index.deleteDocument(removed)

        runner(UnconfinedTestDispatcher(testScheduler)).process(listOf(parsed, removed, 999)) { }

        assertThat(index.docs.getValue(parsed).chunkCount).isEqualTo(9)
        assertThat(index.docs).doesNotContainKey(removed)
    }

    @Test
    fun processingAgainGivesTheSameResult() = runTest {
        val text = (1..30).joinToString("\n\n") { "Línea $it del documento repetido con texto." }
        val id = document("doc.txt", "text/plain", text.toByteArray())
        val runner = runner(UnconfinedTestDispatcher(testScheduler))

        runner.process(listOf(id)) { }
        val first = index.docs.getValue(id).chunkCount
        index.updateStatus(id, DocumentStatus.PENDING)
        runner.process(listOf(id)) { }

        assertThat(index.docs.getValue(id).chunkCount).isEqualTo(first)
    }

    @Test
    fun stoppingBetweenPagesLeavesTheDocumentPending() = runTest {
        lateinit var job: Job
        pdf = object : PdfOpener {
            override val isAvailable = true
            override fun open(file: File): PdfPages = object : PdfPages {
                override val pageCount = 5
                override fun pageText(index: Int): String {
                    if (index == 2) job.cancel()
                    return "Página $index."
                }
                override fun close() = Unit
            }
        }
        val id = document("largo.pdf", "application/pdf", FakePdfOpener.pdf("x"), pages = 5)
        val dispatcher = StandardTestDispatcher(testScheduler)

        job = launch(dispatcher) { runner(dispatcher).process(listOf(id)) { } }
        advanceUntilIdle()

        assertThat(job.isCancelled).isTrue()
        assertThat(index.docs.getValue(id).status).isEqualTo(DocumentStatus.PENDING)
    }

    @Test
    fun logsCarryNoNamesOrText() = runTest {
        val id = document("Contrato confidencial.txt", "text/plain", "cláusula secreta".toByteArray())

        runner(UnconfinedTestDispatcher(testScheduler)).process(listOf(id)) { }

        val log = logger.lines.joinToString("\n")
        assertThat(log).doesNotContain("Contrato")
        assertThat(log).doesNotContain("secreta")
        assertThat(log).doesNotContain(dir.path)
        assertThat(log).contains("ingest parsed id=$id")
    }
}
