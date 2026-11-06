package com.lisdatasolutions.microrag.testing

import com.lisdatasolutions.microrag.domain.rag.ChatLibraryState
import com.lisdatasolutions.microrag.domain.rag.ChunkInput
import com.lisdatasolutions.microrag.domain.rag.ChunkView
import com.lisdatasolutions.microrag.domain.rag.DocumentNames
import com.lisdatasolutions.microrag.domain.rag.DocumentRef
import com.lisdatasolutions.microrag.domain.rag.DocumentStatus
import com.lisdatasolutions.microrag.domain.rag.DuplicateDocumentException
import com.lisdatasolutions.microrag.domain.rag.Folder
import com.lisdatasolutions.microrag.domain.rag.FolderList
import com.lisdatasolutions.microrag.domain.rag.IndexCompatibility
import com.lisdatasolutions.microrag.domain.model.ModelId
import com.lisdatasolutions.microrag.domain.rag.IndexCounts
import com.lisdatasolutions.microrag.domain.rag.IndexMode
import com.lisdatasolutions.microrag.domain.rag.PageInput
import com.lisdatasolutions.microrag.domain.rag.PageKey
import com.lisdatasolutions.microrag.domain.rag.IndexStorageFullException
import com.lisdatasolutions.microrag.domain.rag.IndexUnavailableException
import com.lisdatasolutions.microrag.domain.rag.LibraryEntry
import com.lisdatasolutions.microrag.domain.rag.LibraryFolderFilter
import com.lisdatasolutions.microrag.domain.rag.LibrarySummary
import com.lisdatasolutions.microrag.domain.rag.NewDocument
import com.lisdatasolutions.microrag.domain.rag.QuantizedState
import com.lisdatasolutions.microrag.domain.rag.RagIndex
import com.lisdatasolutions.microrag.domain.rag.ScoredChunk
import com.lisdatasolutions.microrag.domain.rag.ScoredPage
import com.lisdatasolutions.microrag.domain.rag.SearchScope
import com.lisdatasolutions.microrag.domain.rag.StoredDocument
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * In-memory [RagIndex] for JVM tests: documents and stored chunks. Reindex shadows, the cascade on
 * delete and the atomic publication follow DefaultRagIndex (F08 research R5, R6). Searches return what
 * a test programs, unfiltered, so a test can play a document that stops being searchable after the
 * search; loading with `searchableOnly` filters as DefaultRagIndex does (F10 research R7).
 */
class FakeRagIndex(initial: IndexCompatibility = IndexCompatibility.Compatible) : RagIndex {

    private val state = MutableStateFlow(initial)
    override val compatibility: StateFlow<IndexCompatibility> = state

    private val changes = MutableStateFlow(0L)
    override val documentChanges: StateFlow<Long> = changes

    val docs = linkedMapOf<Long, StoredDocument>()

    /** F19: the text embedder rag_meta records, and whether the page space matches (contract E1, E3). */
    override val textEmbedder = MutableStateFlow(ModelId.EG2_270M)
    override val pagesUsable = MutableStateFlow(true)

    /** F19: stored pages per document, by page number, as INSERT OR IGNORE leaves them. */
    val pages = linkedMapOf<Long, java.util.SortedMap<Int, PageInput>>()

    /** Set to make the next insertPage fail as a full disk would. */
    var failNextPageWithFullDisk = false

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

    /** F14: the minimums of every refreshQuantized call, and a switch to make the next one fail. */
    val quantizedRefreshes = mutableListOf<Int>()
    var failNextQuantizedRefresh = false
    private var quantizedPreloaded = false

    override suspend fun quantizedState(): QuantizedState =
        QuantizedState(current = !quantDirty, preloaded = quantizedPreloaded && !quantDirty, searchableChunks = searchableChunkCount())

    override suspend fun refreshQuantized(minChunks: Int): QuantizedState {
        quantizedRefreshes += minChunks
        quantizedPreloaded = false
        if (failNextQuantizedRefresh) {
            failNextQuantizedRefresh = false
            throw IllegalStateException("refresh failed")
        }
        if (searchableChunkCount() >= minChunks && searchableChunkCount() > 0) {
            quantDirty = false
            quantizedPreloaded = true
        }
        return quantizedState()
    }

    private fun searchableChunkCount(): Int =
        docs.values.filter { it.status == DocumentStatus.DONE && it.included }.sumOf { chunks[it.id]?.size ?: 0 }

