package com.lisdatasolutions.microrag.testing

import com.lisdatasolutions.microrag.domain.chat.AnswerEngine
import com.lisdatasolutions.microrag.domain.chat.AnswerEvent
import com.lisdatasolutions.microrag.domain.chat.AnswerSource
import com.lisdatasolutions.microrag.domain.chat.ChatRepository
import com.lisdatasolutions.microrag.domain.chat.ChatStorageException
import com.lisdatasolutions.microrag.domain.chat.ChatStorageState
import com.lisdatasolutions.microrag.domain.chat.ChatTurn
import com.lisdatasolutions.microrag.domain.chat.TurnState
import com.lisdatasolutions.microrag.domain.chat.TurnUpdate
import com.lisdatasolutions.microrag.domain.indexing.IndexingRepository
import com.lisdatasolutions.microrag.domain.indexing.IndexingState
import com.lisdatasolutions.microrag.domain.model.ComputeBackend
import com.lisdatasolutions.microrag.domain.retrieval.ContextRetriever
import com.lisdatasolutions.microrag.domain.retrieval.RetrievalOutcome
import com.lisdatasolutions.microrag.domain.retrieval.RetrievalRequest
import com.lisdatasolutions.microrag.domain.retrieval.RetrievalResult
import com.lisdatasolutions.microrag.domain.retrieval.RetrievalTimings
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

/**
 * In-memory [ChatRepository] with the semantics of `chat.db` (F11 contracts/chat-api.md): turns in order,
 * sources replaced as a whole, cascade on delete. [storage] plays an unreadable file and [failWrites] a full disk.
 */
class InMemoryChatRepository : ChatRepository {
    var storage = ChatStorageState.OK
    var failWrites = false
    var now = 0L

    private val state = MutableStateFlow<ChatStorageState?>(null)
    override val storageState: StateFlow<ChatStorageState?> = state

    private val changes = MutableStateFlow(0L)
    private val conversations = linkedMapOf<Long, Long>()
    val turns = linkedMapOf<Long, ChatTurn>()
    private var nextConversation = 1L
    private var nextTurn = 1L
    val writes = mutableListOf<String>()

    override suspend fun open(): ChatStorageState = storage.also { state.value = it }

    override fun conversation(id: Long): Flow<List<ChatTurn>> =
        changes.map { turns.values.filter { it.conversationId == id }.sortedBy { it.ordinal } }.distinctUntilChanged()

    override suspend fun turn(id: Long): ChatTurn? = turns[id]

    override suspend fun lastConversationId(): Long? = conversations.maxByOrNull { it.value }?.key

    override suspend fun createConversation(now: Long): Long = write("create") {
        nextConversation++.also { conversations[it] = now }
    }

    override suspend fun insertTurn(conversationId: Long, question: String, partialCoverage: Boolean, now: Long): ChatTurn =
        write("insert") {
            val ordinal = turns.values.count { it.conversationId == conversationId }
            val turn = ChatTurn(
                id = nextTurn++, conversationId = conversationId, ordinal = ordinal, question = question, answer = "",
                state = TurnState.RETRIEVING, failure = null, refusal = false, partialCoverage = partialCoverage, promptBlocks = 0,
                sources = emptyList(), createdAt = now, finishedAt = null,
            )
            turns[turn.id] = turn
            conversations[conversationId] = now
            turn
        }

    override suspend fun updateTurn(turnId: Long, update: TurnUpdate) = write("update:${update.state ?: ""}") {
        turns.computeIfPresent(turnId) { _, t ->
            t.copy(
                state = update.state ?: t.state, answer = update.answer ?: t.answer, failure = update.failure ?: t.failure,
                refusal = update.refusal ?: t.refusal, promptBlocks = update.promptBlocks ?: t.promptBlocks,
                finishedAt = update.finishedAt ?: t.finishedAt,
            )
        }
        Unit
    }

