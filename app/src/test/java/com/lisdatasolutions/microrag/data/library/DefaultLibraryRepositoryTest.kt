package com.lisdatasolutions.microrag.data.library

import com.google.common.truth.Truth.assertThat
import com.lisdatasolutions.microrag.data.indexing.DefaultIndexingRepository
import com.lisdatasolutions.microrag.data.indexing.FakeThermalStatusSource
import com.lisdatasolutions.microrag.data.indexing.InMemoryIngestRunStore
import com.lisdatasolutions.microrag.data.ingest.DocumentFileStore
import com.lisdatasolutions.microrag.data.ingest.DocumentFiles
import com.lisdatasolutions.microrag.domain.ingest.DocumentFormat
import com.lisdatasolutions.microrag.data.indexing.RunPauseCause
import com.lisdatasolutions.microrag.domain.indexing.PauseReason
import com.lisdatasolutions.microrag.domain.ingest.IngestError
import com.lisdatasolutions.microrag.domain.library.DetailResult
import com.lisdatasolutions.microrag.domain.library.DocumentDetail
import com.lisdatasolutions.microrag.domain.library.DocumentDetailState
import com.lisdatasolutions.microrag.domain.library.DocumentPreview
import com.lisdatasolutions.microrag.domain.library.ReindexState
import com.lisdatasolutions.microrag.domain.library.LibraryDocument
import com.lisdatasolutions.microrag.domain.library.LibraryDocumentState
import com.lisdatasolutions.microrag.domain.library.LibraryFilter
import com.lisdatasolutions.microrag.domain.library.LibraryQuery
import com.lisdatasolutions.microrag.domain.library.LibraryResult
import com.lisdatasolutions.microrag.domain.library.ReindexRow
import com.lisdatasolutions.microrag.domain.rag.ChunkInput
import com.lisdatasolutions.microrag.domain.rag.DocumentStatus
import com.lisdatasolutions.microrag.domain.rag.IndexCompatibility
import com.lisdatasolutions.microrag.domain.rag.NewDocument
import com.lisdatasolutions.microrag.testing.FakeIngestScheduler
import com.lisdatasolutions.microrag.testing.FakeRagIndex
import com.lisdatasolutions.microrag.testing.RecordingLogger
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test

/** F08 (contracts/library-api.md): the Library repository on the fake index, the run in memory and real copies. */
@OptIn(ExperimentalCoroutinesApi::class)
class DefaultLibraryRepositoryTest {

    private val dir: File = Files.createTempDirectory("documents").toFile()
    private val index = FakeRagIndex()
    private val scheduler = FakeIngestScheduler()
    private val runStore = InMemoryIngestRunStore()
    private val logger = RecordingLogger()
    private val store = DocumentFileStore(dir)

    @After
    fun tearDown() {
        dir.setWritable(true)
        dir.deleteRecursively()
    }

    private fun TestScope.repository(files: DocumentFiles? = null): DefaultLibraryRepository {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val indexing = DefaultIndexingRepository(index, scheduler, runStore, FakeThermalStatusSource(), dispatcher, logger)
        return DefaultLibraryRepository(index, files ?: DocumentFiles(store, index, dispatcher, logger), runStore, indexing, dispatcher, logger) { 0L }
    }

    /** A document with its private copy in the store, unless [copy] is false. */
    private suspend fun doc(
        name: String,
        status: DocumentStatus = DocumentStatus.DONE,
        chunks: Int = 0,
        mime: String = "application/pdf",
        error: IngestError? = null,
        copy: Boolean = true,
    ): Long {
        val file = File(dir, "copy-${name.hashCode()}.bin")
        if (copy) file.writeText(name)
        val id = index.addDocument(
            NewDocument(displayName = name, contentHash = name, mimeType = mime, sizeBytes = 2048, pageCount = 7, localPath = file.path),
        )
        if (chunks > 0) index.insertChunks(id, (0 until chunks).map { chunkInput(it) })
        if (status != DocumentStatus.PENDING) index.updateStatus(id, status, error = error?.code, chunkCount = chunks.takeIf { it > 0 })
        return id
    }

    private fun chunkInput(ordinal: Int, content: String = "Texto $ordinal.", page: Int? = ordinal + 1) =
        ChunkInput(ordinal, null, page, content, 1, FloatArray(256))

    private fun copyOf(id: Long): File = File(index.docs.getValue(id).localPath!!)

