package com.lisdatasolutions.microrag.feature.chat.scope

import androidx.compose.ui.state.ToggleableState
import androidx.lifecycle.SavedStateHandle
import com.google.common.truth.Truth.assertThat
import com.lisdatasolutions.microrag.domain.chat.ChatScope
import com.lisdatasolutions.microrag.domain.library.FolderDocumentRefsUseCase
import com.lisdatasolutions.microrag.domain.library.LibraryDocumentState
import com.lisdatasolutions.microrag.domain.library.ObserveFoldersUseCase
import com.lisdatasolutions.microrag.domain.library.ObserveLibraryUseCase
import com.lisdatasolutions.microrag.domain.rag.DocumentRef
import com.lisdatasolutions.microrag.domain.rag.Folder
import com.lisdatasolutions.microrag.domain.rag.FolderRef
import com.lisdatasolutions.microrag.testing.FakeLibraryRepository
import com.lisdatasolutions.microrag.testing.libraryDocument
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

/** F17 T034 (K8, wireframe §6, M6, D-072): the «Buscar en» selector. */
@OptIn(ExperimentalCoroutinesApi::class)
class ScopeSelectorViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val library = FakeLibraryRepository()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        library.folderRows.value = listOf(Folder(1, "Legal", 10, 0), Folder(2, "Motores", 20, 0), Folder(3, "Vacía", 30, 0), Folder(4, "Archivo", 40, 0))
        library.documents.value = listOf(
            libraryDocument(11, "Contrato.pdf", folderId = 1),
            libraryDocument(12, "Garantía.pdf", folderId = 1),
            libraryDocument(13, "Anexo.pdf", folderId = 1, included = false),
            libraryDocument(21, "Bomba.pdf", folderId = 2).copy(state = LibraryDocumentState.Pending),
            libraryDocument(41, "Viejo.pdf", folderId = 4, included = false),
            libraryDocument(51, "Manual.pdf"),
        )
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(saved: SavedStateHandle = SavedStateHandle()) = ScopeSelectorViewModel(
        saved, ObserveFoldersUseCase(library), ObserveLibraryUseCase(library), FolderDocumentRefsUseCase(library), dispatcher,
    )

    private fun ref(id: Long) = DocumentRef(id, id * 10)
    private val legal = FolderRef(1, 10)

    private fun ScopeSelectorViewModel.row(id: Long?) = state.value.folders!!.single { it.id == id }

    @Test
    fun onlyFoldersWithDocumentsAreOffered_GeneralLast() = runTest(dispatcher) {
        val vm = viewModel()
        vm.start(ChatScope.WholeLibrary)
        advanceUntilIdle()

        val rows = vm.state.value.folders!!
        assertThat(rows.map { it.name }).containsExactly("Archivo", "Legal", "Motores", null).inOrder()
        assertThat(rows.map { it.count }).containsExactly(1, 3, 1, 1).inOrder()
        assertThat(vm.state.value.whole).isTrue()
        assertThat(vm.state.value.canApply).isTrue()
        // D-072: every document excluded, so it cannot be checked.
        assertThat(vm.row(4).selectable).isFalse()
        assertThat(vm.row(1).selectable).isTrue()
    }

    @Test
    fun checkingAFolderUnchecksTheWholeLibrary_andTheWholeLibraryUnchecksEverything() = runTest(dispatcher) {
        val vm = viewModel()
        vm.start(ChatScope.WholeLibrary)
        advanceUntilIdle()

        vm.onToggleFolder(1)
        advanceUntilIdle()
        assertThat(vm.state.value.whole).isFalse()
        assertThat(vm.row(1).check).isEqualTo(ToggleableState.On)
        assertThat(vm.result()).isEqualTo(ChatScope.Some(setOf(legal), general = false, documents = emptySet()))

        vm.onWholeLibrary()
        advanceUntilIdle()
        assertThat(vm.row(1).check).isEqualTo(ToggleableState.Off)
        assertThat(vm.result()).isEqualTo(ChatScope.WholeLibrary)
    }

    @Test
    fun generalAndLooseDocuments_andTheStateInBetween() = runTest(dispatcher) {
        val vm = viewModel()
        vm.start(ChatScope.Some(emptySet(), general = true, documents = setOf(ref(21))))
        advanceUntilIdle()

        assertThat(vm.row(null).check).isEqualTo(ToggleableState.On)
        assertThat(vm.row(2).check).isEqualTo(ToggleableState.Indeterminate)

        vm.onExpand(2)
        advanceUntilIdle()
        val bomb = vm.row(2).documents!!.single()
        assertThat(bomb.checked).isTrue()
        // Pending: it can be checked, and says it is not indexed yet.
        assertThat(bomb.selectable).isTrue()
        assertThat(bomb.note).isEqualTo(ScopeNote.PENDING)

        vm.onToggleDocument(2, ref(21))
        vm.onToggleFolder(null)
        advanceUntilIdle()
        assertThat(vm.state.value.canApply).isFalse()
        assertThat(vm.result()).isNull()
    }

    /** M6 (D-072): unchecking one document of a whole folder keeps the others as loose documents. */
    @Test
    fun uncheckingADocumentOfAWholeFolderTurnsTheRestIntoLooseDocuments() = runTest(dispatcher) {
        val vm = viewModel()
        vm.start(ChatScope.Some(setOf(legal), general = false, documents = emptySet()))
        advanceUntilIdle()
        vm.onExpand(1)
        advanceUntilIdle()
        assertThat(vm.row(1).documents!!.filter { it.checked }.map { it.name }).containsExactly("Contrato.pdf", "Garantía.pdf")

        vm.onToggleDocument(1, ref(11))
        advanceUntilIdle()

        assertThat(vm.row(1).check).isEqualTo(ToggleableState.Indeterminate)
        val scope = vm.result() as ChatScope.Some
        assertThat(scope.folders).isEmpty()
        assertThat(scope.documents).containsAtLeast(ref(12), ref(13))
        assertThat(scope.documents).doesNotContain(ref(11))
    }

    @Test
    fun excludedDocumentsAreShownButCannotBeChecked() = runTest(dispatcher) {
        val vm = viewModel()
        vm.start(ChatScope.Some(setOf(legal), general = false, documents = emptySet()))
        vm.onExpand(1)
        advanceUntilIdle()

        val annex = vm.row(1).documents!!.single { it.name == "Anexo.pdf" }
        assertThat(annex.selectable).isFalse()
        assertThat(annex.checked).isFalse()
        assertThat(annex.note).isEqualTo(ScopeNote.NOT_INCLUDED)

        vm.onToggleFolder(4)
        advanceUntilIdle()
        assertThat(vm.row(4).check).isEqualTo(ToggleableState.Off)
    }

    @Test
    fun anOpenFolderLoadsInBlocks() = runTest(dispatcher) {
        library.documents.value = (1L..60L).map { libraryDocument(100 + it, "Doc $it.pdf", folderId = 1) }
        val vm = viewModel()
        vm.start(ChatScope.WholeLibrary)
        vm.onExpand(1)
        advanceUntilIdle()
        assertThat(vm.row(1).documents).hasSize(50)
        assertThat(vm.row(1).hasMore).isTrue()

        vm.onLoadMore(1)
        advanceUntilIdle()

        assertThat(vm.row(1).documents).hasSize(60)
        assertThat(vm.row(1).hasMore).isFalse()
        vm.onExpand(1)
        advanceUntilIdle()
        assertThat(vm.row(1).expanded).isFalse()
        assertThat(vm.row(1).documents).isNull()
    }

    /** A rotation or a process death keeps what is checked and what is open. */
    @Test
    fun theSelectionLivesInTheSavedStateHandle() = runTest(dispatcher) {
        val saved = SavedStateHandle()
        val vm = viewModel(saved)
        vm.start(ChatScope.WholeLibrary)
        advanceUntilIdle()
        vm.onToggleFolder(1)
        vm.onExpand(2)
        advanceUntilIdle()

        val restored = viewModel(saved)
        advanceUntilIdle()

        assertThat(restored.row(1).check).isEqualTo(ToggleableState.On)
        assertThat(restored.row(2).expanded).isTrue()
        assertThat(restored.result()).isEqualTo(ChatScope.Some(setOf(legal), general = false, documents = emptySet()))
    }

    @Test
    fun theCodecReadsWhatItWrites() {
        val selection = ScopeSelection(whole = false, folders = setOf(legal, FolderRef(2, 20)), general = true, documents = setOf(ref(7)))

        assertThat(ScopeSelection.decode(selection.encode())).isEqualTo(selection)
        assertThat(ScopeSelection.decode(ScopeSelection(whole = true).encode())).isEqualTo(ScopeSelection(whole = true))
        assertThat(ScopeSelection.decode(ScopeSelection(whole = false).encode())!!.isEmpty).isTrue()
        assertThat(ScopeSelection.decode("basura")).isNull()
    }
}
