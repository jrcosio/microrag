package com.lisdatasolutions.microrag.domain.chat

import com.lisdatasolutions.microrag.domain.indexing.IndexingState
import com.lisdatasolutions.microrag.domain.indexing.ObserveIndexingUseCase
import com.lisdatasolutions.microrag.domain.model.ComputeBackend
import com.lisdatasolutions.microrag.domain.model.LocalAiStatus
import com.lisdatasolutions.microrag.domain.multimodal.MultimodalStage
import com.lisdatasolutions.microrag.domain.multimodal.MultimodalStore
import com.lisdatasolutions.microrag.domain.onboarding.ObserveLocalAiUseCase
import com.lisdatasolutions.microrag.domain.rag.ChatLibraryState
import com.lisdatasolutions.microrag.domain.rag.IndexCompatibility
import com.lisdatasolutions.microrag.domain.rag.IndexUnavailableException
import com.lisdatasolutions.microrag.domain.rag.PageKey
import com.lisdatasolutions.microrag.domain.rag.RagIndex
import com.lisdatasolutions.microrag.domain.rag.SearchScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onStart

/**
 * Whether the chat can be used (F11 research R11), from the real state of the saved conversations, the
 * models, the indexing batch and the library. Nothing is emitted before every source has been looked at
 * (constitution V); [retries] reopens the index check after «Reintentar» (C8).
 */
class ObserveChatAvailabilityUseCase(
    private val chats: ChatRepository,
    private val observeLocalAi: ObserveLocalAiUseCase,
    private val engine: AnswerEngine,
    private val observeIndexing: ObserveIndexingUseCase,
    private val index: RagIndex,
    /** F20 (contract M3): with the mode on, the documents with pages only can be asked too. */
    private val multimodal: MultimodalStore,
) {
    /** F17 (contract F8): [scope] is what the chat asks; the global states come first (F17 research R8, M5). */
    operator fun invoke(retries: Flow<Int> = flowOf(0), scope: SearchScope = SearchScope.All): Flow<ChatAvailability> = flow {
        if (chats.open() != ChatStorageState.OK) {
            emit(ChatAvailability.StorageUnavailable)
            return@flow
        }
        val indexing = observeIndexing().filter { it != IndexingState.Loading }
        val pagesInChat = combine(multimodal.state, index.pagesUsable) { stored, usable -> stored.stage == MultimodalStage.ON && usable }
        emitAll(
            combine(observeLocalAi(), engine.preparedBackend(), indexing, library(retries, scope), pagesInChat) { ai, backend, run, library, pages ->
                decide(ai, backend, run, library, scoped = scope is SearchScope.Only, pagesInChat = pages)
            },
        )
    }.distinctUntilChanged()

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun library(retries: Flow<Int>, scope: SearchScope): Flow<ChatLibraryState?> = retries.flatMapLatest {
        flow {
            if (index.open() != IndexCompatibility.Compatible) {
                emit(null)
                return@flow
            }
            emitAll(index.documentChanges.mapLatest { index.chatLibraryState(scope) })
        }.catch { error -> if (error is IndexUnavailableException) emit(null) else throw error }
    }

    /**
     * Precedence of research R11, and F17 R8 for a scope: an empty library and a running batch are said for the
     * whole library first; then nothing ready, in the library or in the scope. A null [library] is an index that
     * cannot be read.
     */
    private fun decide(
        ai: LocalAiStatus,
        backend: ComputeBackend?,
        indexing: IndexingState,
        library: ChatLibraryState?,
        scoped: Boolean,
        pagesInChat: Boolean,
    ): ChatAvailability =
        when {
            ai != LocalAiStatus.READY || backend == null -> ChatAvailability.ModelsNotReady
            library == null -> ChatAvailability.IndexUnavailable
            library.documents == 0 -> ChatAvailability.EmptyLibrary
            indexing is IndexingState.Active && indexing.pause == null -> ChatAvailability.WaitingForIndexing
            library.searchable + (if (pagesInChat) library.visualOnly else 0) == 0 ->
                if (scoped) ChatAvailability.NothingInScope else ChatAvailability.NothingReady
            else -> ChatAvailability.Ready(cpuMode = backend == ComputeBackend.CPU, partialCoverage = library.pendingIncluded > 0)
        }
}

