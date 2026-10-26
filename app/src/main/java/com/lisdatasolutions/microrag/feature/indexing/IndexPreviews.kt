package com.lisdatasolutions.microrag.feature.indexing

import androidx.compose.runtime.Composable
import com.lisdatasolutions.microrag.core.designsystem.preview.LisdataPreviews
import com.lisdatasolutions.microrag.core.designsystem.theme.LisdataTheme
import com.lisdatasolutions.microrag.domain.indexing.CurrentDocument
import com.lisdatasolutions.microrag.domain.indexing.FailedDocument
import com.lisdatasolutions.microrag.domain.indexing.IndexingState
import com.lisdatasolutions.microrag.domain.indexing.PauseReason
import com.lisdatasolutions.microrag.domain.indexing.RunProgress
import com.lisdatasolutions.microrag.domain.ingest.IngestError

/** Labelled sample states for previews and UI tests only (constitution V): never shown in the app. */
object IndexPreviewData {

    private val manual = CurrentDocument(1, "Manual de procesos.pdf", CurrentDocument.Stage.EMBEDDING)
    private val failed = listOf(
        FailedDocument(7, "Contrato escaneado.pdf", IngestError.NO_TEXT),
        FailedDocument(8, "Informe anual.pdf", IngestError.EMBEDDING_FAILED),
    )

    private fun active(
        progress: RunProgress = RunProgress(done = 8_432, total = 12_400, remainingSeconds = null),
        current: CurrentDocument? = manual,
        queued: Int = 0,
        pause: PauseReason? = null,
        errors: List<FailedDocument> = emptyList(),
    ) = IndexUiState(IndexingState.Active(progress, current, queued, pause, errors))

    /** PNG 08. */
    val png = active()
    val running = active(progress = RunProgress(done = 8_432, total = 12_400, remainingSeconds = 1_340), queued = 2)
    val preparing = active(
        progress = RunProgress(done = 0, total = null, remainingSeconds = null),
        current = CurrentDocument(2, "Normativa interna.pdf", CurrentDocument.Stage.EXTRACTING),
        queued = 3,
    )
    val paused = active(pause = PauseReason.USER)
    val thermal = active(pause = PauseReason.THERMAL)
    val waitingForSystem = active(pause = PauseReason.SYSTEM_LIMIT, current = null, queued = 1)
    val noSpace = active(pause = PauseReason.NO_SPACE)
    val modelMissing = active(pause = PauseReason.MODEL_MISSING, current = null)
    val runningWithErrors = active(errors = failed)
    val idle = IndexUiState(IndexingState.Idle(emptyList()))
    val idleWithErrors = IndexUiState(IndexingState.Idle(failed))
    val idleWithOneRetryable = IndexUiState(IndexingState.Idle(listOf(failed[1])))
    val unavailable = IndexUiState(IndexingState.IndexUnavailable)
    val loading = IndexUiState(IndexingState.Loading)

    val all: List<Pair<String, IndexUiState>> = listOf(
        "png" to png,
        "running" to running,
        "preparing" to preparing,
        "paused" to paused,
        "thermal" to thermal,
        "waitingForSystem" to waitingForSystem,
        "noSpace" to noSpace,
        "modelMissing" to modelMissing,
        "runningWithErrors" to runningWithErrors,
        "idle" to idle,
        "idleWithErrors" to idleWithErrors,
        "idleWithOneRetryable" to idleWithOneRetryable,
        "unavailable" to unavailable,
        "loading" to loading,
    )
}

@LisdataPreviews
@Composable
private fun IndexPngPreview() = LisdataTheme { IndexScreen(IndexPreviewData.png, IndexActions()) }

@LisdataPreviews
@Composable
private fun IndexRunningPreview() = LisdataTheme { IndexScreen(IndexPreviewData.running, IndexActions()) }

@LisdataPreviews
@Composable
private fun IndexPreparingPreview() = LisdataTheme { IndexScreen(IndexPreviewData.preparing, IndexActions()) }

@LisdataPreviews
@Composable
private fun IndexPausedPreview() = LisdataTheme { IndexScreen(IndexPreviewData.paused, IndexActions()) }

@LisdataPreviews
@Composable
private fun IndexThermalPreview() = LisdataTheme { IndexScreen(IndexPreviewData.thermal, IndexActions()) }

@LisdataPreviews
@Composable
private fun IndexWaitingForSystemPreview() = LisdataTheme { IndexScreen(IndexPreviewData.waitingForSystem, IndexActions()) }

@LisdataPreviews
@Composable
private fun IndexNoSpacePreview() = LisdataTheme { IndexScreen(IndexPreviewData.noSpace, IndexActions()) }

@LisdataPreviews
@Composable
private fun IndexModelMissingPreview() = LisdataTheme { IndexScreen(IndexPreviewData.modelMissing, IndexActions()) }

@LisdataPreviews
@Composable
private fun IndexRunningWithErrorsPreview() = LisdataTheme { IndexScreen(IndexPreviewData.runningWithErrors, IndexActions()) }

@LisdataPreviews
@Composable
private fun IndexIdlePreview() = LisdataTheme { IndexScreen(IndexPreviewData.idle, IndexActions()) }

@LisdataPreviews
@Composable
private fun IndexIdleWithErrorsPreview() = LisdataTheme { IndexScreen(IndexPreviewData.idleWithErrors, IndexActions()) }

@LisdataPreviews
@Composable
private fun IndexIdleWithOneRetryablePreview() = LisdataTheme { IndexScreen(IndexPreviewData.idleWithOneRetryable, IndexActions()) }

@LisdataPreviews
@Composable
private fun IndexUnavailablePreview() = LisdataTheme { IndexScreen(IndexPreviewData.unavailable, IndexActions()) }

@LisdataPreviews
@Composable
private fun IndexLoadingPreview() = LisdataTheme { IndexScreen(IndexPreviewData.loading, IndexActions()) }
