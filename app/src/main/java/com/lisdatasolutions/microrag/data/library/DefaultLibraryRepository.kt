package com.lisdatasolutions.microrag.data.library

import android.os.SystemClock
import com.lisdatasolutions.microrag.core.logging.AppLogger
import com.lisdatasolutions.microrag.data.indexing.IngestRunStore
import com.lisdatasolutions.microrag.data.ingest.DocumentFiles
import com.lisdatasolutions.microrag.domain.indexing.IndexingRepository
import com.lisdatasolutions.microrag.domain.ingest.DocumentFormat
import com.lisdatasolutions.microrag.domain.ingest.IngestError
import com.lisdatasolutions.microrag.domain.library.DetailResult
import com.lisdatasolutions.microrag.domain.library.DocumentPreview
import com.lisdatasolutions.microrag.domain.library.LibraryDocument
import com.lisdatasolutions.microrag.domain.library.LibraryDocumentState
import com.lisdatasolutions.microrag.domain.library.LibraryFilter
import com.lisdatasolutions.microrag.domain.library.LibraryPage
import com.lisdatasolutions.microrag.domain.library.LibraryQuery
import com.lisdatasolutions.microrag.domain.library.LibraryRepository
import com.lisdatasolutions.microrag.domain.library.LibraryResult
import com.lisdatasolutions.microrag.domain.library.ReindexRow
import com.lisdatasolutions.microrag.domain.rag.DocumentStatus
import com.lisdatasolutions.microrag.domain.rag.IndexCompatibility
import com.lisdatasolutions.microrag.domain.rag.IndexUnavailableException
import com.lisdatasolutions.microrag.domain.rag.LibraryEntry
import com.lisdatasolutions.microrag.domain.rag.RagIndex
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.mapLatest

/**
 * The Library and the document detail (F08 contracts/library-api.md) on the index, the indexing run
 * and the private copies. Index work runs on the index's own dispatcher, files and flows on [io].
 * Logs carry ids, counts and times only (rule L11).
 */
class DefaultLibraryRepository(
    private val index: RagIndex,
    private val files: DocumentFiles,
    private val runStore: IngestRunStore,
    private val indexing: IndexingRepository,
    private val io: CoroutineDispatcher,
    private val logger: AppLogger,
    private val clock: () -> Long = SystemClock::elapsedRealtime,
) : LibraryRepository {

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun page(query: LibraryQuery): Flow<LibraryResult> = flow {
        if (index.open() != IndexCompatibility.Compatible) {
            emit(LibraryResult.Unavailable)
            return@flow
        }
        // Rule R6: a copy left behind by a delete interrupted earlier goes now.
        files.cleanOrphansOnce()
        emitAll(index.documentChanges.mapLatest { load(query) })
    }.catch { error ->
        if (error is IndexUnavailableException) emit(LibraryResult.Unavailable) else throw error
    }.distinctUntilChanged().flowOn(io)

    /** Rule L1: two queries on `documents`, never on `chunks`. */
    private suspend fun load(query: LibraryQuery): LibraryResult {
        val statuses = query.filter.statuses()
        val entries = index.libraryPage(query.text, statuses, query.limit)
        val total = index.libraryCount(query.text, statuses)
        return LibraryResult.Loaded(LibraryPage(entries.map { it.toLibraryDocument() }, total))
    }

    override fun detail(documentId: Long): Flow<DetailResult> = TODO("F08 US3 (T032)")

    override suspend fun preview(documentId: Long, chunkId: Long?): DocumentPreview? = TODO("F08 US3 (T032)")

    override suspend fun setIncluded(documentId: Long, included: Boolean): Unit = TODO("F08 US2 (T026)")

    override suspend fun delete(documentId: Long): Unit = TODO("F08 US4 (T040)")

    override suspend fun removeFromIndex(documentId: Long): Unit = TODO("F08 US4 (T040)")

    override suspend fun reindex(documentId: Long): Unit = TODO("F08 US4 (T040)")

    override suspend fun discardReindex(documentId: Long): Unit = TODO("F08 US4 (T040)")

    private fun LibraryFilter.statuses(): Set<DocumentStatus> = when (this) {
        LibraryFilter.ALL -> DocumentStatus.entries.toSet()
        LibraryFilter.INDEXED -> setOf(DocumentStatus.DONE)
        // G16: everything not indexed, each with its own chip.
        LibraryFilter.PENDING -> DocumentStatus.entries.toSet() - DocumentStatus.DONE
    }

    /** Research R10. */
    private fun LibraryEntry.toLibraryDocument() = LibraryDocument(
        id = document.id,
        displayName = document.displayName,
        format = DocumentFormat.fromMime(document.mimeType),
        state = when (document.status) {
            DocumentStatus.DONE -> LibraryDocumentState.Indexed(
                chunks = document.chunkCount ?: 0,
                reindex = when (shadowStatus) {
                    null -> null
                    DocumentStatus.ERROR -> ReindexRow.FAILED
                    else -> ReindexRow.RUNNING
                },
            )
            DocumentStatus.PENDING, DocumentStatus.PARSED, DocumentStatus.PAUSED -> LibraryDocumentState.Pending
            DocumentStatus.EMBEDDING -> LibraryDocumentState.Indexing
            DocumentStatus.ERROR -> LibraryDocumentState.Failed(document.error.toIngestError())
            DocumentStatus.OUT_OF_INDEX -> LibraryDocumentState.OutOfIndex
        },
        included = document.included,
    )

    private fun String?.toIngestError(): IngestError = IngestError.fromCode(this) ?: IngestError.EXTRACTION_FAILED

    private companion object {
        const val TAG = "MicroRAG"
    }
}
