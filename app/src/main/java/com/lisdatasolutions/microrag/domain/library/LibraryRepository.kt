package com.lisdatasolutions.microrag.domain.library

import kotlinx.coroutines.flow.Flow

/** The Library and the document detail (F08 contracts/library-api.md). */
interface LibraryRepository {
    /** A Library page, computed again when the index changes (research R2). */
    fun page(query: LibraryQuery): Flow<LibraryResult>

    /** The detail; [DetailResult.NotFound] when the document does not exist or disappears (research R8). */
    fun detail(documentId: Long): Flow<DetailResult>

    /** The first chunk, or the cited one with its neighbouring sentences (research R7); null if not indexed. */
    suspend fun preview(documentId: Long, chunkId: Long?): DocumentPreview?

    /** Only indexed documents change (rule L3). */
    suspend fun setIncluded(documentId: Long, included: Boolean)

    /** Deletes the document, its shadow, chunks and private copy, and takes them out of the run (rule L4). */
    suspend fun delete(documentId: Long)

    /** «Quitar del índice» of an indexed document (rule L5). */
    suspend fun removeFromIndex(documentId: Long)

    /** «Reindexar documento», by state (rule L6). */
    suspend fun reindex(documentId: Long)

    /** Drops a failed reindex shadow, keeping the original as it is (rule L8). */
    suspend fun discardReindex(documentId: Long)
}

sealed interface LibraryResult {
    data class Loaded(val page: LibraryPage) : LibraryResult

    /** The index is incompatible or unreadable (F04): nothing to list (rule L12). */
    data object Unavailable : LibraryResult
}

sealed interface DetailResult {
    data class Loaded(val detail: DocumentDetail) : DetailResult
    data object NotFound : DetailResult

    /** The index is incompatible or unreadable: not the same as a deleted document (rule L12, FR-023). */
    data object Unavailable : DetailResult
}
