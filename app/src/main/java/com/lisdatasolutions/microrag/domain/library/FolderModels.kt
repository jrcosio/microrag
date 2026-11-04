package com.lisdatasolutions.microrag.domain.library

// F17 folders (data-model §3): one level, one folder per document; a document with no folder is in «General».

/** [documentCount] leaves out the reindex shadows, as every Library count (F08). */
data class Folder(val id: Long, val name: String, val documentCount: Int)

/** The Library root: the folders by folded name, and how many documents are in «General». */
data class FolderList(val folders: List<Folder>, val unfiledCount: Int)

/** Which documents a Library list shows (research R5). */
sealed interface LibraryFolderFilter {
    /** Every folder: the search of the root. */
    data object All : LibraryFolderFilter

    /** «General»: the documents with no folder. */
    data object Unfiled : LibraryFolderFilter

    data class Folder(val id: Long) : LibraryFolderFilter
}

/** FR-003: the rules of a folder name; the index decides uniqueness, by [Valid.norm]. */
sealed interface FolderNameCheck {
    data class Valid(val name: String, val norm: String) : FolderNameCheck
    data object Empty : FolderNameCheck
    data object TooLong : FolderNameCheck

    /** «General», in any form: the group of the documents with no folder. */
    data object Reserved : FolderNameCheck
}

sealed interface FolderResult {
    data class Done(val id: Long) : FolderResult
    data object Duplicate : FolderResult
    data class Invalid(val reason: FolderNameCheck) : FolderResult
    data object Failed : FolderResult
}

/** What deleting a folder does with its documents (FR-005, clarify Q2). */
enum class FolderDeletion { MOVE_TO_UNFILED, DELETE_DOCUMENTS }
