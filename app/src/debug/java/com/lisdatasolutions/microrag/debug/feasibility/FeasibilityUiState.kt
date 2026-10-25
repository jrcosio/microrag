package com.lisdatasolutions.microrag.debug.feasibility

import com.lisdatasolutions.microrag.domain.model.ModelFamily
import com.lisdatasolutions.microrag.domain.model.ModelFile
import com.lisdatasolutions.microrag.domain.model.ModelFileState

/** A catalog file as shown by the diagnostics; [devCopyBytes] is set while a copy awaits import. */
data class ModelRow(
    val file: ModelFile,
    val state: ModelFileState,
    val devCopyBytes: Long? = null,
)

data class FeasibilityUiState(
    val models: List<ModelRow>,
    /** Importing or verifying model files. */
    val modelsBusy: Boolean = false,
) {
    val canImport: Boolean get() = !modelsBusy && models.any { it.devCopyBytes != null }

    val canRunEmbeddings: Boolean get() = !modelsBusy && hasVerified(ModelFamily.EMBEDDING)

    val canRunGeneration: Boolean get() = !modelsBusy && hasVerified(ModelFamily.LLM)

    val noModelsAtAll: Boolean
        get() = models.all { it.state == ModelFileState.Missing && it.devCopyBytes == null }

    private fun hasVerified(family: ModelFamily) =
        models.any { it.file.family == family && it.state is ModelFileState.Verified }
}
