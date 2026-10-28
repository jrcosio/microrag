package com.lisdatasolutions.microrag.feature.document

import androidx.compose.runtime.Composable
import com.lisdatasolutions.microrag.core.designsystem.preview.LisdataPreviews
import com.lisdatasolutions.microrag.core.designsystem.theme.LisdataTheme
import com.lisdatasolutions.microrag.domain.indexing.PauseReason
import com.lisdatasolutions.microrag.domain.ingest.DocumentFormat
import com.lisdatasolutions.microrag.domain.ingest.IngestError
import com.lisdatasolutions.microrag.domain.library.DocumentDetail
import com.lisdatasolutions.microrag.domain.library.DocumentDetailState
import com.lisdatasolutions.microrag.domain.library.DocumentPreview
import com.lisdatasolutions.microrag.domain.library.ReindexState
import java.util.Calendar

/** Labelled sample states for previews and UI tests only (constitution V): never shown in the app. */
object DocumentPreviewData {

    /** 12 October of the current year, so it reads «12 oct.» as in PNG 07. */
    private val october12: Long = Calendar.getInstance().apply {
        set(Calendar.MONTH, Calendar.OCTOBER)
        set(Calendar.DAY_OF_MONTH, 12)
        set(Calendar.HOUR_OF_DAY, 10)
    }.timeInMillis

    val manual = DocumentDetail(
        id = 1, displayName = "Manual de procesos.pdf", format = DocumentFormat.PDF, state = DocumentDetailState.Indexed,
        included = true, pages = 48, chunks = 860, sizeBytes = 2_400_000, importedAtMs = october12,
        canReindex = true, copyMissing = false, reindex = null,
    )

    val citation = DocumentPreview(
        page = 12,
        before = "Antes de cerrar el proceso, se",
        text = "debe registrar la validación final",
        after = "en el sistema documental.",
        highlighted = true,
        citedChunkMissing = false,
    )

    private val firstChunk = DocumentPreview(1, null, "Este manual describe los procesos internos de la empresa.", null, false, false)

    private fun loaded(detail: DocumentDetail, preview: PreviewContent = PreviewContent.None) =
        DocumentUiState(content = DocumentContent.Loaded(detail), preview = preview, included = detail.included)

    /** PNG 07, opened from a citation. */
    val png = loaded(manual, PreviewContent.Shown(citation))
    val firstChunkOnly = loaded(manual, PreviewContent.Shown(firstChunk))
    val excluded = loaded(manual.copy(included = false), PreviewContent.Shown(firstChunk))
    val plainText = loaded(
        manual.copy(id = 2, displayName = "Notas de campo.txt", format = DocumentFormat.TXT, pages = null, chunks = 12, sizeBytes = 18_000),
        PreviewContent.Shown(DocumentPreview(null, null, "Notas tomadas durante la visita a la planta.", null, false, false)),
    )
    val pending = loaded(manual.copy(state = DocumentDetailState.Pending, chunks = null, canReindex = false))
    val indexing = loaded(manual.copy(state = DocumentDetailState.Indexing(done = 120, total = 430), chunks = null, canReindex = false))
    val failed = loaded(manual.copy(state = DocumentDetailState.Failed(IngestError.NO_TEXT), chunks = null, canReindex = false))
    val failedRetryable = loaded(manual.copy(state = DocumentDetailState.Failed(IngestError.EMBEDDING_FAILED), chunks = null, canReindex = false))
    val outOfIndex = loaded(manual.copy(state = DocumentDetailState.OutOfIndex, chunks = null))
    val reindexing = loaded(
        manual.copy(canReindex = false, reindex = ReindexState.Running(shadowId = 9, done = 320, total = 860, pause = null)),
        PreviewContent.Shown(firstChunk),
    )
    val reindexingPaused = loaded(
        manual.copy(canReindex = false, reindex = ReindexState.Running(shadowId = 9, done = 320, total = 860, pause = PauseReason.THERMAL)),
        PreviewContent.Shown(firstChunk),
    )
    val reindexFailed = loaded(
        manual.copy(canReindex = false, reindex = ReindexState.Failed(shadowId = 9, error = IngestError.EMBEDDING_FAILED)),
        PreviewContent.Shown(firstChunk),
    )
    val citationMissing = loaded(manual, PreviewContent.Shown(firstChunk.copy(citedChunkMissing = true)))
    val copyMissing = loaded(manual.copy(canReindex = false, copyMissing = true), PreviewContent.Shown(firstChunk))
    val deleteDialog = png.copy(dialog = DocumentDialog.DELETE)
    val removeDialog = png.copy(dialog = DocumentDialog.REMOVE_FROM_INDEX)
    val deleting = png.copy(deleting = true)
    val loading = DocumentUiState()
    val unavailable = DocumentUiState(content = DocumentContent.Unavailable)

