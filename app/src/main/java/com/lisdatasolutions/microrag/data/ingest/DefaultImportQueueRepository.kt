package com.lisdatasolutions.microrag.data.ingest

import android.os.SystemClock
import com.lisdatasolutions.microrag.core.logging.AppLogger
import com.lisdatasolutions.microrag.domain.ingest.DocumentFormat
import com.lisdatasolutions.microrag.domain.ingest.ImportQueue
import com.lisdatasolutions.microrag.domain.ingest.ImportQueueRepository
import com.lisdatasolutions.microrag.domain.ingest.IngestError
import com.lisdatasolutions.microrag.domain.ingest.IngestProgress
import com.lisdatasolutions.microrag.domain.ingest.QueueItem
import com.lisdatasolutions.microrag.domain.ingest.QueueItemState
import com.lisdatasolutions.microrag.domain.ingest.QueueKey
import com.lisdatasolutions.microrag.domain.rag.DocumentStatus
import com.lisdatasolutions.microrag.domain.rag.IndexCompatibility
import com.lisdatasolutions.microrag.domain.rag.RagIndex
import com.lisdatasolutions.microrag.domain.rag.StoredDocument
import com.lisdatasolutions.microrag.rag.ingest.PdfOpener
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * The import queue (F06 research R5–R7, contracts/ingest-api.md). It combines three sources:
 * - import attempts, held here in memory: they survive Activity recreation but not process death,
 *   which leaves no partial copy behind (D-038);
 * - documents not indexed yet, from the index: they survive everything;
 * - the processing work, from [scheduler].
 *
 * Imports run one at a time, in selection order, in the application [scope]. [fileLock] keeps the
 * orphan cleanup away from a copy in progress.
 */
