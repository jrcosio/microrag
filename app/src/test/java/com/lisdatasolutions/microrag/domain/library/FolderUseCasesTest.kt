package com.lisdatasolutions.microrag.domain.library

import com.google.common.truth.Truth.assertThat
import com.lisdatasolutions.microrag.core.di.ApplicationScope
import com.lisdatasolutions.microrag.domain.rag.Folder
import com.lisdatasolutions.microrag.domain.rag.FolderList
import com.lisdatasolutions.microrag.testing.FakeLibraryRepository
import com.lisdatasolutions.microrag.testing.RecordingLogger
import com.lisdatasolutions.microrag.testing.libraryDocument
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Test

/** F17 T014 (contract §2, research R4, FR-005, SC-006): the folder use cases on a fake Library. */
class FolderUseCasesTest {

    private val repository = FakeLibraryRepository()
    private val logger = RecordingLogger()
    private val applicationScope = ApplicationScope(CoroutineScope(SupervisorJob() + Dispatchers.Unconfined))

    private val create = CreateFolderUseCase(repository)
    private val rename = RenameFolderUseCase(repository)
    private val delete = DeleteFolderUseCase(repository, applicationScope, logger)

    private fun withLegal() {
        repository.folderRows.value = listOf(Folder(1, "Legal", createdAtMs = 10, documentCount = 0))
        repository.documents.value = listOf(
            libraryDocument(11, "Contrato secreto.pdf", folderId = 1, folderName = "Legal"),
            libraryDocument(12, "Garantía.pdf", folderId = 1, folderName = "Legal"),
            libraryDocument(13, "Manual.pdf"),
        )
    }

    @Test
    fun createGivesDoneDuplicateInvalidOrFailed() = runBlocking<Unit> {
        assertThat(create("  Legal ")).isEqualTo(FolderResult.Done(1))
        assertThat(repository.calls).containsExactly("createFolder Legal")
        assertThat(create("LÉGAL")).isEqualTo(FolderResult.Duplicate)
        assertThat(create(" ")).isEqualTo(FolderResult.Invalid(FolderNameCheck.Empty))
        assertThat(create("general")).isEqualTo(FolderResult.Invalid(FolderNameCheck.Reserved))
        assertThat(create("x".repeat(41))).isEqualTo(FolderResult.Invalid(FolderNameCheck.TooLong))
        // Invalid names never reach the index.
        assertThat(repository.calls).hasSize(2)

        repository.nextFolderResult = FolderResult.Failed
        assertThat(create("Motores")).isEqualTo(FolderResult.Failed)
    }

    @Test
    fun renameGivesDoneDuplicateInvalidOrFailed() = runBlocking<Unit> {
        withLegal()
        repository.folderRows.value += Folder(2, "Motores", createdAtMs = 20, documentCount = 0)

        assertThat(rename(1, "legal")).isEqualTo(FolderResult.Done(1))
        assertThat(repository.folderRows.value.first().name).isEqualTo("legal")
        assertThat(rename(1, "MOTORES")).isEqualTo(FolderResult.Duplicate)
        assertThat(rename(1, "General")).isEqualTo(FolderResult.Invalid(FolderNameCheck.Reserved))

        repository.nextFolderResult = FolderResult.Failed
        assertThat(rename(1, "Contratos")).isEqualTo(FolderResult.Failed)
    }

    @Test
    fun movingTheDocumentsLeavesThemAllInGeneral() = runBlocking<Unit> {
        withLegal()

        val result = delete(1, FolderDeletion.MOVE_TO_UNFILED)

        assertThat(result).isEqualTo(FolderDeletionResult(folderDeleted = true, failedDocuments = 0))
        assertThat(repository.calls).containsExactly("deleteFolder 1 true")
        assertThat(repository.documents.value.map { it.id }).containsExactly(11L, 12L, 13L)
        assertThat(repository.documents.value.all { it.folderId == null }).isTrue()
    }

    @Test
    fun deletingTheDocumentsTakesEachByTheDeletePath_thenTheEmptyFolder() = runBlocking<Unit> {
        withLegal()

        val result = delete(1, FolderDeletion.DELETE_DOCUMENTS)

        assertThat(result).isEqualTo(FolderDeletionResult(folderDeleted = true, failedDocuments = 0))
        assertThat(repository.calls).containsExactly("delete 11", "delete 12", "deleteFolder 1 false").inOrder()
        assertThat(repository.documents.value.map { it.id }).containsExactly(13L)
        assertThat(repository.folders().first()).isEqualTo(FoldersResult.Loaded(FolderList(emptyList(), 1)))
    }

    /** M4: a document that could not go keeps the folder, and is counted. */
    @Test
    fun documentsThatFailStayWithTheirFolder() = runBlocking<Unit> {
        withLegal()
        repository.undeletable = setOf(12)

        val result = delete(1, FolderDeletion.DELETE_DOCUMENTS)

        assertThat(result).isEqualTo(FolderDeletionResult(folderDeleted = false, failedDocuments = 1))
        assertThat(repository.calls).doesNotContain("deleteFolder 1 false")
        assertThat(repository.documents.value.single { it.id == 12L }.folderId).isEqualTo(1)
        assertThat(repository.folderRows.value.map { it.id }).containsExactly(1L)
    }

    @Test
    fun aFolderThatCannotGoIsNotDeleted() = runBlocking<Unit> {
        withLegal()
        repository.failDeleteFolder = true

        assertThat(delete(1, FolderDeletion.MOVE_TO_UNFILED).folderDeleted).isFalse()
    }

    /** FR-025: ids and counts only. */
    @Test
    fun theLogHasNoNames() = runBlocking<Unit> {
        withLegal()

        delete(1, FolderDeletion.DELETE_DOCUMENTS)

        assertThat(logger.lines).isNotEmpty()
        logger.lines.forEach { line ->
            assertThat(line).doesNotContain("Legal")
            assertThat(line).doesNotContain("Contrato")
        }
    }
}