    val all: List<Pair<String, DocumentUiState>> = listOf(
        "png" to png,
        "firstChunkOnly" to firstChunkOnly,
        "excluded" to excluded,
        "plainText" to plainText,
        "pending" to pending,
        "indexing" to indexing,
        "failed" to failed,
        "failedRetryable" to failedRetryable,
        "outOfIndex" to outOfIndex,
        "reindexing" to reindexing,
        "reindexingPaused" to reindexingPaused,
        "reindexFailed" to reindexFailed,
        "citationMissing" to citationMissing,
        "copyMissing" to copyMissing,
        "deleteDialog" to deleteDialog,
        "removeDialog" to removeDialog,
        "deleting" to deleting,
        "loading" to loading,
        "unavailable" to unavailable,
    )
}

@LisdataPreviews
@Composable
private fun DocumentPngPreview() = LisdataTheme { DocumentScreen(DocumentPreviewData.png, DocumentActions()) }

@LisdataPreviews
@Composable
private fun DocumentPlainTextPreview() = LisdataTheme { DocumentScreen(DocumentPreviewData.plainText, DocumentActions()) }

@LisdataPreviews
@Composable
private fun DocumentPendingPreview() = LisdataTheme { DocumentScreen(DocumentPreviewData.pending, DocumentActions()) }

@LisdataPreviews
@Composable
private fun DocumentIndexingPreview() = LisdataTheme { DocumentScreen(DocumentPreviewData.indexing, DocumentActions()) }

@LisdataPreviews
@Composable
private fun DocumentFailedPreview() = LisdataTheme { DocumentScreen(DocumentPreviewData.failedRetryable, DocumentActions()) }

@LisdataPreviews
@Composable
private fun DocumentOutOfIndexPreview() = LisdataTheme { DocumentScreen(DocumentPreviewData.outOfIndex, DocumentActions()) }

@LisdataPreviews
@Composable
private fun DocumentReindexingPreview() = LisdataTheme { DocumentScreen(DocumentPreviewData.reindexingPaused, DocumentActions()) }

@LisdataPreviews
@Composable
private fun DocumentReindexFailedPreview() = LisdataTheme { DocumentScreen(DocumentPreviewData.reindexFailed, DocumentActions()) }

@LisdataPreviews
@Composable
private fun DocumentCopyMissingPreview() = LisdataTheme { DocumentScreen(DocumentPreviewData.copyMissing, DocumentActions()) }

@LisdataPreviews
@Composable
private fun DocumentDeleteDialogPreview() = LisdataTheme { DocumentScreen(DocumentPreviewData.deleteDialog, DocumentActions()) }

@LisdataPreviews
@Composable
private fun DocumentLoadingPreview() = LisdataTheme { DocumentScreen(DocumentPreviewData.loading, DocumentActions()) }

@LisdataPreviews
@Composable
private fun DocumentUnavailablePreview() = LisdataTheme { DocumentScreen(DocumentPreviewData.unavailable, DocumentActions()) }
