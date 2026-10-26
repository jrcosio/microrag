package com.lisdatasolutions.microrag.testing

import com.lisdatasolutions.microrag.data.ingest.DocumentSource
import com.lisdatasolutions.microrag.data.ingest.SourceInfo
import com.lisdatasolutions.microrag.rag.ingest.PdfDamagedException
import com.lisdatasolutions.microrag.rag.ingest.PdfEncryptedException
import com.lisdatasolutions.microrag.rag.ingest.PdfOpener
import com.lisdatasolutions.microrag.rag.ingest.PdfPages
import com.lisdatasolutions.microrag.rag.ingest.PdfUnavailableException
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.util.Collections

/** A picked file for [FakeDocumentSource]: its bytes, or the failure the provider gives. */
data class FakeFile(
    val info: SourceInfo,
    val bytes: ByteArray = ByteArray(0),
    val describeError: Exception? = null,
    val readError: IOException? = null,
    val readErrorAfter: Int = 0,
    /** When set, reading waits for it: holds an import in the middle of its copy. */
    val gate: java.util.concurrent.CountDownLatch? = null,
)

class FakeDocumentSource : DocumentSource {
    val files = linkedMapOf<String, FakeFile>()
    val opened: MutableList<String> = Collections.synchronizedList(mutableListOf())

    fun add(uri: String, name: String, bytes: ByteArray, mime: String? = null, size: Long? = bytes.size.toLong()) {
        files[uri] = FakeFile(SourceInfo(name, size, mime), bytes)
    }

    override fun describe(uri: String): SourceInfo {
        val file = files[uri] ?: throw FileNotFoundException(uri)
        file.describeError?.let { throw it }
        return file.info
    }

    override fun open(uri: String): InputStream {
        val file = files[uri] ?: throw FileNotFoundException(uri)
        opened += uri
        file.gate?.let { gate ->
            return object : InputStream() {
                private val bytes = ByteArrayInputStream(file.bytes)

                override fun read(): Int {
                    gate.await()
                    return bytes.read()
                }
            }
        }
        val error = file.readError ?: return ByteArrayInputStream(file.bytes)
        return object : InputStream() {
            private var read = 0

            override fun read(): Int {
                if (read >= file.readErrorAfter) throw error
                return file.bytes[read++].toInt() and 0xFF
            }
        }
    }
}

/** PDF pages from text; a file whose content starts with one of the markers fails accordingly. */
class FakePdfOpener(var isAvailableValue: Boolean = true) : PdfOpener {
    override val isAvailable: Boolean get() = isAvailableValue

    override fun open(file: File): PdfPages {
        if (!isAvailableValue) throw PdfUnavailableException()
        val text = file.readText(Charsets.ISO_8859_1)
        when {
            ENCRYPTED in text -> throw PdfEncryptedException()
            DAMAGED in text -> throw PdfDamagedException()
        }
        val pages = text.substringAfter("\n", "").split(PAGE_BREAK).let { if (it == listOf("")) emptyList() else it }
        val forcedCount = Regex("pages=(\\d+)").find(text)?.groupValues?.get(1)?.toInt()
        return object : PdfPages {
            override val pageCount: Int = forcedCount ?: pages.size

            override fun pageText(index: Int): String = pages.getOrElse(index) { "" }

            override fun close() = Unit
        }
    }

    companion object {
        const val ENCRYPTED = "%ENCRYPTED"
        const val DAMAGED = "%DAMAGED"
        const val PAGE_BREAK = "\u000C"

        /** A fake PDF: header line, then pages separated by form feeds. */
        fun pdf(vararg pages: String, marker: String = ""): ByteArray =
            ("%PDF-1.4 $marker\n" + pages.joinToString(PAGE_BREAK)).toByteArray(Charsets.ISO_8859_1)
    }
}

/** Processing work as tests set it (contracts/ingest-api.md). */
class FakeIngestScheduler : com.lisdatasolutions.microrag.data.ingest.IngestScheduler {
    override val activeDocumentIds = kotlinx.coroutines.flow.MutableStateFlow<Set<Long>>(emptySet())
    override val progress = kotlinx.coroutines.flow.MutableStateFlow<com.lisdatasolutions.microrag.domain.ingest.IngestProgress?>(null)
    override val work = kotlinx.coroutines.flow.MutableStateFlow(com.lisdatasolutions.microrag.data.ingest.IngestWork.NONE)
    val enqueued = mutableListOf<List<Long>>()
    val replaced = mutableListOf<List<Long>>()
    var cancels = 0

    override suspend fun enqueue(documentIds: List<Long>) {
        enqueued += documentIds
        activeDocumentIds.value = activeDocumentIds.value + documentIds
        if (documentIds.isNotEmpty()) work.value = com.lisdatasolutions.microrag.data.ingest.IngestWork(unfinished = true, running = false, waitingForSystem = false)
    }

    override suspend fun replace(documentIds: List<Long>) {
        replaced += documentIds
        activeDocumentIds.value = documentIds.toSet()
        work.value = com.lisdatasolutions.microrag.data.ingest.IngestWork(unfinished = documentIds.isNotEmpty(), running = false, waitingForSystem = false)
    }

    override suspend fun cancel() {
        cancels++
        activeDocumentIds.value = emptySet()
        work.value = com.lisdatasolutions.microrag.data.ingest.IngestWork.NONE
    }
}

/** Queue repository whose queue tests set; records every call (F06 ViewModel tests). */
class FakeImportQueueRepository : com.lisdatasolutions.microrag.domain.ingest.ImportQueueRepository {
    val state = kotlinx.coroutines.flow.MutableStateFlow(com.lisdatasolutions.microrag.domain.ingest.ImportQueue.EMPTY)
    override val queue: kotlinx.coroutines.flow.Flow<com.lisdatasolutions.microrag.domain.ingest.ImportQueue> = state
    val imported = mutableListOf<List<String>>()
    val retried = mutableListOf<com.lisdatasolutions.microrag.domain.ingest.QueueKey>()
    val removed = mutableListOf<com.lisdatasolutions.microrag.domain.ingest.QueueKey>()
    var starts = 0
    var startResult = 1
    var startGate: kotlinx.coroutines.CompletableDeferred<Unit>? = null

    override fun import(uris: List<String>) {
        imported += uris
    }

    override suspend fun retry(key: com.lisdatasolutions.microrag.domain.ingest.QueueKey) {
        retried += key
    }

    override suspend fun remove(key: com.lisdatasolutions.microrag.domain.ingest.QueueKey) {
        removed += key
    }

    override suspend fun startProcessing(): Int {
        starts++
        startGate?.await()
        return startResult
    }
}
