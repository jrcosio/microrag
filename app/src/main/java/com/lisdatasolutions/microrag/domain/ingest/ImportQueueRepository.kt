package com.lisdatasolutions.microrag.domain.ingest

import com.lisdatasolutions.microrag.domain.rag.IndexMode
import kotlinx.coroutines.flow.Flow

/** The import queue (F06 contracts/ingest-api.md). */
interface ImportQueueRepository {
    /** The queue as the user sees it; the first collection in a process cleans orphan files. */
    val queue: Flow<ImportQueue>

    /**
     * Starts importing [uris] (strings from the system picker) one by one, in the application scope, into
     * [folderId] (null: «General»), fixed for each attempt as it is chosen (F17 contract F6, M3). F19 (contract
     * I1): [indexMode] is the selection's, kept by a retry; it only counts for a PDF with the multimodal mode on.
     */
    fun import(uris: List<String>, folderId: Long? = null, indexMode: IndexMode = IndexMode.TEXT)

    /** Retries a failed attempt from its URI, or puts a failed document back in the processing queue. */
    suspend fun retry(key: QueueKey)

    /** Cancels an import, or deletes a non-indexed document and its private copy. */
    suspend fun remove(key: QueueKey)

    /** Enqueues every Ready document for processing; returns how many were enqueued. */
    suspend fun startProcessing(): Int
}
