package com.lisdatasolutions.microrag.debug.feasibility

import com.lisdatasolutions.microrag.debug.feasibility.report.ExitCriteria
import com.lisdatasolutions.microrag.debug.feasibility.report.ScenarioResult
import com.lisdatasolutions.microrag.debug.feasibility.report.exitCriteriaOf
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
    val isRunning: Boolean = false,
    val progress: SuiteProgress? = null,
    val results: List<ScenarioResult> = emptyList(),
    val lastReportPath: String? = null,
    /** Set while the suite waits for the user to confirm measuring on a hot device (FR-015). */
    val thermalPrompt: ThermalLevel? = null,
    val runError: String? = null,
) {
    private val idle: Boolean get() = !modelsBusy && !isRunning

    val canImport: Boolean get() = idle && models.any { it.devCopyBytes != null }

    val canVerify: Boolean get() = idle

    val canRunEmbeddings: Boolean get() = idle && hasVerified(ModelFamily.EMBEDDING)

    val canRunGeneration: Boolean get() = idle && hasVerified(ModelFamily.LLM)

    val canRunAll: Boolean get() = canRunEmbeddings || canRunGeneration

    val exitCriteria: ExitCriteria get() = exitCriteriaOf(results)

    val noModelsAtAll: Boolean
        get() = models.all { it.state == ModelFileState.Missing && it.devCopyBytes == null }

    private fun hasVerified(family: ModelFamily) =
        models.any { it.file.family == family && it.state is ModelFileState.Verified }
}
