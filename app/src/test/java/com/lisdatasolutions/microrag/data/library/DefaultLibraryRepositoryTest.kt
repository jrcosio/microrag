package com.lisdatasolutions.microrag.data.library

import com.google.common.truth.Truth.assertThat
import com.lisdatasolutions.microrag.data.indexing.DefaultIndexingRepository
import com.lisdatasolutions.microrag.data.indexing.FakeThermalStatusSource
import com.lisdatasolutions.microrag.data.indexing.InMemoryIngestRunStore
import com.lisdatasolutions.microrag.data.ingest.DocumentFileStore
import com.lisdatasolutions.microrag.data.ingest.DocumentFiles
import com.lisdatasolutions.microrag.domain.ingest.DocumentFormat
import com.lisdatasolutions.microrag.domain.ingest.IngestError
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
        return DefaultLibraryRepository(index, files ?: DocumentFiles(store, index, dispatcher, logger), runStore, indexing, dispatcher, logger)
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

    @Test
    fun page_logsNoNames() = runTest {
        doc("Contrato confidencial.pdf", chunks = 1)

        documents(LibraryQuery(text = "confidencial"))

        assertThat(logger.lines.joinToString("\n")).doesNotContain("onfidencial")
    }
}