    private suspend fun TestScope.page(query: LibraryQuery = LibraryQuery()): LibraryResult = repository().page(query).first()

    private suspend fun TestScope.documents(query: LibraryQuery = LibraryQuery()): List<LibraryDocument> =
        (page(query) as LibraryResult.Loaded).page.documents

    // US1 (T015)

    @Test
    fun page_givesEveryDocumentNewestFirstWithItsState() = runTest {
        val pending = doc("Pendiente.pdf", DocumentStatus.PENDING)
        val parsed = doc("Troceado.txt", DocumentStatus.PARSED, mime = "text/plain")
        val embedding = doc("A medias.md", DocumentStatus.EMBEDDING, chunks = 2, mime = "text/markdown")
        val done = doc("Manual.pdf", chunks = 3)
        val excluded = doc("Excluido.pdf", chunks = 1).also { index.setIncluded(it, false) }
        val failed = doc("Roto.pdf", DocumentStatus.ERROR, error = IngestError.NO_TEXT)
        val out = doc("Fuera.pdf", chunks = 2).also { index.removeFromIndex(it) }
        val reindexing = doc("Reindexando.pdf", chunks = 4).also { index.addReindexShadow(it) }
        val reindexFailed = doc("Falló.pdf", chunks = 5).also {
            index.updateStatus(index.addReindexShadow(it), DocumentStatus.ERROR, error = IngestError.EMBEDDING_FAILED.code)
        }
        val unknown = doc("Hoja.xlsx", DocumentStatus.ERROR, mime = "application/vnd.ms-excel", error = IngestError.UNSUPPORTED_FORMAT)

        val result = page() as LibraryResult.Loaded

        assertThat(result.page.total).isEqualTo(10)
        assertThat(result.page.hasMore).isFalse()
        assertThat(result.page.documents).containsExactly(
            LibraryDocument(unknown, "Hoja.xlsx", null, LibraryDocumentState.Failed(IngestError.UNSUPPORTED_FORMAT), true),
            LibraryDocument(reindexFailed, "Falló.pdf", DocumentFormat.PDF, LibraryDocumentState.Indexed(5, ReindexRow.FAILED), true),
            LibraryDocument(reindexing, "Reindexando.pdf", DocumentFormat.PDF, LibraryDocumentState.Indexed(4, ReindexRow.RUNNING), true),
            LibraryDocument(out, "Fuera.pdf", DocumentFormat.PDF, LibraryDocumentState.OutOfIndex, true),
            LibraryDocument(failed, "Roto.pdf", DocumentFormat.PDF, LibraryDocumentState.Failed(IngestError.NO_TEXT), true),
            LibraryDocument(excluded, "Excluido.pdf", DocumentFormat.PDF, LibraryDocumentState.Indexed(1, null), false),
            LibraryDocument(done, "Manual.pdf", DocumentFormat.PDF, LibraryDocumentState.Indexed(3, null), true),
            LibraryDocument(embedding, "A medias.md", DocumentFormat.MARKDOWN, LibraryDocumentState.Indexing, true),
            LibraryDocument(parsed, "Troceado.txt", DocumentFormat.TXT, LibraryDocumentState.Pending, true),
            LibraryDocument(pending, "Pendiente.pdf", DocumentFormat.PDF, LibraryDocumentState.Pending, true),
        ).inOrder()
    }

    @Test
    fun page_filtersSearchesAndLimits() = runTest {
        val guide = doc("Guía de instalación.pdf", chunks = 1)
        val catalog = doc("CATÁLOGO.pdf", DocumentStatus.PENDING)
        val failed = doc("Guía rota.pdf", DocumentStatus.ERROR, error = IngestError.DAMAGED)
        val out = doc("Normas.pdf", chunks = 1).also { index.removeFromIndex(it) }

        assertThat(documents(LibraryQuery(filter = LibraryFilter.INDEXED)).map { it.id }).containsExactly(guide)
        assertThat(documents(LibraryQuery(filter = LibraryFilter.PENDING)).map { it.id }).containsExactly(out, failed, catalog).inOrder()
        assertThat(documents(LibraryQuery(text = "GUIA")).map { it.id }).containsExactly(failed, guide).inOrder()
        assertThat(documents(LibraryQuery(text = "guia", filter = LibraryFilter.PENDING)).map { it.id }).containsExactly(failed)
        assertThat(documents(LibraryQuery(text = "catalogo")).map { it.id }).containsExactly(catalog)

        val limited = (page(LibraryQuery(limit = 2)) as LibraryResult.Loaded).page
        assertThat(limited.documents.map { it.id }).containsExactly(out, failed).inOrder()
        assertThat(limited.total).isEqualTo(4)
        assertThat(limited.hasMore).isTrue()
        assertThat((page(LibraryQuery(text = "nada")) as LibraryResult.Loaded).page.total).isEqualTo(0)
    }

