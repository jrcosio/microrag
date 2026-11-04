package com.lisdatasolutions.microrag.feature.folders

import androidx.compose.runtime.Composable
import com.lisdatasolutions.microrag.core.designsystem.preview.LisdataPreviews
import com.lisdatasolutions.microrag.core.designsystem.theme.LisdataTheme
import com.lisdatasolutions.microrag.domain.library.FolderDeletion

/** Labelled sample dialogs for previews and UI tests only (constitution V): K3 and K4 of the wireframe. */
object FolderPreviewData {
    val create = FolderDialog.Name(renaming = null, name = "Legal", error = null, canSave = true)
    val duplicate = FolderDialog.Name(renaming = null, name = "Legal", error = FolderFieldError.DUPLICATE, canSave = false)
    val rename = FolderDialog.Name(renaming = 1, name = "Contratos", error = null, canSave = true)
    val delete = FolderDialog.Delete(id = 1, name = "Legal", documents = 12, choice = FolderDeletion.MOVE_TO_UNFILED)
    val confirmDocuments = FolderDialog.ConfirmDocuments(id = 1, documents = 12)

    val all: List<Pair<String, FolderDialog>> = listOf(
        "create" to create,
        "duplicate" to duplicate,
        "rename" to rename,
        "delete" to delete,
        "confirmDocuments" to confirmDocuments,
    )
}

@LisdataPreviews
@Composable
private fun FolderCreatePreview() = LisdataTheme { FolderDialogs(FolderPreviewData.duplicate, busy = false, FolderDialogActions()) }

@LisdataPreviews
@Composable
private fun FolderDeletePreview() = LisdataTheme { FolderDialogs(FolderPreviewData.delete, busy = false, FolderDialogActions()) }

@LisdataPreviews
@Composable
private fun FolderConfirmDocumentsPreview() =
    LisdataTheme { FolderDialogs(FolderPreviewData.confirmDocuments, busy = true, FolderDialogActions()) }
