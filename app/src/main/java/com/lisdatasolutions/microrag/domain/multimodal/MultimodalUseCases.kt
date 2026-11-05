package com.lisdatasolutions.microrag.domain.multimodal

import com.lisdatasolutions.microrag.domain.model.ModelCatalog
import com.lisdatasolutions.microrag.domain.model.ModelId
import com.lisdatasolutions.microrag.domain.onboarding.DeviceResources
import com.lisdatasolutions.microrag.domain.onboarding.ModelDownloadController
import com.lisdatasolutions.microrag.domain.onboarding.ModelSetupPolicy
import com.lisdatasolutions.microrag.domain.onboarding.OnboardingStore
import com.lisdatasolutions.microrag.domain.onboarding.PauseModelDownloadUseCase
import com.lisdatasolutions.microrag.domain.onboarding.ResumeModelDownloadUseCase
import com.lisdatasolutions.microrag.domain.rag.IndexMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** What «Activar» did (contract M3). */
sealed interface EnableMultimodalResult {
    /** The 440M is being downloaded (or checked, if it was already here). */
    data object Started : EnableMultimodalResult

    /** Turned on again at once: the index already uses the 440M (D-079). */
    data object On : EnableMultimodalResult

    /** Not enough room for the 440M and the margin; the stage shows it with Reintentar and Cancelar. */
    data class NoSpace(val missingBytes: Long) : EnableMultimodalResult
}

/**
 * The multimodal mode's activation (F19 research R1, contract M2): one instance for the app, which moves
 * `DOWNLOADING → VALIDATING → SWITCHING → ON` by itself and takes the stage up again after a closed app.
 */
interface MultimodalController {
    /** Called from `Application.onCreate`: follows the saved stage from then on. */
    fun start()

    suspend fun enable(): EnableMultimodalResult

    /** «Desactivar» (contract M4): back to OFF, keeping pages and models; while downloading, it cancels. */
    suspend fun disable()

    /** «Reintentar» after a download failure or missing space; stays in DOWNLOADING. */
    suspend fun retry(): EnableMultimodalResult

    /** «Cancelar» while downloading: deletes the partial file and goes back to OFF. */
    suspend fun cancelDownload()
}

/** The on-device identity check of research R3, run by [ValidateIdentityUseCase]. */
fun interface IdentityChecker {
    suspend fun check(): IdentityCheck
}

/** Contract M5: the 20 sample texts with both models on the CPU; passes with a minimum cosine of 0.9999. */
class ValidateIdentityUseCase(private val checker: IdentityChecker) {
    suspend operator fun invoke(): IdentityCheck = checker.check()
}

/** What Ajustes shows: the stage, its error, the default mode and the 440M's download. */
class ObserveMultimodalUseCase(
    private val store: MultimodalStore,
    private val downloads: ModelDownloadController,
    private val onboarding: OnboardingStore,
    private val resources: DeviceResources,
) {
    operator fun invoke(): Flow<MultimodalState> =
        combine(store.state, downloads.progress, onboarding.downloadPaused, resources.hasInternet) { stored, progress, paused, online ->
            val downloading = stored.stage == MultimodalStage.DOWNLOADING
            val model = ModelCatalog.byId(ModelId.EG2_440M)
            MultimodalState(
                stage = stored.stage,
                error = stored.error,
                defaultMode = stored.defaultMode,
                pageDetail = stored.pageDetail,
                download = progress.takeIf { downloading && it.active == ModelId.EG2_440M },
                downloadPaused = downloading && paused,
                downloadBytes = model.sizeBytes,
                offline = downloading && !online,
                missingBytes = if (downloading && stored.error == MultimodalError.NO_SPACE) {
                    val needed = model.sizeBytes - downloads.partialBytes(model.id) + ModelSetupPolicy.SPACE_MARGIN_BYTES
                    (needed - resources.freeBytes()).coerceAtLeast(0)
                } else {
                    null
                },
            )
        }.distinctUntilChanged()
}

/** «Aceptar» on a failed activation (wireframe §2): the reason goes, the mode stays off. */
class DismissMultimodalErrorUseCase(private val store: MultimodalStore) {
    suspend operator fun invoke(): Boolean = store.write { if (it.stage == MultimodalStage.OFF) it.copy(error = null) else it }
}

class EnableMultimodalUseCase(private val controller: MultimodalController) {
    suspend operator fun invoke(): EnableMultimodalResult = controller.enable()
}

class DisableMultimodalUseCase(private val controller: MultimodalController) {
    suspend operator fun invoke() = controller.disable()
}

class RetryMultimodalUseCase(private val controller: MultimodalController) {
    suspend operator fun invoke(): EnableMultimodalResult = controller.retry()
}

class CancelMultimodalDownloadUseCase(private val controller: MultimodalController) {
    suspend operator fun invoke() = controller.cancelDownload()
}

/** «Pausar» on the 440M's download: F03's, the partial file is kept. */
class PauseMultimodalDownloadUseCase(private val pause: PauseModelDownloadUseCase) {
    suspend operator fun invoke() = pause()
}

/** «Reanudar» on the 440M's download: F03's, from where it stopped. */
class ResumeMultimodalDownloadUseCase(private val resume: ResumeModelDownloadUseCase) {
    suspend operator fun invoke() = resume()
}

/** «Modo al importar PDF» (contract U6): false when the copy could not be written. */
class SetDefaultImportModeUseCase(private val store: MultimodalStore) {
    suspend operator fun invoke(mode: IndexMode): Boolean = store.write { it.copy(defaultMode = mode) }
}

/** F20 (contract M2): saves the page detail; false keeps the previous one. */
class SetPageDetailUseCase(private val store: MultimodalStore) {
    suspend operator fun invoke(detail: PageDetail): Boolean = store.write { it.copy(pageDetail = detail) }
}

/** What «Detalle de las páginas» needs (contract U2): whether the mode is on, and the saved detail. */
data class PageDetailSettings(val on: Boolean = false, val detail: PageDetail = PageDetail.BALANCED)

class ObservePageDetailUseCase(private val store: MultimodalStore) {
    operator fun invoke(): Flow<PageDetailSettings> =
        store.state.map { PageDetailSettings(it.stage == MultimodalStage.ON, it.pageDetail) }.distinctUntilChanged()
}

/** What the Ingesta needs of the mode (contract U8): whether it is on, and Ajustes' «Modo al importar PDF». */
data class ImportModeSettings(val on: Boolean = false, val defaultMode: IndexMode = IndexMode.BOTH)

class ObserveImportModeUseCase(private val store: MultimodalStore) {
    operator fun invoke(): Flow<ImportModeSettings> =
        store.state.map { ImportModeSettings(it.stage == MultimodalStage.ON, it.defaultMode) }.distinctUntilChanged()
}