/**
 * A conversation with the answer in progress laid over it. A turn left RETRIEVING or GENERATING that is
 * not the active one belongs to a dead process (research R8): it reads as INTERRUPTED even before the
 * session's startup has saved it so. An unsaved answer (C16) has no turn: it comes only in [ConversationView.active].
 */
class ObserveConversationUseCase(private val repository: ChatRepository, private val session: ChatSession) {
    operator fun invoke(conversationId: Long): Flow<ConversationView> =
        combine(repository.conversation(conversationId), session.active) { turns, active ->
            val mine = active?.takeIf { it.conversationId == conversationId }
            val shown = turns.map { turn ->
                when {
                    mine != null && turn.id == mine.turnId -> turn.copy(state = mine.state, answer = mine.text)
                    turn.state == TurnState.RETRIEVING || turn.state == TurnState.GENERATING -> turn.copy(state = TurnState.INTERRUPTED)
                    else -> turn
                }
            }
            ConversationView(shown, mine)
        }.distinctUntilChanged()
}

class AskQuestionUseCase(private val session: ChatSession) {
    suspend operator fun invoke(conversationId: Long?, question: String, scope: ChatScope = ChatScope.WholeLibrary): Long? =
        session.ask(conversationId, question, scope)
}

class CancelAnswerUseCase(private val session: ChatSession) {
    operator fun invoke() = session.cancel()
}

class RetryAnswerUseCase(private val session: ChatSession) {
    suspend operator fun invoke(turnId: Long): Boolean = session.retry(turnId)
}

class StartNewConversationUseCase(private val session: ChatSession) {
    operator fun invoke() = session.startNewConversation()
}

/** F13 Q7: deletes one conversation through the session; false when `chat.db` refused it. Never throws. */
class DeleteConversationUseCase(private val session: ChatSession) {
    suspend operator fun invoke(conversationId: Long): Boolean = try {
        session.delete(conversationId)
        true
    } catch (e: ChatStorageException) {
        false
    }
}

class LastConversationUseCase(private val repository: ChatRepository) {
    suspend operator fun invoke(): Long? = repository.lastConversationId()
}

/** F12 P12: the conversations «Borrar historial» would delete; null when `chat.db` cannot be read. */
class CountConversationsUseCase(private val repository: ChatRepository) {
    suspend operator fun invoke(): Int? = try {
        repository.conversationCount()
    } catch (e: ChatStorageException) {
        null
    }
}

/** F12 P12: whether the confirmation must say that an answer in progress will stop. */
class IsAnswerInProgressUseCase(private val session: ChatSession) {
    operator fun invoke(): Boolean = session.active.value?.state.let { it == TurnState.RETRIEVING || it == TurnState.GENERATING }
}

/** F12 P9: deletes every conversation through the session; false when `chat.db` refused it. Never throws. */
class ClearChatHistoryUseCase(private val session: ChatSession) {
    suspend operator fun invoke(): Boolean = try {
        session.deleteAll()
        true
    } catch (e: ChatStorageException) {
        false
    }
}

/** F12 P11: bumped by every deletion of the whole history. */
class ObserveHistoryClearedUseCase(private val session: ChatSession) {
    operator fun invoke(): StateFlow<Long> = session.historyCleared
}

/**
 * F13 Q1–Q5: the history list for a growing [limit] (research R1). Titles keep single spaces; a storage
 * that cannot be read gives [HistoryContent.Unavailable], and nothing is deleted.
 */
class ObserveConversationsUseCase(private val repository: ChatRepository) {
    @OptIn(ExperimentalCoroutinesApi::class)
    operator fun invoke(limit: Flow<Int>): Flow<HistoryContent> = limit.distinctUntilChanged().flatMapLatest { rows ->
        repository.conversations(rows)
            .map<ConversationPage, HistoryContent> { page -> HistoryContent.Page(page.copy(entries = page.entries.map { it.copy(title = normalized(it.title)) })) }
            .catch { error -> if (error is ChatStorageException) emit(HistoryContent.Unavailable) else throw error }
    }.onStart { emit(HistoryContent.Loading) }.distinctUntilChanged()

    private fun normalized(title: String): String = title.replace(WHITESPACE, " ").trim()

    private companion object {
        val WHITESPACE = Regex("\\s+")
    }
}

/** F13 Q6: shows another conversation; an answer of another one stops first. */
class OpenConversationUseCase(private val session: ChatSession) {
    suspend operator fun invoke(conversationId: Long) = session.openConversation(conversationId)
}

