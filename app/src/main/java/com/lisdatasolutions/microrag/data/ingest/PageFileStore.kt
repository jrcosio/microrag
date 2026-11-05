package com.lisdatasolutions.microrag.data.ingest

import java.io.File
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * The page images of the multimodal mode in `noBackupFilesDir/pages/` (F19 contract I6, research R9 and R11).
 * A page is written to a `.part` file and renamed, and its row is stored while the lock is held, so the startup
 * sweep, which shares the lock (analyze M6), never sees a file without its row or deletes one being written.
 */
class PageFileStore(private val dir: File, private val io: CoroutineDispatcher) {

    private val lock = Mutex()
    private val swept = AtomicBoolean(false)

    /** Writes [name] and runs [commit], which stores its row; if [commit] throws, the file goes and it rethrows. */
    suspend fun <T> write(name: String, bytes: ByteArray, commit: suspend () -> T): T = lock.withLock {
        val file = File(dir, name)
        withContext(io) {
            if (!dir.isDirectory && !dir.mkdirs()) throw IOException("cannot create the pages folder")
            val part = File(dir, name + PART_SUFFIX)
            part.writeBytes(bytes)
            if (!part.renameTo(file)) {
                part.delete()
                throw IOException("cannot move a page into place")
            }
        }
        try {
            commit()
        } catch (e: Throwable) {
            withContext(io) { file.delete() }
            throw e
        }
    }

    /** Deletes the images of rows that are already gone (after the transaction that removed them, research R11). */
    suspend fun delete(names: Collection<String>) {
        if (names.isEmpty()) return
        lock.withLock { withContext(io) { names.forEach { File(dir, it).delete() } } }
    }

    /**
     * Contract I7: deletes every file no row names, and leftovers of an interrupted write. Returns how many. [named]
     * is null when the index cannot say (closed or incompatible): then nothing is deleted.
     */
    suspend fun sweep(named: suspend () -> Set<String>?): Int = lock.withLock {
        val keep = named() ?: return@withLock 0
        withContext(io) { dir.listFiles()?.filter { it.isFile && it.name !in keep }?.count { it.delete() } ?: 0 }
    }

    /** The startup sweep, once per process, as F08's orphan copies (contract I7). */
    suspend fun sweepOnce(named: suspend () -> Set<String>?): Int = if (swept.compareAndSet(false, true)) sweep(named) else 0

    /** At start: only with images on disk, so a phone that never used the mode opens nothing for it. */
    suspend fun sweepAtStart(named: suspend () -> Set<String>?): Int =
        if (withContext(io) { dir.list().orEmpty().isNotEmpty() }) sweepOnce(named) else 0

    fun file(name: String): File = File(dir, name)

    companion object {
        private const val PART_SUFFIX = ".part"

        /** One image per page: `<content hash>-p<page>.jpg` (research R9); a shadow has its own hash. */
        fun nameOf(contentHash: String, page: Int): String = "${contentHash.replace(UNSAFE, "_")}-p$page.jpg"

        private val UNSAFE = Regex("[^A-Za-z0-9_-]")
    }
}
