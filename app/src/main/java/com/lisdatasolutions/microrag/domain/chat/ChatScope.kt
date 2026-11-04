package com.lisdatasolutions.microrag.domain.chat

// F17 (data-model §3, research R7–R8): what a conversation asks. A scope keeps each folder and document
// with when it was created, so a reused id never matches something else (D-065).

data class DocumentRef(val id: Long, val createdAtMs: Long)

data class FolderRef(val id: Long, val createdAtMs: Long)

sealed interface ChatScope {
    /** Old conversations, and «Nuevo chat» the first time (FR-020b). */
    data object WholeLibrary : ChatScope

    /** Whole folders, «General» as one more folder (D-072) and loose documents. Never empty (FR-021). */
    data class Some(val folders: Set<FolderRef>, val general: Boolean, val documents: Set<DocumentRef>) : ChatScope {
        init {
            require(folders.isNotEmpty() || general || documents.isNotEmpty()) { "an empty scope is the whole library" }
        }
    }
}

/** What a saved scope means now: what still exists, and what fell out, for the notices of K10. */
data class ResolvedScope(
    val scope: ChatScope,
    val droppedFolders: Int = 0,
    val droppedDocuments: Int = 0,
    /** Nothing of a [ChatScope.Some] was left, so it is the whole library again. */
    val fellBackToWholeLibrary: Boolean = false,
) {
    val changed: Boolean get() = droppedFolders > 0 || droppedDocuments > 0 || fellBackToWholeLibrary
}
