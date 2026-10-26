package com.lisdatasolutions.microrag.feature.onboarding.setup

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.lisdatasolutions.microrag.core.designsystem.preview.LisdataPreviews
import com.lisdatasolutions.microrag.core.designsystem.theme.LisdataTheme
import com.lisdatasolutions.microrag.domain.model.ComputeBackend
import com.lisdatasolutions.microrag.domain.model.ModelId
import com.lisdatasolutions.microrag.domain.model.ModelSetupStatus
import com.lisdatasolutions.microrag.domain.model.NetworkKind
import com.lisdatasolutions.microrag.domain.model.SetupProblem
import com.lisdatasolutions.microrag.domain.model.SetupStepResolver
import com.lisdatasolutions.microrag.domain.model.StorageStatus
import com.lisdatasolutions.microrag.domain.onboarding.DownloadConfirmation

/** Labelled sample states for previews and UI tests only (constitution V): never shown in the app. */
object SetupPreviewData {
    private const val GEMMA = 2_588_147_712L
    private const val EMBEDDING = 164_626_432L

    fun state(
        gemma: ModelSetupStatus,
        embedding: ModelSetupStatus,
        storage: StorageStatus = StorageStatus.Offline,
        confirmation: ConfirmationUi? = null,
    ): SetupUiState {
        val statuses = listOf(gemma, embedding)
        return SetupUiState(
            step = SetupStepResolver.step(statuses),
            rows = listOf(ModelRowUi(ModelId.GEMMA4_E2B, gemma), ModelRowUi(ModelId.EG2_270M, embedding)),
            storage = storage,
            blocker = SetupStepResolver.blocker(statuses, storage),
            confirmation = confirmation,
        )
    }

    val notConfigured = state(ModelSetupStatus.Unconfigured(), ModelSetupStatus.Unconfigured())
    val downloading = state(
        ModelSetupStatus.Queued(after = ModelId.EG2_270M, bytesDone = 0, totalBytes = GEMMA),
        ModelSetupStatus.Downloading(bytesDone = 69_000_000, totalBytes = EMBEDDING),
    )
    val pausedAndWaiting = state(
        ModelSetupStatus.Paused(bytesDone = 880_000_000, totalBytes = GEMMA),
        ModelSetupStatus.WaitingForNetwork(bytesDone = 12_000_000, totalBytes = EMBEDDING),
    )
    val verifyingAndPreparing = state(
        ModelSetupStatus.Verifying(bytesDone = 1_200_000_000, totalBytes = GEMMA),
        ModelSetupStatus.Preparing,
    )
    val ready = state(ModelSetupStatus.Ready(ComputeBackend.GPU, GEMMA), ModelSetupStatus.Ready(ComputeBackend.CPU, EMBEDDING))
    val readyCpuWithoutSpace = state(
        ModelSetupStatus.Ready(ComputeBackend.CPU, GEMMA),
        ModelSetupStatus.Ready(ComputeBackend.CPU, EMBEDDING),
        storage = StorageStatus.NoSpace(200_000_000),
    )
    val problems = state(
        ModelSetupStatus.Failed(SetupProblem.HashMismatch),
        ModelSetupStatus.Incompatible(SetupProblem.WrongFile),
    )
    val noSpaceAndImporting = state(
        ModelSetupStatus.NoSpace(neededBytes = 1_400_000_000),
        ModelSetupStatus.Importing(bytesDone = 80_000_000, totalBytes = EMBEDDING),
        storage = StorageStatus.NoSpace(1_400_000_000),
    )
    val failedDownload = state(
        ModelSetupStatus.Failed(SetupProblem.DownloadFailed(503)),
        ModelSetupStatus.Incompatible(SetupProblem.DeviceIncompatible),
    )
    val confirming = notConfigured.copy(
        confirmation = ConfirmationUi(
            target = ModelId.GEMMA4_E2B,
            details = DownloadConfirmation(
                models = listOf(ModelId.EG2_270M to EMBEDDING, ModelId.GEMMA4_E2B to GEMMA),
                totalBytes = EMBEDDING + GEMMA,
                freeBytes = 96_000_000_000,
                freeAfterBytes = 96_000_000_000 - EMBEDDING - GEMMA,
                network = NetworkKind.MOBILE,
                lowRam = true,
                missingBytes = null,
            ),
        ),
    )

    val all: List<Pair<String, SetupUiState>> = listOf(
        "not-configured" to notConfigured,
        "downloading" to downloading,
        "paused-waiting" to pausedAndWaiting,
        "verifying-preparing" to verifyingAndPreparing,
        "ready" to ready,
        "ready-cpu-no-space" to readyCpuWithoutSpace,
        "problems" to problems,
        "no-space-importing" to noSpaceAndImporting,
        "failed-download" to failedDownload,
    )
}

@LisdataPreviews
@Composable
private fun SetupNotConfiguredPreview() = LisdataTheme { SetupScreen(SetupPreviewData.notConfigured, needsNotificationPermission = false, actions = SetupActions()) }

@LisdataPreviews
@Composable
private fun SetupDownloadingPreview() = LisdataTheme { SetupScreen(SetupPreviewData.downloading, needsNotificationPermission = true, actions = SetupActions()) }

@Preview(name = "Pausa y sin red", widthDp = 390, heightDp = 1000)
@Composable
private fun SetupPausedPreview() = LisdataTheme { SetupScreen(SetupPreviewData.pausedAndWaiting, needsNotificationPermission = false, actions = SetupActions()) }

@Preview(name = "Verificando y preparando", widthDp = 390, heightDp = 1000)
@Composable
private fun SetupVerifyingPreview() = LisdataTheme { SetupScreen(SetupPreviewData.verifyingAndPreparing, needsNotificationPermission = false, actions = SetupActions()) }

@LisdataPreviews
@Composable
private fun SetupReadyPreview() = LisdataTheme { SetupScreen(SetupPreviewData.ready, needsNotificationPermission = false, actions = SetupActions()) }

@Preview(name = "Errores", widthDp = 390, heightDp = 1000)
@Composable
private fun SetupProblemsPreview() = LisdataTheme { SetupScreen(SetupPreviewData.problems, needsNotificationPermission = false, actions = SetupActions()) }

@Preview(name = "Sin espacio e importando", widthDp = 390, heightDp = 1000)
@Composable
private fun SetupNoSpacePreview() = LisdataTheme { SetupScreen(SetupPreviewData.noSpaceAndImporting, needsNotificationPermission = false, actions = SetupActions()) }

@Preview(name = "Confirmación", widthDp = 390, heightDp = 844)
@Composable
private fun SetupConfirmationPreview() = LisdataTheme { SetupScreen(SetupPreviewData.confirming, needsNotificationPermission = true, actions = SetupActions()) }
