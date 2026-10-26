package com.lisdatasolutions.microrag.data.ingest

import androidx.work.Data
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.lisdatasolutions.microrag.domain.ingest.IngestProgress
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** The processing work as the queue sees it (F06 research R7, contracts/ingest-api.md). */
interface IngestScheduler {
    /** Documents in an enqueued, blocked or running request: «En cola» unless they are the current one. */
    val activeDocumentIds: Flow<Set<Long>>

    /** Stage of the document being processed, if any. */
    val progress: Flow<IngestProgress?>
}

class WorkManagerIngestScheduler(private val workManager: WorkManager) : IngestScheduler {

    private val infos: Flow<List<WorkInfo>> get() = workManager.getWorkInfosForUniqueWorkFlow(UNIQUE_NAME)

    override val activeDocumentIds: Flow<Set<Long>> = infos.map { list ->
        list.filterNot { it.state.isFinished }.flatMapTo(HashSet()) { info -> info.tags.mapNotNull(::documentIdOf) }
    }.distinctUntilChanged()

    override val progress: Flow<IngestProgress?> = infos.map { list ->
        list.firstOrNull { it.state == WorkInfo.State.RUNNING }?.progress?.let(::progressOf)
    }.distinctUntilChanged()

    companion object {
        const val UNIQUE_NAME = "ingest"
        const val TAG = "ingest"
        const val DOCUMENT_TAG_PREFIX = "ingest-doc:"
        const val KEY_DOCUMENT_IDS = "doc_ids"
        const val KEY_DOCUMENT = "doc"
        const val KEY_STAGE = "stage"
        const val KEY_PAGE = "page"
        const val KEY_PAGES = "pages"
        const val STAGE_EXTRACTING = "extracting"
        const val STAGE_CHUNKING = "chunking"

        fun documentTag(id: Long) = "$DOCUMENT_TAG_PREFIX$id"

        fun documentIdOf(tag: String): Long? = tag.takeIf { it.startsWith(DOCUMENT_TAG_PREFIX) }
            ?.removePrefix(DOCUMENT_TAG_PREFIX)?.toLongOrNull()

        fun progressData(progress: IngestProgress): Data = Data.Builder()
            .putLong(KEY_DOCUMENT, progress.documentId)
            .putString(KEY_STAGE, if (progress.stage == IngestProgress.Stage.EXTRACTING) STAGE_EXTRACTING else STAGE_CHUNKING)
            .putInt(KEY_PAGE, progress.page ?: -1)
            .putInt(KEY_PAGES, progress.pages ?: -1)
            .build()

        fun progressOf(data: Data): IngestProgress? {
            val id = data.getLong(KEY_DOCUMENT, -1).takeIf { it >= 0 } ?: return null
            val stage = when (data.getString(KEY_STAGE)) {
                STAGE_EXTRACTING -> IngestProgress.Stage.EXTRACTING
                STAGE_CHUNKING -> IngestProgress.Stage.CHUNKING
                else -> return null
            }
            return IngestProgress(id, stage, data.getInt(KEY_PAGE, -1).takeIf { it >= 0 }, data.getInt(KEY_PAGES, -1).takeIf { it >= 0 })
        }
    }
}