    @Test
    fun page_isComputedAgainWhenTheIndexChanges() = runTest {
        val first = doc("Uno.pdf", chunks = 1)
        val values = mutableListOf<LibraryResult>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { repository().page(LibraryQuery()).toList(values) }
        advanceUntilIdle()

        val second = doc("Dos.pdf", DocumentStatus.PENDING)
        index.setIncluded(first, false)
        advanceUntilIdle()

        val last = (values.last() as LibraryResult.Loaded).page
        assertThat(last.documents.map { it.id }).containsExactly(second, first).inOrder()
        assertThat(last.documents.last().included).isFalse()
    }

    @Test
    fun page_neverReadsTheChunks() = runTest {
        repeat(5) { doc("Doc $it.pdf", chunks = 10) }
        doc("A medias.pdf", DocumentStatus.EMBEDDING, chunks = 3)
        val reads = index.chunkReads

        documents()
        documents(LibraryQuery(text = "doc", filter = LibraryFilter.INDEXED))

        assertThat(index.chunkReads).isEqualTo(reads)
    }

    @Test
    fun page_withAnUnavailableIndex_isUnavailable() = runTest {
        index.setCompatibility(IndexCompatibility.Incompatible("embedding_dims", "256", "768"))

        assertThat(page()).isEqualTo(LibraryResult.Unavailable)
    }

    @Test
    fun firstObservation_cleansOrphansOnce() = runTest {
        val kept = copyOf(doc("Guardado.pdf"))
        val orphan = File(dir, "a".repeat(64) + ".pdf").apply { writeText("orphan") }
        val repository = repository()

        repository.page(LibraryQuery()).first()
        assertThat(orphan.exists()).isFalse()
        assertThat(kept.exists()).isTrue()

        val later = File(dir, "b".repeat(64) + ".pdf").apply { writeText("later") }
        repository.page(LibraryQuery()).first()
        assertThat(later.exists()).isTrue()
    }

    // US2 (T023)

    @Test
    fun setIncluded_onlyChangesIndexedDocuments() = runTest {
        val done = doc("Manual.pdf", chunks = 2)
        val pending = doc("Pendiente.pdf", DocumentStatus.PENDING)
        val out = doc("Fuera.pdf", chunks = 1).also { index.removeFromIndex(it) }
        val repository = repository()

        repository.setIncluded(done, false)
        repository.setIncluded(pending, false)
        repository.setIncluded(out, false)
        repository.setIncluded(9_999, false)

        assertThat(index.docs.getValue(done).included).isFalse()
        assertThat(index.docs.getValue(pending).included).isTrue()
        assertThat(index.docs.getValue(out).included).isTrue()
        // Excluding keeps the chunks (FR-009).
        assertThat(index.chunks.getValue(done)).hasSize(2)
        assertThat(logger.lines).contains("library include id=$done included=false")

        repository.setIncluded(done, true)
        assertThat(index.docs.getValue(done).included).isTrue()
    }

    @Test
    fun setIncluded_withAnUnavailableIndex_doesNothing() = runTest {
        val done = doc("Manual.pdf", chunks = 1)
        index.setCompatibility(IndexCompatibility.Unreadable("read"))

        repository().setIncluded(done, false)

        index.setCompatibility(IndexCompatibility.Compatible)
        assertThat(index.docs.getValue(done).included).isTrue()
    }

    // US3 (T028)

    private suspend fun TestScope.detail(id: Long): DetailResult = repository().detail(id).first()

    private suspend fun TestScope.loaded(id: Long): DocumentDetail = (detail(id) as DetailResult.Loaded).detail

    @Test
    fun detail_givesTheRealFiguresOfAnIndexedDocument() = runTest {
        val id = doc("Manual de procesos.pdf", chunks = 3)

        assertThat(loaded(id)).isEqualTo(
            DocumentDetail(
                id = id, displayName = "Manual de procesos.pdf", format = DocumentFormat.PDF, state = DocumentDetailState.Indexed,
                included = true, pages = 7, chunks = 3, sizeBytes = 2048, importedAtMs = index.docs.getValue(id).createdAtMs,
                canReindex = true, copyMissing = false, reindex = null,
            ),
        )
    }

