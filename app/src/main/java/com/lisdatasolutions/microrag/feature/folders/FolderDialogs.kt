package com.lisdatasolutions.microrag.feature.folders

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.lisdatasolutions.microrag.R
import com.lisdatasolutions.microrag.core.designsystem.component.LisdataConfirmationDialog
import com.lisdatasolutions.microrag.core.designsystem.component.LisdataRadioRow
import com.lisdatasolutions.microrag.core.designsystem.component.LisdataTextField
import com.lisdatasolutions.microrag.core.designsystem.theme.LisdataTheme
import com.lisdatasolutions.microrag.domain.library.FolderDeletion

/** What the folder dialogs can ask for; a [FoldersViewModel] answers them. */
data class FolderDialogActions(
    val onNameChange: (String) -> Unit = {},
    val onSaveName: () -> Unit = {},
    val onDeletionChoice: (FolderDeletion) -> Unit = {},
    val onConfirmDelete: () -> Unit = {},
    val onConfirmDocuments: () -> Unit = {},
    val onDismiss: () -> Unit = {},
)

fun FoldersViewModel.dialogActions() = FolderDialogActions(
    onNameChange = ::onNameChange,
    onSaveName = ::onSaveName,
    onDeletionChoice = ::onDeletionChoice,
    onConfirmDelete = ::onConfirmDelete,
    onConfirmDocuments = ::onConfirmDocuments,
    onDismiss = ::onDismissDialog,
)

/** K3 and K4 (wireframe §3), whichever [dialog] is open. */
@Composable
fun FolderDialogs(dialog: FolderDialog?, busy: Boolean, actions: FolderDialogActions) {
    when (dialog) {
        null -> Unit
        is FolderDialog.Name -> NameDialog(dialog, busy, actions)
        is FolderDialog.Delete -> DeleteDialog(dialog, busy, actions)
        is FolderDialog.ConfirmDocuments -> LisdataConfirmationDialog(
            title = pluralStringResource(R.plurals.folder_delete_documents_title, dialog.documents, dialog.documents),
            body = stringResource(R.string.folder_delete_documents_body),
            confirmText = stringResource(if (busy) R.string.folder_deleting else R.string.folder_delete_documents_confirm),
            onConfirm = actions.onConfirmDocuments,
            onDismiss = actions.onDismiss,
            confirmEnabled = !busy,
        )
    }
}

@Composable
private fun NameDialog(dialog: FolderDialog.Name, busy: Boolean, actions: FolderDialogActions) {
    val field = remember { FocusRequester() }
    LisdataConfirmationDialog(
        title = stringResource(if (dialog.renaming == null) R.string.library_folder_new else R.string.folder_rename),
        body = "",
        confirmText = stringResource(if (dialog.renaming == null) R.string.folder_dialog_create else R.string.folder_dialog_save),
        onConfirm = actions.onSaveName,
        onDismiss = actions.onDismiss,
        destructive = false,
        confirmEnabled = dialog.canSave && !busy,
        focusCancel = false,
        content = {
            LisdataTextField(
                value = dialog.name,
                onValueChange = actions.onNameChange,
                label = stringResource(R.string.folder_dialog_name),
                error = dialog.error?.let { stringResource(it.text()) },
                doneEnabled = dialog.canSave && !busy,
                onDone = actions.onSaveName,
                focusRequester = field,
            )
            // Wireframe §3: the focus starts in the field, once it is in the dialog's window.
            LaunchedEffect(Unit) { runCatching { field.requestFocus() } }
        },
    )
}

/** An empty folder has nothing to choose: only the question. */
@Composable
private fun DeleteDialog(dialog: FolderDialog.Delete, busy: Boolean, actions: FolderDialogActions) {
    LisdataConfirmationDialog(
        title = stringResource(R.string.folder_delete_title, dialog.name),
        body = "",
        confirmText = stringResource(if (busy) R.string.folder_deleting else R.string.folder_delete_confirm),
        onConfirm = actions.onConfirmDelete,
        onDismiss = actions.onDismiss,
        confirmEnabled = !busy,
        content = { if (dialog.documents > 0) DeletionChoices(dialog, actions) },
    )
}

@Composable
private fun DeletionChoices(dialog: FolderDialog.Delete, actions: FolderDialogActions) {
    // Scrolls at 200 % text instead of cutting an option (U5).
    Column(
        modifier = Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(LisdataTheme.spacing.space1),
    ) {
        LisdataRadioRow(
            title = pluralStringResource(R.plurals.folder_delete_move, dialog.documents, dialog.documents),
            selected = dialog.choice == FolderDeletion.MOVE_TO_UNFILED,
            onSelect = { actions.onDeletionChoice(FolderDeletion.MOVE_TO_UNFILED) },
        )
        LisdataRadioRow(
            title = pluralStringResource(R.plurals.folder_delete_documents, dialog.documents, dialog.documents),
            selected = dialog.choice == FolderDeletion.DELETE_DOCUMENTS,
            onSelect = { actions.onDeletionChoice(FolderDeletion.DELETE_DOCUMENTS) },
        )
    }
}

fun FolderFieldError.text(): Int = when (this) {
    FolderFieldError.EMPTY -> R.string.folder_error_empty
    FolderFieldError.DUPLICATE -> R.string.folder_error_duplicate
    FolderFieldError.RESERVED -> R.string.folder_error_reserved
    FolderFieldError.TOO_LONG -> R.string.folder_error_too_long
}

fun FolderMessage.text(): Int = when (this) {
    FolderMessage.DELETED -> R.string.folder_deleted
    FolderMessage.DELETE_PARTIAL -> R.string.folder_delete_partial
    FolderMessage.SAVE_FAILED -> R.string.folder_save_failed
}
