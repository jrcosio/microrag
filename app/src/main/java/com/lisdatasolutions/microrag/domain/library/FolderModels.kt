package com.lisdatasolutions.microrag.domain.library

// F17 folders (data-model §3): the rules and results of the Library's folder actions. The folders
// themselves are index rows, in domain/rag.

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

/** [failedDocuments] are the documents still in the folder after deleting them one by one (M4); the folder stays with them. */
data class FolderDeletionResult(val folderDeleted: Boolean, val failedDocuments: Int)
