package com.lisdatasolutions.microrag.domain.library

import com.lisdatasolutions.microrag.core.di.ApplicationScope
import com.lisdatasolutions.microrag.core.logging.AppLogger
import com.lisdatasolutions.microrag.domain.rag.DocumentRef
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow

// F17 folders in the Library (contracts/folders-scope-api.md §2). Names never go to the log (FR-025).

class ObserveFoldersUseCase(private val repository: LibraryRepository) {
    operator fun invoke(): Flow<FoldersResult> = repository.folders()
}

class CreateFolderUseCase(private val repository: LibraryRepository) {
    suspend operator fun invoke(name: String): FolderResult = when (val check = FolderNames.check(name)) {
        is FolderNameCheck.Valid -> repository.createFolder(check.name, check.norm)
        else -> FolderResult.Invalid(check)
    }
}

class RenameFolderUseCase(private val repository: LibraryRepository) {
    suspend operator fun invoke(id: Long, name: String): FolderResult = when (val check = FolderNames.check(name)) {
        is FolderNameCheck.Valid -> repository.renameFolder(id, check.name, check.norm)
        else -> FolderResult.Invalid(check)
    }
}

/** Every document of a folder, or of «General» with null; null when the index is unavailable (M4, M6). */
class FolderDocumentRefsUseCase(private val repository: LibraryRepository) {
    suspend operator fun invoke(folderId: Long?): List<DocumentRef>? = repository.folderDocumentRefs(folderId)
}

/** F4 (K5): only the folder changes; the state, the inclusion and the chunks stay. */
class MoveDocumentUseCase(private val repository: LibraryRepository) {
    suspend operator fun invoke(documentId: Long, folderId: Long?): Boolean = repository.moveDocument(documentId, folderId)
}

/**
 * F3 (research R4). Moving takes one transaction. Deleting takes each document by the only delete path
 * (rule L4), then the folder only if it is empty; what is left is what failed (M4). It runs in the
 * application scope, so leaving the screen does not stop it halfway.
 */
class DeleteFolderUseCase(
    private val repository: LibraryRepository,
    private val applicationScope: ApplicationScope,
    private val logger: AppLogger,
) {
    suspend operator fun invoke(id: Long, deletion: FolderDeletion): FolderDeletionResult =
        applicationScope.scope.async { delete(id, deletion) }.await()

    private suspend fun delete(id: Long, deletion: FolderDeletion): FolderDeletionResult {
        val result = when (deletion) {
            FolderDeletion.MOVE_TO_UNFILED -> FolderDeletionResult(repository.deleteFolder(id, moveDocuments = true), failedDocuments = 0)
            FolderDeletion.DELETE_DOCUMENTS -> {
                val documents = repository.folderDocumentRefs(id) ?: return FolderDeletionResult(false, failedDocuments = 0)
                documents.forEach { repository.delete(it.id) }
                val left = repository.folderDocumentRefs(id)?.size ?: documents.size
                FolderDeletionResult(folderDeleted = left == 0 && repository.deleteFolder(id, moveDocuments = false), failedDocuments = left)
            }
        }
        logger.i(TAG) { "library folder delete id=$id mode=${deletion.name} deleted=${result.folderDeleted} failed=${result.failedDocuments}" }
        return result
    }

    private companion object {
        const val TAG = "MicroRAG"
    }
}