    /** What vectorSearch returns, best first, before taking k (F10). */
    @Volatile
    var vectorResults: (FloatArray) -> List<ScoredChunk> = { emptyList() }

    /** What textSearch returns, best first, before taking k (F10). */
    @Volatile
    var textResults: (String) -> List<Long> = { emptyList() }

    /** F20: what [pageSearch] returns before its rules (searchable, scope, pagesUsable and k). */
    var pageResults: (FloatArray) -> List<ScoredPage> = { emptyList() }

    /** Thrown by both searches when set, as an index failing under a retrieval would. */
    @Volatile
    var searchError: Throwable? = null

    /** open, searches and loads, in order: "open", "vector:<k>", "text:<k>:<query>", "load" or "load:searchable" (with the ids), "neighbor" or "neighbor:searchable" (F10). */
    val calls: MutableList<String> = java.util.Collections.synchronizedList(mutableListOf())

    /** F17: the scope of every search, load and neighbor, in the order of [calls]. */
    val scopes: MutableList<SearchScope> = java.util.Collections.synchronizedList(mutableListOf())

    /** Every updateStatus call, in order: (document, status). */
    val statusUpdates: MutableList<Pair<Long, DocumentStatus>> = java.util.Collections.synchronizedList(mutableListOf())

    fun setCompatibility(value: IndexCompatibility) {
        state.value = value
    }

    private fun <T> available(block: () -> T): T {
        if (state.value != IndexCompatibility.Compatible) throw IndexUnavailableException(state.value)
        return block()
    }

    override suspend fun open(): IndexCompatibility {
        calls += "open"
        return state.value
    }

    override suspend fun meta(): Map<String, String> = available { emptyMap() }

    override suspend fun addDocument(document: NewDocument): Long = available {
        docs.values.firstOrNull { it.contentHash == document.contentHash }?.let { throw DuplicateDocumentException(it.id) }
        val id = nextId++
        docs[id] = StoredDocument(
            id = id, displayName = document.displayName, contentHash = document.contentHash, mimeType = document.mimeType,
            uri = document.uri, status = DocumentStatus.PENDING, chunkCount = null, error = null, included = true,
            sizeBytes = document.sizeBytes, pageCount = document.pageCount, localPath = document.localPath,
            createdAtMs = now++, updatedAtMs = now, folderId = document.folderId?.takeIf { it in folderRows },
            indexMode = document.indexMode,
        )
        changes.value += 1
        id
    }

    override suspend fun document(id: Long): StoredDocument? = available { docs[id]?.let(::withFolderName)?.let(::withPages) }

    override suspend fun documents(statuses: Set<DocumentStatus>): List<StoredDocument> = available {
        docs.values.filter { it.status in statuses }.sortedWith(compareBy({ it.createdAtMs }, { it.id })).map(::withFolderName)
    }

    private fun library(text: String, statuses: Set<DocumentStatus>, folder: LibraryFolderFilter): List<StoredDocument> {
        val folded = DocumentNames.fold(text.trim())
        return docs.values
            .filter { it.replacesId == null && it.status in statuses && DocumentNames.fold(it.displayName).contains(folded) }
            .filter {
                when (folder) {
                    LibraryFolderFilter.All -> true
                    LibraryFolderFilter.Unfiled -> it.folderId == null
                    is LibraryFolderFilter.Folder -> it.folderId == folder.id
                }
            }
            .sortedWith(compareByDescending<StoredDocument> { it.createdAtMs }.thenByDescending { it.id })
            .map(::withFolderName)
    }

    override suspend fun libraryPage(text: String, statuses: Set<DocumentStatus>, limit: Int, folder: LibraryFolderFilter): List<LibraryEntry> =
        available { library(text, statuses, folder).take(limit.coerceAtLeast(0)).map { LibraryEntry(it, shadow(it.id)?.status) } }

    override suspend fun libraryCount(text: String, statuses: Set<DocumentStatus>, folder: LibraryFolderFilter): Int =
        available { library(text, statuses, folder).size }

    /** F17: folders by id, as `rag.db` keeps them. */
    data class FakeFolder(val name: String, val norm: String, val createdAtMs: Long)

    val folderRows = linkedMapOf<Long, FakeFolder>()
    private var nextFolderId = 1L

    /** Makes every folder write fail, as an index failing under it would. */
    var failFolderWrites = false

