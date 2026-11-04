package com.lisdatasolutions.microrag.domain.chat

import com.google.common.truth.Truth.assertThat
import com.lisdatasolutions.microrag.domain.rag.DocumentRef
import com.lisdatasolutions.microrag.domain.rag.FolderRef
import com.lisdatasolutions.microrag.domain.rag.NewDocument
import com.lisdatasolutions.microrag.domain.rag.SearchScope
import com.lisdatasolutions.microrag.testing.FakeRagIndex
import kotlinx.coroutines.test.runTest
import org.junit.Test

/** F17 T031 and T043 (contract S1, S2; research R8; D-065): a saved scope as it means now. */
class ScopeUseCasesTest {

    private val index = FakeRagIndex()
    private val resolve = ResolveScopeUseCase(index)

    private suspend fun folder(name: String): FolderRef {
        val id = checkNotNull(index.createFolder(name, name.lowercase()))
        return FolderRef(id, index.folderRows.getValue(id).createdAtMs)
    }

    private suspend fun document(name: String): DocumentRef {
        val id = index.addDocument(NewDocument(displayName = name, contentHash = name))
        return DocumentRef(id, index.docs.getValue(id).createdAtMs)
    }

    @Test
    fun theWholeLibraryStaysAsItIs() = runTest {
        assertThat(resolve(ChatScope.WholeLibrary)).isEqualTo(ResolvedScope(ChatScope.WholeLibrary))
    }

    @Test
    fun whatStillExistsIsKept() = runTest {
        val legal = folder("Legal")
        val manual = document("Manual.txt")
        val scope = ChatScope.Some(setOf(legal), general = true, documents = setOf(manual))

        val resolved = resolve(scope)

        assertThat(resolved).isEqualTo(ResolvedScope(scope))
        assertThat(resolved.changed).isFalse()
    }

    /** D-065: a document id reused by another document never matches. */
    @Test
    fun aDocumentCountsOnlyWithItsOwnCreationTime() = runTest {
        val legal = folder("Legal")
        val manual = document("Manual.txt")
        val reused = DocumentRef(manual.id, manual.createdAtMs - 1)
        val gone = DocumentRef(999, 1)

        val resolved = resolve(ChatScope.Some(setOf(legal), general = false, documents = setOf(manual, reused, gone)))

        assertThat(resolved.scope).isEqualTo(ChatScope.Some(setOf(legal), general = false, documents = setOf(manual)))
        assertThat(resolved.droppedDocuments).isEqualTo(2)
        assertThat(resolved.droppedFolders).isEqualTo(0)
        assertThat(resolved.changed).isTrue()
    }

    /** L9: a folder by id and creation time; a deleted one falls out. */
    @Test
    fun aDeletedFolderFallsOut() = runTest {
        val legal = folder("Legal")
        val motors = folder("Motores")
        index.deleteFolder(motors.id, moveDocuments = true)

        val resolved = resolve(ChatScope.Some(setOf(legal, motors, FolderRef(legal.id, legal.createdAtMs + 5)), general = false, documents = emptySet()))

        assertThat(resolved.scope).isEqualTo(ChatScope.Some(setOf(legal), general = false, documents = emptySet()))
        assertThat(resolved.droppedFolders).isEqualTo(2)
    }

    /** FR-021: nothing left is the whole library again, and says so. */
    @Test
    fun nothingLeftIsTheWholeLibraryAgain() = runTest {
        val motors = folder("Motores")
        val manual = document("Manual.txt")
        index.deleteFolder(motors.id, moveDocuments = true)
        index.deleteDocument(manual.id)

        val resolved = resolve(ChatScope.Some(setOf(motors), general = false, documents = setOf(manual)))

        assertThat(resolved).isEqualTo(ResolvedScope(ChatScope.WholeLibrary, droppedFolders = 1, droppedDocuments = 1, fellBackToWholeLibrary = true))
    }

    /** «General» always exists: it is every document without a folder (D-072). */
    @Test
    fun generalNeverFallsOut() = runTest {
        val resolved = resolve(ChatScope.Some(emptySet(), general = true, documents = emptySet()))

        assertThat(resolved.changed).isFalse()
    }

    /** S2 and FR-016: folders go by id, so a folder means what it holds at each question. */
    @Test
    fun theSearchScopeHasTheIds() {
        val scope = ChatScope.Some(setOf(FolderRef(3, 30)), general = true, documents = setOf(DocumentRef(7, 70)))

        assertThat(scope.toSearchScope()).isEqualTo(SearchScope.Only(setOf(3), general = true, documentIds = setOf(7)))
        assertThat(ChatScope.WholeLibrary.toSearchScope()).isEqualTo(SearchScope.All)
    }
}