    @Test
    fun detail_givesEveryStateOfResearchR10() = runTest {
        val pending = doc("Pendiente.pdf", DocumentStatus.PENDING)
        val parsed = doc("Troceado.txt", DocumentStatus.PARSED, mime = "text/plain")
        val embedding = doc("A medias.pdf", DocumentStatus.EMBEDDING).also {
            index.updateStatus(it, DocumentStatus.EMBEDDING, chunkCount = 10)
            index.insertChunks(it, (0 until 4).map { o -> chunkInput(o) })
        }
        val failed = doc("Roto.pdf", DocumentStatus.ERROR, error = IngestError.EMBEDDING_FAILED)
        val out = doc("Fuera.pdf", chunks = 2).also { index.removeFromIndex(it) }

        assertThat(loaded(pending).state).isEqualTo(DocumentDetailState.Pending)
        assertThat(loaded(parsed).state).isEqualTo(DocumentDetailState.Pending)
        assertThat(loaded(embedding).state).isEqualTo(DocumentDetailState.Indexing(done = 4, total = 10))
        assertThat(loaded(failed).state).isEqualTo(DocumentDetailState.Failed(IngestError.EMBEDDING_FAILED))
        assertThat(loaded(out).state).isEqualTo(DocumentDetailState.OutOfIndex)
        // Figures only for what exists; reindexing only from indexed or out of the index (rule L6).
        assertThat(loaded(pending).chunks).isNull()
        assertThat(loaded(embedding).chunks).isNull()
        assertThat(loaded(parsed).pages).isEqualTo(7)
        assertThat(listOf(pending, parsed, embedding, failed).map { loaded(it).canReindex }).containsExactly(false, false, false, false)
        assertThat(loaded(out).canReindex).isTrue()
    }

    @Test
    fun detail_showsTheReindexShadow_withThePauseOfTheRun() = runTest {
        val running = doc("Reindexando.pdf", chunks = 4)
        val shadow = index.addReindexShadow(running)
        index.updateStatus(shadow, DocumentStatus.EMBEDDING, chunkCount = 6)
        index.insertChunks(shadow, (0 until 2).map { chunkInput(it) })
        runStore.edit { it.adding(listOf(shadow)) }
        val failing = doc("Falla.pdf", chunks = 2)
        val failedShadow = index.addReindexShadow(failing)
        index.updateStatus(failedShadow, DocumentStatus.ERROR, error = IngestError.EMBEDDING_FAILED.code)

        val detail = loaded(running)
        assertThat(detail.state).isEqualTo(DocumentDetailState.Indexed)
        assertThat(detail.chunks).isEqualTo(4)
        assertThat(detail.reindex).isEqualTo(ReindexState.Running(shadow, done = 2, total = 6, pause = null))
        assertThat(detail.canReindex).isFalse()
        assertThat(loaded(failing).reindex).isEqualTo(ReindexState.Failed(failedShadow, IngestError.EMBEDDING_FAILED))

        runStore.edit { it.pausedBy(RunPauseCause.USER) }
        assertThat(loaded(running).reindex).isEqualTo(ReindexState.Running(shadow, done = 2, total = 6, pause = PauseReason.USER))
        // A shadow is never a document of its own.
        assertThat(detail(shadow)).isEqualTo(DetailResult.NotFound)
    }

    @Test
    fun detail_ofAMissingDocument_isNotFound_alsoWhenItDisappears() = runTest {
        val id = doc("Manual.pdf", chunks = 1)
        assertThat(detail(9_999)).isEqualTo(DetailResult.NotFound)
        val values = mutableListOf<DetailResult>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { repository().detail(id).toList(values) }
        advanceUntilIdle()
        assertThat(values.last()).isInstanceOf(DetailResult.Loaded::class.java)

        index.deleteDocument(id)
        advanceUntilIdle()

        assertThat(values.last()).isEqualTo(DetailResult.NotFound)
    }

    @Test
    fun detail_withAnUnavailableIndex_isUnavailable_notNotFound() = runTest {
        val id = doc("Manual.pdf", chunks = 1)
        index.setCompatibility(IndexCompatibility.Incompatible("embedding_dims", "256", "768"))

        assertThat(detail(id)).isEqualTo(DetailResult.Unavailable)
    }

