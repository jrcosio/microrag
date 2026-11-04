package com.lisdatasolutions.microrag.feature.folders

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lisdatasolutions.microrag.domain.library.CreateFolderUseCase
import com.lisdatasolutions.microrag.domain.library.DeleteFolderUseCase
import com.lisdatasolutions.microrag.domain.library.FolderDeletion
import com.lisdatasolutions.microrag.domain.library.FolderNameCheck
import com.lisdatasolutions.microrag.domain.library.FolderNames
import com.lisdatasolutions.microrag.domain.library.FolderResult
import com.lisdatasolutions.microrag.domain.library.FoldersResult
import com.lisdatasolutions.microrag.domain.library.ObserveFoldersUseCase
import com.lisdatasolutions.microrag.domain.library.RenameFolderUseCase
import com.lisdatasolutions.microrag.domain.rag.FolderList
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * F17 K1–K4 (contracts/folders-scope-ui.md §1): the folders and their dialogs. The open dialog and the name
 * being typed live in the [SavedStateHandle], so rotating or a process death keep them; a save or a
 * delete runs once, whatever the taps.
 */
class FoldersViewModel(
    private val savedState: SavedStateHandle,
    observeFolders: ObserveFoldersUseCase,
    private val createFolder: CreateFolderUseCase,
    private val renameFolder: RenameFolderUseCase,
    private val deleteFolder: DeleteFolderUseCase,
) : ViewModel() {

    private val folders: StateFlow<FoldersResult?> = observeFolders()
        .catch { error ->
            if (error is CancellationException) throw error
            emit(FoldersResult.Unavailable)
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val dialogKind = savedState.getStateFlow<String?>(KEY_DIALOG, null)
    private val dialogId = savedState.getStateFlow(KEY_ID, 0L)
    private val name = savedState.getStateFlow(KEY_NAME, "")
    private val touched = savedState.getStateFlow(KEY_TOUCHED, false)

    /** The name the index refused as a duplicate, until it is changed. */
    private val taken = savedState.getStateFlow<String?>(KEY_TAKEN, null)
    private val choice = savedState.getStateFlow(KEY_CHOICE, FolderDeletion.MOVE_TO_UNFILED.name)
    private val count = savedState.getStateFlow(KEY_COUNT, 0)

    private val busy = MutableStateFlow(false)
    private val message = MutableStateFlow<FolderMessage?>(null)
    private val deleted = MutableStateFlow<Long?>(null)

    private val created = Channel<Long>(Channel.BUFFERED)

    /** The id of each folder created here, once: a sheet selects it (K5, K6). */
    val createdFolders: Flow<Long> = created.receiveAsFlow()

    private val dialog: Flow<FolderDialog?> = combine(
        combine(dialogKind, dialogId, name, touched, taken) { kind, id, name, touched, taken -> DialogInput(kind, id, name, touched, taken) },
        choice,
        count,
        folders,
    ) { input, choice, count, folders -> input.toDialog(FolderDeletion.valueOf(choice), count, (folders as? FoldersResult.Loaded)?.folders) }

    val state: StateFlow<FoldersUiState> = combine(folders, dialog, busy, message, deleted) { folders, dialog, busy, message, deleted ->
        FoldersUiState(
            folders = (folders as? FoldersResult.Loaded)?.folders,
            unavailable = folders == FoldersResult.Unavailable,
            dialog = dialog,
            busy = busy,
            message = message,
            deletedFolder = deleted,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, FoldersUiState())

    fun onNewFolder() = openName(kind = KIND_CREATE, id = 0, current = "")

    fun onRename(folderId: Long) {
        val folder = loaded()?.folders?.firstOrNull { it.id == folderId } ?: return
        openName(kind = KIND_RENAME, id = folderId, current = folder.name)
    }

    fun onNameChange(value: String) {
        savedState[KEY_NAME] = value
        savedState[KEY_TOUCHED] = true
    }

    fun onSaveName() {
        val dialog = state.value.dialog as? FolderDialog.Name ?: return
        if (!dialog.canSave || !claim()) return
        val typed = dialog.name
        viewModelScope.launch {
            try {
                when (val result = if (dialog.renaming == null) createFolder(typed) else renameFolder(dialog.renaming, typed)) {
                    is FolderResult.Done -> {
                        closeDialog()
                        if (dialog.renaming == null) created.trySend(result.id)
                    }
                    FolderResult.Duplicate -> savedState[KEY_TAKEN] = typed
                    is FolderResult.Invalid -> savedState[KEY_TOUCHED] = true
                    FolderResult.Failed -> message.value = FolderMessage.SAVE_FAILED
                }
            } finally {
                busy.value = false
            }
        }
    }

    fun onDelete(folderId: Long) {
        if (loaded()?.folders?.none { it.id == folderId } != false) return
        open(KIND_DELETE, folderId)
        savedState[KEY_CHOICE] = FolderDeletion.MOVE_TO_UNFILED.name
    }

    fun onDeletionChoice(value: FolderDeletion) {
        if (!busy.value) savedState[KEY_CHOICE] = value.name
    }

    /** K4: moving goes at once; deleting documents asks a second time, unless the folder is empty. */
    fun onConfirmDelete() {
        val dialog = state.value.dialog as? FolderDialog.Delete ?: return
        if (dialog.choice == FolderDeletion.DELETE_DOCUMENTS && dialog.documents > 0) {
            if (busy.value) return
            // The count the user confirms, kept while the documents go one by one.
            savedState[KEY_COUNT] = dialog.documents
            open(KIND_CONFIRM, dialog.id)
        } else {
            runDelete(dialog.id, dialog.choice)
        }
    }

    fun onConfirmDocuments() {
        val dialog = state.value.dialog as? FolderDialog.ConfirmDocuments ?: return
        runDelete(dialog.id, FolderDeletion.DELETE_DOCUMENTS)
    }

    /** Nothing closes while it saves or deletes: the result must be seen. */
    fun onDismissDialog() {
        if (!busy.value) closeDialog()
    }

    fun onMessageShown() {
        message.value = null
        deleted.value = null
    }

    /** The result is in the state before the work stops being busy, so a screen never sees one without the other. */
    private fun runDelete(id: Long, deletion: FolderDeletion) {
        if (!claim()) return
        viewModelScope.launch {
            try {
                val result = deleteFolder(id, deletion)
                closeDialog()
                if (result.folderDeleted) deleted.value = id
                message.value = when {
                    result.failedDocuments > 0 -> FolderMessage.DELETE_PARTIAL
                    result.folderDeleted -> FolderMessage.DELETED
                    else -> FolderMessage.SAVE_FAILED
                }
            } finally {
                busy.value = false
            }
        }
    }

    /** True for the first tap only, until the work ends. */
    private fun claim(): Boolean {
        if (busy.value) return false
        busy.value = true
        return true
    }

    private fun loaded(): FolderList? = (folders.value as? FoldersResult.Loaded)?.folders

    private fun openName(kind: String, id: Long, current: String) {
        if (busy.value) return
        open(kind, id)
        savedState[KEY_NAME] = current
        savedState[KEY_TOUCHED] = false
        savedState[KEY_TAKEN] = null
    }

    private fun open(kind: String, id: Long) {
        savedState[KEY_ID] = id
        savedState[KEY_DIALOG] = kind
    }

    private fun closeDialog() {
        savedState[KEY_DIALOG] = null
        savedState[KEY_NAME] = ""
        savedState[KEY_TAKEN] = null
    }

    private class DialogInput(val kind: String?, val id: Long, val name: String, val touched: Boolean, val taken: String?)

    /** A dialog about a folder that is gone closes by itself (null). */
    private fun DialogInput.toDialog(choice: FolderDeletion, count: Int, folders: FolderList?): FolderDialog? {
        val folder = folders?.folders?.firstOrNull { it.id == id }
        return when (kind) {
            KIND_CREATE, KIND_RENAME -> {
                if (kind == KIND_RENAME && folders != null && folder == null) return null
                val check = FolderNames.check(name)
                val error = when {
                    taken != null && name == taken -> FolderFieldError.DUPLICATE
                    check == FolderNameCheck.Empty -> if (touched) FolderFieldError.EMPTY else null
                    check == FolderNameCheck.TooLong -> FolderFieldError.TOO_LONG
                    check == FolderNameCheck.Reserved -> FolderFieldError.RESERVED
                    else -> null
                }
                FolderDialog.Name(
                    renaming = id.takeIf { kind == KIND_RENAME },
                    name = name,
                    error = error,
                    canSave = check is FolderNameCheck.Valid && error == null,
                )
            }
            KIND_DELETE -> folder?.let { FolderDialog.Delete(it.id, it.name, it.documentCount, choice) }
            KIND_CONFIRM -> folder?.let { FolderDialog.ConfirmDocuments(it.id, count) }
            else -> null
        }
    }

    private companion object {
        const val KEY_DIALOG = "folders_dialog"
        const val KEY_ID = "folders_dialog_id"
        const val KEY_NAME = "folders_dialog_name"
        const val KEY_TOUCHED = "folders_dialog_touched"
        const val KEY_TAKEN = "folders_dialog_taken"
        const val KEY_CHOICE = "folders_dialog_choice"
        const val KEY_COUNT = "folders_dialog_count"
        const val KIND_CREATE = "create"
        const val KIND_RENAME = "rename"
        const val KIND_DELETE = "delete"
        const val KIND_CONFIRM = "confirm"
    }
}
