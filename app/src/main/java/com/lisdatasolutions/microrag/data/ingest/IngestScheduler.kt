package com.lisdatasolutions.microrag.data.ingest

import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.await
import com.lisdatasolutions.microrag.domain.ingest.IngestProgress
import com.lisdatasolutions.microrag.domain.model.ComputeBackend
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** Where the processing work stands (F07 data-model §7). */
data class IngestWork(
    /** Some request is enqueued, blocked or running. */
    val unfinished: Boolean,
    val running: Boolean,
    /** Enqueued again after the system stopped it (quota, time limit, device state). */
    val waitingForSystem: Boolean,
) {
    companion object {
        val NONE = IngestWork(unfinished = false, running = false, waitingForSystem = false)
    }
}

/** The processing work as the queue and the Index screen see it (F06 research R7, F07 research R1, R12). */
interface IngestScheduler {
    /** Documents in an enqueued, blocked or running request: «En cola» unless they are the current one. */
    val activeDocumentIds: Flow<Set<Long>>

    /** Stage of the document being processed, if any. */
    val progress: Flow<IngestProgress?>

    val work: Flow<IngestWork>

    /** Adds a processing request for [documentIds], in this order, after any request still running. */
    suspend fun enqueue(documentIds: List<Long>)

    /** Replaces whatever is enqueued with one request for [documentIds] (reconciliation, rule N14). */
    suspend fun replace(documentIds: List<Long>)

    /** Stops the work: the running worker finishes its chunk and stores its batch (rule N4). */
    suspend fun cancel()
}

class WorkManagerIngestScheduler(private val workManager: WorkManager) : IngestScheduler {

    private val infos: Flow<List<WorkInfo>> get() = workManager.getWorkInfosForUniqueWorkFlow(UNIQUE_NAME)

    override val activeDocumentIds: Flow<Set<Long>> = infos.map { list ->
        list.filterNot { it.state.isFinished }.flatMapTo(HashSet()) { info -> info.tags.mapNotNull(::documentIdOf) }
    }.distinctUntilChanged()

    override val progress: Flow<IngestProgress?> = infos.map { list ->
        list.firstOrNull { it.state == WorkInfo.State.RUNNING }?.progress?.let(::progressOf)
    }.distinctUntilChanged()

    override val work: Flow<IngestWork> = infos.map { list ->
        val unfinished = list.filterNot { it.state.isFinished }
        IngestWork(
            unfinished = unfinished.isNotEmpty(),
            running = unfinished.any { it.state == WorkInfo.State.RUNNING },
            waitingForSystem = unfinished.any { it.state == WorkInfo.State.ENQUEUED && it.stoppedBySystem() },
        )
    }.distinctUntilChanged()

    override suspend fun enqueue(documentIds: List<Long>) = submit(documentIds, ExistingWorkPolicy.APPEND_OR_REPLACE)

    override suspend fun replace(documentIds: List<Long>) = submit(documentIds, ExistingWorkPolicy.REPLACE)

    override suspend fun cancel() {
        workManager.cancelUniqueWork(UNIQUE_NAME).await()
    }

    private suspend fun submit(documentIds: List<Long>, policy: ExistingWorkPolicy) {
        if (documentIds.isEmpty()) return
        val request = OneTimeWorkRequestBuilder<IngestWorker>()
            .setInputData(Data.Builder().putLongArray(KEY_DOCUMENT_IDS, documentIds.toLongArray()).build())
            .addTag(TAG)
            .apply { documentIds.forEach { addTag(documentTag(it)) } }
            .build()
        workManager.enqueueUniqueWork(UNIQUE_NAME, policy, request).await()
    }

    /** The previous attempt ended because the system stopped it, not because the app cancelled it. */
    private fun WorkInfo.stoppedBySystem(): Boolean =
        runAttemptCount > 0 && stopReason != WorkInfo.STOP_REASON_NOT_STOPPED && stopReason != WorkInfo.STOP_REASON_CANCELLED_BY_APP

    companion object {
        const val UNIQUE_NAME = "ingest"
        const val TAG = "ingest"
        const val DOCUMENT_TAG_PREFIX = "ingest-doc:"
        const val KEY_DOCUMENT_IDS = "doc_ids"
        const val KEY_DOCUMENT = "doc"
        const val KEY_STAGE = "stage"
        const val KEY_PAGE = "page"
        const val KEY_PAGES = "pages"
        const val KEY_CHUNKS_DONE = "chunks_done"
        const val KEY_CHUNKS_TOTAL = "chunks_total"
        const val KEY_RATE = "rate"
        const val KEY_BACKEND = "backend"
        const val KEY_WAITING = "waiting"
        const val STAGE_EXTRACTING = "extracting"
        const val STAGE_CHUNKING = "chunking"
        const val STAGE_EMBEDDING = "embedding"
        const val STAGE_CLOSING = "closing"
        const val WAITING_THERMAL = "thermal"

        fun documentTag(id: Long) = "$DOCUMENT_TAG_PREFIX$id"

        fun documentIdOf(tag: String): Long? = tag.takeIf { it.startsWith(DOCUMENT_TAG_PREFIX) }
            ?.removePrefix(DOCUMENT_TAG_PREFIX)?.toLongOrNull()

        private val STAGES = mapOf(
            IngestProgress.Stage.EXTRACTING to STAGE_EXTRACTING,
            IngestProgress.Stage.CHUNKING to STAGE_CHUNKING,
            IngestProgress.Stage.EMBEDDING to STAGE_EMBEDDING,
            IngestProgress.Stage.CLOSING to STAGE_CLOSING,
        )

        fun progressData(progress: IngestProgress): Data = Data.Builder()
            .putLong(KEY_DOCUMENT, progress.documentId)
            .putString(KEY_STAGE, STAGES.getValue(progress.stage))
            .putInt(KEY_PAGE, progress.page ?: -1)
            .putInt(KEY_PAGES, progress.pages ?: -1)
            .putInt(KEY_CHUNKS_DONE, progress.chunksDone ?: -1)
            .putInt(KEY_CHUNKS_TOTAL, progress.chunksTotal ?: -1)
            .putDouble(KEY_RATE, progress.chunksPerSecond ?: -1.0)
            .putString(KEY_BACKEND, progress.backend?.name)
            .putString(KEY_WAITING, if (progress.waiting == IngestProgress.WaitReason.THERMAL) WAITING_THERMAL else null)
            .build()

        fun progressOf(data: Data): IngestProgress? {
            val id = data.getLong(KEY_DOCUMENT, -1).takeIf { it >= 0 } ?: return null
            val stage = STAGES.entries.firstOrNull { it.value == data.getString(KEY_STAGE) }?.key ?: return null
            fun int(key: String) = data.getInt(key, -1).takeIf { it >= 0 }
            return IngestProgress(
                documentId = id,
                stage = stage,
                page = int(KEY_PAGE),
                pages = int(KEY_PAGES),
                chunksDone = int(KEY_CHUNKS_DONE),
                chunksTotal = int(KEY_CHUNKS_TOTAL),
                chunksPerSecond = data.getDouble(KEY_RATE, -1.0).takeIf { it >= 0 },
                backend = data.getString(KEY_BACKEND)?.let { name -> ComputeBackend.entries.firstOrNull { it.name == name } },
                waiting = if (data.getString(KEY_WAITING) == WAITING_THERMAL) IngestProgress.WaitReason.THERMAL else null,
            )
        }
    }
}