    @Test
    fun detail_withoutItsPrivateCopy_cannotBeReindexed_D10() = runTest {
        val indexed = doc("Sin copia.pdf", chunks = 2, copy = false)
        val out = doc("Fuera sin copia.pdf", chunks = 1, copy = false).also { index.removeFromIndex(it) }

        assertThat(loaded(indexed).copyMissing).isTrue()
        assertThat(loaded(indexed).canReindex).isFalse()
        assertThat(loaded(out).canReindex).isFalse()
    }

    private suspend fun indexedWith(name: String, vararg contents: String): Long {
        val id = doc(name)
        index.insertChunks(id, contents.mapIndexed { o, text -> chunkInput(o, text) })
        index.updateStatus(id, DocumentStatus.DONE, chunkCount = contents.size)
        return id
    }

    @Test
    fun preview_withoutCitation_isTheFirstChunk() = runTest {
        val id = indexedWith("Manual.pdf", "Primer fragmento. Con dos frases.", "Segundo fragmento.")

        assertThat(repository().preview(id, chunkId = null)).isEqualTo(
            DocumentPreview(page = 1, before = null, text = "Primer fragmento. Con dos frases.", after = null, highlighted = false, citedChunkMissing = false),
        )
    }

    @Test
    fun preview_ofACitation_highlightsTheChunkBetweenItsNeighbouringSentences() = runTest {
        val id = indexedWith(
            "Manual.pdf",
            "Introducción. Antes de cerrar el proceso, se",
            "debe registrar la validación final",
            "en el sistema documental. Después se archiva.",
        )

        assertThat(repository().preview(id, index.chunkId(id, 1))).isEqualTo(
            DocumentPreview(
                page = 2, before = "Antes de cerrar el proceso, se", text = "debe registrar la validación final",
                after = "en el sistema documental.", highlighted = true, citedChunkMissing = false,
            ),
        )
        // The first and the last chunk have one neighbour only.
        val first = repository().preview(id, index.chunkId(id, 0))!!
        assertThat(first.before).isNull()
        assertThat(first.after).isEqualTo("debe registrar la validación final")
        assertThat(repository().preview(id, index.chunkId(id, 2))!!.after).isNull()
    }

    @Test
    fun preview_ofACitationThatIsNotHere_isTheNormalPreviewWithANotice_L9() = runTest {
        val id = indexedWith("Manual.pdf", "Primero.", "Segundo.")
        val other = indexedWith("Otro.pdf", "Ajeno.")

        val foreign = repository().preview(id, index.chunkId(other, 0))!!
        val missing = repository().preview(id, 9_999)!!

        listOf(foreign, missing).forEach { preview ->
            assertThat(preview.text).isEqualTo("Primero.")
            assertThat(preview.highlighted).isFalse()
            assertThat(preview.citedChunkMissing).isTrue()
        }
    }

    @Test
    fun preview_ofADocumentNotIndexed_orAnUnavailableIndex_isNull() = runTest {
        val pending = doc("Pendiente.pdf", DocumentStatus.PENDING)
        val embedding = doc("A medias.pdf", DocumentStatus.EMBEDDING, chunks = 2)
        val done = indexedWith("Manual.pdf", "Texto.")

        assertThat(repository().preview(pending, null)).isNull()
        assertThat(repository().preview(embedding, null)).isNull()
        assertThat(repository().preview(9_999, null)).isNull()
        index.setCompatibility(IndexCompatibility.Unreadable("read"))
        assertThat(repository().preview(done, null)).isNull()
    }

    // US4 (T036)

    @Test
    fun delete_removesTheDocumentItsShadowItsRunEntriesAndItsCopy_L4() = runTest {
        val id = doc("Manual.pdf", chunks = 3)
        val shadow = index.addReindexShadow(id)
        val other = doc("Otro.pdf", DocumentStatus.PENDING)
        runStore.edit { it.adding(listOf(id, shadow, other)) }
        val copy = copyOf(id)

        repository().delete(id)

        assertThat(index.docs.keys).containsExactly(other)
        assertThat(index.chunks).doesNotContainKey(id)
        assertThat(index.quantDirty).isTrue()
        assertThat(runStore.run.first().documentIds).containsExactly(other)
        assertThat(copy.exists()).isFalse()
        assertThat(copyOf(other).exists()).isTrue()
        assertThat(logger.lines.any { it.startsWith("library delete id=$id chunks=3 in ") }).isTrue()
    }

