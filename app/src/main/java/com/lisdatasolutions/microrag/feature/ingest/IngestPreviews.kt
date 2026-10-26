package com.lisdatasolutions.microrag.feature.ingest

import androidx.compose.runtime.Composable
import com.lisdatasolutions.microrag.core.designsystem.preview.LisdataPreviewSurface
import com.lisdatasolutions.microrag.core.designsystem.preview.LisdataPreviews
import com.lisdatasolutions.microrag.core.designsystem.theme.LisdataTheme
import com.lisdatasolutions.microrag.domain.ingest.DocumentFormat
import com.lisdatasolutions.microrag.domain.ingest.ImportQueue
import com.lisdatasolutions.microrag.domain.ingest.IngestError
import com.lisdatasolutions.microrag.domain.ingest.QueueItem
import com.lisdatasolutions.microrag.domain.ingest.QueueItemState
import com.lisdatasolutions.microrag.domain.ingest.QueueKey

/** Labelled sample states for previews and UI tests only (constitution V): never shown in the app. */
object IngestPreviewData {

    private fun item(id: Long, name: String, size: Long?, format: DocumentFormat?, state: QueueItemState) =
        QueueItem(QueueKey.Document(id), name, size, format, state)

    private fun queue(items: List<QueueItem>, pdfSupported: Boolean = true, indexAvailable: Boolean = true) = ImportQueue(
        items = items,
        readyCount = items.count { it.state == QueueItemState.Ready },
        pdfSupported = pdfSupported,
        indexAvailable = indexAvailable,
    )

    /** The three rows of PNG 05. */
    private val pngRows = listOf(
        item(1, "Manual técnico.pdf", 2_400_000, DocumentFormat.PDF, QueueItemState.Ready),
        item(2, "Normativa interna.pdf", 1_800_000, DocumentFormat.PDF, QueueItemState.Ready),
        item(3, "Notas de reunión.md", 48_000, DocumentFormat.MARKDOWN, QueueItemState.Ready),
    )

    private val errorRows = listOf(
        QueueItem(QueueKey.Attempt("a1"), "Contrato firmado.pdf", 830_000, DocumentFormat.PDF, QueueItemState.ImportFailed(IngestError.ENCRYPTED)),
        QueueItem(QueueKey.Attempt("a2"), "Presentación.pptx", 5_200_000, null, QueueItemState.ImportFailed(IngestError.UNSUPPORTED_FORMAT)),
        item(4, "Factura escaneada.pdf", 950_000, DocumentFormat.PDF, QueueItemState.Failed(IngestError.NO_TEXT)),
        QueueItem(QueueKey.Attempt("a3"), "Manual técnico.pdf", 2_400_000, DocumentFormat.PDF, QueueItemState.ImportFailed(IngestError.DUPLICATE)),
    )

    private val workingRows = listOf(
        QueueItem(QueueKey.Attempt("a4"), "Catálogo 2024.pdf", 9_600_000, DocumentFormat.PDF, QueueItemState.Importing(3_100_000, 9_600_000)),
        item(5, "Manual técnico.pdf", 2_400_000, DocumentFormat.PDF, QueueItemState.Extracting(57, 120)),
        item(6, "Normativa interna.pdf", 1_800_000, DocumentFormat.PDF, QueueItemState.Queued),
        item(7, "Notas de reunión.md", 48_000, DocumentFormat.MARKDOWN, QueueItemState.Prepared(4, null)),
        item(8, "Informe anual.pdf", 3_300_000, DocumentFormat.PDF, QueueItemState.Prepared(212, 3)),
    )

    /** F07: embeddings in progress, a paused run and the new failures (index-ui §Ingesta). */
    private val indexingRows = listOf(
        item(9, "Manual de procesos.pdf", 3_300_000, DocumentFormat.PDF, QueueItemState.Indexing(1_250, 4_300)),
        item(10, "Normativa interna.pdf", 1_800_000, DocumentFormat.PDF, QueueItemState.Paused(64, 430)),
        item(11, "Informe anual.pdf", 2_100_000, DocumentFormat.PDF, QueueItemState.Failed(IngestError.EMBEDDING_FAILED)),
        item(12, "Catálogo 2024.pdf", 9_600_000, DocumentFormat.PDF, QueueItemState.Failed(IngestError.INCOMPLETE)),
    )

    val empty = IngestUiState(loading = false, queue = queue(emptyList()))
    val png = IngestUiState(loading = false, queue = queue(pngRows))
    val working = IngestUiState(loading = false, queue = queue(workingRows))
    val errors = IngestUiState(loading = false, queue = queue(errorRows + pngRows.take(1)))
    val sheet = errors.copy(selected = QueueKey.Attempt("a1"))
    val confirming = working.copy(confirmingRemoval = QueueKey.Document(7))
    val noPdf = IngestUiState(loading = false, queue = queue(pngRows.drop(2), pdfSupported = false))
    val libraryUnavailable = IngestUiState(loading = false, queue = queue(emptyList(), indexAvailable = false))
    val indexing = IngestUiState(loading = false, queue = queue(indexingRows).copy(canStart = true))

    val all: List<Pair<String, IngestUiState>> = listOf(
        "empty" to empty,
        "png" to png,
        "working" to working,
        "errors" to errors,
        "sheet" to sheet,
        "confirming" to confirming,
        "noPdf" to noPdf,
        "libraryUnavailable" to libraryUnavailable,
        "indexing" to indexing,
    )
}

@LisdataPreviews
@Composable
private fun IngestEmptyPreview() = LisdataTheme { IngestScreen(IngestPreviewData.empty, IngestActions()) }

@LisdataPreviews
@Composable
private fun IngestPngPreview() = LisdataTheme { IngestScreen(IngestPreviewData.png, IngestActions()) }

@LisdataPreviews
@Composable
private fun IngestWorkingPreview() = LisdataTheme { IngestScreen(IngestPreviewData.working, IngestActions()) }

@LisdataPreviews
@Composable
private fun IngestErrorsPreview() = LisdataTheme { IngestScreen(IngestPreviewData.errors, IngestActions()) }

@LisdataPreviews
@Composable
private fun IngestNoPdfPreview() = LisdataTheme { IngestScreen(IngestPreviewData.noPdf, IngestActions()) }

@LisdataPreviews
@Composable
private fun IngestLibraryUnavailablePreview() = LisdataTheme { IngestScreen(IngestPreviewData.libraryUnavailable, IngestActions()) }

@LisdataPreviews
@Composable
private fun IngestIndexingPreview() = LisdataTheme { IngestScreen(IngestPreviewData.indexing, IngestActions()) }

@LisdataPreviews
@Composable
private fun QueueItemDetailPreview() = LisdataPreviewSurface {
    IngestPreviewData.sheet.selectedItem?.let { QueueItemDetail(it, onRetry = {}, onRemove = {}) }
}
