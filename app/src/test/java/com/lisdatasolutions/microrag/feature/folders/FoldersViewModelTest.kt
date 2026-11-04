package com.lisdatasolutions.microrag.feature.folders

import androidx.lifecycle.SavedStateHandle
import com.google.common.truth.Truth.assertThat
import com.lisdatasolutions.microrag.core.di.ApplicationScope
import com.lisdatasolutions.microrag.domain.library.CreateFolderUseCase
import com.lisdatasolutions.microrag.domain.library.DeleteFolderUseCase
import com.lisdatasolutions.microrag.domain.library.FolderDeletion
import com.lisdatasolutions.microrag.domain.library.FolderResult
import com.lisdatasolutions.microrag.domain.library.ObserveFoldersUseCase
import com.lisdatasolutions.microrag.domain.library.RenameFolderUseCase
import com.lisdatasolutions.microrag.domain.rag.Folder
import com.lisdatasolutions.microrag.testing.FakeLibraryRepository
import com.lisdatasolutions.microrag.testing.RecordingLogger
import com.lisdatasolutions.microrag.testing.libraryDocument
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

/** F17 T015 (data-model §4, wireframe §1–§3): the folders, their dialogs in the SavedStateHandle and the notices. */
@OptIn(ExperimentalCoroutinesApi::class)
class FoldersViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val repository = FakeLibraryRepository()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(saved: SavedStateHandle = SavedStateHandle()) = FoldersViewModel(
        saved,
        ObserveFoldersUseCase(repository),
        CreateFolderUseCase(repository),
        RenameFolderUseCase(repository),
        DeleteFolderUseCase(repository, ApplicationScope(CoroutineScope(SupervisorJob() + dispatcher)), RecordingLogger()),
    )

    private fun withLegal() {
        repository.folderRows.value = listOf(Folder(1, "Legal", createdAtMs = 10, documentCount = 0), Folder(2, "Motores", 20, 0))
        repository.documents.value = listOf(
            libraryDocument(11, "Contrato.pdf", folderId = 1, folderName = "Legal"),
            libraryDocument(12, "Garantía.pdf", folderId = 1, folderName = "Legal"),
            libraryDocument(13, "Manual.pdf"),
        )
    }

    @Test
    fun theFoldersComeWithTheirCountsAndGeneral() = runTest(dispatcher) {
        withLegal()
        val vm = viewModel()
        advanceUntilIdle()

        val folders = vm.state.value.folders!!
        assertThat(folders.folders.map { it.name to it.documentCount }).containsExactly("Legal" to 2, "Motores" to 0).inOrder()
        assertThat(folders.unfiledCount).isEqualTo(1)
    }

    @Test
    fun anUnavailableIndexSaysSo() = runTest(dispatcher) {
        repository.foldersUnavailable = true
        val vm = viewModel()
        advanceUntilIdle()

        assertThat(vm.state.value.unavailable).isTrue()
        assertThat(vm.state.value.folders).isNull()
    }

    @Test
    fun creating_checksTheNameAsItIsTyped_andClosesOnceDone() = runTest(dispatcher) {
        withLegal()
        val vm = viewModel()
        advanceUntilIdle()
        vm.onNewFolder()
        advanceUntilIdle()
        assertThat(vm.state.value.dialog).isEqualTo(FolderDialog.Name(null, "", error = null, canSave = false))

        vm.onNameChange("General")
        advanceUntilIdle()
        assertThat((vm.state.value.dialog as FolderDialog.Name).error).isEqualTo(FolderFieldError.RESERVED)
        vm.onNameChange("x".repeat(41))
        advanceUntilIdle()
        assertThat((vm.state.value.dialog as FolderDialog.Name).error).isEqualTo(FolderFieldError.TOO_LONG)
        vm.onNameChange("  ")
        advanceUntilIdle()
        assertThat((vm.state.value.dialog as FolderDialog.Name).error).isEqualTo(FolderFieldError.EMPTY)

        vm.onNameChange("Contratos")
        advanceUntilIdle()
        assertThat((vm.state.value.dialog as FolderDialog.Name).canSave).isTrue()
        val created = mutableListOf<Long>()
        backgroundScope.launch { vm.createdFolders.collect { created += it } }
        vm.onSaveName()
        advanceUntilIdle()

        assertThat(vm.state.value.dialog).isNull()
        assertThat(repository.folderRows.value.map { it.name }).contains("Contratos")
        assertThat(created).containsExactly(3L)
    }

    @Test
    fun aDuplicateKeepsTheDialogOpenWithItsNotice_untilTheNameChanges() = runTest(dispatcher) {
        withLegal()
        val vm = viewModel()
        advanceUntilIdle()
        vm.onNewFolder()
        vm.onNameChange("LEGAL")
        advanceUntilIdle()

        vm.onSaveName()
        advanceUntilIdle()
        assertThat(vm.state.value.dialog).isEqualTo(FolderDialog.Name(null, "LEGAL", FolderFieldError.DUPLICATE, canSave = false))

        vm.onNameChange("LEGALES")
        advanceUntilIdle()
        assertThat((vm.state.value.dialog as FolderDialog.Name).error).isNull()
    }

    @Test
    fun aFailedSaveSaysSo() = runTest(dispatcher) {
        withLegal()
        repository.nextFolderResult = FolderResult.Failed
        val vm = viewModel()
        advanceUntilIdle()
        vm.onNewFolder()
        vm.onNameChange("Contratos")
        advanceUntilIdle()

        vm.onSaveName()
        advanceUntilIdle()

        assertThat(vm.state.value.message).isEqualTo(FolderMessage.SAVE_FAILED)
        assertThat(vm.state.value.dialog).isInstanceOf(FolderDialog.Name::class.java)
        vm.onMessageShown()
        advanceUntilIdle()
        assertThat(vm.state.value.message).isNull()
    }

    @Test
    fun renaming_startsFromTheCurrentName() = runTest(dispatcher) {
        withLegal()
        val vm = viewModel()
        advanceUntilIdle()

        vm.onRename(1)
        advanceUntilIdle()
        assertThat(vm.state.value.dialog).isEqualTo(FolderDialog.Name(renaming = 1, name = "Legal", error = null, canSave = true))

        vm.onNameChange("Contratos")
        advanceUntilIdle()
        vm.onSaveName()
        advanceUntilIdle()
        assertThat(repository.calls).containsExactly("renameFolder 1 Contratos")
    }

    /** A rotation or a process death keeps the dialog and what was typed. */
    @Test
    fun theDialogLivesInTheSavedStateHandle() = runTest(dispatcher) {
        withLegal()
        val saved = SavedStateHandle()
        val vm = viewModel(saved)
        advanceUntilIdle()
        vm.onNewFolder()
        vm.onNameChange("Contra")
        advanceUntilIdle()

        val restored = viewModel(saved)
        advanceUntilIdle()

        assertThat(restored.state.value.dialog).isEqualTo(FolderDialog.Name(null, "Contra", error = null, canSave = true))
    }

    @Test
    fun aDoubleTapSavesOnce() = runTest(dispatcher) {
        withLegal()
        val gate = CompletableDeferred<Unit>()
        repository.folderGate = gate
        val vm = viewModel()
        advanceUntilIdle()
        vm.onNewFolder()
        vm.onNameChange("Contratos")
        advanceUntilIdle()

        vm.onSaveName()
        vm.onSaveName()
        advanceUntilIdle()
        assertThat(vm.state.value.busy).isTrue()
        vm.onDismissDialog()
        gate.complete(Unit)
        advanceUntilIdle()

        assertThat(repository.calls).containsExactly("createFolder Contratos")
        assertThat(vm.state.value.busy).isFalse()
        assertThat(vm.state.value.dialog).isNull()
    }

    @Test
    fun deleting_movesByDefault_andSaysItIsDeleted() = runTest(dispatcher) {
        withLegal()
        val vm = viewModel()
        advanceUntilIdle()

        vm.onDelete(1)
        advanceUntilIdle()
        assertThat(vm.state.value.dialog).isEqualTo(FolderDialog.Delete(1, "Legal", 2, FolderDeletion.MOVE_TO_UNFILED))
        vm.onConfirmDelete()
        advanceUntilIdle()

        assertThat(repository.calls).containsExactly("deleteFolder 1 true")
        assertThat(vm.state.value.dialog).isNull()
        assertThat(vm.state.value.message).isEqualTo(FolderMessage.DELETED)
        assertThat(vm.state.value.deletedFolder).isEqualTo(1)
    }

    @Test
    fun deletingTheDocuments_asksASecondTime() = runTest(dispatcher) {
        withLegal()
        val vm = viewModel()
        advanceUntilIdle()
        vm.onDelete(1)
        vm.onDeletionChoice(FolderDeletion.DELETE_DOCUMENTS)
        advanceUntilIdle()

        vm.onConfirmDelete()
        advanceUntilIdle()
        assertThat(vm.state.value.dialog).isEqualTo(FolderDialog.ConfirmDocuments(1, 2))
        assertThat(repository.calls).isEmpty()

        vm.onConfirmDocuments()
        advanceUntilIdle()
        assertThat(repository.calls).containsExactly("delete 11", "delete 12", "deleteFolder 1 false").inOrder()
        assertThat(vm.state.value.message).isEqualTo(FolderMessage.DELETED)
    }

    @Test
    fun documentsThatCouldNotGo_keepTheFolderAndSaySo() = runTest(dispatcher) {
        withLegal()
        repository.undeletable = setOf(12)
        val vm = viewModel()
        advanceUntilIdle()
        vm.onDelete(1)
        vm.onDeletionChoice(FolderDeletion.DELETE_DOCUMENTS)
        advanceUntilIdle()
        vm.onConfirmDelete()
        advanceUntilIdle()

        vm.onConfirmDocuments()
        advanceUntilIdle()

        assertThat(vm.state.value.message).isEqualTo(FolderMessage.DELETE_PARTIAL)
        assertThat(vm.state.value.deletedFolder).isNull()
        assertThat(vm.state.value.folders!!.folders.map { it.id }).contains(1L)
    }

    @Test
    fun anEmptyFolderGoesWithoutTheSecondNotice() = runTest(dispatcher) {
        withLegal()
        val vm = viewModel()
        advanceUntilIdle()
        vm.onDelete(2)
        vm.onDeletionChoice(FolderDeletion.DELETE_DOCUMENTS)
        advanceUntilIdle()
        vm.onConfirmDelete()
        advanceUntilIdle()

        assertThat(repository.calls).containsExactly("deleteFolder 2 false")
    }
}
