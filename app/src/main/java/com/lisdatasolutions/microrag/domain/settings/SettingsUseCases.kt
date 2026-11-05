package com.lisdatasolutions.microrag.domain.settings

import com.lisdatasolutions.microrag.domain.model.ModelCatalog
import com.lisdatasolutions.microrag.domain.model.ModelFamily
import com.lisdatasolutions.microrag.domain.model.ModelId
import com.lisdatasolutions.microrag.domain.model.ModelSetupStatus
import com.lisdatasolutions.microrag.domain.onboarding.ObserveModelSetupUseCase
import com.lisdatasolutions.microrag.domain.onboarding.RefreshLocalAiUseCase
import com.lisdatasolutions.microrag.domain.rag.RagParameters
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

/**
 * The model rows of Ajustes (F12 P13): the catalog data with exactly the Preparation's state, answers
 * first as in PNG 09. Like the Home chip, the files are rescanned first, so nothing is shown from a
 * stale record (constitution V).
 */
class ObserveModelOverviewUseCase(
    private val setup: ObserveModelSetupUseCase,
    private val refresh: RefreshLocalAiUseCase,
) {
    operator fun invoke(): Flow<List<ModelOverview>> = flow {
        refresh()
        // F19 (contract U1): Gemma and the text embedder the index uses, the 270M or the 440M.
        emitAll(setup.rows().map { rows -> rows.map { (id, status) -> overview(id, status) } })
    }.distinctUntilChanged()

    private fun overview(id: ModelId, status: ModelSetupStatus): ModelOverview {
        val file = ModelCatalog.byId(id)
        return ModelOverview(
            id = id,
            displayName = file.displayName,
            fileName = file.fileName,
            sizeBytes = file.sizeBytes,
            sha256 = file.sha256,
            dimensions = if (file.family == ModelFamily.EMBEDDING) RagParameters.DIMS else null,
            status = status,
        )
    }
}

/** What Idiomas opens with (F12 P1): the app language of this moment and whether its file was unreadable (S8). */
class ObserveLanguageSettingsUseCase(private val controller: AppLanguageController) {
    operator fun invoke(): Flow<LanguageSettings> = flow { emit(LanguageSettings(controller.current(), controller.readFailed)) }
}

/**
 * «Guardar preferencias» (F12 P6, R4). Only the app language is a preference (D-061): unchanged writes
 * nothing; a failed apply changes nothing; a saved one leaves «Preferencias guardadas» for the shell to
 * announce after the recreation (P7).
 */
class SaveLanguageSettingsUseCase(
    private val controller: AppLanguageController,
    private val notices: SettingsNotices,
) {
    /** True from Android 13: the system recreates the screen; below it, the screen does. */
    val recreatesItself: Boolean get() = controller.recreatesItself

    suspend operator fun invoke(app: AppLanguage): SaveResult {
        if (app == controller.current()) return SaveResult.Unchanged
        if (!controller.apply(app)) return SaveResult.Failed
        notices.announceSaved()
        return SaveResult.Saved(appLanguageChanged = true)
    }
}
