package com.lisdatasolutions.microrag.feature.document

import com.lisdatasolutions.microrag.domain.library.DocumentDetail
import com.lisdatasolutions.microrag.domain.library.DocumentPreview

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
)

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
