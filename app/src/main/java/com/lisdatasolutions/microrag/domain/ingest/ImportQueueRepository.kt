package com.lisdatasolutions.microrag.domain.ingest

import kotlinx.coroutines.flow.Flow

/** The import queue (F06 contracts/ingest-api.md). */
interface ImportQueueRepository {
    /** The queue as the user sees it; the first collection in a process cleans orphan files. */
    val queue: Flow<ImportQueue>

    /** Starts importing [uris] (strings from the system picker) one by one, in the application scope. */
    fun import(uris: List<String>)

    /** Retries a failed attempt from its URI, or puts a failed document back in the processing queue. */
    suspend fun retry(key: QueueKey)

    /** Cancels an import, or deletes a non-indexed document and its private copy. */
    suspend fun remove(key: QueueKey)

    /** Enqueues every Ready document for processing; returns how many were enqueued. */
    suspend fun startProcessing(): Int
}
