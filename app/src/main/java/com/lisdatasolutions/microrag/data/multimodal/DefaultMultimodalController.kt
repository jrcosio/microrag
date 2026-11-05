package com.lisdatasolutions.microrag.data.multimodal

import com.lisdatasolutions.microrag.core.di.ApplicationScope
import com.lisdatasolutions.microrag.core.logging.AppLogger
import com.lisdatasolutions.microrag.data.indexing.IngestRunStore
import com.lisdatasolutions.microrag.data.indexing.RunPauseCause
import com.lisdatasolutions.microrag.data.ingest.IngestScheduler
import com.lisdatasolutions.microrag.data.models.ModelFileRepository
import com.lisdatasolutions.microrag.domain.indexing.IndexingRepository
import com.lisdatasolutions.microrag.domain.model.DownloadOutcome
import com.lisdatasolutions.microrag.domain.model.LocalModelWork
import com.lisdatasolutions.microrag.domain.model.ModelFileState
import com.lisdatasolutions.microrag.domain.model.ModelId
import com.lisdatasolutions.microrag.domain.multimodal.EnableMultimodalResult
import com.lisdatasolutions.microrag.domain.multimodal.ModelRequirements
import com.lisdatasolutions.microrag.domain.multimodal.MultimodalController
import com.lisdatasolutions.microrag.domain.multimodal.MultimodalError
import com.lisdatasolutions.microrag.domain.multimodal.MultimodalStage
import com.lisdatasolutions.microrag.domain.multimodal.MultimodalStore
import com.lisdatasolutions.microrag.domain.multimodal.StoredMultimodal
import com.lisdatasolutions.microrag.domain.multimodal.ValidateIdentityUseCase
import com.lisdatasolutions.microrag.domain.onboarding.LocalModelWorkTracker
import com.lisdatasolutions.microrag.domain.onboarding.ModelDownloadController
import com.lisdatasolutions.microrag.domain.onboarding.OnboardingStore
import com.lisdatasolutions.microrag.domain.onboarding.PrepareModelsUseCase
import com.lisdatasolutions.microrag.domain.onboarding.StartDownloadResult
import com.lisdatasolutions.microrag.domain.onboarding.StartModelDownloadUseCase
import com.lisdatasolutions.microrag.domain.rag.IndexCompatibility
import com.lisdatasolutions.microrag.domain.rag.RagIndex
import com.lisdatasolutions.microrag.rag.runtime.EngineCoordinator
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * [MultimodalController] (F19 research R1 and R4, contract M2–M4). Every step runs under one lock and reads the
 * stored stage again, so a step never runs twice, and each one can be repeated after a closed app:
 * - **DOWNLOADING:** F03's download of `[EG2_440M]`; once verified, its backend is prepared. A failed download
 *   or missing space stays here with its error (analyze M1); a model this device cannot run goes back to OFF;
 * - **VALIDATING:** the identity check of research R3; below 0.9999, OFF with the 440M deleted;
 * - **SWITCHING:** the ingest pauses, `rag_meta` records the 440M, the mirror follows, the 270M's engines are
 *   released and its file deleted, and the ingest resumes. Repeating it is harmless.
 */
