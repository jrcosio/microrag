package com.lisdatasolutions.microrag.domain.rag

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
    suspend fun libraryPage(text: String, statuses: Set<DocumentStatus>, limit: Int): List<LibraryEntry>

    /** How many documents [libraryPage] would list without a limit. */
    suspend fun libraryCount(text: String, statuses: Set<DocumentStatus>): Int

    /** Id of the document with this content hash, if any (F06 duplicate check). */
    suspend fun documentIdByHash(contentHash: String): Long?

    /**
     * Increases after every committed write that changes documents or their chunks: add, status,
     * inclusion, delete and chunk insertion. Readers reload what they show when it changes.
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
     * not a shadow or its original is not done.
     */
    suspend fun publishReindex(shadowId: Long): Int?

    /**
     * «Quitar del índice» (F08 research R6), in one transaction: deletes the document's shadow and
     * chunks, leaves it [DocumentStatus.OUT_OF_INDEX] with its private copy and inclusion, and marks
     * `quant_dirty`. Returns the chunks removed, or null, changing nothing, when it is not a done document.
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
     * of documents that are done and included are found (F07 rule N7).
     */
    suspend fun vectorSearch(query: FloatArray, k: Int = DEFAULT_K): List<ScoredChunk>

    /**
     * BM25 search (title 3, body 1) of [userQuery] normalized with SpanishText; no terms gives an empty
     * list. Only chunks of documents that are done and included are found (F07 rule N7).
     */
    suspend fun textSearch(userQuery: String, k: Int = DEFAULT_K): List<Long>

    /** Unknown ids are left out of the map. */
    suspend fun loadChunks(ids: Collection<Long>): Map<Long, ChunkView>

    /** The chunk at [ordinal] of [documentId], if any. */
    suspend fun neighbor(documentId: Long, ordinal: Int): ChunkView?

    suspend fun counts(): IndexCounts

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