    @Test
    fun delete_ofAShadow_neverDeletesTheCopyItShares() = runTest {
        val id = doc("Manual.pdf", chunks = 1)
        val shadow = index.addReindexShadow(id)

        repository().delete(shadow)

        assertThat(index.docs.keys).containsExactly(id)
        assertThat(copyOf(id).exists()).isTrue()
    }

    @Test
    fun delete_whenTheCopyCannotBeDeleted_leavesItForTheOrphanCleanup() = runTest {
        val id = doc("Manual.pdf", chunks = 1)
        val copy = copyOf(id)
        dir.setWritable(false)

        repository().delete(id)

        assertThat(index.docs).doesNotContainKey(id)
        assertThat(copy.exists()).isTrue()
        dir.setWritable(true)
        // The next process cleans it up.
        DocumentFiles(store, index, UnconfinedTestDispatcher(testScheduler), logger).cleanOrphansOnce()
        assertThat(copy.exists()).isFalse()
    }

    @Test
    fun removeFromIndex_keepsTheDocumentAndItsCopyOutOfTheIndexAndTheRun_L5() = runTest {
        val id = doc("Manual.pdf", chunks = 4)
        index.setIncluded(id, false)
        val shadow = index.addReindexShadow(id)
        runStore.edit { it.adding(listOf(id, shadow)) }

        repository().removeFromIndex(id)

        val removed = index.docs.getValue(id)
        assertThat(removed.status).isEqualTo(DocumentStatus.OUT_OF_INDEX)
        assertThat(removed.included).isFalse()
        assertThat(index.chunks).doesNotContainKey(id)
        assertThat(index.docs).doesNotContainKey(shadow)
        assertThat(runStore.run.first().documentIds).isEmpty()
        assertThat(copyOf(id).exists()).isTrue()
        assertThat(logger.lines.any { it.startsWith("library out-of-index id=$id chunks=4 in ") }).isTrue()
    }

    @Test
    fun removeFromIndex_ofADocumentNotIndexed_doesNothing() = runTest {
        val pending = doc("Pendiente.pdf", DocumentStatus.PENDING)
        runStore.edit { it.adding(listOf(pending)) }

        repository().removeFromIndex(pending)

        assertThat(index.docs.getValue(pending).status).isEqualTo(DocumentStatus.PENDING)
        assertThat(runStore.run.first().documentIds).containsExactly(pending)
    }

    @Test
    fun reindex_ofAnIndexedDocument_addsAShadowToTheRunAndEnqueuesIt_L6() = runTest {
        val id = doc("Manual.pdf", chunks = 2)
        val waiting = doc("Sin enviar.pdf", DocumentStatus.PENDING)
        runStore.edit { it.pausedBy(RunPauseCause.USER) }

        repository().reindex(id)

        val shadow = index.shadowOf(id)!!
        assertThat(shadow.status).isEqualTo(DocumentStatus.PENDING)
        val run = runStore.run.first()
        assertThat(run.documentIds).containsExactly(shadow.id)
        assertThat(run.pauseCause).isNull()
        assertThat(scheduler.enqueued).containsExactly(listOf(shadow.id))
        assertThat(run.documentIds).doesNotContain(waiting)
        assertThat(logger.lines).contains("library reindex id=$id shadow=${shadow.id}")
        // The original keeps answering meanwhile.
        assertThat(index.docs.getValue(id).status).isEqualTo(DocumentStatus.DONE)
        assertThat(index.chunks.getValue(id)).hasSize(2)

        // A second tap with a live shadow does nothing.
        repository().reindex(id)
        assertThat(index.docs.values.count { it.replacesId == id }).isEqualTo(1)
    }

    @Test
    fun reindex_ofADocumentOutOfTheIndex_putsItBackInTheRun_L6() = runTest {
        val id = doc("Manual.pdf", chunks = 2).also { index.removeFromIndex(it) }
        val waiting = doc("Sin enviar.pdf", DocumentStatus.PENDING)

        repository().reindex(id)

        assertThat(index.docs.getValue(id).status).isEqualTo(DocumentStatus.PENDING)
        assertThat(runStore.run.first().documentIds).containsExactly(id)
        assertThat(scheduler.enqueued).containsExactly(listOf(id))
        assertThat(runStore.run.first().documentIds).doesNotContain(waiting)
    }

