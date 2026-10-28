package com.lisdatasolutions.microrag.testing

import com.lisdatasolutions.microrag.domain.rag.ChunkInput
import com.lisdatasolutions.microrag.domain.rag.ChunkView
import com.lisdatasolutions.microrag.domain.rag.DocumentNames
import com.lisdatasolutions.microrag.domain.rag.DocumentStatus
import com.lisdatasolutions.microrag.domain.rag.DuplicateDocumentException
import com.lisdatasolutions.microrag.domain.rag.IndexCompatibility
import com.lisdatasolutions.microrag.domain.rag.IndexCounts
import com.lisdatasolutions.microrag.domain.rag.IndexStorageFullException
import com.lisdatasolutions.microrag.domain.rag.IndexUnavailableException
import com.lisdatasolutions.microrag.domain.rag.LibraryEntry
import com.lisdatasolutions.microrag.domain.rag.NewDocument
import com.lisdatasolutions.microrag.domain.rag.RagIndex
import com.lisdatasolutions.microrag.domain.rag.ScoredChunk
import com.lisdatasolutions.microrag.domain.rag.StoredDocument
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * In-memory [RagIndex] for JVM tests: documents and stored chunks, no search. Reindex shadows, the
 * cascade on delete and the atomic publication follow DefaultRagIndex (F08 research R5, R6).
 */
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

    /** Ordinals insertChunks silently drops, to play a store that lost chunks. */
    var dropOrdinals: Set<Int> = emptySet()

    /** Batches stored, in order: (document, ordinals). */
    val insertedBatches = mutableListOf<Pair<Long, List<Int>>>()
    private var nextId = 1L
    var now = 1_000L

    /** Chunk ids by document and ordinal; a publication moves them with their chunks, as SQLite rows. */
    private val chunkIds = linkedMapOf<Long, MutableMap<Int, Long>>()
    private var nextChunkId = 1L

    /** Calls that read stored chunks, their counts or texts (F08 rule L1: the Library never does). */
    var chunkReads = 0
        private set

    /** `rag_meta.quant_dirty` (D-030): set by every write that changes chunks. */
    var quantDirty = false

    /** Every updateStatus call, in order: (document, status). */
    val statusUpdates: MutableList<Pair<Long, DocumentStatus>> = java.util.Collections.synchronizedList(mutableListOf())

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

    private fun library(text: String, statuses: Set<DocumentStatus>): List<StoredDocument> {
        val folded = DocumentNames.fold(text.trim())
        return docs.values
            .filter { it.replacesId == null && it.status in statuses && DocumentNames.fold(it.displayName).contains(folded) }
            .sortedWith(compareByDescending<StoredDocument> { it.createdAtMs }.thenByDescending { it.id })
    }

    override suspend fun libraryPage(text: String, statuses: Set<DocumentStatus>, limit: Int): List<LibraryEntry> = available {
        library(text, statuses).take(limit.coerceAtLeast(0)).map { LibraryEntry(it, shadow(it.id)?.status) }
    }

    override suspend fun libraryCount(text: String, statuses: Set<DocumentStatus>): Int = available { library(text, statuses).size }

    override suspend fun documentIdByHash(contentHash: String): Long? = available {
        docs.values.firstOrNull { it.contentHash == contentHash }?.id
    }

    override suspend fun updateStatus(id: Long, status: DocumentStatus, error: String?, chunkCount: Int?, skippedPages: Int?) =
        available {
            statusUpdates += id to status
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
        shadow(id)?.let { dropDocument(it.id) }
        dropDocument(id)
        quantDirty = true
        changes.value += 1
    }

    private fun shadow(originalId: Long): StoredDocument? = docs.values.firstOrNull { it.replacesId == originalId }

    private fun dropDocument(id: Long) {
        docs.remove(id)
        chunks.remove(id)
        chunkIds.remove(id)
    }

    override suspend fun addReindexShadow(originalId: Long): Long = available {
        val original = docs[originalId]
        check(original != null && original.status == DocumentStatus.DONE && original.replacesId == null) { "Not a done document" }
        check(shadow(originalId) == null) { "Already being reindexed" }
        val id = nextId++
        docs[id] = original.copy(
            id = id, contentHash = original.contentHash + "#reindex", status = DocumentStatus.PENDING, chunkCount = null,
            error = null, included = true, createdAtMs = now++, updatedAtMs = now, skippedPages = null, replacesId = originalId,
        )
        changes.value += 1
        id
    }

    override suspend fun shadowOf(originalId: Long): StoredDocument? = available { shadow(originalId) }

    override suspend fun publishReindex(shadowId: Long): Int? = available {
        val shadow = docs[shadowId] ?: return@available null
        val original = shadow.replacesId?.let { docs[it] } ?: return@available null
        if (original.status != DocumentStatus.DONE) return@available null
        val moved = chunks.remove(shadowId) ?: sortedMapOf()
        chunks[original.id] = moved
        chunkIds[original.id] = chunkIds.remove(shadowId) ?: mutableMapOf()
        docs[original.id] = original.copy(chunkCount = moved.size, skippedPages = shadow.skippedPages, error = null, updatedAtMs = now++)
        docs.remove(shadowId)
        quantDirty = true
        changes.value += 1
        moved.size
    }

    override suspend fun removeFromIndex(id: Long): Int? = available {
        val document = docs[id]?.takeIf { it.status == DocumentStatus.DONE && it.replacesId == null } ?: return@available null
        shadow(id)?.let { dropDocument(it.id) }
        val removed = chunks.remove(id)?.size ?: 0
        chunkIds.remove(id)
        docs[id] = document.copy(status = DocumentStatus.OUT_OF_INDEX, chunkCount = null, error = null, updatedAtMs = now++)
        quantDirty = true
        changes.value += 1
        removed
    }

    override suspend fun storedChunkCounts(ids: Collection<Long>): Map<Long, Int> = available {
        chunkReads++
        ids.associateWith { chunks[it]?.size ?: 0 }
    }

    override suspend fun storedChunkContents(documentId: Long): Map<Int, String> = available {
        chunkReads++
        chunks[documentId].orEmpty().mapValuesTo(LinkedHashMap()) { it.value.content }
    }

    override suspend fun deleteChunks(documentId: Long) = available {
        chunks.remove(documentId)
        chunkIds.remove(documentId)
        quantDirty = true
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
        val ids = chunkIds.getOrPut(documentId) { mutableMapOf() }
        val inserted = chunks.filterNot { it.ordinal in dropOrdinals }.count { chunk ->
            (stored.putIfAbsent(chunk.ordinal, chunk) == null).also { if (it) ids[chunk.ordinal] = nextChunkId++ }
        }
        insertedBatches += documentId to chunks.map { it.ordinal }
        quantDirty = true
        if (inserted > 0) changes.value += 1
        inserted
    }

    override suspend fun vectorSearch(query: FloatArray, k: Int): List<ScoredChunk> = available { emptyList() }

    override suspend fun textSearch(userQuery: String, k: Int): List<Long> = available { emptyList() }

    override suspend fun loadChunks(ids: Collection<Long>): Map<Long, ChunkView> = available {
        chunkReads++
        val wanted = ids.toSet()
        chunkIds.flatMap { (documentId, byOrdinal) -> byOrdinal.mapNotNull { (ordinal, id) -> if (id in wanted) view(documentId, ordinal) else null } }
            .associateBy { it.id }
    }

    override suspend fun neighbor(documentId: Long, ordinal: Int): ChunkView? = available {
        chunkReads++
        view(documentId, ordinal)
    }

    private fun view(documentId: Long, ordinal: Int): ChunkView? {
        val chunk = chunks[documentId]?.get(ordinal) ?: return null
        return ChunkView(
            id = chunkIds.getValue(documentId).getValue(ordinal), documentId = documentId, ordinal = ordinal,
            headingPath = chunk.headingPath, page = chunk.page, content = chunk.content, tokenEstimate = chunk.tokenEstimate,
            documentName = docs.getValue(documentId).displayName,
        )
    }

    /** The id the chunk at [ordinal] of [documentId] got when stored. */
    fun chunkId(documentId: Long, ordinal: Int): Long = chunkIds.getValue(documentId).getValue(ordinal)

    override suspend fun counts(): IndexCounts = available { IndexCounts(docs.size, 0, 0) }

    override suspend fun close() = Unit
}
