package com.lisdatasolutions.microrag.domain.onboarding

import com.lisdatasolutions.microrag.core.di.ApplicationScope
import com.lisdatasolutions.microrag.core.logging.AppLogger
import com.lisdatasolutions.microrag.data.models.ModelFileRepository
import com.lisdatasolutions.microrag.domain.model.DownloadOutcome
import com.lisdatasolutions.microrag.domain.model.LocalModelWork
import com.lisdatasolutions.microrag.domain.model.ModelCatalog
import com.lisdatasolutions.microrag.domain.model.ModelFileState
import com.lisdatasolutions.microrag.domain.model.ModelId
import com.lisdatasolutions.microrag.domain.model.OnboardingStage
import com.lisdatasolutions.microrag.domain.model.SetupProblem
import com.lisdatasolutions.microrag.domain.model.StorageStatus
import com.lisdatasolutions.microrag.rag.runtime.CompatibilityResult
import com.lisdatasolutions.microrag.rag.runtime.ModelCompatibility
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The setup screen became visible (US4): rescan files, resume a download that lost its job, verify
 * complete but unrecorded files. A failure waits for «Reintentar»; missing space is rechecked.
 */
class ReconcileDownloadsUseCase(
    private val repository: ModelFileRepository,
    private val controller: ModelDownloadController,
    private val store: OnboardingStore,
    private val resources: DeviceResources,
    private val logger: AppLogger,
) {
    suspend operator fun invoke() {
        repository.refresh()
        controller.deleteOrphanPartials()
        ModelCatalog.required
            .filter { repository.states.value[it.id] is ModelFileState.Unverified }
            .forEach { repository.verify(it.id) }

        val requested = store.downloadRequested.first()
        val pending = pendingDownloads(repository, controller).filter { it.first.id in requested }
        if (pending.isEmpty() || store.downloadPaused.first() || controller.isScheduledOrRunning()) return
        val outcomes = controller.progress.value.outcomes
        if (outcomes.values.any { it is DownloadOutcome.Failed }) return
        val bytes = pending.sumOf { it.second }
        if (outcomes.values.any { it is DownloadOutcome.NoSpace } &&
            resources.freeBytes() < bytes + ModelSetupPolicy.SPACE_MARGIN_BYTES
        ) {
            return
        }
        logger.i(TAG) { "reconcile: rescheduling ${pending.size} model(s)" }
        controller.schedule(bytes)
    }

    private companion object {
        const val TAG = "MicroRAG"
    }
}

/**
 * Runs the on-device test on verified files without a recorded backend, one at a time and in the
 * download order (FR-021). Runs in the application scope so a rotation does not cancel it.
 */
class PrepareModelsUseCase(
    private val repository: ModelFileRepository,
    private val checker: ModelCompatibility,
    private val tracker: LocalModelWorkTracker,
    private val applicationScope: ApplicationScope,
) {
    private val mutex = Mutex()

    /** Starts preparing in the background; a call while preparing does nothing. */
    fun launch() {
        if (mutex.isLocked) return
        applicationScope.scope.launch { prepareAll() }
    }

    suspend fun prepareAll() {
        if (!mutex.tryLock()) return
        try {
            for (file in ModelCatalog.required) {
                val state = repository.states.value[file.id] as? ModelFileState.Verified ?: continue
                if (file.id in repository.preparedBackends.value || tracker.work.value[file.id] is LocalModelWork.Rejected) continue
                tracker.set(file.id, LocalModelWork.Preparing)
                try {
                    when (val result = checker.check(file, state.path)) {
                        is CompatibilityResult.Compatible -> {
                            repository.markPrepared(file.id, result.backend)
                            tracker.set(file.id, null)
                        }
                        CompatibilityResult.Incompatible -> tracker.set(file.id, LocalModelWork.Rejected(SetupProblem.DeviceIncompatible))
                    }
                } catch (e: CancellationException) {
                    tracker.set(file.id, null)
                    throw e
                }
            }
        } finally {
            mutex.unlock()
        }
    }
}

