package com.lisdatasolutions.microrag.domain.rag

import com.lisdatasolutions.microrag.domain.model.ModelId
import kotlinx.coroutines.flow.StateFlow

/**
 * The local RAG index (F04 contracts/rag-index-api.md): documents, chunks with their vectors, the
 * FTS5 index and `rag_meta`. Used by ingestion (F06/F07), retrieval (F10) and citations (F11).
 *
 * Every operation but [compatibility], [open] and [close] throws [IndexUnavailableException] unless
 * the index is [IndexCompatibility.Compatible]; the first operation opens it.
 */
interface RagIndex {
    val compatibility: StateFlow<IndexCompatibility>

    /** Opens (or creates) the index and validates it. Idempotent. */
    suspend fun open(): IndexCompatibility

    suspend fun meta(): Map<String, String>

    /**
     * F19 research R2 (contract E1): the text embedder `rag_meta` records, the 270M or the 440M. Before the index
     * opens, the value it was built with; the only source for whoever embeds text.
     */
    val textEmbedder: StateFlow<ModelId>

    /** F19 contract E3: the page keys of `rag_meta` match this app, or no page was stored yet. */
    val pagesUsable: StateFlow<Boolean>

    /**
     * F19 research R4 (contract E2): records [to] as the text embedder in one transaction, after checking that the
     * index still records the other accepted one. False when it already records [to]. Chunks and vectors stay.
     */
    suspend fun switchTextEmbedder(to: ModelId): Boolean

    /** F19 contract P1: the page numbers already stored for [documentId], to resume where it stopped. */
    suspend fun storedPages(documentId: Long): Set<Int>

    /**
     * F19 contract P2: stores one page in its own transaction; the first page of the index also writes the page keys
     * of `rag_meta`. False when it was already stored. Throws [IndexStorageFullException] as [insertChunks] does, and
     * [IllegalStateException] when the stored pages belong to another page space ([pagesUsable] is false).
     */
    suspend fun insertPage(documentId: Long, page: PageInput): Boolean

    /**
     * F19 contract P3 (research R11): the image files of [documentId] and of its reindex shadow. Callers read them
     * before deleting or replacing the rows, and delete the files after; the startup sweep catches the rest.
     */
    suspend fun pageFiles(documentId: Long): List<String>

    /** F19 contract P6: every image file the index names, for the startup sweep. */
    suspend fun allPageFiles(): Set<String>

    /** F19 contract P8: the mode of a fresh shadow, or of an errored or out-of-index document before it is retried. */
    suspend fun setIndexMode(id: Long, mode: IndexMode)

    /** Registers a user document as pending; throws [DuplicateDocumentException] for a known content hash. */
    suspend fun addDocument(document: NewDocument): Long

    suspend fun document(id: Long): StoredDocument?

    /** Documents in [statuses], oldest first (creation time, then id); reindex shadows included (F07 runs them). */
    suspend fun documents(statuses: Set<DocumentStatus>): List<StoredDocument>

    /**
     * A Library page (F08 research R2): documents that are not reindex shadows, with a status in
     * [statuses] and a name holding [text] without case or accents (blank: any name), newest first, at
     * most [limit]. Each entry carries its shadow's status. Never reads the chunks (rule L1).
     */
    suspend fun libraryPage(
        text: String,
        statuses: Set<DocumentStatus>,
        limit: Int,
        folder: LibraryFolderFilter = LibraryFolderFilter.All,
    ): List<LibraryEntry>

    /** How many documents [libraryPage] would list without a limit. */
    suspend fun libraryCount(text: String, statuses: Set<DocumentStatus>, folder: LibraryFolderFilter = LibraryFolderFilter.All): Int

    /** F17 (contract F1): the folders by folded name with their documents, and the documents in «General»; one aggregate query. */
    suspend fun folders(): FolderList

    /** F2: the new folder's id, or null when another folder has [norm]. */
    suspend fun createFolder(name: String, norm: String): Long?

    /** F2: null when another folder has [norm]; false when [id] does not exist. */
    suspend fun renameFolder(id: Long, name: String, norm: String): Boolean?

    /**
     * F3: with [moveDocuments], its documents go to «General» and the folder goes, in one transaction;
     * without, the folder goes only if no document is left in it. Returns whether it went.
     */
    suspend fun deleteFolder(id: Long, moveDocuments: Boolean): Boolean

    /** F3: every document of a folder, or of «General» with null, newest first; shadows left out. */
    suspend fun folderDocumentRefs(folderId: Long?): List<DocumentRef>

    /**
     * F4: puts [documentId] in [folderId] (null: «General»), and nothing else changes: its state, inclusion
     * and chunks stay. False when the document or the folder does not exist, or it is a reindex shadow.
     */
    suspend fun moveDocument(documentId: Long, folderId: Long?): Boolean

    /** Id of the document with this content hash, if any (F06 duplicate check). */
    suspend fun documentIdByHash(contentHash: String): Long?

    /**
     * Increases after every committed write that changes documents or their chunks: add, status,
     * inclusion, delete and chunk insertion, and the folders (F17). Readers reload what they show when it changes.
     */
    val documentChanges: StateFlow<Long>

