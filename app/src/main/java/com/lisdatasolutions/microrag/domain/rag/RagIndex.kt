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

    /** Documents in [statuses], oldest first (creation time, then id). */
    suspend fun documents(statuses: Set<DocumentStatus>): List<StoredDocument>

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

    /** Deletes the document with its chunks and their FTS entries. */
    suspend fun deleteDocument(id: Long)

    /**
     * Stores a batch in one transaction; chunks already stored (same document and ordinal) are
     * ignored. An invalid batch is rejected whole with [IllegalArgumentException]. Returns the
     * number of chunks inserted.
     */
    suspend fun insertChunks(documentId: Long, chunks: List<ChunkInput>): Int

    /** Exact top-[k] search by cosine distance; [query] has 256 components and unit norm. */
    suspend fun vectorSearch(query: FloatArray, k: Int = DEFAULT_K): List<ScoredChunk>

    /** BM25 search (title 3, body 1) of [userQuery] normalized with SpanishText; no terms gives an empty list. */
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
