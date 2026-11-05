package com.lisdatasolutions.microrag.testing

import com.lisdatasolutions.microrag.domain.ingest.DocumentFormat
import com.lisdatasolutions.microrag.domain.library.DetailResult
import com.lisdatasolutions.microrag.domain.library.DocumentPreview
import com.lisdatasolutions.microrag.domain.library.LibraryDocument
import com.lisdatasolutions.microrag.domain.library.LibraryDocumentState
import com.lisdatasolutions.microrag.domain.library.LibraryFilter
import com.lisdatasolutions.microrag.domain.library.LibraryPage
import com.lisdatasolutions.microrag.domain.library.LibraryQuery
import com.lisdatasolutions.microrag.domain.library.LibraryRepository
import com.lisdatasolutions.microrag.domain.library.LibraryResult
import com.lisdatasolutions.microrag.domain.library.FolderResult
import com.lisdatasolutions.microrag.domain.library.FoldersResult
import com.lisdatasolutions.microrag.domain.rag.DocumentNames
import com.lisdatasolutions.microrag.domain.rag.DocumentRef
import com.lisdatasolutions.microrag.domain.rag.Folder
import com.lisdatasolutions.microrag.domain.rag.FolderList
import com.lisdatasolutions.microrag.domain.rag.LibraryFolderFilter
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import com.lisdatasolutions.microrag.domain.library.SummaryResult
import com.lisdatasolutions.microrag.domain.rag.LibrarySummary

/** An indexed PDF row, for tests that only care about ids, names and folders (F17). */
fun libraryDocument(id: Long, name: String, folderId: Long? = null, folderName: String? = null, included: Boolean = true) = LibraryDocument(
    id, name, DocumentFormat.PDF, LibraryDocumentState.Indexed(3, null), included = included, folderId = folderId, folderName = folderName,
    createdAtMs = id * 10,
)

/**
 * A [LibraryRepository] for ViewModel tests: pages come from [documents] (newest first) filtered as the
 * index does; details are set per document. Every call is recorded.
 */
class FakeLibraryRepository : LibraryRepository {
    /** F17: the folders as the index keeps them; the counts come from [documents]. */
    val folderRows = MutableStateFlow<List<Folder>>(emptyList())
    var foldersUnavailable = false

    /** The next folder writes return these, when set; otherwise they act on [folderRows]. */
    var nextFolderResult: FolderResult? = null
    var failDeleteFolder = false

    /** Documents [delete] leaves in place, as a delete that fails would (M4). */
    var undeletable: Set<Long> = emptySet()

    /** When set, folder writes wait for it: holds «Eliminando…» and the double tap tests. */
    var folderGate: CompletableDeferred<Unit>? = null

    override fun folders(): Flow<FoldersResult> = flow {
        if (foldersUnavailable) {
            emit(FoldersResult.Unavailable)
            return@flow
        }
        emitAll(
            combine(folderRows, documents) { folders, docs ->
                FoldersResult.Loaded(
                    FolderList(
                        folders.sortedBy { DocumentNames.fold(it.name) }.map { f ->
                            val inside = docs.filter { it.folderId == f.id }
                            f.copy(documentCount = inside.size, includedCount = inside.count { it.included })
                        },
                        docs.count { it.folderId == null },
                        docs.count { it.folderId == null && it.included },
                    ),
                )
            },
        )
    }

    override suspend fun createFolder(name: String, norm: String): FolderResult {
        calls += "createFolder $name"
        folderGate?.await()
        nextFolderResult?.let { return it }
        if (folderRows.value.any { DocumentNames.fold(it.name) == norm }) return FolderResult.Duplicate
        val id = (folderRows.value.maxOfOrNull { it.id } ?: 0) + 1
        folderRows.value = folderRows.value + Folder(id, name, createdAtMs = id * 10, documentCount = 0)
        return FolderResult.Done(id)
    }

    override suspend fun renameFolder(id: Long, name: String, norm: String): FolderResult {
        calls += "renameFolder $id $name"
        folderGate?.await()
        nextFolderResult?.let { return it }
        if (folderRows.value.any { it.id != id && DocumentNames.fold(it.name) == norm }) return FolderResult.Duplicate
        folderRows.value = folderRows.value.map { if (it.id == id) it.copy(name = name) else it }
        return FolderResult.Done(id)
    }

    override suspend fun deleteFolder(id: Long, moveDocuments: Boolean): Boolean {
        calls += "deleteFolder $id $moveDocuments"
        folderGate?.await()
        if (failDeleteFolder) return false
        val inside = documents.value.filter { it.folderId == id }
        if (!moveDocuments && inside.isNotEmpty()) return false
        documents.value = documents.value.map { if (it.folderId == id) it.copy(folderId = null, folderName = null) else it }
        folderRows.value = folderRows.value.filterNot { it.id == id }
        return true
    }

    var failMove = false

