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
import com.lisdatasolutions.microrag.domain.rag.Folder
import com.lisdatasolutions.microrag.domain.rag.FolderList
import com.lisdatasolutions.microrag.domain.rag.LibraryFolderFilter
import com.lisdatasolutions.microrag.feature.folders.FoldersUiState

/** Labelled sample states for previews and UI tests only (constitution V): never shown in the app. */
object LibraryPreviewData {

    private fun indexed(
        id: Long,
        name: String,
        chunks: Int,
        format: DocumentFormat = DocumentFormat.PDF,
        included: Boolean = true,
        folder: String? = null,
    ) = LibraryDocument(id, name, format, LibraryDocumentState.Indexed(chunks, null), included, folderName = folder)

    private val legal = LibraryFolderFilter.Folder(1)

    /** F17 K1: the folders every sample shows; «Legal» is the folder of the folder samples. */
    val folders = FoldersUiState(
        folders = FolderList(
            listOf(Folder(1, "Legal", createdAtMs = 1, documentCount = 24), Folder(2, "Motores", createdAtMs = 2, documentCount = 5)),
            unfiledCount = 3,
        ),
    )

    private val pngDocuments = listOf(
        indexed(1, "Manual de procesos.pdf", 860),
        indexed(2, "Normativa interna.pdf", 520),
        indexed(3, "Informe anual.pdf", 1_120, included = false),
        indexed(4, "Notas del proyecto.md", 96, DocumentFormat.MARKDOWN),
    )

    /** PNG 06, inside «Legal» since F17 (K2): four of its 24 documents. */
    val png = LibraryUiState(content = LibraryContent.Documents(pngDocuments, total = 24, hasMore = true), folder = legal)

    /** K1: the root without a search lists the folders. */
    val root = LibraryUiState(content = LibraryContent.Documents(pngDocuments, total = 32, hasMore = true))

    /** K1: the root's search looks in every folder and names each row's. */
    val rootSearch = LibraryUiState(
        text = "manual",
        content = LibraryContent.Documents(
            listOf(indexed(1, "Manual de procesos.pdf", 860, folder = "Legal"), indexed(5, "Manual del motor.pdf", 210)),
            total = 2,
            hasMore = false,
        ),
    )

    /** K2: «Esta carpeta está vacía». */
    val folderEmpty = LibraryUiState(content = LibraryContent.Empty, folder = LibraryFolderFilter.Folder(2))

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
        folder = LibraryFolderFilter.Unfiled,
    )

    /** [count] loaded rows of [total], to scroll and load more. */
    fun many(count: Int, total: Int) = LibraryUiState(
        content = LibraryContent.Documents(
            (1..count).map { indexed(it.toLong(), "Documento $it.pdf", it * 10) },
            total = total,
            hasMore = count < total,
        ),
        folder = legal,
    )

    val loading = LibraryUiState(content = LibraryContent.Loading)
    val empty = LibraryUiState(content = LibraryContent.Empty)
    val noResults = LibraryUiState(text = "presupuesto", filter = LibraryFilter.INDEXED, content = LibraryContent.NoResults)
    val error = LibraryUiState(content = LibraryContent.Error)
    val unavailable = LibraryUiState(content = LibraryContent.Unavailable)

    val all: List<Pair<String, LibraryUiState>> = listOf(
        "png" to png,
        "root" to root,
        "rootSearch" to rootSearch,
        "folderEmpty" to folderEmpty,
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
private fun LibraryPngPreview() = LisdataTheme { LibraryScreen(LibraryPreviewData.png, LibraryActions(), folders = LibraryPreviewData.folders) }

@LisdataPreviews
@Composable
private fun LibraryAllStatesPreview() = LisdataTheme { LibraryScreen(LibraryPreviewData.allStates, LibraryActions(), folders = LibraryPreviewData.folders) }

@LisdataPreviews
@Composable
private fun LibraryLoadingPreview() = LisdataTheme { LibraryScreen(LibraryPreviewData.loading, LibraryActions(), folders = LibraryPreviewData.folders) }

@LisdataPreviews
@Composable
private fun LibraryEmptyPreview() = LisdataTheme { LibraryScreen(LibraryPreviewData.empty, LibraryActions(), folders = LibraryPreviewData.folders) }

@LisdataPreviews
@Composable
private fun LibraryNoResultsPreview() = LisdataTheme { LibraryScreen(LibraryPreviewData.noResults, LibraryActions(), folders = LibraryPreviewData.folders) }

@LisdataPreviews
@Composable
private fun LibraryErrorPreview() = LisdataTheme { LibraryScreen(LibraryPreviewData.error, LibraryActions(), folders = LibraryPreviewData.folders) }

@LisdataPreviews
@Composable
private fun LibraryUnavailablePreview() = LisdataTheme { LibraryScreen(LibraryPreviewData.unavailable, LibraryActions(), folders = LibraryPreviewData.folders) }

@LisdataPreviews
@Composable
private fun LibraryRootPreview() = LisdataTheme { LibraryScreen(LibraryPreviewData.root, LibraryActions(), folders = LibraryPreviewData.folders) }

@LisdataPreviews
@Composable
private fun LibraryRootSearchPreview() =
    LisdataTheme { LibraryScreen(LibraryPreviewData.rootSearch, LibraryActions(), folders = LibraryPreviewData.folders) }

@LisdataPreviews
@Composable
private fun LibraryFolderEmptyPreview() =
    LisdataTheme { LibraryScreen(LibraryPreviewData.folderEmpty, LibraryActions(), folders = LibraryPreviewData.folders) }
