package com.lisdatasolutions.microrag.feature.settings

import androidx.compose.runtime.Composable
import com.lisdatasolutions.microrag.core.designsystem.preview.LisdataPreviews
import com.lisdatasolutions.microrag.core.designsystem.theme.LisdataTheme
import com.lisdatasolutions.microrag.domain.model.ComputeBackend
import com.lisdatasolutions.microrag.domain.model.ModelCatalog
import com.lisdatasolutions.microrag.domain.model.ModelId
import com.lisdatasolutions.microrag.domain.model.ModelSetupStatus
import com.lisdatasolutions.microrag.domain.rag.RagParameters
import com.lisdatasolutions.microrag.domain.settings.AppLanguage
import com.lisdatasolutions.microrag.domain.settings.LanguageSettings
import com.lisdatasolutions.microrag.domain.settings.ModelOverview
import com.lisdatasolutions.microrag.domain.settings.ThemePreference
import com.lisdatasolutions.microrag.domain.settings.ThemeSettings

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

    /** S2: the sheet of Gemma open while it is not set up. */
    val sheet = SettingsUiState(models = listOf(unconfiguredGemma, embeddingCpu), openSheet = ModelId.GEMMA4_E2B)

    /** S6: the confirmation with an answer in progress. */
    val confirmClear = png.copy(confirmClear = ConfirmClear(stopsAnswer = true))

    /** S6: the notice after clearing. */
    val cleared = png.copy(message = SettingsMessage.HISTORY_CLEARED)

    val all: List<Pair<String, SettingsUiState>> = listOf(
        "png" to png,
        "opening" to opening,
        "degraded" to degraded,
        "sheet" to sheet,
        "confirmClear" to confirmClear,
        "cleared" to cleared,
    )
}

/** Labelled sample states of Tema for previews and UI tests only (constitution V; F16 wireframe §3–§4). */
object ThemePreviewData {

    /** «Sistema», saved and marked. */
    val system = ThemeUiState(saved = ThemeSettings(ThemePreference.SYSTEM), selected = ThemePreference.SYSTEM)

    val opening = ThemeUiState()

    /** «Oscuro» marked, not saved yet. */
    val dark = system.copy(selected = ThemePreference.DARK)

    /** A3: saving. */
    val saving = dark.copy(saving = true)

    /** A5: the stored copy could not be read. */
    val readFailed = ThemeUiState(saved = ThemeSettings(ThemePreference.SYSTEM, readFailed = true), selected = ThemePreference.SYSTEM)

    /** A4: the save failed. */
    val saveFailed = dark.copy(saveFailed = true)

    val all: List<Pair<String, ThemeUiState>> = listOf(
        "system" to system,
        "opening" to opening,
        "dark" to dark,
        "saving" to saving,
        "readFailed" to readFailed,
        "saveFailed" to saveFailed,
    )
}

/** Labelled sample states of Idiomas for previews and UI tests only (constitution V). */
object LanguagesPreviewData {

    /** PNG 10, with «Español» as the only answer language (S12). */
    val png = LanguagesUiState(saved = LanguageSettings(AppLanguage.SPANISH), selectedApp = AppLanguage.SPANISH)

    val opening = LanguagesUiState()

    /** English marked, not saved yet. */
    val english = png.copy(selectedApp = AppLanguage.ENGLISH)

    /** S8: saving. */
    val saving = english.copy(saving = true)

    /** S8: the stored choice could not be read. */
    val readFailed = LanguagesUiState(saved = LanguageSettings(AppLanguage.SPANISH, readFailed = true), selectedApp = AppLanguage.SPANISH)

    /** S8: the save failed. */
    val saveFailed = english.copy(saveFailed = true)

    val all: List<Pair<String, LanguagesUiState>> = listOf(
        "png" to png,
        "opening" to opening,
        "english" to english,
        "saving" to saving,
        "readFailed" to readFailed,
        "saveFailed" to saveFailed,
    )
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

@LisdataPreviews
@Composable
private fun LanguagesPngPreview() = LisdataTheme { LanguagesScreen(LanguagesPreviewData.png, LanguagesActions()) }

@LisdataPreviews
@Composable
private fun LanguagesSavingPreview() = LisdataTheme { LanguagesScreen(LanguagesPreviewData.saving, LanguagesActions()) }

@LisdataPreviews
@Composable
private fun LanguagesReadFailedPreview() = LisdataTheme { LanguagesScreen(LanguagesPreviewData.readFailed, LanguagesActions()) }

@LisdataPreviews
@Composable
private fun ThemeSystemPreview() = LisdataTheme { ThemeScreen(ThemePreviewData.system, ThemeActions()) }

@LisdataPreviews
@Composable
private fun ThemeSavingPreview() = LisdataTheme { ThemeScreen(ThemePreviewData.saving, ThemeActions()) }

@LisdataPreviews
@Composable
private fun ThemeReadFailedPreview() = LisdataTheme { ThemeScreen(ThemePreviewData.readFailed, ThemeActions()) }