    override suspend fun moveDocument(documentId: Long, folderId: Long?): Boolean {
        calls += "move $documentId $folderId"
        if (failMove || (folderId != null && folderRows.value.none { it.id == folderId })) return false
        val name = folderRows.value.firstOrNull { it.id == folderId }?.name
        documents.value = documents.value.map { if (it.id == documentId) it.copy(folderId = folderId, folderName = name) else it }
        details[documentId]?.let { flow ->
            (flow.value as? DetailResult.Loaded)?.let { flow.value = DetailResult.Loaded(it.detail.copy(folderId = folderId, folderName = name)) }
        }
        return true
    }

    override suspend fun folderDocumentRefs(folderId: Long?): List<DocumentRef>? =
        if (foldersUnavailable) null else documents.value.filter { it.folderId == folderId }.map { DocumentRef(it.id, it.createdAtMs) }

    val documents = MutableStateFlow<List<LibraryDocument>>(emptyList())

    /** False keeps the page loading; [pageError] makes the next observation fail. */
    val pageReady = MutableStateFlow(true)
    var pageError: Throwable? = null
    var unavailable = false
    val queries = mutableListOf<LibraryQuery>()

    val details = mutableMapOf<Long, MutableStateFlow<DetailResult?>>()
    val previews = mutableMapOf<Pair<Long, Long?>, DocumentPreview?>()
    val previewRequests = mutableListOf<Pair<Long, Long?>>()
    val calls = mutableListOf<String>()

    /** When set, setIncluded waits for it: the switch must already show the new value. */
    var includeGate: CompletableDeferred<Unit>? = null

    /** When set, delete waits for it: holds «Eliminando…». */
    var deleteGate: CompletableDeferred<Unit>? = null

    override fun page(query: LibraryQuery): Flow<LibraryResult> = flow {
        queries += query
        pageError?.let {
            pageError = null
            throw it
        }
        if (unavailable) {
            emit(LibraryResult.Unavailable)
            return@flow
        }
        emitAll(
            combine(documents, pageReady) { docs, ready -> if (ready) docs else null }
                .filterNotNull()
                .map { docs ->
                    val matching = docs.filter { it.matches(query) }
                    LibraryResult.Loaded(LibraryPage(matching.take(query.limit), matching.size))
                },
        )
    }

    /** Home's summary: [summaryResult] as it is set (null keeps it loading); [summaryError] makes the next observation fail. */
    val summaryResult = MutableStateFlow<SummaryResult?>(SummaryResult.Loaded(LibrarySummary(0, 0)))
    var summaryError: Throwable? = null
    var summaryObservations = 0

    override fun summary(): Flow<SummaryResult> = flow {
        summaryObservations++
        summaryError?.let {
            summaryError = null
            throw it
        }
        emitAll(summaryResult.filterNotNull())
    }

    private fun LibraryDocument.matches(query: LibraryQuery): Boolean {
        val indexed = state is LibraryDocumentState.Indexed
        val filtered = when (query.filter) {
            LibraryFilter.ALL -> true
            LibraryFilter.INDEXED -> indexed
            LibraryFilter.PENDING -> !indexed
        }
        val inFolder = when (val folder = query.folder) {
            LibraryFolderFilter.All -> true
            LibraryFolderFilter.Unfiled -> folderId == null
            is LibraryFolderFilter.Folder -> folderId == folder.id
        }
        return filtered && inFolder && DocumentNames.fold(displayName).contains(DocumentNames.fold(query.text.trim()))
    }

    override fun detail(documentId: Long): Flow<DetailResult> =
        details.getOrPut(documentId) { MutableStateFlow(DetailResult.NotFound) }.filterNotNull()

    override suspend fun preview(documentId: Long, chunkId: Long?): DocumentPreview? {
        previewRequests += documentId to chunkId
        return previews[documentId to chunkId]
    }

    override suspend fun setIncluded(documentId: Long, included: Boolean) {
        calls += "include $documentId $included"
        includeGate?.await()
        documents.value = documents.value.map { if (it.id == documentId) it.copy(included = included) else it }
        details[documentId]?.let { flow ->
            (flow.value as? DetailResult.Loaded)?.let { flow.value = DetailResult.Loaded(it.detail.copy(included = included)) }
        }
    }

    override suspend fun delete(documentId: Long) {
        calls += "delete $documentId"
        deleteGate?.await()
        if (documentId in undeletable) return
        documents.value = documents.value.filterNot { it.id == documentId }
        details[documentId]?.value = DetailResult.NotFound
    }

    override suspend fun removeFromIndex(documentId: Long) {
        calls += "remove $documentId"
    }

    override suspend fun reindex(documentId: Long, mode: com.lisdatasolutions.microrag.domain.rag.IndexMode?) {
        calls += if (mode == null) "reindex $documentId" else "reindex $documentId $mode"
    }

    override suspend fun discardReindex(documentId: Long) {
        calls += "discard $documentId"
    }
}
