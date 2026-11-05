package com.lisdatasolutions.microrag.testing

import com.lisdatasolutions.microrag.domain.chat.ActiveAnswer
import com.lisdatasolutions.microrag.domain.chat.AnswerEngine
import com.lisdatasolutions.microrag.domain.chat.AnswerEvent
import com.lisdatasolutions.microrag.domain.chat.AnswerPrompt
import com.lisdatasolutions.microrag.domain.chat.AnswerSource
import com.lisdatasolutions.microrag.domain.chat.ChatRepository
import com.lisdatasolutions.microrag.domain.chat.ChatScope
import com.lisdatasolutions.microrag.domain.chat.ChatSession
import com.lisdatasolutions.microrag.domain.chat.ChatStorageException
import com.lisdatasolutions.microrag.domain.chat.ChatStorageState
import com.lisdatasolutions.microrag.domain.chat.ChatTurn
import com.lisdatasolutions.microrag.domain.chat.ConversationPage
import com.lisdatasolutions.microrag.domain.chat.ConversationSummary
import com.lisdatasolutions.microrag.domain.chat.HistoryLimits
import com.lisdatasolutions.microrag.domain.chat.ObserveChatAvailabilityUseCase
import com.lisdatasolutions.microrag.domain.chat.PromptPart
import com.lisdatasolutions.microrag.domain.chat.ResolvedScope
import com.lisdatasolutions.microrag.domain.chat.TurnState
import com.lisdatasolutions.microrag.domain.chat.TurnUpdate
import com.lisdatasolutions.microrag.domain.indexing.IndexingRepository
import com.lisdatasolutions.microrag.domain.indexing.IndexingState
import com.lisdatasolutions.microrag.domain.indexing.ObserveIndexingUseCase
import com.lisdatasolutions.microrag.domain.model.ComputeBackend
import com.lisdatasolutions.microrag.domain.model.ModelCatalog
import com.lisdatasolutions.microrag.domain.onboarding.ObserveLocalAiUseCase
import com.lisdatasolutions.microrag.domain.rag.DocumentStatus
import com.lisdatasolutions.microrag.domain.rag.NewDocument
import com.lisdatasolutions.microrag.domain.retrieval.ContextRetriever
import com.lisdatasolutions.microrag.domain.retrieval.RetrievalOutcome
import com.lisdatasolutions.microrag.domain.retrieval.RetrievalRequest
import com.lisdatasolutions.microrag.domain.retrieval.RetrievalResult
import com.lisdatasolutions.microrag.domain.retrieval.RetrievalTimings
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
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

    override suspend fun lastConversationId(): Long? = asked().firstOrNull()

    override fun conversations(limit: Int): Flow<ConversationPage> = changes.map {
        if (storage != ChatStorageState.OK) throw ChatStorageException("fake storage unavailable")
        val ids = asked()
        ConversationPage(ids.take(limit).map(::summary), ids.size)
    }.distinctUntilChanged()

    /** Conversations with questions: the newest activity first, then the one created later (F13 Q1, Q2). */
    private fun asked(): List<Long> = conversations.entries
        .filter { (id, _) -> turns.values.any { it.conversationId == id } }
        .sortedWith(compareByDescending<Map.Entry<Long, Long>> { it.value }.thenByDescending { it.key })
        .map { it.key }

    private fun summary(id: Long): ConversationSummary {
        val mine = turns.values.filter { it.conversationId == id }
        return ConversationSummary(
            id = id, title = mine.minBy { it.ordinal }.question.take(HistoryLimits.TITLE_CHARS),
            lastActivityAt = conversations.getValue(id), questions = mine.size,
        )
    }

    /** F17: the scopes as `chat.db` keeps them; a conversation without an entry is on the whole library. */
    val scopes = mutableMapOf<Long, ChatScope>()
    var lastScope: ChatScope = ChatScope.WholeLibrary

    override suspend fun createConversation(now: Long, scope: ChatScope): Long = write("create") {
        nextConversation++.also {
            conversations[it] = now
            if (scope != ChatScope.WholeLibrary) scopes[it] = scope
        }
    }

    override suspend fun insertTurn(conversationId: Long, question: String, partialCoverage: Boolean, now: Long): ChatTurn =
        write("insert") {
            // The foreign key of chat.db: a deleted conversation takes no more turns (F12 R10).
            if (conversationId !in conversations) throw ChatStorageException("fake: no conversation $conversationId")
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
                finishedAt = update.finishedAt ?: t.finishedAt, pageTokens = update.pageTokens ?: t.pageTokens,
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
            t.copy(
                state = TurnState.RETRIEVING, answer = "", failure = null, refusal = false, promptBlocks = 0, sources = emptyList(),
                finishedAt = null, pageTokens = null,
            )
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
        scopes.remove(id)
        turns.values.removeAll { it.conversationId == id }
        Unit
    }

    override suspend fun deleteAll() = write("deleteAll") {
        conversations.clear()
        turns.clear()
        scopes.clear()
        lastScope = ChatScope.WholeLibrary
    }

    override suspend fun conversationScope(id: Long): ChatScope =
        if (storage != ChatStorageState.OK) ChatScope.WholeLibrary else scopes[id] ?: ChatScope.WholeLibrary

    override suspend fun setConversationScope(id: Long, scope: ChatScope) = write("scope") {
        if (id !in conversations) throw ChatStorageException("fake: no conversation $id")
        if (scope == ChatScope.WholeLibrary) scopes.remove(id) else scopes[id] = scope
        Unit
    }

    override suspend fun lastScope(): ChatScope = if (storage != ChatStorageState.OK) ChatScope.WholeLibrary else lastScope

    override suspend fun setLastScope(scope: ChatScope) = write("lastScope") { lastScope = scope }

    override suspend fun conversationCount(): Int = read { turns.values.map { it.conversationId }.distinct().size }

    override suspend fun conversationExists(id: Long): Boolean = read { id in conversations }

    private fun <T> read(block: () -> T): T {
        if (storage != ChatStorageState.OK) throw ChatStorageException("fake storage unavailable")
        return block()
    }

    private fun <T> write(name: String, block: () -> T): T {
        if (storage != ChatStorageState.OK || failWrites) throw ChatStorageException("fake storage refused $name")
        writes += name
        return block().also { changes.value++ }
    }
}