    /** Puts [documentId] in [folderId] (null: «General»), as a move does. */
    fun file(documentId: Long, folderId: Long?) {
        docs.computeIfPresent(documentId) { _, d -> d.copy(folderId = folderId) }
        changes.value += 1
    }

    private fun withFolderName(document: StoredDocument): StoredDocument =
        document.copy(folderName = document.folderId?.let { folderRows[it]?.name })

    override suspend fun folders(): FolderList = available {
        FolderList(
            folders = folderRows.entries.sortedWith(compareBy({ it.value.norm }, { it.key })).map { (id, f) ->
                val inside = docs.values.filter { it.replacesId == null && it.folderId == id }
                Folder(id, f.name, f.createdAtMs, inside.size, inside.count { it.included })
            },
            unfiledCount = docs.values.count { it.replacesId == null && it.folderId == null },
            unfiledIncludedCount = docs.values.count { it.replacesId == null && it.folderId == null && it.included },
        )
    }

    override suspend fun createFolder(name: String, norm: String): Long? = available {
        if (failFolderWrites) throw IllegalStateException("fake folder write failed")
        if (folderRows.values.any { it.norm == norm }) return@available null
        nextFolderId++.also {
            folderRows[it] = FakeFolder(name, norm, now++)
            changes.value += 1
        }
    }

    override suspend fun renameFolder(id: Long, name: String, norm: String): Boolean? = available {
        if (failFolderWrites) throw IllegalStateException("fake folder write failed")
        val folder = folderRows[id] ?: return@available false
        if (folderRows.any { (other, f) -> other != id && f.norm == norm }) return@available null
        folderRows[id] = folder.copy(name = name, norm = norm)
        changes.value += 1
        true
    }

    override suspend fun deleteFolder(id: Long, moveDocuments: Boolean): Boolean = available {
        if (failFolderWrites) throw IllegalStateException("fake folder write failed")
        if (id !in folderRows) return@available false
        val inside = docs.values.filter { it.folderId == id }
        if (!moveDocuments && inside.isNotEmpty()) return@available false
        inside.forEach { docs[it.id] = it.copy(folderId = null) }
        folderRows.remove(id)
        changes.value += 1
        true
    }

    override suspend fun moveDocument(documentId: Long, folderId: Long?): Boolean = available {
        val document = docs[documentId]?.takeIf { it.replacesId == null } ?: return@available false
        if (folderId != null && folderId !in folderRows) return@available false
        docs[documentId] = document.copy(folderId = folderId)
        changes.value += 1
        true
    }

