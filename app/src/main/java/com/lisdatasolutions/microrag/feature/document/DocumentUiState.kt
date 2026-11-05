package com.lisdatasolutions.microrag.feature.document

import com.lisdatasolutions.microrag.domain.ingest.DocumentFormat
import com.lisdatasolutions.microrag.domain.ingest.IngestError
import com.lisdatasolutions.microrag.domain.library.DocumentDetail
import com.lisdatasolutions.microrag.domain.library.DocumentDetailState
import com.lisdatasolutions.microrag.domain.library.DocumentPreview
import com.lisdatasolutions.microrag.domain.rag.FolderList
import com.lisdatasolutions.microrag.domain.rag.IndexMode

/** P07 state (F08 contracts/library-ui.md): the document, its preview and what the user is doing with it. */
data class DocumentUiState(
    val content: DocumentContent = DocumentContent.Loading,
    val preview: PreviewContent = PreviewContent.Loading,
    /** The switch: what the user asked for until the index shows it. */
    val included: Boolean = true,
    val dialog: DocumentDialog? = null,
    /** D8: «Eliminando…» until the document is gone. */
    val deleting: Boolean = false,
    /** An action is being applied: actions are disabled meanwhile. */
    val busy: Boolean = false,
    /** A7: the last change, read politely by TalkBack. */
    val announcement: DocumentAnnouncement? = null,
    /** F17 K5: the folders for «Mover a carpeta»; null while they load. */
    val folders: FolderList? = null,
    /** The open «Mover a carpeta» sheet and the folder chosen in it (null: «General»). */
    val move: MoveSheet? = null,
    val moveMessage: MoveMessage? = null,
    /** F19 (contracts U12–U14): the multimodal mode is on, so «Reindexar» asks for the mode first. */
    val multimodalOn: Boolean = false,
    /** The open mode sheet of «Reindexar documento». */
    val choosingReindexMode: Boolean = false,
    /** F20 (contract U9, research R10): opened from a cited page that is no longer stored. */
    val citedPageMissing: Boolean = false,
) {
    private val detail: DocumentDetail? get() = (content as? DocumentContent.Loaded)?.detail

    /** U12: its pages wait for the mode, which is off. */
    val pagesNeedMultimodal: Boolean get() = !multimodalOn && (detail?.let { it.indexMode != IndexMode.TEXT && it.pagesStored > 0 } == true)

    /** U14: a PDF without text can be indexed as pages, only with the mode on. */
    val canReindexAsPages: Boolean
        get() = multimodalOn && detail?.let {
            !it.copyMissing && it.format == DocumentFormat.PDF && (it.state as? DocumentDetailState.Failed)?.error == IngestError.NO_TEXT
        } == true
}

data class MoveSheet(val selected: Long?)

/** The notice after a move: «Movido a «Legal»», or that it could not be moved. */
sealed interface MoveMessage {
    /** [folderName] null is «General». */
    data class Moved(val folderName: String?) : MoveMessage
    data object Failed : MoveMessage
}

/** A7. */
enum class DocumentAnnouncement { REMOVED_FROM_INDEX, REINDEXING, EXCLUDED, INCLUDED }

sealed interface DocumentContent {
    /** D9. */
    data object Loading : DocumentContent

    /** D11: the index is incompatible or unreadable; nothing can be written. */
    data object Unavailable : DocumentContent

    data class Loaded(val detail: DocumentDetail) : DocumentContent
}

sealed interface PreviewContent {
    data object Loading : PreviewContent

    /** Not indexed, or indexed without chunks: the state card takes its place. */
    data object None : PreviewContent

    data class Shown(val preview: DocumentPreview) : PreviewContent

    /** F20 (contract U9): the cited page, instead of the fragment; [image] is a loose JPG or PNG, with no page number. */
    data class Page(val documentId: Long, val page: Int, val documentName: String, val image: Boolean) : PreviewContent
}

/** D8: the confirmations. */
enum class DocumentDialog { DELETE, REMOVE_FROM_INDEX }

/** One-off requests that need the navigation. */
sealed interface DocumentEvent {
    /** R8: the document does not exist or disappeared: back, with «Este documento ya no existe». */
    data object Gone : DocumentEvent

    /** D8: the user deleted it: back to the Library, which announces it (A7). */
    data object Deleted : DocumentEvent
}