/** F13 Q8: the ids of the conversations deleted one by one. */
class ObserveConversationDeletionsUseCase(private val session: ChatSession) {
    operator fun invoke(): SharedFlow<Long> = session.deletions
}

/** F13 Q9: the conversation whose answer is being searched for or generated; null otherwise. */
class ObserveAnswerInProgressUseCase(private val session: ChatSession) {
    operator fun invoke(): Flow<Long?> = session.active
        .map { answer -> answer?.takeIf { it.state == TurnState.RETRIEVING || it.state == TurnState.GENERATING }?.conversationId }
        .distinctUntilChanged()
}

/** F13 Q16: whether a restored conversation still exists; true when it cannot be checked, so a read failure never empties the chat. */
class ConversationExistsUseCase(private val repository: ChatRepository) {
    suspend operator fun invoke(conversationId: Long): Boolean = try {
        repository.conversationExists(conversationId)
    } catch (e: ChatStorageException) {
        true
    }
}

/**
 * Loads Gemma ahead of the first question, only when the chat is Ready (never during a batch, D-053). F20 (contract
 * M4): with vision when the next answer will likely carry a page, the mode on and searchable documents with pages.
 */
class WarmUpAnswerEngineUseCase(
    private val engine: AnswerEngine,
    private val observeAvailability: ObserveChatAvailabilityUseCase,
    private val multimodal: MultimodalStore,
    private val index: RagIndex,
) {
    suspend operator fun invoke() {
        if (observeAvailability().first() !is ChatAvailability.Ready) return
        engine.warmUp(visual = withPages())
    }

    private suspend fun withPages(): Boolean = try {
        multimodal.state.first().stage == MultimodalStage.ON && index.pagesUsable.value && index.chatLibraryState().withPages > 0
    } catch (e: IndexUnavailableException) {
        false
    }
}

/**
 * Whether each cited source can still be opened (research R12), by chunk id, again on every change of the
 * index: a deleted document is gone; a document whose fragment is gone (reindexed, out of the index)
 * still opens, with F08's notice. With the index unavailable every source reads available: the detail
 * shows its own error.
 */
class ResolveSourcesUseCase(private val index: RagIndex) {
    @OptIn(ExperimentalCoroutinesApi::class)
    operator fun invoke(sources: List<CitedSource>): Flow<Map<CitedSource, SourceAvailability>> =
        if (sources.isEmpty()) flowOf(emptyMap()) else index.documentChanges.mapLatest { resolve(sources) }
            .catch { error -> if (error is IndexUnavailableException) emit(sources.associateWith { SourceAvailability.AVAILABLE }) else throw error }
            .distinctUntilChanged()

    /**
     * By cited source, not by chunk: the same chunk id may be two documents' at two times (Q15). F20 (contract H3,
     * H4): a page or an image is looked for in the stored pages, whatever the mode.
     */
    private suspend fun resolve(cited: List<CitedSource>): Map<CitedSource, SourceAvailability> {
        if (index.open() != IndexCompatibility.Compatible) return cited.associateWith { SourceAvailability.AVAILABLE }
        val created = cited.map { it.source.documentId }.distinct().associateWith { index.document(it)?.createdAtMs }
        val (fragments, pages) = cited.partition { it.source.kind == SourceKind.FRAGMENT }
        val chunks = if (fragments.isEmpty()) emptyMap() else index.loadChunks(fragments.map { it.source.chunkId })
        val stored = if (pages.isEmpty()) emptySet() else index.existingPages(pages.map { PageKey(it.source.documentId, it.source.page ?: 0) })
        return cited.associateWith { (source, citedAt) ->
            val documentCreated = created[source.documentId]
            when {
                // F13 Q15: rag.db reuses the id of a deleted last document; one created after the answer is another.
                documentCreated == null || documentCreated > citedAt -> SourceAvailability.DOCUMENT_GONE
                source.kind != SourceKind.FRAGMENT ->
                    if (PageKey(source.documentId, source.page ?: 0) in stored) SourceAvailability.AVAILABLE else SourceAvailability.PAGE_GONE
                chunks[source.chunkId]?.documentId != source.documentId -> SourceAvailability.FRAGMENT_GONE
                else -> SourceAvailability.AVAILABLE
            }
        }
    }
}
