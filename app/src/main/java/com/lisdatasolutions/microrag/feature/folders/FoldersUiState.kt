package com.lisdatasolutions.microrag.feature.folders

import com.lisdatasolutions.microrag.domain.library.FolderDeletion
import com.lisdatasolutions.microrag.domain.rag.FolderList

/** F17 folders (data-model §4): the Library root, a folder's «⋯», and «Nueva carpeta…» of the sheets. */
data class FoldersUiState(
    /** Null while loading. */
    val folders: FolderList? = null,
    /** The index is incompatible or unreadable. */
    val unavailable: Boolean = false,
    val dialog: FolderDialog? = null,
    /** A save or a delete is running: the dialog's action waits and a second tap does nothing. */
    val busy: Boolean = false,
    val message: FolderMessage? = null,
    /** The folder this screen just deleted, so the screen inside it goes back (K2). */
    val deletedFolder: Long? = null,
)

/** K3 and K4. */
sealed interface FolderDialog {
    /** «Nueva carpeta» ([renaming] null) or «Renombrar carpeta». */
    data class Name(val renaming: Long?, val name: String, val error: FolderFieldError?, val canSave: Boolean) : FolderDialog

    data class Delete(val id: Long, val name: String, val documents: Int, val choice: FolderDeletion) : FolderDialog

    /** The second notice before deleting the documents too (constitution V). */
    data class ConfirmDocuments(val id: Long, val documents: Int) : FolderDialog
}

/** The notices under the name field (wireframe §3). */
enum class FolderFieldError { EMPTY, DUPLICATE, RESERVED, TOO_LONG }

/** One-off notices of the snackbar. */
enum class FolderMessage { DELETED, DELETE_PARTIAL, SAVE_FAILED }
