package com.lisdatasolutions.microrag.domain.chat

import com.lisdatasolutions.microrag.domain.rag.FolderRef
import com.lisdatasolutions.microrag.domain.rag.RagIndex
import com.lisdatasolutions.microrag.domain.rag.SearchScope

/**
 * F17 S1 (research R8): a saved scope as it means now. A folder counts while it exists with the same
 * creation time, «General» always, and a document only with its own id and creation time (D-065). If
 * nothing is left, it is the whole library again (FR-021).
 */
class ResolveScopeUseCase(private val index: RagIndex) {
    suspend operator fun invoke(scope: ChatScope): ResolvedScope {
        if (scope !is ChatScope.Some) return ResolvedScope(scope)
        val existing = if (scope.folders.isEmpty()) emptySet() else index.folders().folders.map { FolderRef(it.id, it.createdAtMs) }.toSet()
        val folders = scope.folders.filterTo(LinkedHashSet()) { it in existing }
        val documents = scope.documents.filterTo(LinkedHashSet()) { ref ->
            index.document(ref.id)?.let { it.createdAtMs == ref.createdAtMs && it.replacesId == null } == true
        }
        val droppedFolders = scope.folders.size - folders.size
        val droppedDocuments = scope.documents.size - documents.size
        if (folders.isEmpty() && !scope.general && documents.isEmpty()) {
            return ResolvedScope(ChatScope.WholeLibrary, droppedFolders, droppedDocuments, fellBackToWholeLibrary = true)
        }
        return ResolvedScope(ChatScope.Some(folders, scope.general, documents), droppedFolders, droppedDocuments)
    }
}

/** S2: what the searches of one question receive; folders by id, as they are now (FR-016). */
fun ChatScope.toSearchScope(): SearchScope = when (this) {
    ChatScope.WholeLibrary -> SearchScope.All
    is ChatScope.Some -> SearchScope.Only(
        folderIds = folders.mapTo(LinkedHashSet()) { it.id },
        general = general,
        documentIds = documents.mapTo(LinkedHashSet()) { it.id },
    )
}

/** S1: the scope of a conversation as it means now, for its chip and its notices (K10). */
class ConversationScopeUseCase(private val session: ChatSession) {
    suspend operator fun invoke(conversationId: Long): ResolvedScope = session.scopeOf(conversationId)
}

/** FR-020b: what «Nuevo chat» starts with. */
class LastScopeUseCase(private val session: ChatSession) {
    suspend operator fun invoke(): ResolvedScope = session.lastScope()
}

/** S3: the selector's «Aplicar»; false when `chat.db` refused it. A chat without questions only keeps it as the last one. */
class ApplyScopeUseCase(private val session: ChatSession) {
    suspend operator fun invoke(conversationId: Long?, scope: ChatScope): Boolean = try {
        if (conversationId == null) session.setLastScope(scope) else session.setScope(conversationId, scope)
        true
    } catch (e: ChatStorageException) {
        false
    }
}
