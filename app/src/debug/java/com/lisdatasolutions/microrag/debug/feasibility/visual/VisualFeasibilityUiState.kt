package com.lisdatasolutions.microrag.debug.feasibility.visual

import com.lisdatasolutions.microrag.debug.feasibility.ModelRow
import com.lisdatasolutions.microrag.debug.feasibility.ThermalLevel
import com.lisdatasolutions.microrag.debug.feasibility.visual.report.DeviceSetSummary
import com.lisdatasolutions.microrag.debug.feasibility.visual.report.VisualScenario
import com.lisdatasolutions.microrag.domain.model.ModelFileState
import com.lisdatasolutions.microrag.domain.model.ModelId

/** The files the visual diagnostic uses (FR-002). */
val VISUAL_MODELS = listOf(ModelId.GEMMA4_E2B, ModelId.EG2_440M, ModelId.EG2_270M)

data class VisualFeasibilityUiState(
    val models: List<ModelRow>,
    val modelsBusy: Boolean = false,
    val deviceSet: DeviceSetSummary = DeviceSetSummary(present = false, verified = 0, rejected = emptyList()),
    val isRunning: Boolean = false,
    val progress: VisualProgress? = null,
    val results: List<VisualScenario> = emptyList(),
    val lastReportPath: String? = null,
    /** Set while the suite waits for the user to confirm measuring on a hot device. */
    val thermalPrompt: ThermalLevel? = null,
    val runError: String? = null,
) {
    private val idle: Boolean get() = !modelsBusy && !isRunning

    val canImport: Boolean get() = idle && models.any { it.devCopyBytes != null }

    val canReload: Boolean get() = idle

    /** A block can run when its files are verified; one without them says why in its result instead. */
    fun canRun(block: VisualBlock): Boolean = idle && when (block) {
        VisualBlock.GEMMA_INFO -> verified(ModelId.GEMMA4_E2B) || verified(ModelId.EG2_440M)
        VisualBlock.EMBED_TEXT -> verified(ModelId.EG2_440M) || verified(ModelId.EG2_270M)
        VisualBlock.EMBED_IMAGE, VisualBlock.PARITY, VisualBlock.PROJECTION -> verified(ModelId.EG2_440M) && deviceSet.present
        VisualBlock.RASTER, VisualBlock.IMAGE_DECODE -> deviceSet.present
        VisualBlock.GEMMA_PAGES, VisualBlock.READING -> verified(ModelId.GEMMA4_E2B) && deviceSet.present
    }

    val canRunAll: Boolean get() = VisualBlock.entries.any(::canRun)

    private fun verified(id: ModelId) = models.any { it.file.id == id && it.state is ModelFileState.Verified }
}
