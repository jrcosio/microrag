package com.lisdatasolutions.microrag.feature.chat

import android.os.Build
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.lisdatasolutions.microrag.R
import com.lisdatasolutions.microrag.core.designsystem.model.AnswerContent
import com.lisdatasolutions.microrag.core.designsystem.model.AnswerState
import com.lisdatasolutions.microrag.core.designsystem.model.CitationUi
import com.lisdatasolutions.microrag.core.navigation.Chat
import com.lisdatasolutions.microrag.domain.chat.ActiveAnswer
import com.lisdatasolutions.microrag.domain.chat.AnswerSource
import com.lisdatasolutions.microrag.domain.chat.AskQuestionUseCase
import com.lisdatasolutions.microrag.domain.chat.CancelAnswerUseCase
import com.lisdatasolutions.microrag.domain.chat.ChatAvailability
import com.lisdatasolutions.microrag.domain.chat.ChatLimits
import com.lisdatasolutions.microrag.domain.chat.ChatTurn
import com.lisdatasolutions.microrag.domain.chat.ConversationView
import com.lisdatasolutions.microrag.domain.chat.DeleteConversationUseCase
import com.lisdatasolutions.microrag.domain.chat.LastConversationUseCase
import com.lisdatasolutions.microrag.domain.chat.ObserveChatAvailabilityUseCase
import com.lisdatasolutions.microrag.domain.chat.ObserveConversationUseCase
import com.lisdatasolutions.microrag.domain.chat.PromptBlockRef
import com.lisdatasolutions.microrag.domain.chat.ResolveSourcesUseCase
import com.lisdatasolutions.microrag.domain.chat.RetryAnswerUseCase
import com.lisdatasolutions.microrag.domain.chat.SourceAvailability
import com.lisdatasolutions.microrag.domain.chat.StartNewConversationUseCase
import com.lisdatasolutions.microrag.domain.chat.TurnFailure
import com.lisdatasolutions.microrag.domain.chat.TurnState
import com.lisdatasolutions.microrag.domain.chat.WarmUpAnswerEngineUseCase
import com.lisdatasolutions.microrag.rag.answer.AnswerReading
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * The chat (F11, contracts/chat-ui.md). The conversation comes from the session and `chat.db`; this
 * ViewModel only formats it, resolves which conversation the entry shows (research R13) and forwards
 * the user's actions. The answer itself lives in the session, so it outlives this screen (FR-011).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModel(
    private val savedState: SavedStateHandle,
    observeAvailability: ObserveChatAvailabilityUseCase,
    private val observeConversation: ObserveConversationUseCase,
    private val ask: AskQuestionUseCase,
    private val cancelAnswer: CancelAnswerUseCase,
    private val retryAnswer: RetryAnswerUseCase,
    private val startNewConversation: StartNewConversationUseCase,
    private val deleteConversation: DeleteConversationUseCase,
    private val lastConversation: LastConversationUseCase,
    private val warmUp: WarmUpAnswerEngineUseCase,
    private val resolveSources: ResolveSourcesUseCase,
    /** The confirmation of a copy is the system's from Android 13 (research R14). */
    private val sdkInt: Int = Build.VERSION.SDK_INT,
) : ViewModel() {

    private val route = savedState.toRoute<Chat>()

    /** The conversation of this entry; null for a new one not saved yet. */
    private val conversationId = MutableStateFlow<Long?>(savedState[CONVERSATION])
    private val resolved = MutableStateFlow(savedState[RESOLVED] ?: false)
    private val question = savedState.getStateFlow(QUESTION, "")
    private val confirmDelete = savedState.getStateFlow(CONFIRM_DELETE, false)
    private val availabilityRetries = MutableStateFlow(0)
    private val copied = MutableStateFlow<Long?>(null)
    private val announcement = MutableStateFlow<ChatAnnouncement?>(null)
    private var copyJob: Job? = null

    private val events = Channel<ChatEvent>(Channel.BUFFERED)
    val eventFlow: Flow<ChatEvent> = events.receiveAsFlow()

    private val availability: StateFlow<ChatAvailability?> = observeAvailability(availabilityRetries)
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val view: StateFlow<ConversationView?> = combine(conversationId, resolved) { id, done -> id to done }
        .flatMapLatest { (id, done) ->
            when {
                !done -> flowOf(null)
                id == null -> flowOf(ConversationView(emptyList(), null))
                else -> observeConversation(id)
            }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val sources: StateFlow<Map<Long, SourceAvailability>> = view
        .map { v -> v?.turns.orEmpty().flatMap { it.sources } }
        .flatMapLatest { resolveSources(it) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    val state: StateFlow<ChatUiState> = combine(
        combine(view, availability, sources, ::Triple),
        combine(question, confirmDelete, copied, announcement) { q, confirm, copy, said -> Local(q, confirm, copy, said) },
    ) { (view, availability, sources), local ->
        val turns = view?.let { turnsOf(it, availability, sources) }.orEmpty()
        ChatUiState(
            conversationId = conversationId.value,
            availability = availability,
            turns = turns,
            question = local.question,
            answering = view?.active?.let { it.state == TurnState.RETRIEVING || it.state == TurnState.GENERATING } == true,
            confirmDelete = local.confirmDelete,
            copiedTurnId = local.copied,
            announcement = local.announcement,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, ChatUiState())

    private data class Local(val question: String, val confirmDelete: Boolean, val copied: Long?, val announcement: ChatAnnouncement?)

    init {
        if (!resolved.value) viewModelScope.launch { resolve() }
        viewModelScope.launch { announceChanges() }
    }

    /** Research R13: the saved id, the route's id, a new conversation, or the last one. */
    private suspend fun resolve() {
        val id = when {
            route.conversationId != null -> route.conversationId
            route.newConversation -> {
                // A new conversation from Home stops any answer in progress (research R6).
                startNewConversation()
                null
            }
            else -> lastConversation()
        }
        setConversation(id)
    }

    private fun setConversation(id: Long?) {
        conversationId.value = id
        savedState[CONVERSATION] = id
        resolved.value = true
        savedState[RESOLVED] = true
    }

    /** One announcement per change of the answer in progress, never its text (FR-039). */
    private suspend fun announceChanges() {
        var last: Pair<Long, TurnState>? = null
        view.collect { v ->
            val turn = v?.turns?.lastOrNull() ?: return@collect
            val key = turn.id to turn.state
            if (key == last) return@collect
            val active = v.active?.turnId == turn.id
            if (last != null && last?.first == turn.id || active) {
                announcement.value = when (turn.state) {
                    TurnState.RETRIEVING -> ChatAnnouncement.SEARCHING
                    TurnState.GENERATING -> ChatAnnouncement.GENERATING
                    TurnState.COMPLETED -> ChatAnnouncement.COMPLETED
                    TurnState.CANCELLED -> ChatAnnouncement.CANCELLED
                    TurnState.INTERRUPTED -> ChatAnnouncement.INTERRUPTED
                    TurnState.FAILED -> ChatAnnouncement.FAILED
                }
            }
            last = key
        }
    }

    fun onQuestionChange(text: String) {
        // research R10: at most MAX_QUESTION_CHARS UTF-16 units, never cutting a surrogate pair.
        var cut = text.take(ChatLimits.MAX_QUESTION_CHARS)
        if (cut.isNotEmpty() && cut.last().isHighSurrogate()) cut = cut.dropLast(1)
        savedState[QUESTION] = cut
    }

    fun onSend() {
        // The question as typed, not as last combined into the state: a tap right after typing sends it whole.
        val text = question.value
        if (!state.value.copy(question = text).canSend) return
        viewModelScope.launch {
            val id = ask(conversationId.value, text) ?: return@launch
            setConversation(id)
            savedState[QUESTION] = ""
        }
    }

    fun onStop() = cancelAnswer()

    fun onRetry(turnId: Long) {
        viewModelScope.launch { retryAnswer(turnId) }
    }

    /** «Nuevo chat» of «⋯»: resolved here, without navigating to the same destination (research R13). */
    fun onNewChat() {
        startNewConversation()
        setConversation(null)
    }

    fun onDelete() {
        if (conversationId.value != null) savedState[CONFIRM_DELETE] = true
    }

    fun onDismissDelete() {
        savedState[CONFIRM_DELETE] = false
    }

    fun onConfirmDelete() {
        savedState[CONFIRM_DELETE] = false
        val id = conversationId.value ?: return
        viewModelScope.launch {
            deleteConversation(id)
            setConversation(null)
            announcement.value = ChatAnnouncement.DELETED
        }
    }

    /** C10: the text shown and its sources; the screen writes it to the clipboard. */
    fun onCopy(turnId: Long, text: String) {
        viewModelScope.launch { events.send(ChatEvent.Copy(text)) }
        // U10: up to Android 12 the system says nothing, so the footer says it for 2 s and TalkBack reads it.
        if (sdkInt <= Build.VERSION_CODES.S_V2) {
            copied.value = turnId
            announcement.value = ChatAnnouncement.COPIED
            copyJob?.cancel()
            copyJob = viewModelScope.launch {
                delay(COPIED_MS)
                copied.value = null
                if (announcement.value == ChatAnnouncement.COPIED) announcement.value = null
            }
        }
    }

    fun onOpenSource(target: SourceTarget) {
        viewModelScope.launch { events.send(ChatEvent.OpenSource(target)) }
    }

    /** C8 at screen level: check the index again. */
    fun onRetryAvailability() {
        availabilityRetries.value++
    }

    /** Back on screen (ON_START): load Gemma if the chat is ready; it was released when the app was hidden. */
    fun onStart() {
        viewModelScope.launch { warmUp() }
    }

    private fun turnsOf(view: ConversationView, availability: ChatAvailability?, available: Map<Long, SourceAvailability>): List<TurnUi> {
        val canRetry = availability is ChatAvailability.Ready && view.active?.state?.let { it == TurnState.RETRIEVING || it == TurnState.GENERATING } != true
        val saved = view.turns.map { turn ->
            val active = view.active?.takeIf { it.turnId == turn.id }
            turnUi(turn, active, available, canRetry)
        }
        // C16: an answer that could not be saved has no turn; it shows from memory.
        val unsaved = view.active?.takeIf { a -> a.turnId == ActiveAnswer.UNSAVED || saved.none { it.id == a.turnId } }
        return saved + listOfNotNull(unsaved?.let { unsavedTurn(it) })
    }

    private fun turnUi(turn: ChatTurn, active: ActiveAnswer?, available: Map<Long, SourceAvailability>, canRetry: Boolean): TurnUi {
        val streaming = turn.state == TurnState.RETRIEVING || turn.state == TurnState.GENERATING
        val retryable = turn.state == TurnState.FAILED || turn.state == TurnState.INTERRUPTED
        val blocks = active?.blocks.orEmpty()
        val blockCount = if (streaming) blocks.size else turn.promptBlocks
        val sources = if (streaming || turn.refusal) emptyList() else turn.sources
        val content = if (turn.refusal) AnswerContent.Empty else AnswerFormatter.format(turn.answer, blockCount)
        return TurnUi(
            id = turn.id,
            question = turn.question,
            state = answerState(turn.state),
            status = status(turn.state),
            content = content,
            refusal = turn.refusal,
            failureText = failureText(turn.state, turn.failure),
            notes = notes(turn.state, turn.refusal, content, sources, turn.partialCoverage, active?.saveFailed == true),
            sources = sources.map { sourceRow(it, available[it.chunkId] ?: SourceAvailability.AVAILABLE) },
            chipTargets = if (streaming) chipTargets(turn.answer, blocks) else sources.map { SourceTarget(it.documentId, it.chunkId) },
            retryable = retryable,
            canRetry = canRetry && retryable,
        )
    }

    private fun unsavedTurn(active: ActiveAnswer): TurnUi {
        val streaming = active.state == TurnState.RETRIEVING || active.state == TurnState.GENERATING
        val content = if (active.refusal) AnswerContent.Empty else AnswerFormatter.format(active.text, active.blocks.size)
        val targets = chipTargets(active.text, active.blocks)
        return TurnUi(
            id = ActiveAnswer.UNSAVED,
            question = active.question,
            state = answerState(active.state),
            status = status(active.state),
            content = content,
            refusal = active.refusal,
            failureText = failureText(active.state, active.failure),
            notes = if (streaming) emptyList() else listOf(R.string.chat_save_failed),
            sources = emptyList(),
            chipTargets = targets,
            // Not saved, so there is no turn to retry (C16).
            retryable = false,
            canRetry = false,
        )
    }

    /** Chip `k` opens the k-th block in first-citation order (D-053), as the saved sources will be. */
    private fun chipTargets(text: String, blocks: List<PromptBlockRef>): List<SourceTarget> {
        val byNumber = blocks.associateBy { it.promptNumber }
        return AnswerReading.citedOrder(text, blocks.size).mapNotNull { byNumber[it] }.map { SourceTarget(it.documentId, it.chunkId) }
    }

    private fun sourceRow(source: AnswerSource, availability: SourceAvailability): SourceRow = SourceRow(
        position = source.position,
        target = SourceTarget(source.documentId, source.chunkId),
        citation = CitationUi(
            documentName = source.documentName,
            page = source.page,
            fragment = source.firstOrdinal + 1,
            available = availability != SourceAvailability.DOCUMENT_GONE,
            lastFragment = (source.lastOrdinal + 1).takeIf { source.lastOrdinal > source.firstOrdinal },
        ),
    )

    private fun notes(
        state: TurnState, refusal: Boolean, content: AnswerContent, sources: List<AnswerSource>, partial: Boolean, saveFailed: Boolean,
    ): List<Int> {
        if (state == TurnState.RETRIEVING || state == TurnState.GENERATING) return emptyList()
        return buildList {
            if (state == TurnState.COMPLETED && !refusal && !content.isEmpty && sources.isEmpty()) add(R.string.chat_no_citations)
            if (partial) add(R.string.chat_partial_coverage)
            if (saveFailed) add(R.string.chat_save_failed)
        }
    }

    private fun answerState(state: TurnState): AnswerState = when (state) {
        TurnState.RETRIEVING, TurnState.GENERATING -> AnswerState.Streaming
        TurnState.COMPLETED -> AnswerState.Completed
        TurnState.CANCELLED -> AnswerState.Cancelled
        TurnState.FAILED, TurnState.INTERRUPTED -> AnswerState.Failed
    }

    private fun status(state: TurnState): TurnStatus? = when (state) {
        TurnState.RETRIEVING -> TurnStatus.SEARCHING
        TurnState.GENERATING -> TurnStatus.GENERATING
        else -> null
    }

    private fun failureText(state: TurnState, failure: TurnFailure?): Int? = when {
        state == TurnState.INTERRUPTED -> R.string.chat_interrupted
        state == TurnState.FAILED && failure == TurnFailure.INDEX -> R.string.chat_index_failed
        else -> null
    }

    private companion object {
        const val CONVERSATION = "chat_conversation"
        const val RESOLVED = "chat_resolved"
        const val QUESTION = "chat_question"
        const val CONFIRM_DELETE = "chat_confirm_delete"
        const val COPIED_MS = 2_000L
    }
}

/** One-off events: the screen opens a source or writes the clipboard. */
sealed interface ChatEvent {
    data class OpenSource(val target: SourceTarget) : ChatEvent

    data class Copy(val text: String) : ChatEvent
}