    /** [error] is always written (null clears it); [chunkCount] and [skippedPages] only when not null. */
    suspend fun updateStatus(
        id: Long,
        status: DocumentStatus,
        error: String? = null,
        chunkCount: Int? = null,
        skippedPages: Int? = null,
    )

    suspend fun setIncluded(id: Long, included: Boolean)

    /** Deletes the document with its reindex shadow, their chunks and FTS entries, and marks `quant_dirty` (F08 R6). */
    suspend fun deleteDocument(id: Long)

    /**
     * Registers a reindex shadow of a done document (F08 research R5): a pending document with the same
     * name, type, size, pages and private copy, `replaces_id` set and never searchable. Throws
     * [IllegalStateException] when [originalId] is missing, not done, a shadow, or already has one.
     */
    suspend fun addReindexShadow(originalId: Long): Long

    /** The reindex shadow of [originalId], if any. */
    suspend fun shadowOf(originalId: Long): StoredDocument?

    /**
     * Puts the shadow's chunks in place of its original's in one transaction (F08 research R5): the
     * original keeps its id, status and inclusion, the chunks keep their ids, vectors and FTS rows, and
     * the shadow is deleted. Returns the chunks published, or null, changing nothing, when [shadowId] is
     * not a shadow or its original is not done. F19 (contract P5): the shadow's pages and index mode replace
     * the original's too.
     */
    suspend fun publishReindex(shadowId: Long): Int?

    /**
     * «Quitar del índice» (F08 research R6), in one transaction: deletes the document's shadow and
     * chunks, leaves it [DocumentStatus.OUT_OF_INDEX] with its private copy and inclusion, and marks
     * `quant_dirty`. Returns the chunks removed, or null, changing nothing, when it is not a done document.
     * F19 (contract P4): its pages go too.
     */
    suspend fun removeFromIndex(id: Long): Int?

    /** Stored chunks per document, for [ids]; ids without chunks count 0 (F07 research R8). */
    suspend fun storedChunkCounts(ids: Collection<Long>): Map<Long, Int>

    /** Ordinal → content of the stored chunks of [documentId], in ordinal order (F07 research R9). */
    suspend fun storedChunkContents(documentId: Long): Map<Int, String>

    /** Deletes the chunks of [documentId] and their FTS rows, keeping the document (F07 research R9). */
    suspend fun deleteChunks(documentId: Long)

    /**
     * Stores a batch in one transaction; chunks already stored (same document and ordinal) are
     * ignored. An invalid batch is rejected whole with [IllegalArgumentException], and a full disk with
     * [IndexStorageFullException]. The FTS title is the document's [DocumentTitle]. Returns the number
     * of chunks inserted.
     */
    suspend fun insertChunks(documentId: Long, chunks: List<ChunkInput>): Int

    /**
     * Exact top-[k] search by cosine distance; [query] has 256 components and unit norm. Only chunks
     * of documents that are done and included are found (F07 rule N7), and only in [scope] (F17).
     */
    suspend fun vectorSearch(query: FloatArray, k: Int = DEFAULT_K, scope: SearchScope = SearchScope.All): List<ScoredChunk>

    /**
     * BM25 search (title 3, body 1) of [userQuery] normalized with SpanishText; no terms gives an empty
     * list. Only chunks of documents that are done and included are found (F07 rule N7), and only in [scope].
     */
    suspend fun textSearch(userQuery: String, k: Int = DEFAULT_K, scope: SearchScope = SearchScope.All): List<Long>

    /**
     * Unknown ids are left out of the map. With [searchableOnly], so are chunks of documents that are not
     * done and included, the rule of the searches (F10 research R7): what retrieval loads after searching.
     * Chunks of documents outside [scope] are left out too (F17).
     */
    suspend fun loadChunks(ids: Collection<Long>, searchableOnly: Boolean = false, scope: SearchScope = SearchScope.All): Map<Long, ChunkView>

    /** The chunk at [ordinal] of [documentId], if any; only if [loadChunks] would load it with the same arguments. */
    suspend fun neighbor(documentId: Long, ordinal: Int, searchableOnly: Boolean = false, scope: SearchScope = SearchScope.All): ChunkView?

    suspend fun counts(): IndexCounts

    /** All documents without shadows, and the stored chunks of the done ones, in one query on `documents` (F09 R2). */
    suspend fun librarySummary(): LibrarySummary

    /**
     * Documents without shadows, the searchable ones and the included ones still to index, in one query (F11
     * R11). F17 (contract F8): the last two count only in [scope]; the first is always the whole library.
     */
    suspend fun chatLibraryState(scope: SearchScope = SearchScope.All): ChatLibraryState

    /** Closes the connection and goes back to [IndexCompatibility.Closed]; the next operation reopens it. */
    suspend fun close()

    companion object {
        const val DEFAULT_K = 50
    }
}

class IndexUnavailableException(val state: IndexCompatibility) : IllegalStateException("RAG index unavailable: $state")

class DuplicateDocumentException(val existingId: Long) : IllegalStateException("Document already registered: $existingId")

/** The index could not write because the device is out of space (SQLITE_FULL, F07 research R10). */
class IndexStorageFullException(cause: Throwable) : IllegalStateException("No space left to write the index", cause)
