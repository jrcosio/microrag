package com.lisdatasolutions.microrag.domain.library

import com.lisdatasolutions.microrag.domain.indexing.PauseReason
import com.lisdatasolutions.microrag.domain.ingest.DocumentFormat
import com.lisdatasolutions.microrag.domain.ingest.IngestError
import com.lisdatasolutions.microrag.domain.rag.IndexMode
import com.lisdatasolutions.microrag.domain.rag.LibraryFolderFilter

/** The Library filter chips (G16): no «Errores»; «Pendientes» is everything not indexed, each with its chip. */
enum class LibraryFilter { ALL, INDEXED, PENDING }

/** What the Library asks for; the ViewModel keeps it in its SavedStateHandle (F08 research R9), the folder by route (F17). */
data class LibraryQuery(
    val text: String = "",
    val filter: LibraryFilter = LibraryFilter.ALL,
    val limit: Int = PAGE,
    val folder: LibraryFolderFilter = LibraryFolderFilter.All,
) {
    companion object {
        /** Rows loaded per block (research R2). */
        const val PAGE = 50
    }
}

/** [documents] are the first `limit` matches of [total], newest first. */
data class LibraryPage(val documents: List<LibraryDocument>, val total: Int) {
    val hasMore: Boolean get() = documents.size < total
}

data class LibraryDocument(
    val id: Long,
    val displayName: String,
    /** From the stored MIME type; null if it is not one of the MVP formats. */
    val format: DocumentFormat?,
    val state: LibraryDocumentState,
    val included: Boolean,
    /** F17: null is «General»; the root's search shows the name in the data line. */
    val folderId: Long? = null,
    val folderName: String? = null,
    /** F17: with [id], what a chat scope keeps of it (D-065). */
    val createdAtMs: Long = 0,
    /** F19 (contract U11): its mode, and its pages for the data line of Visual and Both. */
    val indexMode: IndexMode = IndexMode.TEXT,
    val pages: Int? = null,
) {
    /** Inclusion only means something for indexed documents (FR-008). */
    val canChangeInclusion: Boolean get() = state is LibraryDocumentState.Indexed
}

/** The reindex shadow of an indexed document, as its row shows it (research R10, B7). */
enum class ReindexRow { RUNNING, FAILED }

/** A Library row's state (research R10). */
sealed interface LibraryDocumentState {
    data class Indexed(val chunks: Int, val reindex: ReindexRow?) : LibraryDocumentState
    data object Pending : LibraryDocumentState
    data object Indexing : LibraryDocumentState
    data class Failed(val error: IngestError) : LibraryDocumentState
    data object OutOfIndex : LibraryDocumentState
}

/** The document detail (PNG 07); every figure comes from the index, never made up (constitution V). */
data class DocumentDetail(
    val id: Long,
    val displayName: String,
    val format: DocumentFormat?,
    val state: DocumentDetailState,
    val included: Boolean,
    /** Null for TXT and Markdown, shown as «—». */
    val pages: Int?,
    /** Null while not indexed, shown as «—». */
    val chunks: Int?,
    val sizeBytes: Long?,
    val importedAtMs: Long,
    /** «Reindexar documento» can start now: indexed without a shadow, or out of the index, with its copy. */
    val canReindex: Boolean,
    /** The private copy is gone: it cannot be indexed again, only deleted (D10). */
    val copyMissing: Boolean,
    /** Null without a reindex shadow. */
    val reindex: ReindexState?,
    /** F17: null is «General» (L6). */
    val folderId: Long? = null,
    val folderName: String? = null,
    /** F19 (contract U12): its mode and the page images stored. */
    val indexMode: IndexMode = IndexMode.TEXT,
    val pagesStored: Int = 0,
) {
    val canChangeInclusion: Boolean get() = state == DocumentDetailState.Indexed

    /** Indexed with pages and no chunks (F19 research R13): only the chat with the mode on uses it (F20, FR-006b). */
    val pagesOnly: Boolean get() = indexMode != IndexMode.TEXT && state == DocumentDetailState.Indexed && (chunks ?: 0) == 0
}

sealed interface DocumentDetailState {
    data object Indexed : DocumentDetailState
    data object Pending : DocumentDetailState

    /** [done] stored chunks of [total] from the chunking; [total] is null until it is chunked. */
    data class Indexing(val done: Int, val total: Int?) : DocumentDetailState
    data class Failed(val error: IngestError) : DocumentDetailState
    data object OutOfIndex : DocumentDetailState
}

/** A reindex in progress or failed (D5, D6); [shadowId] is what F07's «Reintentar» retries. */
sealed interface ReindexState {
    val shadowId: Long

    /** [pause] is why the run is not advancing, if it is not (D5). */
    data class Running(override val shadowId: Long, val done: Int, val total: Int?, val pause: PauseReason?) : ReindexState
    data class Failed(override val shadowId: Long, val error: IngestError) : ReindexState
}

/** The content preview (research R7); [highlighted] only for the chunk of a citation. */
data class DocumentPreview(
    /** The chunk's page; null for TXT and Markdown, and then there is no page chip. */
    val page: Int?,
    /** Last sentence of the previous chunk, only with a citation. */
    val before: String?,
    val text: String,
    /** First sentence of the next chunk, only with a citation. */
    val after: String?,
    val highlighted: Boolean,
    /** The citation pointed to a chunk this document no longer has (D7). */
    val citedChunkMissing: Boolean,
)
