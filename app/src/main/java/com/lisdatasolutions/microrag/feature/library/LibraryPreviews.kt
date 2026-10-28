package com.lisdatasolutions.microrag.feature.library

import androidx.compose.runtime.Composable
import com.lisdatasolutions.microrag.core.designsystem.preview.LisdataPreviews
import com.lisdatasolutions.microrag.core.designsystem.theme.LisdataTheme
import com.lisdatasolutions.microrag.domain.ingest.DocumentFormat
import com.lisdatasolutions.microrag.domain.ingest.IngestError
import com.lisdatasolutions.microrag.domain.library.LibraryDocument
import com.lisdatasolutions.microrag.domain.library.LibraryDocumentState
import com.lisdatasolutions.microrag.domain.library.LibraryFilter
import com.lisdatasolutions.microrag.domain.library.ReindexRow

/** Labelled sample states for previews and UI tests only (constitution V): never shown in the app. */
object LibraryPreviewData {

    private fun indexed(id: Long, name: String, chunks: Int, format: DocumentFormat = DocumentFormat.PDF, included: Boolean = true) =
        LibraryDocument(id, name, format, LibraryDocumentState.Indexed(chunks, null), included)

    private val pngDocuments = listOf(
        indexed(1, "Manual de procesos.pdf", 860),
        indexed(2, "Normativa interna.pdf", 520),
        indexed(3, "Informe anual.pdf", 1_120, included = false),
        indexed(4, "Notas del proyecto.md", 96, DocumentFormat.MARKDOWN),
    )

    /** PNG 06: four of the 24 documents. */
    val png = LibraryUiState(content = LibraryContent.Documents(pngDocuments, total = 24, hasMore = true))

    /** Every row of research R10, with B5–B7. */
    val allStates = LibraryUiState(
        content = LibraryContent.Documents(
            listOf(
                indexed(10, "Contrato.pdf", 412),
                indexed(11, "Informe anual.pdf", 1_120, included = false),
                LibraryDocument(12, "Normativa interna.pdf", DocumentFormat.PDF, LibraryDocumentState.Indexed(520, ReindexRow.RUNNING), true),
                LibraryDocument(13, "Catálogo 2024.pdf", DocumentFormat.PDF, LibraryDocumentState.Indexed(240, ReindexRow.FAILED), true),
                LibraryDocument(14, "Guía de instalación.pdf", DocumentFormat.PDF, LibraryDocumentState.Pending, true),
                LibraryDocument(15, "Notas de campo.txt", DocumentFormat.TXT, LibraryDocumentState.Indexing, true),
                LibraryDocument(16, "Contrato escaneado.pdf", DocumentFormat.PDF, LibraryDocumentState.Failed(IngestError.NO_TEXT), true),
                LibraryDocument(17, "Normas antiguas.pdf", DocumentFormat.PDF, LibraryDocumentState.OutOfIndex, true),
            ),
            total = 8,
            hasMore = false,
        ),
    )

    /** [count] loaded rows of [total], to scroll and load more. */
    fun many(count: Int, total: Int) = LibraryUiState(
        content = LibraryContent.Documents(
            (1..count).map { indexed(it.toLong(), "Documento $it.pdf", it * 10) },
            total = total,
            hasMore = count < total,
        ),
    )

    val loading = LibraryUiState(content = LibraryContent.Loading)
    val empty = LibraryUiState(content = LibraryContent.Empty)
    val noResults = LibraryUiState(text = "presupuesto", filter = LibraryFilter.INDEXED, content = LibraryContent.NoResults)
    val error = LibraryUiState(content = LibraryContent.Error)
    val unavailable = LibraryUiState(content = LibraryContent.Unavailable)

    val all: List<Pair<String, LibraryUiState>> = listOf(
        "png" to png,
        "allStates" to allStates,
        "loading" to loading,
        "empty" to empty,
        "noResults" to noResults,
        "error" to error,
        "unavailable" to unavailable,
    )
}

@LisdataPreviews
@Composable
private fun LibraryPngPreview() = LisdataTheme { LibraryScreen(LibraryPreviewData.png, LibraryActions()) }

@LisdataPreviews
@Composable
private fun LibraryAllStatesPreview() = LisdataTheme { LibraryScreen(LibraryPreviewData.allStates, LibraryActions()) }

@LisdataPreviews
@Composable
private fun LibraryLoadingPreview() = LisdataTheme { LibraryScreen(LibraryPreviewData.loading, LibraryActions()) }

@LisdataPreviews
@Composable
private fun LibraryEmptyPreview() = LisdataTheme { LibraryScreen(LibraryPreviewData.empty, LibraryActions()) }

@LisdataPreviews
@Composable
private fun LibraryNoResultsPreview() = LisdataTheme { LibraryScreen(LibraryPreviewData.noResults, LibraryActions()) }

@LisdataPreviews
@Composable
private fun LibraryErrorPreview() = LisdataTheme { LibraryScreen(LibraryPreviewData.error, LibraryActions()) }

@LisdataPreviews
@Composable
private fun LibraryUnavailablePreview() = LisdataTheme { LibraryScreen(LibraryPreviewData.unavailable, LibraryActions()) }
