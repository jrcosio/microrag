package com.lisdatasolutions.microrag.domain.multimodal

import com.lisdatasolutions.microrag.domain.model.ModelCatalog
import com.lisdatasolutions.microrag.domain.model.ModelFile
import com.lisdatasolutions.microrag.domain.model.ModelId
import com.lisdatasolutions.microrag.domain.rag.IndexCompatibility
import com.lisdatasolutions.microrag.domain.rag.RagIndex
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf

/** The models the app needs at this moment (F19 research R5, contract M6). */
data class RequiredModels(
    /** The text embedder the index records: the 270M, or the 440M once the multimodal mode switched to it. */
    val textEmbedder: ModelId,
    /** The mode is being turned on: its 440M is downloaded, checked and switched to. */
    val activating: Boolean = false,
) {
    /** What the app needs to answer, in download order (F03 FR-014): what completes onboarding and lights the Home chip. */
    val core: List<ModelFile> = listOf(textEmbedder, ModelId.GEMMA4_E2B).map(ModelCatalog::byId)

    /** What is downloaded, kept and prepared: [core], then the 440M while [activating]. */
    val files: List<ModelFile> =
        core + listOfNotNull(ModelCatalog.byId(ModelId.EG2_440M).takeIf { activating && textEmbedder != ModelId.EG2_440M })

    /** The model rows of Preparación and Ajustes, answers first as in PNG 02 and 09 (contract U1). */
    val shown: List<ModelId> = listOf(ModelId.GEMMA4_E2B, textEmbedder)

    companion object {
        /** A new install (FR-008): the 270M and Gemma, as F03 sets them up. */
        val FIRST_RUN = RequiredModels(ModelId.EG2_270M)
    }
}

/** The live list that replaces the fixed `ModelCatalog.required` (research R5). */
interface ModelRequirements {
    /** Emits once the mode's copy has been read, so nothing is decided on a default. */
    fun required(): Flow<RequiredModels>

    suspend fun current(): RequiredModels = required().first()

    /** A new install's models, for the screens and tests that never see the mode. */
    object FirstRun : ModelRequirements {
        override fun required(): Flow<RequiredModels> = flowOf(RequiredModels.FIRST_RUN)
    }
}

/**
 * The text embedder of the open index, or the mode's mirror before it opens (analyze H3), so a 270M deleted
 * after the switch is never asked for again; plus the 440M while the mode is being turned on.
 */
class DefaultModelRequirements(
    private val index: RagIndex,
    private val store: MultimodalStore,
) : ModelRequirements {
    override fun required(): Flow<RequiredModels> =
        combine(index.compatibility, index.textEmbedder, store.state) { compatibility, embedder, stored ->
            val text = if (compatibility == IndexCompatibility.Compatible) embedder else stored.textEmbedder
            RequiredModels(text, stored.stage.activating)
        }.distinctUntilChanged()
}
