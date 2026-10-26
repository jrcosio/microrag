package com.lisdatasolutions.microrag.domain.ingest

import kotlinx.coroutines.flow.Flow

/** The import queue as the screen shows it (F06 contracts/ingest-api.md). */
class ObserveImportQueueUseCase(private val repository: ImportQueueRepository) {
    operator fun invoke(): Flow<ImportQueue> = repository.queue
}

/** Files picked with the system picker; the import runs in the application scope. */
class ImportDocumentsUseCase(private val repository: ImportQueueRepository) {
    operator fun invoke(uris: List<String>) {
        if (uris.isNotEmpty()) repository.import(uris)
    }
}

/** Enqueues the Ready documents for extraction and chunking; returns how many (contracts I9). */
class StartIndexingUseCase(private val repository: ImportQueueRepository) {
    suspend operator fun invoke(): Int = repository.startProcessing()
}