/** «Reintentar» on a failed row: forget the failure and run the download queue again (FR-019). */
class RetryModelUseCase(
    private val repository: ModelFileRepository,
    private val controller: ModelDownloadController,
    private val store: OnboardingStore,
    private val tracker: LocalModelWorkTracker,
) {
    suspend operator fun invoke(id: ModelId) {
        tracker.set(id, null)
        repository.refresh()
        store.requestDownload(setOf(id))
        controller.schedule(pendingDownloads(repository, controller).sumOf { it.second })
    }
}

/** «Seleccionar archivo del dispositivo» (FR-024): copies and verifies a picked file as [id]. */
class ImportModelFileUseCase(
    private val repository: ModelFileRepository,
    private val source: ModelFileSource,
    private val tracker: LocalModelWorkTracker,
    private val prepare: PrepareModelsUseCase,
    private val applicationScope: ApplicationScope,
) {
    fun launch(id: ModelId, uri: String) {
        applicationScope.scope.launch { import(id, uri) }
    }

    suspend fun import(id: ModelId, uri: String) {
        val file = ModelCatalog.byId(id)
        if (source.sizeOf(uri)?.let { it != file.sizeBytes } == true) {
            // Not the expected model: nothing is copied (contracts/onboarding-ui.md).
            tracker.set(id, LocalModelWork.Rejected(SetupProblem.WrongFile))
            return
        }
        tracker.set(id, LocalModelWork.Importing(0, file.sizeBytes))
        val state = try {
            source.open(uri).use { input ->
                repository.installFromStream(id, CountingStream(input) { tracker.set(id, LocalModelWork.Importing(it, file.sizeBytes)) })
            }
        } catch (e: CancellationException) {
            tracker.set(id, null)
            throw e
        } catch (_: IOException) {
            ModelFileState.Failed("unreadable")
        } catch (_: SecurityException) {
            ModelFileState.Failed("no access")
        }
        when (state) {
            is ModelFileState.Verified -> {
                tracker.set(id, null)
                prepare.prepareAll()
            }
            is ModelFileState.Mismatch -> tracker.set(id, LocalModelWork.Rejected(SetupProblem.WrongFile))
            else -> tracker.set(id, LocalModelWork.Rejected(SetupProblem.Storage))
        }
    }

    /** Reports bytes read about every 0.5 % of the expected size. */
    private class CountingStream(input: InputStream, private val onBytes: (Long) -> Unit) : FilterInputStream(input) {
        private var count = 0L
        private var reported = 0L

        override fun read(): Int = super.read().also { if (it >= 0) advance(1) }

        override fun read(b: ByteArray, off: Int, len: Int): Int = super.read(b, off, len).also { if (it > 0) advance(it.toLong()) }

        private fun advance(bytes: Long) {
            count += bytes
            if (count - reported >= REPORT_BYTES) {
                reported = count
                onBytes(count)
            }
        }

        private companion object {
            const val REPORT_BYTES = 8L * 1024 * 1024
        }
    }
}

/** Private folders and free space for the storage card (FR-025). */
class PrepareStorageUseCase(
    private val repository: ModelFileRepository,
    private val controller: ModelDownloadController,
    private val resources: DeviceResources,
) {
    operator fun invoke(): StorageStatus {
        if (!resources.prepareStorage()) return StorageStatus.NoSpace(ModelSetupPolicy.SPACE_MARGIN_BYTES)
        val needed = pendingDownloads(repository, controller).sumOf { it.second } + ModelSetupPolicy.SPACE_MARGIN_BYTES
        val free = resources.freeBytes()
        return if (free < needed) StorageStatus.NoSpace(needed - free) else StorageStatus.Offline
    }
}

/** «Continuar»: only with both models ready (FR-020, FR-021); later launches open Home. */
class CompleteOnboardingUseCase(
    private val repository: ModelFileRepository,
    private val store: OnboardingStore,
) {
    suspend operator fun invoke(): Boolean {
        val ready = ModelCatalog.required.all {
            repository.states.value[it.id] is ModelFileState.Verified && it.id in repository.preparedBackends.value
        }
        if (ready) store.setStage(OnboardingStage.COMPLETED)
        return ready
    }
}
