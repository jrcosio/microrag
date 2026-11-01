package com.lisdatasolutions.microrag.feature.settings

import androidx.compose.runtime.Composable
import com.lisdatasolutions.microrag.core.designsystem.preview.LisdataPreviews
import com.lisdatasolutions.microrag.core.designsystem.theme.LisdataTheme
import com.lisdatasolutions.microrag.domain.model.ComputeBackend
import com.lisdatasolutions.microrag.domain.model.ModelCatalog
import com.lisdatasolutions.microrag.domain.model.ModelId
import com.lisdatasolutions.microrag.domain.model.ModelSetupStatus
import com.lisdatasolutions.microrag.domain.rag.RagParameters
import com.lisdatasolutions.microrag.domain.settings.ModelOverview

/** Labelled sample states for previews and UI tests only (constitution V): never shown in the app. */
object SettingsPreviewData {

    private fun overview(id: ModelId, status: ModelSetupStatus): ModelOverview = ModelCatalog.byId(id).let {
        ModelOverview(id, it.displayName, it.fileName, it.sizeBytes, it.sha256, if (id == ModelId.EG2_270M) RagParameters.DIMS else null, status)
    }

    private val gemmaGpu = overview(ModelId.GEMMA4_E2B, ModelSetupStatus.Ready(ComputeBackend.GPU, ModelCatalog.byId(ModelId.GEMMA4_E2B).sizeBytes))
    private val embeddingCpu = overview(ModelId.EG2_270M, ModelSetupStatus.Ready(ComputeBackend.CPU, ModelCatalog.byId(ModelId.EG2_270M).sizeBytes))

    /** PNG 09: both models ready on the device. */
    val png = SettingsUiState(models = listOf(gemmaGpu, embeddingCpu))

    /** Just opened: the files are being rescanned, so the model rows show no state. */
    val opening = SettingsUiState()

    /** S1: Gemma only on CPU, and the embedding model being tested on the device. */
    val degraded = SettingsUiState(
        models = listOf(
            overview(ModelId.GEMMA4_E2B, ModelSetupStatus.Ready(ComputeBackend.CPU, ModelCatalog.byId(ModelId.GEMMA4_E2B).sizeBytes)),
            overview(ModelId.EG2_270M, ModelSetupStatus.Preparing),
        ),
    )

    /** S2: the sheet of a model that is not set up. */
    val unconfiguredGemma = overview(ModelId.GEMMA4_E2B, ModelSetupStatus.Unconfigured())

    val readyEmbedding = embeddingCpu
}

@LisdataPreviews
@Composable
private fun SettingsPngPreview() = LisdataTheme { SettingsScreen(SettingsPreviewData.png, SettingsActions()) }

@LisdataPreviews
@Composable
private fun SettingsOpeningPreview() = LisdataTheme { SettingsScreen(SettingsPreviewData.opening, SettingsActions()) }

@LisdataPreviews
@Composable
private fun SettingsDegradedPreview() = LisdataTheme { SettingsScreen(SettingsPreviewData.degraded, SettingsActions()) }

@LisdataPreviews
@Composable
private fun ModelSheetReadyPreview() = LisdataTheme { ModelSheetContent(SettingsPreviewData.readyEmbedding, onCompleteSetup = {}) }

@LisdataPreviews
@Composable
private fun ModelSheetUnconfiguredPreview() = LisdataTheme { ModelSheetContent(SettingsPreviewData.unconfiguredGemma, onCompleteSetup = {}) }
