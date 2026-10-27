package com.lisdatasolutions.microrag.data.indexing

import com.lisdatasolutions.microrag.core.logging.AppLogger
import com.lisdatasolutions.microrag.data.device.ThermalLevel
import com.lisdatasolutions.microrag.data.device.ThermalStatusSource
import com.lisdatasolutions.microrag.data.ingest.IngestScheduler
import com.lisdatasolutions.microrag.data.ingest.IngestWork
import com.lisdatasolutions.microrag.domain.indexing.CurrentDocument
import com.lisdatasolutions.microrag.domain.indexing.FailedDocument
import com.lisdatasolutions.microrag.domain.indexing.IndexingRepository
import com.lisdatasolutions.microrag.domain.indexing.IndexingState
import com.lisdatasolutions.microrag.domain.indexing.PauseReason
import com.lisdatasolutions.microrag.domain.indexing.RunProgress
import com.lisdatasolutions.microrag.domain.ingest.IngestError
import com.lisdatasolutions.microrag.domain.ingest.IngestProgress
import com.lisdatasolutions.microrag.domain.rag.DocumentStatus
import com.lisdatasolutions.microrag.domain.rag.IndexCompatibility
import com.lisdatasolutions.microrag.domain.rag.IndexUnavailableException
import com.lisdatasolutions.microrag.domain.rag.RagIndex
import kotlin.math.ceil
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.withContext

/**
 * The indexing run as the Index screen shows it and the user controls it (F07 contracts/indexing-api.md).
 * Progress comes from what is stored and from the real chunking (research R8); the pause reason follows
 * data-model §7. Index and DataStore work runs on [io], never on the caller's thread.
 */
