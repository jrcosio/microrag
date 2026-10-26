package com.lisdatasolutions.microrag.testing

import com.lisdatasolutions.microrag.domain.rag.ChunkInput
import com.lisdatasolutions.microrag.domain.rag.ChunkView
import com.lisdatasolutions.microrag.domain.rag.DocumentStatus
import com.lisdatasolutions.microrag.domain.rag.DuplicateDocumentException
import com.lisdatasolutions.microrag.domain.rag.IndexCompatibility
import com.lisdatasolutions.microrag.domain.rag.IndexCounts
import com.lisdatasolutions.microrag.domain.rag.IndexStorageFullException
import com.lisdatasolutions.microrag.domain.rag.IndexUnavailableException
import com.lisdatasolutions.microrag.domain.rag.NewDocument
import com.lisdatasolutions.microrag.domain.rag.RagIndex
import com.lisdatasolutions.microrag.domain.rag.ScoredChunk
import com.lisdatasolutions.microrag.domain.rag.StoredDocument
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** In-memory [RagIndex] for JVM tests of ingestion: documents and stored chunks, no search. */
class FakeRagIndex(initial: IndexCompatibility = IndexCompatibility.Compatible) : RagIndex {

    private val state = MutableStateFlow(initial)
    override val compatibility: StateFlow<IndexCompatibility> = state

    private val changes = MutableStateFlow(0L)
    override val documentChanges: StateFlow<Long> = changes

    val docs = linkedMapOf<Long, StoredDocument>()

    /** Stored chunks per document, by ordinal, as INSERT OR IGNORE leaves them. */
    val chunks = linkedMapOf<Long, java.util.SortedMap<Int, ChunkInput>>()

    /** Set to make the next insertChunks fail as a full disk would. */
    var failNextInsertWithFullDisk = false

    /** Batches stored, in order: (document, ordinals). */
    val insertedBatches = mutableListOf<Pair<Long, List<Int>>>()
    private var nextId = 1L
    var now = 1_000L

    fun setCompatibility(value: IndexCompatibility) {
        state.value = value
    }

    private fun <T> available(block: () -> T): T {
        if (state.value != IndexCompatibility.Compatible) throw IndexUnavailableException(state.value)
        return block()
    }

    override suspend fun open(): IndexCompatibility = state.value

    override suspend fun meta(): Map<String, String> = available { emptyMap() }

    override suspend fun addDocument(document: NewDocument): Long = available {
        docs.values.firstOrNull { it.contentHash == document.contentHash }?.let { throw DuplicateDocumentException(it.id) }
        val id = nextId++
        docs[id] = StoredDocument(
            id = id, displayName = document.displayName, contentHash = document.contentHash, mimeType = document.mimeType,
            uri = document.uri, status = DocumentStatus.PENDING, chunkCount = null, error = null, included = true,
            sizeBytes = document.sizeBytes, pageCount = document.pageCount, localPath = document.localPath,
            createdAtMs = now++, updatedAtMs = now,
        )
        changes.value += 1
        id
    }

    override suspend fun document(id: Long): StoredDocument? = available { docs[id] }

    override suspend fun documents(statuses: Set<DocumentStatus>): List<StoredDocument> = available {
        docs.values.filter { it.status in statuses }.sortedWith(compareBy({ it.createdAtMs }, { it.id }))
    }

    override suspend fun documentIdByHash(contentHash: String): Long? = available {
        docs.values.firstOrNull { it.contentHash == contentHash }?.id
    }

    override suspend fun updateStatus(id: Long, status: DocumentStatus, error: String?, chunkCount: Int?, skippedPages: Int?) =
        available {
            val doc = docs[id] ?: return@available
            docs[id] = doc.copy(
                status = status, error = error, chunkCount = chunkCount ?: doc.chunkCount,
                skippedPages = skippedPages ?: doc.skippedPages, updatedAtMs = now++,
            )
            changes.value += 1
        }

    override suspend fun setIncluded(id: Long, included: Boolean) = available {
        docs[id]?.let { docs[id] = it.copy(included = included) }
        changes.value += 1
    }

    override suspend fun deleteDocument(id: Long) = available {
        docs.remove(id)
        chunks.remove(id)
        changes.value += 1
    }

    override suspend fun storedChunkCounts(ids: Collection<Long>): Map<Long, Int> = available {
        ids.associateWith { chunks[it]?.size ?: 0 }
    }

    override suspend fun storedChunkContents(documentId: Long): Map<Int, String> = available {
        chunks[documentId].orEmpty().mapValuesTo(LinkedHashMap()) { it.value.content }
    }

    override suspend fun deleteChunks(documentId: Long) = available {
        chunks.remove(documentId)
        changes.value += 1
    }

    override suspend fun insertChunks(documentId: Long, chunks: List<ChunkInput>): Int = available {
        require(chunks.map { it.ordinal }.toSet().size == chunks.size) { "Repeated ordinals in the batch" }
        requireNotNull(docs[documentId]) { "Unknown document $documentId" }
        if (failNextInsertWithFullDisk) {
            failNextInsertWithFullDisk = false
            throw IndexStorageFullException(RuntimeException("Error code: 13, message: database or disk is full"))
        }
        val stored = this.chunks.getOrPut(documentId) { sortedMapOf() }
        val inserted = chunks.count { stored.putIfAbsent(it.ordinal, it) == null }
        insertedBatches += documentId to chunks.map { it.ordinal }
        if (inserted > 0) changes.value += 1
        inserted
    }

    override suspend fun vectorSearch(query: FloatArray, k: Int): List<ScoredChunk> = available { emptyList() }

    override suspend fun textSearch(userQuery: String, k: Int): List<Long> = available { emptyList() }

    override suspend fun loadChunks(ids: Collection<Long>): Map<Long, ChunkView> = available { emptyMap() }

    override suspend fun neighbor(documentId: Long, ordinal: Int): ChunkView? = available { null }

    override suspend fun counts(): IndexCounts = available { IndexCounts(docs.size, 0, 0) }

    override suspend fun close() = Unit
}
