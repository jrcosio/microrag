package com.lisdatasolutions.microrag.domain.indexing

import kotlinx.coroutines.flow.Flow

// The Index screen's only way to the run (F07 contracts/indexing-api.md), thin like those of F03 and F06.

class ObserveIndexingUseCase(private val repository: IndexingRepository) {
    operator fun invoke(): Flow<IndexingState> = repository.state
}

/** Every non-indexed document joins the run; returns how many documents it has to index. */
class StartIndexingRunUseCase(private val repository: IndexingRepository) {
    suspend operator fun invoke(): Int = repository.start()
}

class PauseIndexingUseCase(private val repository: IndexingRepository) {
    suspend operator fun invoke() = repository.pause()
}

class ResumeIndexingUseCase(private val repository: IndexingRepository) {
    suspend operator fun invoke() = repository.resume()
}

class RetryIndexingUseCase(private val repository: IndexingRepository) {
    suspend operator fun invoke(documentId: Long) = repository.retry(documentId)
}

/** On app start, with the app visible (research R12). */
class ReconcileIndexingUseCase(private val repository: IndexingRepository) {
    suspend operator fun invoke() = repository.reconcile()
}