class DefaultIndexingRepository(
    private val index: RagIndex,
    private val scheduler: IngestScheduler,
    private val runStore: IngestRunStore,
    private val thermal: ThermalStatusSource,
    private val io: CoroutineDispatcher,
    private val logger: AppLogger,
    private val alarm: IndexingAlarm = IndexingAlarm.NONE,
) : IndexingRepository {

    private class Inputs(val run: IngestRun, val progress: IngestProgress?, val work: IngestWork, val heat: ThermalLevel)

    @OptIn(ExperimentalCoroutinesApi::class)
    override val state: Flow<IndexingState> = combine(
        index.documentChanges, runStore.run, scheduler.progress, scheduler.work, thermal.level,
    ) { _, run, progress, work, heat -> Inputs(run, progress, work, heat) }
        .mapLatest(::compute)
        .onStart { emit(IndexingState.Loading) }
        .distinctUntilChanged()
        .flowOn(io)

    private suspend fun compute(inputs: Inputs): IndexingState = try {
        val all = index.documents(DocumentStatus.entries.toSet()).associateBy { it.id }
        val failed = all.values.filter { it.status == DocumentStatus.ERROR }.map {
            FailedDocument(it.id, it.displayName, IngestError.fromCode(it.error) ?: IngestError.EXTRACTION_FAILED)
        }
        val members = inputs.run.documentIds.mapNotNull { all[it] }.filter { it.status != DocumentStatus.ERROR }
        val unfinished = members.filter { it.status in UNFINISHED }
        if (unfinished.isEmpty()) {
            IndexingState.Idle(failed)
        } else {
            val total = if (members.any { it.status == DocumentStatus.PENDING }) null else members.sumOf { it.chunkCount ?: 0 }
            val stored = index.storedChunkCounts(members.map { it.id })
            val done = members.sumOf { stored[it.id] ?: 0 }
            val running = inputs.progress?.takeIf { inputs.work.running && unfinished.any { doc -> doc.id == it.documentId } }
            val current = running?.let { progress ->
                all[progress.documentId]?.let { CurrentDocument(it.id, it.displayName, progress.stage.forScreen()) }
            } ?: unfinished.firstOrNull { it.status == DocumentStatus.EMBEDDING }?.let {
                // Paused or waiting, the document left halfway is still the current one: its stage shows «En pausa» (P3).
                CurrentDocument(it.id, it.displayName, CurrentDocument.Stage.EMBEDDING)
            }
            val pause = when {
                inputs.run.pauseCause != null -> inputs.run.pauseCause.reason()
                inputs.heat == ThermalLevel.SEVERE_OR_WORSE || running?.waiting == IngestProgress.WaitReason.THERMAL -> PauseReason.THERMAL
                inputs.work.waitingForSystem -> PauseReason.SYSTEM_LIMIT
                else -> null
            }
            val remaining = running?.chunksPerSecond
                ?.takeIf { pause == null && total != null && it > 0 }
                ?.let { rate -> ceil((total!! - done).coerceAtLeast(0) / rate).toLong() }
            IndexingState.Active(
                progress = RunProgress(done = done, total = total, remainingSeconds = remaining),
                current = current,
                queuedDocuments = unfinished.size - if (current != null) 1 else 0,
                pause = pause,
                failed = failed,
            )
        }
    } catch (e: IndexUnavailableException) {
        IndexingState.IndexUnavailable
    }

    override suspend fun start(): Int = withContext(io) {
        val unfinished = index.documents(UNFINISHED).map { it.id }
        val run = runStore.edit { current ->
            // Finished documents leave first, so the new run counts only what is left (rule N17).
            current.without(current.documentIds.filterNot { it in unfinished }).adding(unfinished).resumed()
        }
        if (unfinished.isNotEmpty()) enqueue(unfinished)
        logger.i(TAG) { "ingest start run=${run.documentIds.size}" }
        run.documentIds.size
    }

    override suspend fun pause() = withContext(io) {
        // Saved first, so neither reconcile nor another screen enqueues the work again (rule N8).
        runStore.edit { it.pausedBy(RunPauseCause.USER) }
        scheduler.cancel()
        alarm.cancel()
        logger.i(TAG) { "ingest paused by the user" }
    }

    override suspend fun resume() = withContext(io) {
        val run = runStore.edit { it.resumed() }
        val unfinished = unfinishedOf(run)
        if (unfinished.isNotEmpty()) enqueue(unfinished)
        logger.i(TAG) { "ingest resumed run=${unfinished.size}" }
    }

    override suspend fun retry(documentId: Long) = withContext(io) {
        val document = index.document(documentId) ?: return@withContext
        val error = IngestError.fromCode(document.error)
        if (document.status != DocumentStatus.ERROR || error?.retryable != true) return@withContext
        index.updateStatus(documentId, DocumentStatus.PENDING, error = null)
        runStore.edit { it.adding(listOf(documentId)).resumed() }
        enqueue(listOf(documentId))
        logger.i(TAG) { "ingest retry id=$documentId" }
    }

    override suspend fun reconcile() = withContext(io) {
        if (index.open() != IndexCompatibility.Compatible) return@withContext
        val run = runStore.run.first()
        if (run.paused) return@withContext
        val unfinished = unfinishedOf(run)
        if (unfinished.isEmpty()) return@withContext
        // Also when woken by it: while the run is unfinished, the next wake-up stays set (D-041).
        alarm.arm()
        val work = scheduler.work.first()
        when {
            !work.unfinished -> scheduler.enqueue(unfinished)
            work.waitingForSystem -> scheduler.replace(unfinished)
            else -> return@withContext
        }
        logger.i(TAG) { "ingest reconciled run=${unfinished.size}" }
    }

    /** Appends to running work only what it does not have yet; with no work, everything (rule N14). */
    private suspend fun enqueue(ids: List<Long>) {
        val work = scheduler.work.first()
        val active = scheduler.activeDocumentIds.first()
        val missing = if (work.unfinished) ids.filterNot { it in active } else ids
        if (missing.isNotEmpty()) scheduler.enqueue(missing)
        alarm.arm()
    }

    private suspend fun unfinishedOf(run: IngestRun): List<Long> =
        run.documentIds.filter { index.document(it)?.status in UNFINISHED }

    private fun RunPauseCause.reason(): PauseReason = when (this) {
        RunPauseCause.USER -> PauseReason.USER
        RunPauseCause.NO_SPACE -> PauseReason.NO_SPACE
        RunPauseCause.MODEL_MISSING -> PauseReason.MODEL_MISSING
    }

    private fun IngestProgress.Stage.forScreen(): CurrentDocument.Stage = when (this) {
        IngestProgress.Stage.EXTRACTING -> CurrentDocument.Stage.EXTRACTING
        IngestProgress.Stage.CHUNKING -> CurrentDocument.Stage.CHUNKING
        IngestProgress.Stage.EMBEDDING -> CurrentDocument.Stage.EMBEDDING
        IngestProgress.Stage.CLOSING -> CurrentDocument.Stage.CLOSING
    }

    private companion object {
        const val TAG = "MicroRAG"
        val UNFINISHED = setOf(DocumentStatus.PENDING, DocumentStatus.PARSED, DocumentStatus.EMBEDDING)
    }
}
