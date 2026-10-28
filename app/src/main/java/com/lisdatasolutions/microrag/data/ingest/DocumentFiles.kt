package com.lisdatasolutions.microrag.data.ingest

import com.lisdatasolutions.microrag.core.logging.AppLogger
import com.lisdatasolutions.microrag.domain.rag.DocumentStatus
import com.lisdatasolutions.microrag.domain.rag.RagIndex
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * The private copies as the Ingest and the Library share them (F06 research R5, F08 research R6). Its
 * lock keeps the orphan cleanup away from a copy being written or deleted; the cleanup runs once per
 * process, from whichever screen is observed first.
 */
class DocumentFiles(
    private val store: DocumentFileStore,
    private val index: RagIndex,
    private val io: CoroutineDispatcher,
    private val logger: AppLogger,
) {
    private val lock = Mutex()
    private val orphansCleaned = AtomicBoolean(false)

    /** Runs [block] with the copies locked, as an import writing its copy does. */
    suspend fun <T> withLock(block: suspend () -> T): T = lock.withLock { block() }

    /**
     * The first call in a process deletes the copies no document references. They are read under the
     * lock: read before it, an import finishing meanwhile would have its new copy taken for an orphan.
     */
    suspend fun cleanOrphansOnce() {
        if (!orphansCleaned.compareAndSet(false, true)) return
        val removed = lock.withLock {
            val referenced = index.documents(DocumentStatus.entries.toSet()).mapNotNullTo(HashSet()) { it.localPath }
            withContext(io) { store.cleanOrphans(referenced) }
        }
        if (removed > 0) logger.i(TAG) { "ingest orphans removed=$removed" }
    }

    /** Deletes a private copy; false when it is left behind, for the next cleanup to take. */
    suspend fun delete(path: String?): Boolean = lock.withLock { withContext(io) { store.delete(path) } }

    /** Whether the private copy is still there (D10); checked off the caller's thread. */
    suspend fun exists(path: String?): Boolean = path != null && withContext(io) { File(path).isFile }

    private companion object {
        const val TAG = "MicroRAG"
    }
}
