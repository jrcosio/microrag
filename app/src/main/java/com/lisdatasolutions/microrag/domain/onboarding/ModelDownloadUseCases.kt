package com.lisdatasolutions.microrag.domain.onboarding

import com.lisdatasolutions.microrag.data.models.ModelFileRepository
import com.lisdatasolutions.microrag.domain.model.ModelCatalog
import com.lisdatasolutions.microrag.domain.model.ModelFile
import com.lisdatasolutions.microrag.domain.model.ModelFileState
import com.lisdatasolutions.microrag.domain.model.ModelId
import com.lisdatasolutions.microrag.domain.model.ModelSetupStatus
import com.lisdatasolutions.microrag.domain.model.NetworkKind
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged

/** Rows of the setup screen, recomputed whenever any source changes (data-model §2). */
class ObserveModelSetupUseCase(
    private val repository: ModelFileRepository,
    private val controller: ModelDownloadController,
    private val store: OnboardingStore,
    private val resources: DeviceResources,
    private val localWork: LocalModelWorkTracker,
) {
    operator fun invoke(): Flow<Map<ModelId, ModelSetupStatus>> {
        val files = combine(repository.states, repository.preparedBackends, localWork.work) { states, prepared, work ->
            Triple(states, prepared, work)
        }
        val intent = combine(store.downloadRequested, store.downloadPaused, resources.hasInternet) { requested, paused, online ->
            Triple(requested, paused, online)
        }
        return combine(files, controller.progress, intent) { (states, prepared, work), progress, (requested, paused, online) ->
            val snapshot = SetupSnapshot(
                files = states,
                preparedBackends = prepared,
                partialBytes = ModelCatalog.required.associate { it.id to controller.partialBytes(it.id) },
                download = progress,
                localWork = work,
                requested = requested,
                paused = paused,
                hasInternet = online,
            )
            ModelSetupStatusResolver.resolve(ModelCatalog.required, snapshot)
        }.distinctUntilChanged()
    }
}

/** What the confirmation dialog shows before the first download (FR-009, G31). */
data class DownloadConfirmation(
    val models: List<Pair<ModelId, Long>>,
    val totalBytes: Long,
    val freeBytes: Long,
    val freeAfterBytes: Long,
    val network: NetworkKind,
    val lowRam: Boolean,
    /** Bytes missing when free space does not cover the download plus the margin; null when it fits. */
    val missingBytes: Long?,
)

/** Required models that still need bytes: not verified yet, with what is already downloaded subtracted. */
internal fun pendingDownloads(repository: ModelFileRepository, controller: ModelDownloadController): List<Pair<ModelFile, Long>> =
    ModelCatalog.required
        .filter { repository.states.value[it.id] !is ModelFileState.Verified }
        .map { it to (it.sizeBytes - controller.partialBytes(it.id)).coerceAtLeast(0) }

class BuildDownloadConfirmationUseCase(
    private val repository: ModelFileRepository,
    private val controller: ModelDownloadController,
    private val resources: DeviceResources,
) {
    operator fun invoke(): DownloadConfirmation {
        val pending = pendingDownloads(repository, controller)
        val total = pending.sumOf { it.second }
        val free = resources.freeBytes()
        val needed = total + ModelSetupPolicy.SPACE_MARGIN_BYTES
        return DownloadConfirmation(
            models = pending.map { (file, _) -> file.id to file.sizeBytes },
            totalBytes = total,
            freeBytes = free,
            freeAfterBytes = (free - total).coerceAtLeast(0),
            network = resources.currentNetwork(),
            lowRam = resources.totalRamBytes() < DeviceResources.LOW_RAM_THRESHOLD_BYTES,
            missingBytes = (needed - free).takeIf { it > 0 },
        )
    }
}

sealed interface StartDownloadResult {
    data object Started : StartDownloadResult

    data class NoSpace(val missingBytes: Long) : StartDownloadResult
}

/** «Descargar»: records the request and schedules the background queue (FR-012, FR-016). */
class StartModelDownloadUseCase(
    private val repository: ModelFileRepository,
    private val controller: ModelDownloadController,
    private val resources: DeviceResources,
    private val store: OnboardingStore,
    private val tracker: LocalModelWorkTracker,
) {
    suspend operator fun invoke(): StartDownloadResult {
        val pending = pendingDownloads(repository, controller)
        val bytes = pending.sumOf { it.second }
        val missing = bytes + ModelSetupPolicy.SPACE_MARGIN_BYTES - resources.freeBytes()
        if (missing > 0) return StartDownloadResult.NoSpace(missing)
        // A downloaded file replaces any rejected import for the same model.
        pending.forEach { (file, _) -> tracker.set(file.id, null) }
        store.requestDownload(pending.map { it.first.id }.toSet())
        controller.schedule(bytes)
        return StartDownloadResult.Started
    }
}

/** «Detener» (FR-018): the partial file is kept. */
class PauseModelDownloadUseCase(
    private val store: OnboardingStore,
    private val controller: ModelDownloadController,
) {
    suspend operator fun invoke() {
        store.setPaused(true)
        controller.cancel()
    }
}

class ResumeModelDownloadUseCase(
    private val repository: ModelFileRepository,
    private val store: OnboardingStore,
    private val controller: ModelDownloadController,
) {
    suspend operator fun invoke() {
        store.setPaused(false)
        controller.schedule(pendingDownloads(repository, controller).sumOf { it.second })
    }
}
