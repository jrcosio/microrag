package com.lisdatasolutions.microrag.domain.library

import kotlinx.coroutines.flow.Flow

// The two screens' only way to the Library (F08 contracts/library-api.md), thin like those of F06 and F07.

class ObserveLibraryUseCase(private val repository: LibraryRepository) {
    operator fun invoke(query: LibraryQuery): Flow<LibraryResult> = repository.page(query)
}

class ObserveDocumentUseCase(private val repository: LibraryRepository) {
    operator fun invoke(documentId: Long): Flow<DetailResult> = repository.detail(documentId)
}

class LoadPreviewUseCase(private val repository: LibraryRepository) {
    suspend operator fun invoke(documentId: Long, chunkId: Long?): DocumentPreview? = repository.preview(documentId, chunkId)
}

class SetIncludedUseCase(private val repository: LibraryRepository) {
    suspend operator fun invoke(documentId: Long, included: Boolean) = repository.setIncluded(documentId, included)
}

/** The only way to delete a document, also for «Quitar de la cola» in the Ingest (rule L4). */
class DeleteDocumentUseCase(private val repository: LibraryRepository) {
    suspend operator fun invoke(documentId: Long) = repository.delete(documentId)
}

class RemoveFromIndexUseCase(private val repository: LibraryRepository) {
    suspend operator fun invoke(documentId: Long) = repository.removeFromIndex(documentId)
}

class ReindexDocumentUseCase(private val repository: LibraryRepository) {
    suspend operator fun invoke(documentId: Long) = repository.reindex(documentId)
}

class DiscardReindexUseCase(private val repository: LibraryRepository) {
    suspend operator fun invoke(documentId: Long) = repository.discardReindex(documentId)
}