    @Test
    fun reindex_retriesARetryableFailure_andDoesNothingInOtherStates_L6() = runTest {
        val retryable = doc("Reintentable.pdf", DocumentStatus.ERROR, error = IngestError.EMBEDDING_FAILED)
        val final = doc("Escaneado.pdf", DocumentStatus.ERROR, error = IngestError.NO_TEXT)
        val pending = doc("Pendiente.pdf", DocumentStatus.PENDING)
        val embedding = doc("A medias.pdf", DocumentStatus.EMBEDDING, chunks = 1)
        val repository = repository()

        listOf(retryable, final, pending, embedding).forEach { repository.reindex(it) }

        assertThat(index.docs.getValue(retryable).status).isEqualTo(DocumentStatus.PENDING)
        assertThat(index.docs.getValue(final).status).isEqualTo(DocumentStatus.ERROR)
        assertThat(index.docs.getValue(pending).status).isEqualTo(DocumentStatus.PENDING)
        assertThat(index.docs.getValue(embedding).status).isEqualTo(DocumentStatus.EMBEDDING)
        assertThat(scheduler.enqueued.flatten()).containsExactly(retryable)
        assertThat(index.docs.values.none { it.replacesId != null }).isTrue()
    }

    @Test
    fun reindex_withoutThePrivateCopy_doesNothing_D10() = runTest {
        val indexed = doc("Sin copia.pdf", chunks = 1, copy = false)
        val out = doc("Fuera sin copia.pdf", chunks = 1, copy = false).also { index.removeFromIndex(it) }

        repository().reindex(indexed)
        repository().reindex(out)

        assertThat(index.shadowOf(indexed)).isNull()
        assertThat(index.docs.getValue(out).status).isEqualTo(DocumentStatus.OUT_OF_INDEX)
        assertThat(runStore.run.first().documentIds).isEmpty()
    }

    @Test
    fun discardReindex_dropsOnlyAFailedShadow_L8() = runTest {
        val id = doc("Manual.pdf", chunks = 2)
        val shadow = index.addReindexShadow(id)
        index.insertChunks(shadow, listOf(chunkInput(0)))
        index.updateStatus(shadow, DocumentStatus.ERROR, error = IngestError.EMBEDDING_FAILED.code)
        runStore.edit { it.adding(listOf(shadow)) }
        val running = doc("Otro.pdf", chunks = 1)
        val runningShadow = index.addReindexShadow(running)

        repository().discardReindex(id)
        repository().discardReindex(running)

        assertThat(index.docs).doesNotContainKey(shadow)
        assertThat(index.chunks).doesNotContainKey(shadow)
        assertThat(index.docs.getValue(id).status).isEqualTo(DocumentStatus.DONE)
        assertThat(index.chunks.getValue(id)).hasSize(2)
        assertThat(copyOf(id).exists()).isTrue()
        assertThat(runStore.run.first().documentIds).isEmpty()
        assertThat(index.docs).containsKey(runningShadow)
    }

    @Test
    fun actions_withAnUnavailableIndex_doNothing_L12() = runTest {
        val id = doc("Manual.pdf", chunks = 2)
        val repository = repository()
        index.setCompatibility(IndexCompatibility.Unreadable("read"))

        repository.delete(id)
        repository.removeFromIndex(id)
        repository.reindex(id)
        repository.discardReindex(id)

        index.setCompatibility(IndexCompatibility.Compatible)
        assertThat(index.docs.getValue(id).status).isEqualTo(DocumentStatus.DONE)
        assertThat(index.shadowOf(id)).isNull()
        assertThat(copyOf(id).exists()).isTrue()
    }

    @Test
    fun actions_logNoNames() = runTest {
        val a = doc("Contrato confidencial A.pdf", chunks = 1)
        val b = doc("Contrato confidencial B.pdf", chunks = 1)
        val repository = repository()

        repository.setIncluded(a, false)
        repository.reindex(a)
        repository.removeFromIndex(b)
        repository.reindex(b)
        repository.delete(a)

        val log = logger.lines.joinToString("\n")
        assertThat(log).doesNotContain("onfidencial")
        assertThat(log).doesNotContain(dir.path)
    }

    @Test
    fun page_logsNoNames() = runTest {
        doc("Contrato confidencial.pdf", chunks = 1)

        documents(LibraryQuery(text = "confidencial"))

        assertThat(logger.lines.joinToString("\n")).doesNotContain("onfidencial")
    }
}