    override suspend fun folderDocumentRefs(folderId: Long?): List<DocumentRef> = available {
        library("", DocumentStatus.entries.toSet(), folderId?.let { LibraryFolderFilter.Folder(it) } ?: LibraryFolderFilter.Unfiled)
            .map { DocumentRef(it.id, it.createdAtMs) }
    }

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
        pages.remove(id)
    }

    private fun withPages(document: StoredDocument): StoredDocument = document.copy(pagesStored = pages[document.id]?.size ?: 0)

    override suspend fun switchTextEmbedder(to: ModelId): Boolean = available {
        if (textEmbedder.value == to) return@available false
        textEmbedder.value = to
        true
    }

    override suspend fun storedPages(documentId: Long): Set<Int> = available { pages[documentId]?.keys?.toSet().orEmpty() }

    override suspend fun insertPage(documentId: Long, page: PageInput): Boolean = available {
        require(docs.containsKey(documentId)) { "Unknown document $documentId" }
        if (failNextPageWithFullDisk) {
            failNextPageWithFullDisk = false
            throw IndexStorageFullException(IllegalStateException("database or disk is full"))
        }
        val stored = pages.getOrPut(documentId) { sortedMapOf() }
        if (page.page in stored) return@available false
        stored[page.page] = page
        changes.value += 1
        true
    }

    override suspend fun pageFiles(documentId: Long): List<String> = available {
        (listOf(documentId) + listOfNotNull(shadow(documentId)?.id)).flatMap { id -> pages[id]?.values?.map { it.fileName }.orEmpty() }
    }

    override suspend fun allPageFiles(): Set<String> = available { pages.values.flatMap { it.values.map(PageInput::fileName) }.toSet() }

    override suspend fun setIndexMode(id: Long, mode: IndexMode) = available {
        docs[id]?.let { docs[id] = it.copy(indexMode = mode, updatedAtMs = now++) }
        changes.value += 1
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
        pages.remove(original.id)
        pages.remove(shadowId)?.let { pages[original.id] = it }
        docs[original.id] = original.copy(
            chunkCount = moved.size, skippedPages = shadow.skippedPages, pageCount = shadow.pageCount, indexMode = shadow.indexMode,
            error = null, updatedAtMs = now++,
        )
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
        pages.remove(id)
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

    override suspend fun vectorSearch(query: FloatArray, k: Int, scope: SearchScope): List<ScoredChunk> = available {
        calls += "vector:$k"
        scopes += scope
        searchError?.let { throw it }
        vectorResults(query).take(k)
    }

    override suspend fun textSearch(userQuery: String, k: Int, scope: SearchScope): List<Long> = available {
        calls += "text:$k:$userQuery"
        scopes += scope
        searchError?.let { throw it }
        textResults(userQuery).take(k)
    }

    override suspend fun loadChunks(ids: Collection<Long>, searchableOnly: Boolean, scope: SearchScope): Map<Long, ChunkView> = available {
        calls += (if (searchableOnly) "load:searchable" else "load") + ids.joinToString(",", prefix = ":")
        scopes += scope
        chunkReads++
        val wanted = ids.toSet()
        chunkIds.filterKeys { (!searchableOnly || searchable(it)) && docs[it]?.let { d -> inScope(d, scope) } == true }
            .flatMap { (documentId, byOrdinal) -> byOrdinal.mapNotNull { (ordinal, id) -> if (id in wanted) view(documentId, ordinal) else null } }
            .associateBy { it.id }
    }

    override suspend fun neighbor(documentId: Long, ordinal: Int, searchableOnly: Boolean, scope: SearchScope): ChunkView? = available {
        calls += if (searchableOnly) "neighbor:searchable" else "neighbor"
        scopes += scope
        chunkReads++
        if ((searchableOnly && !searchable(documentId)) || docs[documentId]?.let { inScope(it, scope) } != true) null else view(documentId, ordinal)
    }

    private fun searchable(documentId: Long): Boolean = docs[documentId]?.let { it.status == DocumentStatus.DONE && it.included } == true

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

    override suspend fun librarySummary(): LibrarySummary = available {
        val library = docs.values.filter { it.replacesId == null }
        LibrarySummary(library.size, library.filter { it.status == DocumentStatus.DONE }.sumOf { it.chunkCount ?: 0 })
    }

    override suspend fun chatLibraryState(scope: SearchScope): ChatLibraryState = available {
        val library = docs.values.filter { it.replacesId == null }
        val inScope = library.filter { inScope(it, scope) }
        val pending = setOf(DocumentStatus.PENDING, DocumentStatus.PARSED, DocumentStatus.EMBEDDING, DocumentStatus.PAUSED)
        // DefaultRagIndex.PAGES_ONLY.
        val pagesOnly = { d: StoredDocument -> d.indexMode != IndexMode.TEXT && (d.chunkCount ?: 0) == 0 }
        ChatLibraryState(
            documents = library.size,
            searchable = inScope.count { it.status == DocumentStatus.DONE && it.included && !pagesOnly(it) },
            pendingIncluded = inScope.count { it.status in pending && it.included },
            visualOnly = inScope.count { it.status == DocumentStatus.DONE && it.included && pagesOnly(it) },
            withPages = inScope.count { it.status == DocumentStatus.DONE && it.included && !pages[it.id].isNullOrEmpty() },
        )
    }

    override suspend fun pageSearch(query: FloatArray, k: Int, scope: SearchScope): List<ScoredPage> = available {
        calls += "pages:$k"
        scopes += scope
        searchError?.let { throw it }
        if (!pagesUsable.value) return@available emptyList()
        pageResults(query).filter { page ->
            docs[page.documentId]?.let { it.status == DocumentStatus.DONE && it.included && inScope(it, scope) } == true
        }.take(k)
    }

    override suspend fun existingPages(keys: Collection<PageKey>): Set<PageKey> = available {
        keys.filter { pages[it.documentId]?.containsKey(it.page) == true }.toSet()
    }

    override suspend fun pageFile(documentId: Long, page: Int): String? = available { pages[documentId]?.get(page)?.fileName }

    /** F17: the rule of DefaultRagIndex.scopeCondition. */
    fun inScope(document: StoredDocument, scope: SearchScope): Boolean = when (scope) {
        SearchScope.All -> true
        is SearchScope.Only -> document.folderId in scope.folderIds || (scope.general && document.folderId == null) || document.id in scope.documentIds
    }

    override suspend fun close() = Unit
}
