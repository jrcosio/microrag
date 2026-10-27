package com.lisdatasolutions.microrag.data.ingest

import com.google.common.truth.Truth.assertThat
import com.lisdatasolutions.microrag.data.device.ThermalLevel
import com.lisdatasolutions.microrag.data.indexing.FakeEmbeddingSessions
import com.lisdatasolutions.microrag.data.indexing.FakeTextEmbedder
import com.lisdatasolutions.microrag.data.indexing.FakeThermalStatusSource
import com.lisdatasolutions.microrag.data.indexing.InMemoryIngestRunStore
import com.lisdatasolutions.microrag.data.indexing.RunPauseCause
import com.lisdatasolutions.microrag.domain.ingest.ImportLimits
import com.lisdatasolutions.microrag.domain.ingest.IngestError
import com.lisdatasolutions.microrag.domain.ingest.IngestProgress
import com.lisdatasolutions.microrag.domain.rag.ChunkInput
import com.lisdatasolutions.microrag.domain.rag.DocumentStatus
import com.lisdatasolutions.microrag.domain.rag.NewDocument
import com.lisdatasolutions.microrag.domain.rag.RagParameters
import com.lisdatasolutions.microrag.rag.ingest.Chunk
import com.lisdatasolutions.microrag.rag.ingest.Chunker
import com.lisdatasolutions.microrag.rag.ingest.DocumentExtractor
import com.lisdatasolutions.microrag.rag.ingest.PdfOpener
import com.lisdatasolutions.microrag.rag.ingest.PdfPages
import com.lisdatasolutions.microrag.rag.ingest.RawBlock
import com.lisdatasolutions.microrag.rag.runtime.EmbeddingPrefixes
import com.lisdatasolutions.microrag.testing.FakePdfOpener
import com.lisdatasolutions.microrag.testing.FakeRagIndex
import com.lisdatasolutions.microrag.testing.RecordingLogger
import java.io.File
import java.io.IOException
import java.nio.file.Files
import kotlin.math.ceil
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
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
        IngestRunner(index, DocumentExtractor(pdf), FakeEmbeddingSessions.withoutModel(), InMemoryIngestRunStore(), FakeThermalStatusSource(), { Long.MAX_VALUE }, dispatcher, logger, clock = { 0L }, progressIntervalMs = 10)

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

    // F07 T021: phase 2, embeddings and batches (research R8–R9, rules N1–N5, N11, N16, N17).

    private val runStore = InMemoryIngestRunStore()
    private val sessions = FakeEmbeddingSessions()
    private val embedder get() = sessions.embedder

    private val thermal = FakeThermalStatusSource()

    private fun indexingRunner(freeBytes: () -> Long = { Long.MAX_VALUE }) = IngestRunner(
        index, DocumentExtractor(pdf), sessions, runStore, thermal, freeBytes,
        Dispatchers.IO, logger, clock = System::currentTimeMillis, progressIntervalMs = 10,
    )

    private fun paragraphs(count: Int, tag: String = "") =
        (1..count).joinToString("\n\n") { "Párrafo $tag$it del manual, con texto bastante largo para que el troceado agrupe varios párrafos en cada fragmento." }

    private fun chunksOf(text: String) = Chunker().chunk(listOf(RawBlock(emptyList(), text, null)))

    /** About 170 chunks: more than two batches. */
    private val LONG = 2_200

    @Test
    fun everyPendingDocumentIsChunkedBeforeAnyEmbedding() = runBlocking<Unit> {
        val a = document("a.txt", "text/plain", paragraphs(20, "a").toByteArray())
        val b = document("b.txt", "text/plain", paragraphs(20, "b").toByteArray())
        val seenAtFirstEmbedding = mutableListOf<DocumentStatus>()
        embedder.beforeEmbedding = {
            if (seenAtFirstEmbedding.isEmpty()) seenAtFirstEmbedding += listOf(index.docs.getValue(a).status, index.docs.getValue(b).status)
        }

        indexingRunner().process(listOf(a, b)) { }

        assertThat(seenAtFirstEmbedding).doesNotContain(DocumentStatus.PENDING)
        assertThat(index.docs.getValue(a).status).isEqualTo(DocumentStatus.DONE)
        assertThat(index.docs.getValue(b).status).isEqualTo(DocumentStatus.DONE)
    }

    @Test
    fun progressAlsoCountsTheWholeRun_asTheIndexScreenDoes() = runBlocking<Unit> {
        val a = document("a.txt", "text/plain", paragraphs(20, "a").toByteArray())
        val b = document("b.txt", "text/plain", paragraphs(20, "b").toByteArray())
        val progress = mutableListOf<IngestProgress>()

        indexingRunner().process(listOf(a, b)) { progress += it }

        // D-042: the notification shows the run, as the screen does, not only the document in progress.
        val chunksOfA = index.docs.getValue(a).chunkCount!!
        val total = chunksOfA + index.docs.getValue(b).chunkCount!!
        val embeddingB = progress.last { it.documentId == b && it.stage == IngestProgress.Stage.EMBEDDING }
        assertThat(embeddingB.runChunksTotal).isEqualTo(total)
        assertThat(embeddingB.runChunksDone).isEqualTo(chunksOfA + embeddingB.chunksDone!!)
        assertThat(progress.single { it.documentId == b && it.stage == IngestProgress.Stage.CLOSING }.runChunksDone).isEqualTo(total)
    }

    @Test
    fun chunksAreStoredInBatchesOf64AndTheDocumentEndsDone() = runBlocking<Unit> {
        val text = paragraphs(LONG)
        val expected = chunksOf(text)
        val id = document("largo.txt", "text/plain", text.toByteArray())
        val progress = mutableListOf<IngestProgress>()

        indexingRunner().process(listOf(id)) { progress += it }

        assertThat(expected.size).isGreaterThan(128)
        assertThat(index.insertedBatches.map { it.second.size }).containsExactlyElementsIn(
            List(expected.size / 64) { 64 } + listOfNotNull((expected.size % 64).takeIf { it > 0 }),
        ).inOrder()
        val doc = index.docs.getValue(id)
        assertThat(doc.status).isEqualTo(DocumentStatus.DONE)
        assertThat(doc.chunkCount).isEqualTo(expected.size)
        assertThat(index.chunks.getValue(id).keys).containsExactlyElementsIn(0 until expected.size).inOrder()
        assertThat(progress.map { it.stage }).containsAtLeast(IngestProgress.Stage.EMBEDDING, IngestProgress.Stage.CLOSING).inOrder()
    }

    @Test
    fun theEmbeddedTextHasPrefixAndHeaderAndTheStoredOneHasNeither() = runBlocking<Unit> {
        val id = document("Manual técnico.md", "text/markdown", "# Instalación\nCopia el archivo en la carpeta.".toByteArray())

        indexingRunner().process(listOf(id)) { }

        val stored = index.chunks.getValue(id).getValue(0)
        assertThat(stored.content).isEqualTo("Copia el archivo en la carpeta.")
        assertThat(stored.headingPath).isEqualTo("Instalación")
        assertThat(embedder.embedded).containsExactly(EmbeddingPrefixes.document(stored.content, "Manual técnico › Instalación"))
        assertThat(stored.tokenEstimate).isEqualTo(ceil(stored.content.length / RagParameters.CHARS_PER_TOKEN).toInt())
        assertThat(stored.embedding).isEqualTo(FakeTextEmbedder.vectorOf(embedder.embedded.single()))
    }

    @Test
    fun resuming_skipsTheStoredChunks() = runBlocking<Unit> {
        val text = paragraphs(LONG)
        val expected = chunksOf(text)
        val id = document("largo.txt", "text/plain", text.toByteArray())
        index.updateStatus(id, DocumentStatus.EMBEDDING, chunkCount = expected.size)
        index.insertChunks(id, expected.take(130).map { it.toStored() })

        indexingRunner().process(listOf(id)) { }

        assertThat(embedder.embedded).hasSize(expected.size - 130)
        assertThat(index.docs.getValue(id).status).isEqualTo(DocumentStatus.DONE)
        assertThat(index.chunks.getValue(id)).hasSize(expected.size)
    }

    @Test
    fun storedChunksThatDoNotMatch_areDroppedAndTheDocumentIsIndexedAgain() = runBlocking<Unit> {
        val text = paragraphs(100)
        val expected = chunksOf(text)
        val id = document("doc.txt", "text/plain", text.toByteArray())
        index.updateStatus(id, DocumentStatus.EMBEDDING, chunkCount = expected.size)
        index.insertChunks(id, listOf(expected[0].copy(content = "Otro troceado").toStored()))

        indexingRunner().process(listOf(id)) { }

        assertThat(embedder.embedded).hasSize(expected.size)
        assertThat(index.chunks.getValue(id).getValue(0).content).isEqualTo(expected[0].content)
        assertThat(index.docs.getValue(id).status).isEqualTo(DocumentStatus.DONE)
    }

    @Test
    fun aDocumentMissingChunksAtClosing_isIncomplete() = runBlocking<Unit> {
        val id = document("doc.txt", "text/plain", paragraphs(30).toByteArray())
        index.dropOrdinals = setOf(1)

        indexingRunner().process(listOf(id)) { }

        assertThat(index.docs.getValue(id).error).isEqualTo(IngestError.INCOMPLETE.code)
    }

    @Test
    fun aBrokenEngine_failsThatDocumentAndTheNextOneGoesOn() = runBlocking<Unit> {
        val broken = document("a.txt", "text/plain", paragraphs(10, "a").toByteArray())
        val good = document("b.txt", "text/plain", paragraphs(10, "b").toByteArray())
        embedder.failEngineTimes = 1

        indexingRunner().process(listOf(broken, good)) { }

        assertThat(index.docs.getValue(broken).error).isEqualTo(IngestError.EMBEDDING_FAILED.code)
        assertThat(index.docs.getValue(good).status).isEqualTo(DocumentStatus.DONE)
    }

    @Test
    fun aTextThatNeverFits_failsItsDocument() = runBlocking<Unit> {
        val id = document("a.txt", "text/plain", paragraphs(3).toByteArray())
        embedder.maxInputChars = 0

        indexingRunner().process(listOf(id)) { }

        assertThat(index.docs.getValue(id).error).isEqualTo(IngestError.EMBEDDING_FAILED.code)
    }

    @Test
    fun aDocumentRemovedMidway_isSkippedWithoutWriting() = runBlocking<Unit> {
        val removed = document("a.txt", "text/plain", paragraphs(LONG, "a").toByteArray())
        val next = document("b.txt", "text/plain", paragraphs(10, "b").toByteArray())
        var calls = 0
        embedder.beforeEmbedding = { if (++calls == 70) runBlocking { index.deleteDocument(removed) } }

        indexingRunner().process(listOf(removed, next)) { }

        assertThat(index.docs).doesNotContainKey(removed)
        assertThat(index.chunks).doesNotContainKey(removed)
        assertThat(index.docs.getValue(next).status).isEqualTo(DocumentStatus.DONE)
    }

    @Test
    fun cancelling_storesThePartialBatch() = runBlocking<Unit> {
        val id = document("largo.txt", "text/plain", paragraphs(LONG).toByteArray())
        lateinit var job: Job
        var calls = 0
        embedder.beforeEmbedding = { if (++calls == 70) job.cancel() }

        job = launch(Dispatchers.Default) { indexingRunner().process(listOf(id)) { } }
        job.join()

        val stored = index.chunks.getValue(id).size
        assertThat(stored).isAtLeast(70)
        assertThat(stored).isAtMost(71)
        assertThat(index.docs.getValue(id).status).isEqualTo(DocumentStatus.EMBEDDING)
    }

    @Test
    fun aPendingDocumentAddedToTheRunMidway_isIndexedToo() = runBlocking<Unit> {
        val first = document("a.txt", "text/plain", paragraphs(100, "a").toByteArray())
        val added = document("b.txt", "text/plain", paragraphs(10, "b").toByteArray())
        runStore.edit { it.adding(listOf(first)) }
        var calls = 0
        embedder.beforeEmbedding = { if (++calls == 5) runBlocking { runStore.edit { it.adding(listOf(added)) } } }

        indexingRunner().process(listOf(first)) { }

        assertThat(index.docs.getValue(added).status).isEqualTo(DocumentStatus.DONE)
        assertThat(runStore.run.first().documentIds).isEmpty()
    }

    @Test
    fun theRunStaysWhileADocumentIsUnfinished() = runBlocking<Unit> {
        val done = document("a.txt", "text/plain", paragraphs(5).toByteArray())
        val other = document("b.txt", "text/plain", paragraphs(5).toByteArray())
        runStore.edit { it.adding(listOf(done, other)) }
        sessions.ready = false

        indexingRunner().process(listOf(done)) { }

        assertThat(runStore.run.first().documentIds).containsExactly(done, other)
        assertThat(runStore.run.first().pauseCause).isEqualTo(RunPauseCause.MODEL_MISSING)
    }

    @Test
    fun indexingLogsCarryNoNamesOrText() = runBlocking<Unit> {
        val id = document("Contrato confidencial.txt", "text/plain", "cláusula secreta del contrato".toByteArray())

        indexingRunner().process(listOf(id)) { }

        val log = logger.lines.joinToString("\n")
        assertThat(log).doesNotContain("Contrato")
        assertThat(log).doesNotContain("secreta")
        assertThat(log).contains("ingest done id=$id")
    }


    // F07 T046 (research R10): heat, space and a missing model.

    @Test
    fun severeHeat_waitsWithTheEngineReleasedAndGoesOnWhenCooler() = runBlocking<Unit> {
        val id = document("largo.txt", "text/plain", paragraphs(LONG).toByteArray())
        var calls = 0
        embedder.beforeEmbedding = { if (++calls == 70) thermal.level.value = ThermalLevel.SEVERE_OR_WORSE }
        val waited = CompletableDeferred<IngestProgress>()

        val job = launch(Dispatchers.Default) {
            indexingRunner().process(listOf(id)) { if (it.waiting == IngestProgress.WaitReason.THERMAL) waited.complete(it) }
        }
        val waiting = withTimeout(30_000) { waited.await() }
        assertThat(waiting.stage).isEqualTo(IngestProgress.Stage.EMBEDDING)
        assertThat(index.docs.getValue(id).status).isEqualTo(DocumentStatus.EMBEDDING)
        thermal.level.value = ThermalLevel.MODERATE
        withTimeout(30_000) { job.join() }

        assertThat(sessions.opened).isEqualTo(2)
        assertThat(index.docs.getValue(id).status).isEqualTo(DocumentStatus.DONE)
    }

    @Test
    fun tooLittleSpaceBeforeABatch_pausesTheRunWithoutPublishing() = runBlocking<Unit> {
        val id = document("largo.txt", "text/plain", paragraphs(LONG).toByteArray())
        runStore.edit { it.adding(listOf(id)) }

        indexingRunner(freeBytes = { ImportLimits.SPACE_MARGIN_BYTES - 1 }).process(listOf(id)) { }

        assertThat(index.docs.getValue(id).status).isEqualTo(DocumentStatus.EMBEDDING)
        assertThat(runStore.run.first().pauseCause).isEqualTo(RunPauseCause.NO_SPACE)
        assertThat(index.chunks[id].orEmpty()).isEmpty()
    }

    @Test
    fun aFullDiskWhileWriting_pausesTheRunToo() = runBlocking<Unit> {
        val id = document("largo.txt", "text/plain", paragraphs(LONG).toByteArray())
        index.failNextInsertWithFullDisk = true

        indexingRunner().process(listOf(id)) { }

        assertThat(index.docs.getValue(id).status).isEqualTo(DocumentStatus.EMBEDDING)
        assertThat(runStore.run.first().pauseCause).isEqualTo(RunPauseCause.NO_SPACE)
    }

    @Test
    fun aPausedRun_isLeftAlone() = runBlocking<Unit> {
        val id = document("a.txt", "text/plain", paragraphs(5).toByteArray())
        runStore.edit { it.adding(listOf(id)).pausedBy(RunPauseCause.USER) }

        indexingRunner().process(listOf(id)) { }

        assertThat(index.docs.getValue(id).status).isEqualTo(DocumentStatus.PENDING)
    }

    @Test
    fun withoutTheModel_itChunksAndPausesBeforeLoadingAnything() = runBlocking<Unit> {
        val id = document("a.txt", "text/plain", paragraphs(5).toByteArray())
        sessions.ready = false

        indexingRunner().process(listOf(id)) { }

        assertThat(index.docs.getValue(id).status).isEqualTo(DocumentStatus.PARSED)
        assertThat(runStore.run.first().pauseCause).isEqualTo(RunPauseCause.MODEL_MISSING)
        assertThat(sessions.opened).isEqualTo(0)
    }

    private fun Chunk.toStored() = ChunkInput(
        ordinal = ordinal, headingPath = headingPath.ifBlank { null }, page = page, content = content,
        tokenEstimate = 1, embedding = FakeTextEmbedder.vectorOf(content),
    )
}
