package com.lisdatasolutions.microrag.testing

import com.lisdatasolutions.microrag.data.indexing.DefaultIndexingRepository
import com.lisdatasolutions.microrag.data.indexing.FakeThermalStatusSource
import com.lisdatasolutions.microrag.data.indexing.InMemoryIngestRunStore
import com.lisdatasolutions.microrag.data.ingest.DocumentFileStore
import com.lisdatasolutions.microrag.data.ingest.DocumentFiles
import com.lisdatasolutions.microrag.data.ingest.IngestScheduler
import com.lisdatasolutions.microrag.data.ingest.IngestWork
import com.lisdatasolutions.microrag.data.library.DefaultLibraryRepository
import com.lisdatasolutions.microrag.domain.ingest.IngestProgress
import java.io.Closeable
import java.io.File
import java.util.Collections
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * The Library repository of F08 on a [TempRagIndex], private copies in a temporary folder, the run in
 * memory and a scheduler that only records what is enqueued (the work is run by the tests themselves).
 */
class TempLibrary(val temp: TempRagIndex = TempRagIndex()) : Closeable {
    val dir: File = File(TempRagIndex.context.cacheDir, "library-test-${UUID.randomUUID()}").apply { mkdirs() }
    val runStore = InMemoryIngestRunStore()
    val scheduler = RecordingIngestScheduler()
    val logger get() = temp.logger
    val files = DocumentFiles(DocumentFileStore(dir), temp.index, Dispatchers.IO, temp.logger)
    val indexing = DefaultIndexingRepository(temp.index, scheduler, runStore, FakeThermalStatusSource(), Dispatchers.IO, temp.logger)
    val repository = DefaultLibraryRepository(temp.index, files, runStore, indexing, Dispatchers.IO, temp.logger)

    /** A private copy in the store's folder with [content]. */
    fun copy(name: String, content: String): File = File(dir, name).apply { writeText(content) }

    override fun close() {
        temp.close()
        dir.deleteRecursively()
    }
}

class RecordingIngestScheduler : IngestScheduler {
    override val activeDocumentIds = MutableStateFlow(emptySet<Long>())
    override val progress = MutableStateFlow<IngestProgress?>(null)
    override val work = MutableStateFlow(IngestWork.NONE)
    val enqueued: MutableList<List<Long>> = Collections.synchronizedList(mutableListOf())

    override suspend fun enqueue(documentIds: List<Long>) {
        enqueued += documentIds
    }

    override suspend fun replace(documentIds: List<Long>) {
        enqueued += documentIds
    }

    override suspend fun cancel() = Unit
}
