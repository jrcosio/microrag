package com.lisdatasolutions.microrag.domain.settings

import com.lisdatasolutions.microrag.domain.model.ModelCatalog
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
        emitAll(setup().map { statuses -> ORDER.map { id -> overview(id, statuses[id] ?: ModelSetupStatus.Unconfigured()) } })
    }.distinctUntilChanged()

    private fun overview(id: ModelId, status: ModelSetupStatus): ModelOverview {
        val file = ModelCatalog.byId(id)
        return ModelOverview(
            id = id,
            displayName = file.displayName,
            fileName = file.fileName,
            sizeBytes = file.sizeBytes,
            sha256 = file.sha256,
            dimensions = if (id == ModelId.EG2_270M) RagParameters.DIMS else null,
            status = status,
        )
    }

    private companion object {
        val ORDER = listOf(ModelId.GEMMA4_E2B, ModelId.EG2_270M)
    }
}