class DefaultMultimodalController(
    private val store: MultimodalStore,
    private val index: RagIndex,
    private val models: ModelFileRepository,
    private val downloads: ModelDownloadController,
    private val onboarding: OnboardingStore,
    private val startDownload: StartModelDownloadUseCase,
    private val prepare: PrepareModelsUseCase,
    private val tracker: LocalModelWorkTracker,
    private val requirements: ModelRequirements,
    private val validateIdentity: ValidateIdentityUseCase,
    private val coordinator: EngineCoordinator,
    private val runStore: IngestRunStore,
    private val scheduler: IngestScheduler,
    private val indexing: IndexingRepository,
    private val applicationScope: ApplicationScope,
    private val logger: AppLogger,
) : MultimodalController {

    private val lock = Mutex()

    override fun start() {
        applicationScope.scope.launch {
            lock.withLock { reconcileDownload() }
            combine(store.state, models.states, models.preparedBackends, downloads.progress, tracker.work) { _, _, _, _, _ -> }
                .conflate()
                .collect { lock.withLock { advance() } }
        }
    }

    override suspend fun enable(): EnableMultimodalResult = lock.withLock {
        val stored = store.state.first()
        when {
            stored.stage == MultimodalStage.ON -> return@withLock EnableMultimodalResult.On
            stored.stage.activating -> return@withLock EnableMultimodalResult.Started
        }
        // D-079: turning it on again is immediate once the index uses the 440M.
        if (index.open() == IndexCompatibility.Compatible && index.textEmbedder.value == ModelId.EG2_440M) {
            turnedOn()
            return@withLock EnableMultimodalResult.On
        }
        store.write { it.copy(stage = MultimodalStage.DOWNLOADING, error = null) }
        logger.i(TAG) { "multimodal activation started" }
        download()
    }

    override suspend fun retry(): EnableMultimodalResult = lock.withLock {
        if (store.state.first().stage != MultimodalStage.DOWNLOADING) return@withLock EnableMultimodalResult.Started
        tracker.set(ModelId.EG2_440M, null)
        download()
    }

    override suspend fun disable() = lock.withLock {
        when (store.state.first().stage) {
            MultimodalStage.DOWNLOADING -> cancelLocked()
            // D-079: pages and models stay, so turning it on again is immediate.
            MultimodalStage.ON -> {
                store.write { it.copy(stage = MultimodalStage.OFF, error = null) }
                logger.i(TAG) { "multimodal off" }
            }
            // The check and the switch take seconds and are not interrupted (contract U2).
            MultimodalStage.OFF, MultimodalStage.VALIDATING, MultimodalStage.SWITCHING -> Unit
        }
    }

    override suspend fun cancelDownload() = lock.withLock {
        if (store.state.first().stage == MultimodalStage.DOWNLOADING) cancelLocked()
    }

    /** Under the lock: what the stored stage needs next, if anything. */
    private suspend fun advance() {
        val stored = store.state.first()
        try {
            when (stored.stage) {
                MultimodalStage.DOWNLOADING -> downloading(stored)
                MultimodalStage.VALIDATING -> validating()
                MultimodalStage.SWITCHING -> switching()
                MultimodalStage.OFF, MultimodalStage.ON -> Unit
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.w(TAG, e) { "multimodal ${stored.stage} failed: ${e.javaClass.simpleName}" }
            fail(MultimodalError.PREPARE_FAILED)
        }
    }

    private suspend fun downloading(stored: StoredMultimodal) {
        val id = ModelId.EG2_440M
        if (models.states.value[id] is ModelFileState.Verified) {
            when {
                tracker.work.value[id] is LocalModelWork.Rejected -> fail(MultimodalError.PREPARE_FAILED)
                id in models.preparedBackends.value -> {
                    store.write { it.copy(stage = MultimodalStage.VALIDATING, error = null) }
                    logger.i(TAG) { "multimodal 440M ready, validating" }
                }
                // F03's on-device test; it records the backend or rejects the model.
                else -> prepare.prepareAll()
            }
            return
        }
        val error = when (downloads.progress.value.outcomes[id]) {
            is DownloadOutcome.NoSpace -> MultimodalError.NO_SPACE
            is DownloadOutcome.Failed -> MultimodalError.DOWNLOAD_FAILED
            else -> return
        }
        if (stored.error != error) store.write { it.copy(error = error) }
    }

    private suspend fun validating() {
        val check = validateIdentity()
        if (check.passed) {
            store.write { it.copy(stage = MultimodalStage.SWITCHING, error = null) }
        } else {
            fail(MultimodalError.IDENTITY_MISMATCH)
        }
    }

    private suspend fun switching() {
        val held = holdIngest()
        try {
            // Waits for the ingest to let the 270M go (research R4, step 1).
            coordinator.release(ModelId.EG2_270M)
            check(index.open() == IndexCompatibility.Compatible) { "index unavailable" }
            index.switchTextEmbedder(ModelId.EG2_440M)
            store.write { it.copy(textEmbedder = ModelId.EG2_440M) }
            coordinator.release(ModelId.EG2_270M)
            models.delete(ModelId.EG2_270M)
            tracker.set(ModelId.EG2_270M, null)
            onboarding.clearRequest(ModelId.EG2_270M)
        } finally {
            if (held) releaseIngest()
        }
        turnedOn()
        logger.i(TAG) { "multimodal on: the 440M embeds the text" }
    }

    /** ON, and the documents that waited for the mode go on (contract M2). */
    private suspend fun turnedOn() {
        store.write { it.copy(stage = MultimodalStage.ON, error = null) }
        indexing.reconcile()
    }

    /** Back to OFF with [error]; the 440M goes unless the index already embeds with it. */
    private suspend fun fail(error: MultimodalError) {
        logger.w(TAG) { "multimodal activation stopped: $error" }
        store.write { it.copy(stage = MultimodalStage.OFF, error = error) }
        forget440M()
    }

    private suspend fun cancelLocked() {
        downloads.cancel()
        store.write { it.copy(stage = MultimodalStage.OFF, error = null) }
        forget440M()
        logger.i(TAG) { "multimodal download cancelled" }
    }

    private suspend fun forget440M() {
        val id = ModelId.EG2_440M
        onboarding.clearRequest(id)
        tracker.set(id, null)
        if (requirements.current().textEmbedder != id) {
            coordinator.release(id)
            models.delete(id)
        }
        // With the stage OFF, the 440M is no longer required, so its partial file goes too.
        downloads.deleteOrphanPartials(requirements.current().files)
    }

    /** A download left without its job (a cleared app, a reboot that lost it) shows Reintentar. */
    private suspend fun reconcileDownload() {
        val stored = store.state.first()
        if (stored.stage != MultimodalStage.DOWNLOADING || stored.error != null) return
        if (models.states.value[ModelId.EG2_440M] is ModelFileState.Verified) return
        if (onboarding.downloadPaused.first() || downloads.isScheduledOrRunning()) return
        store.write { it.copy(error = MultimodalError.DOWNLOAD_FAILED) }
    }

    private suspend fun download(): EnableMultimodalResult = when (val result = startDownload(only = setOf(ModelId.EG2_440M))) {
        StartDownloadResult.Started -> {
            store.write { it.copy(error = null) }
            EnableMultimodalResult.Started
        }
        is StartDownloadResult.NoSpace -> {
            store.write { it.copy(error = MultimodalError.NO_SPACE) }
            EnableMultimodalResult.NoSpace(result.missingBytes)
        }
    }

    /** Pauses a running ingest with MODEL_SWITCH; a pause the user or the space set stays theirs. */
    private suspend fun holdIngest(): Boolean {
        val run = runStore.edit { if (it.paused) it else it.pausedBy(RunPauseCause.MODEL_SWITCH) }
        if (run.pauseCause != RunPauseCause.MODEL_SWITCH) return false
        scheduler.cancel()
        return true
    }

    private suspend fun releaseIngest() {
        if (runStore.run.first().pauseCause == RunPauseCause.MODEL_SWITCH) indexing.resume()
    }

    private companion object {
        const val TAG = "MicroRAG"
    }
}
