package com.lisdatasolutions.microrag.data.library

import android.os.SystemClock
import com.lisdatasolutions.microrag.core.logging.AppLogger
import com.lisdatasolutions.microrag.data.indexing.IngestRunStore
import com.lisdatasolutions.microrag.data.ingest.DocumentFiles
import com.lisdatasolutions.microrag.domain.indexing.IndexingRepository
import com.lisdatasolutions.microrag.domain.indexing.IndexingState
import com.lisdatasolutions.microrag.domain.indexing.PauseReason
import com.lisdatasolutions.microrag.domain.ingest.DocumentFormat
import com.lisdatasolutions.microrag.domain.ingest.IngestError
import com.lisdatasolutions.microrag.domain.library.DetailResult
import com.lisdatasolutions.microrag.domain.library.DocumentDetail
import com.lisdatasolutions.microrag.domain.library.DocumentDetailState
import com.lisdatasolutions.microrag.domain.library.DocumentPreview
import com.lisdatasolutions.microrag.domain.library.LibraryDocument
import com.lisdatasolutions.microrag.domain.library.LibraryDocumentState
import com.lisdatasolutions.microrag.domain.library.LibraryFilter
import com.lisdatasolutions.microrag.domain.library.LibraryPage
import com.lisdatasolutions.microrag.domain.library.LibraryQuery
import com.lisdatasolutions.microrag.domain.library.LibraryRepository
import com.lisdatasolutions.microrag.domain.library.LibraryResult
import com.lisdatasolutions.microrag.domain.library.ReindexRow
import com.lisdatasolutions.microrag.domain.library.ReindexState
import com.lisdatasolutions.microrag.domain.rag.DocumentStatus
import com.lisdatasolutions.microrag.domain.rag.IndexCompatibility
import com.lisdatasolutions.microrag.domain.rag.IndexUnavailableException
import com.lisdatasolutions.microrag.domain.rag.LibraryEntry
import com.lisdatasolutions.microrag.domain.rag.RagIndex
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.withContext

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

    /** Research R8 and R10: the document, its shadow, what is stored and the run's pause, again on every change. */
    @OptIn(ExperimentalCoroutinesApi::class)
    override fun detail(documentId: Long): Flow<DetailResult> = flow {
        if (index.open() != IndexCompatibility.Compatible) {
            emit(DetailResult.Unavailable)
            return@flow
        }
        emitAll(
            // The run's state, once known: D5 shows its pause cause.
            combine(index.documentChanges, indexing.state.filter { it != IndexingState.Loading }) { _, run ->
                (run as? IndexingState.Active)?.pause
            }
                .mapLatest { pause -> loadDetail(documentId, pause) },
        )
    }.catch { error ->
        if (error is IndexUnavailableException) emit(DetailResult.Unavailable) else throw error
    }.distinctUntilChanged().flowOn(io)

    private suspend fun loadDetail(documentId: Long, pause: PauseReason?): DetailResult {
        // A shadow is never shown as a document of its own (rule L2).
        val document = index.document(documentId)?.takeIf { it.replacesId == null } ?: return DetailResult.NotFound
        val shadow = index.shadowOf(documentId)
        val counting = listOfNotNull(document.id.takeIf { document.status == DocumentStatus.EMBEDDING }, shadow?.id)
        val stored = if (counting.isEmpty()) emptyMap() else index.storedChunkCounts(counting)
        val copyMissing = !files.exists(document.localPath)
        val state = when (document.status) {
            DocumentStatus.DONE -> DocumentDetailState.Indexed
            DocumentStatus.PENDING, DocumentStatus.PARSED, DocumentStatus.PAUSED -> DocumentDetailState.Pending
            DocumentStatus.EMBEDDING -> DocumentDetailState.Indexing(stored[document.id] ?: 0, document.chunkCount)
            DocumentStatus.ERROR -> DocumentDetailState.Failed(document.error.toIngestError())
            DocumentStatus.OUT_OF_INDEX -> DocumentDetailState.OutOfIndex
        }
        val reindex = shadow?.let {
            if (it.status == DocumentStatus.ERROR) {
                ReindexState.Failed(it.id, it.error.toIngestError())
            } else {
                ReindexState.Running(it.id, stored[it.id] ?: 0, it.chunkCount, pause)
            }
        }
        val reindexable = (document.status == DocumentStatus.DONE && shadow == null) || document.status == DocumentStatus.OUT_OF_INDEX
        return DetailResult.Loaded(
            DocumentDetail(
                id = document.id,
                displayName = document.displayName,
                format = DocumentFormat.fromMime(document.mimeType),
                state = state,
                included = document.included,
                pages = document.pageCount,
                chunks = document.chunkCount.takeIf { document.status == DocumentStatus.DONE },
                sizeBytes = document.sizeBytes,
                importedAtMs = document.createdAtMs,
                canReindex = reindexable && !copyMissing,
                copyMissing = copyMissing,
                reindex = reindex,
            ),
        )
    }

    /** Research R7 and rule L9; the sentences are split off the index's thread. */
    override suspend fun preview(documentId: Long, chunkId: Long?): DocumentPreview? = try {
        val document = index.document(documentId)
        if (document == null || document.status != DocumentStatus.DONE) {
            null
        } else {
            val cited = chunkId?.let { index.loadChunks(listOf(it))[it] }?.takeIf { it.documentId == documentId }
            if (cited != null) {
                val previous = index.neighbor(documentId, cited.ordinal - 1)
                val next = index.neighbor(documentId, cited.ordinal + 1)
                withContext(io) {
                    DocumentPreview(
                        page = cited.page,
                        before = previous?.content?.let(PreviewText::lastSentence),
                        text = cited.content,
                        after = next?.content?.let(PreviewText::firstSentence),
                        highlighted = true,
                        citedChunkMissing = false,
                    )
                }
            } else {
                index.neighbor(documentId, 0)?.let { first ->
                    DocumentPreview(first.page, null, first.content, null, highlighted = false, citedChunkMissing = chunkId != null)
                }
            }
        }
    } catch (e: IndexUnavailableException) {
        null
    }

    override suspend fun setIncluded(documentId: Long, included: Boolean) = writing {
        // Rule L3: only indexed documents; F07 searches follow it on their next call.
        val document = index.document(documentId) ?: return@writing
        if (document.status != DocumentStatus.DONE || document.replacesId != null) return@writing
        index.setIncluded(documentId, included)
        logger.i(TAG) { "library include id=$documentId included=$included" }
    }

    /**
     * Rule L4, the only delete path (the Ingest's «Quitar de la cola» too): the index in one transaction,
     * then the run, then the private copy. If the process dies or the copy cannot go, the orphan cleanup
     * takes it later (research R6).
     */
    override suspend fun delete(documentId: Long) = writing {
        val document = index.document(documentId) ?: return@writing
        val shadow = index.shadowOf(documentId)
        val started = clock()
        index.deleteDocument(documentId)
        runStore.edit { it.without(listOfNotNull(documentId, shadow?.id)) }
        // A shadow shares its original's copy.
        if (document.replacesId == null && !files.delete(document.localPath)) {
            logger.w(TAG) { "library delete id=$documentId copy left for the orphan cleanup" }
        }
        logger.i(TAG) { "library delete id=$documentId chunks=${document.chunkCount ?: 0} in ${clock() - started} ms" }
    }

    /** Rule L5: only indexed documents; the run forgets the document and its shadow. */
    override suspend fun removeFromIndex(documentId: Long) = writing {
        val shadow = index.shadowOf(documentId)
        val started = clock()
        val removed = index.removeFromIndex(documentId) ?: return@writing
        runStore.edit { it.without(listOfNotNull(documentId, shadow?.id)) }
        logger.i(TAG) { "library out-of-index id=$documentId chunks=$removed in ${clock() - started} ms" }
    }

    /** Rule L6, by state; nothing without the private copy (D10). */
    override suspend fun reindex(documentId: Long) = writing {
        val document = index.document(documentId)?.takeIf { it.replacesId == null } ?: return@writing
        if (!files.exists(document.localPath)) return@writing
        when (document.status) {
            DocumentStatus.DONE -> {
                if (index.shadowOf(documentId) != null) return@writing
                // The original keeps answering until the shadow is published (research R5).
                val shadow = try {
                    index.addReindexShadow(documentId)
                } catch (e: IllegalStateException) {
                    return@writing
                }
                indexing.add(shadow)
                logger.i(TAG) { "library reindex id=$documentId shadow=$shadow" }
            }
            DocumentStatus.ERROR -> indexing.retry(documentId)
            DocumentStatus.OUT_OF_INDEX -> {
                index.updateStatus(documentId, DocumentStatus.PENDING)
                indexing.add(documentId)
                logger.i(TAG) { "library reindex id=$documentId" }
            }
            else -> Unit
        }
    }

    /** Rule L8: only a failed shadow goes; the original and its copy stay as they are. */
    override suspend fun discardReindex(documentId: Long) = writing {
        val shadow = index.shadowOf(documentId)?.takeIf { it.status == DocumentStatus.ERROR } ?: return@writing
        index.deleteDocument(shadow.id)
        runStore.edit { it.without(listOf(shadow.id)) }
        logger.i(TAG) { "library reindex discarded id=$documentId shadow=${shadow.id}" }
    }

    /** Rule L12: with the index unavailable, writing actions do nothing; the screens already say why. */
    private suspend fun writing(action: suspend () -> Unit) {
        try {
            action()
        } catch (e: IndexUnavailableException) {
            logger.w(TAG) { "library action skipped: index ${e.state.javaClass.simpleName}" }
        }
    }

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
