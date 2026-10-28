package com.lisdatasolutions.microrag.testing

import com.lisdatasolutions.microrag.domain.library.DetailResult
import com.lisdatasolutions.microrag.domain.library.DocumentPreview
import com.lisdatasolutions.microrag.domain.library.LibraryDocument
import com.lisdatasolutions.microrag.domain.library.LibraryDocumentState
import com.lisdatasolutions.microrag.domain.library.LibraryFilter
import com.lisdatasolutions.microrag.domain.library.LibraryPage
import com.lisdatasolutions.microrag.domain.library.LibraryQuery
import com.lisdatasolutions.microrag.domain.library.LibraryRepository
import com.lisdatasolutions.microrag.domain.library.LibraryResult
import com.lisdatasolutions.microrag.domain.rag.DocumentNames
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

/**
 * A [LibraryRepository] for ViewModel tests: pages come from [documents] (newest first) filtered as the
 * index does; details are set per document. Every call is recorded.
 */
class FakeLibraryRepository : LibraryRepository {
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

    private fun LibraryDocument.matches(query: LibraryQuery): Boolean {
        val indexed = state is LibraryDocumentState.Indexed
        val filtered = when (query.filter) {
            LibraryFilter.ALL -> true
            LibraryFilter.INDEXED -> indexed
            LibraryFilter.PENDING -> !indexed
        }
        return filtered && DocumentNames.fold(displayName).contains(DocumentNames.fold(query.text.trim()))
    }

    override fun detail(documentId: Long): Flow<DetailResult> =
        details.getOrPut(documentId) { MutableStateFlow(DetailResult.NotFound) }.filterNotNull()

    override suspend fun preview(documentId: Long, chunkId: Long?): DocumentPreview? {
        previewRequests += documentId to chunkId
        return previews[documentId to chunkId]
    }

    override suspend fun setIncluded(documentId: Long, included: Boolean) {
        calls += "include $documentId $included"
        documents.value = documents.value.map { if (it.id == documentId) it.copy(included = included) else it }
    }

    override suspend fun delete(documentId: Long) {
        calls += "delete $documentId"
        deleteGate?.await()
        documents.value = documents.value.filterNot { it.id == documentId }
        details[documentId]?.value = DetailResult.NotFound
    }

    override suspend fun removeFromIndex(documentId: Long) {
        calls += "remove $documentId"
    }

    override suspend fun reindex(documentId: Long) {
        calls += "reindex $documentId"
    }

    override suspend fun discardReindex(documentId: Long) {
        calls += "discard $documentId"
    }
}