    override suspend fun replaceSources(turnId: Long, sources: List<AnswerSource>) = write("sources") {
        turns.computeIfPresent(turnId) { _, t -> t.copy(sources = sources) }
        Unit
    }

    override suspend fun resetForRetry(turnId: Long) = write("reset") {
        turns.computeIfPresent(turnId) { _, t ->
            t.copy(state = TurnState.RETRIEVING, answer = "", failure = null, refusal = false, promptBlocks = 0, sources = emptyList(), finishedAt = null)
        }
        Unit
    }

    override suspend fun markInterrupted(now: Long): Int = write("interrupted") {
        val stale = turns.values.filter { it.state == TurnState.RETRIEVING || it.state == TurnState.GENERATING }
        stale.forEach { turns[it.id] = it.copy(state = TurnState.INTERRUPTED, finishedAt = now) }
        stale.size
    }

    override suspend fun deleteConversation(id: Long) = write("delete") {
        conversations.remove(id)
        turns.values.removeAll { it.conversationId == id }
        Unit
    }

    override suspend fun deleteAll() = write("deleteAll") {
        conversations.clear()
        turns.clear()
    }

    private fun <T> write(name: String, block: () -> T): T {
        if (storage != ChatStorageState.OK || failWrites) throw ChatStorageException("fake storage refused $name")
        writes += name
        return block().also { changes.value++ }
    }
}

/**
 * [AnswerEngine] that streams [tokens], one every [delayPerTokenMs] of virtual time, after [prefillMs].
 * [failAt] throws before that token; cancellations and prompts are recorded.
 */
class FakeAnswerEngine(var isReady: Boolean = true) : AnswerEngine {
    val backend = MutableStateFlow<ComputeBackend?>(ComputeBackend.GPU)
    var tokens: List<String> = listOf("El plazo es de 30 días [1].")
    var prefillMs = 100L
    var delayPerTokenMs = 10L
    var failAt: Int? = null
    var failure: Exception = IllegalStateException("engine failed")
    val prompts = mutableListOf<String>()
    var warmUps = 0
    var cancellations = 0
    var generating = false
        private set

    override suspend fun ready(): Boolean = isReady

    override fun preparedBackend(): Flow<ComputeBackend?> = backend

    override suspend fun warmUp() {
        warmUps++
    }

    override fun generate(prompt: String): Flow<AnswerEvent> = flow {
        prompts += prompt
        generating = true
        try {
            delay(prefillMs)
            tokens.forEachIndexed { i, token ->
                if (failAt == i) throw failure
                if (i > 0) delay(delayPerTokenMs)
                emit(AnswerEvent.Token(token))
            }
            emit(AnswerEvent.Finished(ttftMs = prefillMs.toDouble(), totalMs = 0.0, decodeTokensPerSecond = null))
        } catch (e: CancellationException) {
            cancellations++
            throw e
        } finally {
            generating = false
        }
    }
}

/** [ContextRetriever] that answers [outcome] after [delayMs] and records every request. */
class FakeContextRetriever(var outcome: RetrievalOutcome = RetrievalOutcome.NoResults) : ContextRetriever {
    val requests = mutableListOf<RetrievalRequest>()
    var delayMs = 50L

    override suspend fun retrieve(request: RetrievalRequest): RetrievalResult {
        requests += request
        delay(delayMs)
        return RetrievalResult(
            searchText = request.question, followUp = request.previousQuestion != null, embeddingCuts = 0,
            timings = RetrievalTimings(totalMs = delayMs.toDouble()), outcome = outcome,
        )
    }
}

/** [IndexingRepository] whose [state] a test sets; the commands are not used by the chat. */
class FakeIndexingRepository(initial: IndexingState = IndexingState.Idle(emptyList())) : IndexingRepository {
    override val state = MutableStateFlow(initial)
    override suspend fun start(): Int = 0
    override suspend fun pause() = Unit
    override suspend fun resume() = Unit
    override suspend fun retry(documentId: Long) = Unit
    override suspend fun add(documentId: Long) = Unit
    override suspend fun reconcile() = Unit
}
