package com.lisdatasolutions.microrag.domain.chat

import com.lisdatasolutions.microrag.domain.indexing.IndexingState
import com.lisdatasolutions.microrag.domain.indexing.ObserveIndexingUseCase
import com.lisdatasolutions.microrag.domain.model.ComputeBackend
import com.lisdatasolutions.microrag.domain.model.LocalAiStatus
import com.lisdatasolutions.microrag.domain.onboarding.ObserveLocalAiUseCase
import com.lisdatasolutions.microrag.domain.rag.ChatLibraryState
import com.lisdatasolutions.microrag.domain.rag.IndexCompatibility
import com.lisdatasolutions.microrag.domain.rag.IndexUnavailableException
import com.lisdatasolutions.microrag.domain.rag.RagIndex
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
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
import kotlinx.coroutines.flow.mapLatest

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
) {
    operator fun invoke(retries: Flow<Int> = flowOf(0)): Flow<ChatAvailability> = flow {
        if (chats.open() != ChatStorageState.OK) {
            emit(ChatAvailability.StorageUnavailable)
            return@flow
        }
        val indexing = observeIndexing().filter { it != IndexingState.Loading }
        emitAll(combine(observeLocalAi(), engine.preparedBackend(), indexing, library(retries), ::decide))
    }.distinctUntilChanged()

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun library(retries: Flow<Int>): Flow<ChatLibraryState?> = retries.flatMapLatest {
        flow {
            if (index.open() != IndexCompatibility.Compatible) {
                emit(null)
                return@flow
            }
            emitAll(index.documentChanges.mapLatest { index.chatLibraryState() })
        }.catch { error -> if (error is IndexUnavailableException) emit(null) else throw error }
    }

    /** Precedence of research R11; a null [library] is an index that cannot be read. */
    private fun decide(ai: LocalAiStatus, backend: ComputeBackend?, indexing: IndexingState, library: ChatLibraryState?): ChatAvailability =
        when {
            ai != LocalAiStatus.READY || backend == null -> ChatAvailability.ModelsNotReady
            library == null -> ChatAvailability.IndexUnavailable
            library.documents == 0 -> ChatAvailability.EmptyLibrary
            indexing is IndexingState.Active && indexing.pause == null -> ChatAvailability.WaitingForIndexing
            library.searchable == 0 -> ChatAvailability.NothingReady
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
    suspend operator fun invoke(conversationId: Long?, question: String): Long? = session.ask(conversationId, question)
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

class DeleteConversationUseCase(private val session: ChatSession) {
    suspend operator fun invoke(conversationId: Long) = session.delete(conversationId)
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

/** Loads Gemma ahead of the first question, only when the chat is Ready (never during a batch, D-053). */
class WarmUpAnswerEngineUseCase(private val engine: AnswerEngine, private val observeAvailability: ObserveChatAvailabilityUseCase) {
    suspend operator fun invoke() {
        if (observeAvailability().first() is ChatAvailability.Ready) engine.warmUp()
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
    operator fun invoke(sources: List<AnswerSource>): Flow<Map<Long, SourceAvailability>> =
        if (sources.isEmpty()) flowOf(emptyMap()) else index.documentChanges.mapLatest { resolve(sources) }
            .catch { error -> if (error is IndexUnavailableException) emit(sources.associate { it.chunkId to SourceAvailability.AVAILABLE }) else throw error }
            .distinctUntilChanged()

    private suspend fun resolve(sources: List<AnswerSource>): Map<Long, SourceAvailability> {
        if (index.open() != IndexCompatibility.Compatible) return sources.associate { it.chunkId to SourceAvailability.AVAILABLE }
        val documents = sources.map { it.documentId }.distinct().associateWith { index.document(it) != null }
        val chunks = index.loadChunks(sources.map { it.chunkId })
        return sources.associate { source ->
            source.chunkId to when {
                documents[source.documentId] != true -> SourceAvailability.DOCUMENT_GONE
                chunks[source.chunkId]?.documentId != source.documentId -> SourceAvailability.FRAGMENT_GONE
                else -> SourceAvailability.AVAILABLE
            }
        }
    }
}
