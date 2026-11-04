package com.lisdatasolutions.microrag.domain.library

import kotlinx.coroutines.flow.Flow
import com.lisdatasolutions.microrag.domain.rag.DocumentRef
import com.lisdatasolutions.microrag.domain.rag.FolderList
import com.lisdatasolutions.microrag.domain.rag.LibrarySummary

/** The Library and the document detail (F08 contracts/library-api.md). */
interface LibraryRepository {
    /** A Library page, computed again when the index changes (research R2). */
    fun page(query: LibraryQuery): Flow<LibraryResult>

    /** Home's counts of the whole library, computed again when the index changes (F09 research R2). */
    fun summary(): Flow<SummaryResult>

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

    /** F17 (contract F1): the folders and «General», computed again when the index changes. */
    fun folders(): Flow<FoldersResult>

    /** F2, with a name already checked by FolderNames: [FolderResult.Done], [FolderResult.Duplicate] or [FolderResult.Failed]. */
    suspend fun createFolder(name: String, norm: String): FolderResult

    suspend fun renameFolder(id: Long, name: String, norm: String): FolderResult

    /** F3: whether the folder went; without [moveDocuments], only if it has no documents left. False when it fails. */
    suspend fun deleteFolder(id: Long, moveDocuments: Boolean): Boolean

    /** Every document of a folder, or of «General» with null; null when the index is unavailable. */
    suspend fun folderDocumentRefs(folderId: Long?): List<DocumentRef>?

    /** F4 (K5): whether it moved; false when the folder is gone or the index fails. */
    suspend fun moveDocument(documentId: Long, folderId: Long?): Boolean
}

sealed interface FoldersResult {
    data class Loaded(val folders: FolderList) : FoldersResult

    /** The index is incompatible or unreadable, as [LibraryResult.Unavailable]. */
    data object Unavailable : FoldersResult
}

sealed interface LibraryResult {
    data class Loaded(val page: LibraryPage) : LibraryResult

    /** The index is incompatible or unreadable (F04): nothing to list (rule L12). */
    data object Unavailable : LibraryResult
}

/** The Home summary (F09 research R2). */
sealed interface SummaryResult {
    data class Loaded(val summary: LibrarySummary) : SummaryResult

    /** The index is incompatible or unreadable (F04), as [LibraryResult.Unavailable]. */
    data object Unavailable : SummaryResult
}

sealed interface DetailResult {
    data class Loaded(val detail: DocumentDetail) : DetailResult
    data object NotFound : DetailResult

    /** The index is incompatible or unreadable: not the same as a deleted document (rule L12, FR-023). */
    data object Unavailable : DetailResult
}