class DefaultImportQueueRepository(
    private val importer: DocumentImporter,
    private val source: DocumentSource,
    private val index: RagIndex,
    private val files: DocumentFileStore,
    private val scheduler: IngestScheduler,
    private val pdf: PdfOpener,
    private val scope: CoroutineScope,
    private val io: CoroutineDispatcher,
    private val logger: AppLogger,
    private val clock: () -> Long = SystemClock::elapsedRealtime,
) : ImportQueueRepository {

    private data class Attempt(
        val id: String,
        val uri: String,
        val displayName: String,
        val sizeBytes: Long?,
        val format: DocumentFormat?,
        val state: QueueItemState,
        /** [RagIndex.documentChanges] once registered: hidden as soon as the documents reload past it. */
        val registeredAt: Long? = null,
    )

    private data class Pending(val attemptId: String, val uri: String, val info: SourceInfo)

    private data class Loaded(val documents: List<StoredDocument>, val version: Long)

    private val attempts = MutableStateFlow<List<Attempt>>(emptyList())
    private val pending = Channel<Pending>(Channel.UNLIMITED)
    private val consumerStarted = AtomicBoolean(false)
    private val orphansCleaned = AtomicBoolean(false)
    private val fileLock = Mutex()

    @Volatile
    private var running: Pair<String, Job>? = null

    override val queue: Flow<ImportQueue> = flow {
        val available = index.open() == IndexCompatibility.Compatible
        if (available && orphansCleaned.compareAndSet(false, true)) cleanOrphans()
        val documents = if (available) {
            index.documentChanges.map { version -> Loaded(index.documents(QUEUE_STATUSES), version) }
        } else {
            flowOf(Loaded(emptyList(), Long.MIN_VALUE))
        }
        emitAll(
            combine(attempts, documents, scheduler.activeDocumentIds, scheduler.progress) { attempts, loaded, active, progress ->
                build(attempts, loaded, active, progress, available)
            },
        )
    }

    override fun import(uris: List<String>) {
        if (uris.isEmpty()) return
        scope.launch(io) {
            for (uri in uris) {
                val info = describeOrNull(uri)
                val attempt = Attempt(
                    id = UUID.randomUUID().toString(),
                    uri = uri,
                    displayName = info?.displayName ?: DEFAULT_NAME,
                    sizeBytes = info?.sizeBytes,
                    format = info?.let { importer.detectFormat(it.displayName, it.mimeType) },
                    state = if (info == null) {
                        QueueItemState.ImportFailed(IngestError.ACCESS_LOST)
                    } else {
                        QueueItemState.Importing(0, info.sizeBytes)
                    },
                )
                attempts.update { list -> list.filterNot { it.registeredAt != null } + attempt }
                if (info != null) submit(Pending(attempt.id, uri, info))
            }
        }
    }

    override suspend fun retry(key: QueueKey) {
        when (key) {
            is QueueKey.Attempt -> {
                val attempt = attempts.value.firstOrNull { it.id == key.id } ?: return
                val failed = attempt.state as? QueueItemState.ImportFailed ?: return
                if (!failed.error.retryable) return
                val info = withContext(io) { describeOrNull(attempt.uri) }
                if (info == null) {
                    updateAttempt(attempt.id) { it.copy(state = QueueItemState.ImportFailed(IngestError.ACCESS_LOST)) }
                    return
                }
                updateAttempt(attempt.id) { it.copy(state = QueueItemState.Importing(0, info.sizeBytes), sizeBytes = info.sizeBytes) }
                submit(Pending(attempt.id, attempt.uri, info))
            }
            is QueueKey.Document -> {
                val document = index.document(key.id) ?: return
                val error = IngestError.fromCode(document.error)
                if (document.status != DocumentStatus.ERROR || error?.retryable != true) return
                index.updateStatus(key.id, DocumentStatus.PENDING, error = null)
                scheduler.enqueue(listOf(key.id))
                logger.i(TAG) { "ingest retry id=${key.id}" }
            }
        }
    }

    override suspend fun remove(key: QueueKey) {
        when (key) {
            is QueueKey.Attempt -> {
                attempts.update { list -> list.filterNot { it.id == key.id } }
                running?.takeIf { it.first == key.id }?.second?.cancel()
            }
            is QueueKey.Document -> {
                val document = index.document(key.id) ?: return
                index.deleteDocument(key.id)
                withContext(io) { fileLock.withLock { files.delete(document.localPath) } }
                logger.i(TAG) { "ingest remove id=${key.id}" }
            }
        }
    }

    override suspend fun startProcessing(): Int {
        val active = scheduler.activeDocumentIds.first()
        val ready = index.documents(setOf(DocumentStatus.PENDING)).map { it.id }.filterNot { it in active }
        if (ready.isEmpty()) return 0
        scheduler.enqueue(ready)
        logger.i(TAG) { "ingest enqueued ${ready.size}" }
        return ready.size
    }

    private fun submit(job: Pending) {
        pending.trySend(job)
        if (consumerStarted.compareAndSet(false, true)) {
            scope.launch(io) {
                for (next in pending) {
                    // Removed or retried meanwhile: only the latest request for an attempt runs.
                    val current = attempts.value.firstOrNull { it.id == next.attemptId } ?: continue
                    if (current.state !is QueueItemState.Importing) continue
                    val work = scope.launch(io) { fileLock.withLock { runImport(next) } }
                    running = next.attemptId to work
                    work.join()
                    running = null
                }
            }
        }
    }

    private suspend fun runImport(job: Pending) {
        var lastUpdate = 0L
        val result = importer.import(job.uri, job.info) { copied, total ->
            val now = clock()
            if (now - lastUpdate >= PROGRESS_INTERVAL_MS || (total != null && copied >= total)) {
                lastUpdate = now
                updateAttempt(job.attemptId) { it.copy(state = QueueItemState.Importing(copied, total)) }
            }
        }
        when (result) {
            is ImportResult.Imported ->
                updateAttempt(job.attemptId) { it.copy(registeredAt = index.documentChanges.value) }
            is ImportResult.Rejected ->
                updateAttempt(job.attemptId) { it.copy(state = QueueItemState.ImportFailed(result.error)) }
        }
    }

    private fun build(
        attempts: List<Attempt>,
        loaded: Loaded,
        active: Set<Long>,
        progress: IngestProgress?,
        available: Boolean,
    ): ImportQueue {
        val attemptItems = attempts
            .filter { it.registeredAt == null || it.registeredAt > loaded.version }
            .map { attempt ->
                QueueItem(
                    key = QueueKey.Attempt(attempt.id),
                    displayName = attempt.displayName,
                    sizeBytes = attempt.sizeBytes,
                    format = attempt.format,
                    state = if (attempt.registeredAt != null) QueueItemState.Ready else attempt.state,
                )
            }
        val documentItems = loaded.documents.map { document ->
            QueueItem(
                key = QueueKey.Document(document.id),
                displayName = document.displayName,
                sizeBytes = document.sizeBytes,
                format = DocumentFormat.fromMime(document.mimeType),
                state = stateOf(document, active, progress),
            )
        }
        val items = attemptItems + documentItems
        return ImportQueue(
            items = items,
            readyCount = items.count { it.state == QueueItemState.Ready },
            pdfSupported = pdf.isAvailable,
            indexAvailable = available,
        )
    }

    private fun stateOf(document: StoredDocument, active: Set<Long>, progress: IngestProgress?): QueueItemState =
        when (document.status) {
            DocumentStatus.PARSED -> QueueItemState.Prepared(document.chunkCount ?: 0, document.skippedPages)
            DocumentStatus.ERROR -> QueueItemState.Failed(IngestError.fromCode(document.error) ?: IngestError.EXTRACTION_FAILED)
            else -> when {
                document.id !in active -> QueueItemState.Ready
                progress?.documentId != document.id -> QueueItemState.Queued
                progress.stage == IngestProgress.Stage.EXTRACTING -> QueueItemState.Extracting(progress.page, progress.pages)
                else -> QueueItemState.Chunking
            }
        }

    private fun updateAttempt(id: String, change: (Attempt) -> Attempt) {
        attempts.update { list -> list.map { if (it.id == id) change(it) else it } }
    }

    private fun describeOrNull(uri: String): SourceInfo? = try {
        source.describe(uri)
    } catch (e: Exception) {
        logger.w(TAG) { "ingest describe failed ${e.javaClass.simpleName}" }
        null
    }

    private suspend fun cleanOrphans() {
        val referenced = index.documents(DocumentStatus.entries.toSet()).mapNotNullTo(HashSet()) { it.localPath }
        val removed = withContext(io) { fileLock.withLock { files.cleanOrphans(referenced) } }
        if (removed > 0) logger.i(TAG) { "ingest orphans removed=$removed" }
    }

    private companion object {
        const val TAG = "MicroRAG"
        const val DEFAULT_NAME = "documento"
        const val PROGRESS_INTERVAL_MS = 150L
        val QUEUE_STATUSES = setOf(DocumentStatus.PENDING, DocumentStatus.PARSED, DocumentStatus.ERROR)
    }
}