/**
 * [AnswerEngine] that streams [tokens], one every [delayPerTokenMs] of virtual time, after [prefillMs].
 * [failAt] throws before that token; cancellations and prompts are recorded. F20: [answerPrompts] keeps each prompt as
 * it came, and [prompts] its text (a visual prompt's text parts joined, each image as `<image>`).
 */
class FakeAnswerEngine(var isReady: Boolean = true) : AnswerEngine {
    val backend = MutableStateFlow<ComputeBackend?>(ComputeBackend.GPU)
    var tokens: List<String> = listOf("El plazo es de 30 días [1].")
    var prefillMs = 100L
    var delayPerTokenMs = 10L
    var failAt: Int? = null
    var failure: Exception = IllegalStateException("engine failed")
    val prompts = mutableListOf<String>()
    val answerPrompts = mutableListOf<AnswerPrompt>()
    var warmUps = 0
    var visualWarmUps = 0
    var cancellations = 0
    var generating = false
        private set

    override suspend fun ready(): Boolean = isReady

    override fun preparedBackend(): Flow<ComputeBackend?> = backend

    override suspend fun warmUp(visual: Boolean) {
        warmUps++
        if (visual) visualWarmUps++
    }

    override fun generate(prompt: AnswerPrompt): Flow<AnswerEvent> = flow {
        answerPrompts += prompt
        prompts += when (prompt) {
            is AnswerPrompt.Text -> prompt.text
            is AnswerPrompt.Visual -> prompt.parts.joinToString("") { if (it is PromptPart.Text) it.text else "<image>" }
        }
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

/**
 * [ChatSession] whose [active] answer a test sets. `ask` saves the question in [repository] as the real
 * session does, and leaves it RETRIEVING; every call is recorded.
 */
class FakeChatSession(private val repository: InMemoryChatRepository) : ChatSession {
    override val active = MutableStateFlow<ActiveAnswer?>(null)
    val calls = mutableListOf<String>()

    /** F17: the scope of every question, in order. */
    val askedScopes = mutableListOf<ChatScope>()

    /** What [scopeOf] and [lastScope] answer for what is saved, as ResolveScopeUseCase would (identity by default). */
    var resolve: (ChatScope) -> ResolvedScope = { ResolvedScope(it) }

    override suspend fun ask(conversationId: Long?, question: String, scope: ChatScope): Long {
        calls += "ask"
        askedScopes += scope
        val id = conversationId ?: repository.createConversation(repository.now, scope)
        if (conversationId != null) repository.setConversationScope(id, scope)
        repository.setLastScope(scope)
        val turn = repository.insertTurn(id, question, partialCoverage = false, now = repository.now)
        active.value = ActiveAnswer(id, turn.id, question, TurnState.RETRIEVING)
        return id
    }

    override suspend fun setScope(conversationId: Long, scope: ChatScope) {
        calls += "scope:$conversationId"
        repository.setConversationScope(conversationId, scope)
        repository.setLastScope(scope)
    }

    override suspend fun scopeOf(conversationId: Long): ResolvedScope = resolve(repository.conversationScope(conversationId))

    override suspend fun lastScope(): ResolvedScope = resolve(repository.lastScope())

    override suspend fun setLastScope(scope: ChatScope) {
        calls += "lastScope"
        repository.setLastScope(scope)
    }

    override fun cancel() {
        calls += "cancel"
    }

    override suspend fun retry(turnId: Long): Boolean {
        calls += "retry:$turnId"
        return true
    }

    override fun startNewConversation() {
        calls += "new"
    }

    override suspend fun openConversation(conversationId: Long) {
        calls += "open:$conversationId"
    }

    override val deletions = MutableSharedFlow<Long>(extraBufferCapacity = 8)

    /** Throws as the real session when the repository refuses the write; announces only a confirmed deletion. */
    override suspend fun delete(conversationId: Long) {
        calls += "delete:$conversationId"
        repository.deleteConversation(conversationId)
        if (active.value?.conversationId == conversationId) active.value = null
        deletions.emit(conversationId)
    }

    override val historyCleared = MutableStateFlow(0L)

    /** Throws as the real session when the repository refuses the write. */
    override suspend fun deleteAll() {
        calls += "deleteAll"
        repository.deleteAll()
        active.value = null
        historyCleared.value += 1
    }
}

/** What the chat reads, over fakes: the models are ready on the GPU; [addDocument] makes the chat Ready. */
class ChatTestGraph {
    val chats = InMemoryChatRepository()
    val session = FakeChatSession(chats)
    val models = InMemoryModelFileRepository(verified = ModelCatalog.required.map { it.id }.toSet()).apply {
        preparedBackends.value = ModelCatalog.required.associate { it.id to ComputeBackend.GPU }
    }
    val engine = FakeAnswerEngine()
    val indexing = FakeIndexingRepository()
    val index = FakeRagIndex()
    /** F17: the folders the chat's chip names. */
    val library = FakeLibraryRepository()
    /** F20: the multimodal mode, off as in a fresh install. */
    val multimodal = FakeMultimodalStore()
    val availability = ObserveChatAvailabilityUseCase(chats, ObserveLocalAiUseCase(models), engine, ObserveIndexingUseCase(indexing), index, multimodal)

    suspend fun addDocument(name: String, status: DocumentStatus = DocumentStatus.DONE): Long {
        val id = index.addDocument(NewDocument(displayName = name, contentHash = name, mimeType = "application/pdf"))
        index.updateStatus(id, status, null, null, null)
        return id
    }
}
