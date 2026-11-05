package com.lisdatasolutions.microrag.feature.chat

import android.os.Build
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.lisdatasolutions.microrag.R
import com.lisdatasolutions.microrag.core.designsystem.model.AnswerContent
import com.lisdatasolutions.microrag.core.designsystem.model.AnswerState
import com.lisdatasolutions.microrag.core.designsystem.model.CitationKind
import com.lisdatasolutions.microrag.core.designsystem.model.CitationUi
import com.lisdatasolutions.microrag.core.navigation.Chat
import com.lisdatasolutions.microrag.domain.chat.ActiveAnswer
import com.lisdatasolutions.microrag.domain.chat.AnswerSource
import com.lisdatasolutions.microrag.domain.chat.ApplyScopeUseCase
import com.lisdatasolutions.microrag.domain.chat.AskQuestionUseCase
import com.lisdatasolutions.microrag.domain.chat.CancelAnswerUseCase
import com.lisdatasolutions.microrag.domain.chat.ChatAvailability
import com.lisdatasolutions.microrag.domain.chat.ChatLimits
import com.lisdatasolutions.microrag.domain.chat.ChatScope
import com.lisdatasolutions.microrag.domain.chat.ChatTurn
import com.lisdatasolutions.microrag.domain.chat.CitedSource
import com.lisdatasolutions.microrag.domain.chat.ConversationExistsUseCase
import com.lisdatasolutions.microrag.domain.chat.ConversationScopeUseCase
import com.lisdatasolutions.microrag.domain.chat.ConversationView
import com.lisdatasolutions.microrag.domain.chat.DeleteConversationUseCase
import com.lisdatasolutions.microrag.domain.chat.LastConversationUseCase
import com.lisdatasolutions.microrag.domain.chat.LastScopeUseCase
import com.lisdatasolutions.microrag.domain.chat.ObserveChatAvailabilityUseCase
import com.lisdatasolutions.microrag.domain.chat.ObserveConversationDeletionsUseCase
import com.lisdatasolutions.microrag.domain.chat.ObserveConversationUseCase
import com.lisdatasolutions.microrag.domain.chat.ObserveHistoryClearedUseCase
import com.lisdatasolutions.microrag.domain.chat.OpenConversationUseCase
import com.lisdatasolutions.microrag.domain.chat.PromptBlockRef
import com.lisdatasolutions.microrag.domain.chat.ResolveSourcesUseCase
import com.lisdatasolutions.microrag.domain.chat.ResolvedScope
import com.lisdatasolutions.microrag.domain.chat.RetryAnswerUseCase
import com.lisdatasolutions.microrag.domain.chat.SourceAvailability
import com.lisdatasolutions.microrag.domain.chat.SourceKind
import com.lisdatasolutions.microrag.domain.chat.StartNewConversationUseCase
import com.lisdatasolutions.microrag.domain.chat.TurnFailure
import com.lisdatasolutions.microrag.domain.chat.TurnState
import com.lisdatasolutions.microrag.domain.chat.WarmUpAnswerEngineUseCase
import com.lisdatasolutions.microrag.domain.chat.toSearchScope
import com.lisdatasolutions.microrag.domain.library.FoldersResult
import com.lisdatasolutions.microrag.domain.library.ObserveFoldersUseCase
import com.lisdatasolutions.microrag.domain.rag.FolderList
import com.lisdatasolutions.microrag.feature.chat.scope.ScopeSelection
import com.lisdatasolutions.microrag.feature.chat.scope.ScopeSummary
import com.lisdatasolutions.microrag.feature.settings.waitTenthsFor
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
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filterNotNull
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
    private val historyCleared: ObserveHistoryClearedUseCase,
    private val openConversation: OpenConversationUseCase,
    private val conversationDeletions: ObserveConversationDeletionsUseCase,
    private val conversationExists: ConversationExistsUseCase,
    /** F17: the folders' names for the chip, and the scope of each conversation (contract S1–S4). */
    observeFolders: ObserveFoldersUseCase,
    private val conversationScope: ConversationScopeUseCase,
    private val lastScope: LastScopeUseCase,
    private val applyScope: ApplyScopeUseCase,
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

    /** F17: the scope of this entry's conversation, or of a chat without questions; null until known. */
    private val scope = MutableStateFlow<ChatScope?>(null)
    private val selectingScope = savedState.getStateFlow(SCOPE_SHEET, false)
    private val scopeNotice = MutableStateFlow<ScopeNotice?>(null)
    private var scopeJob: Job? = null

    /** The availability within the scope (contract S2b); nothing is promised before the scope is known. */
    private val availability: StateFlow<ChatAvailability?> = scope.filterNotNull()
        .map { it.toSearchScope() }
        .distinctUntilChanged()
        .flatMapLatest { observeAvailability(availabilityRetries, it) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val folders: StateFlow<FolderList?> = observeFolders()
        .map { (it as? FoldersResult.Loaded)?.folders }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val summary: StateFlow<ScopeSummary?> = combine(scope, folders) { scope, folders -> scope?.summary(folders) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** Between ON_START and ON_STOP of the screen. */
    private val onScreen = MutableStateFlow(false)

    private val view: StateFlow<ConversationView?> = combine(conversationId, resolved) { id, done -> id to done }
        .flatMapLatest { (id, done) ->
            when {
                !done -> flowOf(null)
                id == null -> flowOf(ConversationView(emptyList(), null))
                else -> observeConversation(id)
            }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** Saved sources with the time of their turn, and the blocks of the answer in progress (F13 Q12, Q15). */
    private val sources: StateFlow<Map<CitedSource, SourceAvailability>> = view
        .map { v ->
            v?.turns.orEmpty().flatMap { turn -> turn.sources.map { CitedSource(it, turn.createdAt) } } +
                v?.active?.blocks.orEmpty().map { it.cited() }
        }
        .flatMapLatest { resolveSources(it) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    val state: StateFlow<ChatUiState> = combine(
        combine(view, availability, sources, ::Triple),
        combine(question, confirmDelete, copied, announcement) { q, confirm, copy, said -> Local(q, confirm, copy, said) },
        combine(summary, selectingScope, scopeNotice, ::Triple),
    ) { (view, availability, sources), local, (summary, selecting, notice) ->
        val turns = view?.let { turnsOf(it, availability, sources) }.orEmpty()
        ChatUiState(
            conversationId = savedConversation,
            availability = availability,
            turns = turns,
            question = local.question,
            answering = view?.active?.let { it.state == TurnState.RETRIEVING || it.state == TurnState.GENERATING } == true,
            confirmDelete = local.confirmDelete,
            copiedTurnId = local.copied,
            announcement = local.announcement,
            scope = summary,
            selectingScope = selecting,
            scopeNotice = notice,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, ChatUiState())

    /** The conversation of this entry when it is saved: not one that only lives in memory (C16). */
    private val savedConversation: Long? get() = conversationId.value?.takeIf { it != ActiveAnswer.UNSAVED }

    private data class Local(val question: String, val confirmDelete: Boolean, val copied: Long?, val announcement: ChatAnnouncement?)

    init {
        if (!resolved.value) viewModelScope.launch { resolve() }
        viewModelScope.launch { announceChanges() }
        // F12 P11: «Borrar historial» in Ajustes deleted what this chat shows; its current value is not news.
        viewModelScope.launch {
            historyCleared().drop(1).collect {
                savedState[CONFIRM_DELETE] = false
                setConversation(null)
            }
        }
        // F13 Q8: this conversation deleted elsewhere (Historial) leaves the chat empty.
        viewModelScope.launch {
            conversationDeletions().collect { id ->
                if (id != conversationId.value) return@collect
                savedState[CONFIRM_DELETE] = false
                setConversation(null)
            }
        }
        // F13 Q16: restored after a process death, the conversation may have been deleted meanwhile.
        if (resolved.value) {
            savedConversation?.let { id -> viewModelScope.launch { if (!conversationExists(id)) setConversation(null) } }
            loadScope(conversationId.value)
        }
        // Gemma loads whenever the chat is ready on screen: back from hidden, and when a batch ends or the
        // models get ready while it shows (found on the phone, T069). Never while hidden: it was released.
        viewModelScope.launch {
            combine(onScreen, availability) { shown, now -> shown && now is ChatAvailability.Ready }
                .distinctUntilChanged()
                .collect { ready -> if (ready) warmUp() }
        }
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
        val changed = id != conversationId.value || scope.value == null
        conversationId.value = id
        savedState[CONVERSATION] = id
        resolved.value = true
        savedState[RESOLVED] = true
        if (changed) loadScope(id)
    }

    /**
     * F17 S1: a saved conversation's scope as it means now, with its notice (K10); a chat without questions
     * keeps its own in the SavedStateHandle, and starts with the last one used (FR-020b).
     */
    private fun loadScope(id: Long?) {
        scopeJob?.cancel()
        scopeJob = viewModelScope.launch {
            val saved = id?.takeIf { it != ActiveAnswer.UNSAVED }
            val pending = ScopeSelection.decode(savedState[PENDING_SCOPE])?.toScope()
            val resolved = when {
                saved != null -> conversationScope(saved)
                pending != null -> ResolvedScope(pending)
                else -> lastScope()
            }
            scope.value = resolved.scope
            scopeNotice.value = when {
                resolved.fellBackToWholeLibrary -> ScopeNotice.WHOLE_AGAIN
                resolved.droppedFolders > 0 -> ScopeNotice.FOLDER_GONE
                resolved.droppedDocuments > 0 -> ScopeNotice.DOCUMENT_GONE
                else -> null
            }
        }
    }

    /** The scope the selector starts from. */
    fun currentScope(): ChatScope = scope.value ?: ChatScope.WholeLibrary

    /** K7 and K9: the chip and «Cambiar ámbito» open the selector. */
    fun onChangeScope() {
        savedState[SCOPE_SHEET] = true
    }

    fun onDismissScope() {
        savedState[SCOPE_SHEET] = false
    }

    /** S3: for the next question; a chat without questions keeps it until its first one, and as the last scope. */
    fun onApplyScope(applied: ChatScope) {
        savedState[SCOPE_SHEET] = false
        scope.value = applied
        scopeNotice.value = null
        val saved = savedConversation
        if (saved == null) savedState[PENDING_SCOPE] = ScopeSelection.of(applied).encode()
        viewModelScope.launch { applyScope(saved, applied) }
    }

    fun onScopeNoticeShown() {
        scopeNotice.value = null
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
                    // F20 (contract U3): «Leyendo 1 página» instead, when a page entered.
                    TurnState.GENERATING -> if (v.active?.takeIf { active }?.pageTokens != null) ChatAnnouncement.READING_PAGE else ChatAnnouncement.GENERATING
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
            val id = ask(conversationId.value, text, scope.value ?: ChatScope.WholeLibrary) ?: return@launch
            savedState[PENDING_SCOPE] = null
            setConversation(id)
            savedState[QUESTION] = ""
        }
    }

    fun onStop() = cancelAnswer()

    fun onRetry(turnId: Long) {
        viewModelScope.launch { retryAnswer(turnId) }
    }

    /**
     * F13 Q11: Historial's request. An answer of another conversation stops, as with «Nuevo chat», and the
     * draft stays; resolved here, without navigating (research R4).
     */
    fun onOpenConversation(id: Long) {
        savedState[CONFIRM_DELETE] = false
        setConversation(id)
        viewModelScope.launch { openConversation(id) }
    }

    /** «Nuevo chat» of «⋯»: resolved here, without navigating to the same destination (research R13). */
    fun onNewChat() {
        startNewConversation()
        // FR-020b: the new chat starts with the last scope used.
        savedState[PENDING_SCOPE] = null
        scope.value = null
        setConversation(null)
    }

    fun onDelete() {
        if (savedConversation != null) savedState[CONFIRM_DELETE] = true
    }

    fun onDismissDelete() {
        savedState[CONFIRM_DELETE] = false
    }

    fun onConfirmDelete() {
        savedState[CONFIRM_DELETE] = false
        val id = savedConversation ?: return
        viewModelScope.launch {
            // F13 R6: a refused deletion keeps the conversation, and says so.
            if (!deleteConversation(id)) {
                announcement.value = ChatAnnouncement.DELETE_FAILED
                return@launch
            }
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

    /** Back on screen (ON_START): Gemma loads if the chat is ready; it was released when the app was hidden. */
    fun onStart() {
        onScreen.value = true
    }

    /** ON_STOP: the chat no longer loads Gemma when it becomes ready. */
    fun onHidden() {
        onScreen.value = false
    }

    private fun turnsOf(view: ConversationView, availability: ChatAvailability?, available: Map<CitedSource, SourceAvailability>): List<TurnUi> {
        val canRetry = availability is ChatAvailability.Ready && view.active?.state?.let { it == TurnState.RETRIEVING || it == TurnState.GENERATING } != true
        val saved = view.turns.map { turn ->
            val active = view.active?.takeIf { it.turnId == turn.id }
            turnUi(turn, active, available, canRetry)
        }
        // C16: an answer that could not be saved has no turn; it shows from memory.
        val unsaved = view.active?.takeIf { a -> a.turnId == ActiveAnswer.UNSAVED || saved.none { it.id == a.turnId } }
        return saved + listOfNotNull(unsaved?.let { unsavedTurn(it, available) })
    }

    private fun turnUi(turn: ChatTurn, active: ActiveAnswer?, available: Map<CitedSource, SourceAvailability>, canRetry: Boolean): TurnUi {
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
            status = status(turn.state, active),
            pageWaitTenths = active?.pageTokens?.let(::waitTenthsFor),
            content = content,
            refusal = turn.refusal,
            failureText = failureText(turn.state, turn.failure),
            notes = notes(turn.state, turn.refusal, content, sources, turn.partialCoverage, active?.saveFailed == true),
            sources = sources.map { sourceRow(it, available[CitedSource(it, turn.createdAt)] ?: SourceAvailability.AVAILABLE) },
            chipTargets = if (streaming) {
                chipTargets(turn.answer, blocks, available)
            } else {
                // F13 Q12: a deleted document's chip opens nothing, as its row.
                sources.map { source -> source.target().takeIf { available[CitedSource(source, turn.createdAt)] != SourceAvailability.DOCUMENT_GONE } }
            },
            retryable = retryable,
            canRetry = canRetry && retryable,
        )
    }

    private fun unsavedTurn(active: ActiveAnswer, available: Map<CitedSource, SourceAvailability>): TurnUi {
        val streaming = active.state == TurnState.RETRIEVING || active.state == TurnState.GENERATING
        val content = if (active.refusal) AnswerContent.Empty else AnswerFormatter.format(active.text, active.blocks.size)
        val targets = chipTargets(active.text, active.blocks, available)
        return TurnUi(
            id = ActiveAnswer.UNSAVED,
            question = active.question,
            state = answerState(active.state),
            status = status(active.state, active),
            pageWaitTenths = active.pageTokens?.let(::waitTenthsFor),
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

    /**
     * Chip `k` opens the k-th block in first-citation order (D-053), as the saved sources will be; null
     * when that block's document was deleted meanwhile (F13 Q12).
     */
    private fun chipTargets(text: String, blocks: List<PromptBlockRef>, available: Map<CitedSource, SourceAvailability>): List<SourceTarget?> {
        val byNumber = blocks.associateBy { it.promptNumber }
        return AnswerReading.citedOrder(text, blocks.size).mapNotNull { byNumber[it] }
            .map { block -> block.target().takeIf { available[block.cited()] != SourceAvailability.DOCUMENT_GONE } }
    }

    /** F20: a page or an image opens the detail on its page, a fragment on its chunk. */
    private fun AnswerSource.target() = SourceTarget(documentId, chunkId, page.takeIf { kind != SourceKind.FRAGMENT })

    private fun PromptBlockRef.target() = SourceTarget(documentId, chunkId, page.takeIf { kind != SourceKind.FRAGMENT })

    /** A prompt block comes from the index of the moment, so any document it names was there before (F13 Q15). */
    private fun PromptBlockRef.cited() = CitedSource(
        AnswerSource(
            position = 0, promptNumber = promptNumber, documentId = documentId, chunkId = chunkId, firstOrdinal = firstOrdinal,
            lastOrdinal = lastOrdinal, documentName = documentName, page = page, kind = kind,
        ),
        citedAt = Long.MAX_VALUE,
    )

    private fun sourceRow(source: AnswerSource, availability: SourceAvailability): SourceRow = SourceRow(
        position = source.position,
        target = source.target(),
        citation = when (source.kind) {
            SourceKind.FRAGMENT -> CitationUi(
                documentName = source.documentName,
                page = source.page,
                fragment = source.firstOrdinal + 1,
                available = availability != SourceAvailability.DOCUMENT_GONE,
                lastFragment = (source.lastOrdinal + 1).takeIf { source.lastOrdinal > source.firstOrdinal },
            )
            // F20 (contract U4, U5): no fragments; an image has no page number to show.
            SourceKind.PAGE, SourceKind.IMAGE -> CitationUi(
                documentName = source.documentName,
                page = source.page.takeIf { source.kind == SourceKind.PAGE },
                fragment = null,
                available = availability != SourceAvailability.DOCUMENT_GONE,
                kind = if (source.kind == SourceKind.PAGE) CitationKind.PAGE else CitationKind.IMAGE,
            )
        },
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

    private fun status(state: TurnState, active: ActiveAnswer?): TurnStatus? = when (state) {
        TurnState.RETRIEVING -> TurnStatus.SEARCHING
        TurnState.GENERATING -> if (active?.pageTokens != null && active.text.isEmpty()) TurnStatus.READING_PAGE else TurnStatus.GENERATING
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
        const val PENDING_SCOPE = "chat_pending_scope"
        const val SCOPE_SHEET = "chat_scope_sheet"
        const val COPIED_MS = 2_000L
    }
}

/** FR-014: the folders by name, «General» last, and the loose documents; a folder not loaded yet is left out. */
private fun ChatScope.summary(folders: FolderList?): ScopeSummary = when (this) {
    ChatScope.WholeLibrary -> ScopeSummary(whole = true)
    is ChatScope.Some -> ScopeSummary(
        whole = false,
        folderNames = this.folders.mapNotNull { ref -> folders?.folders?.firstOrNull { it.id == ref.id }?.name } +
            if (general) listOf<String?>(null) else emptyList(),
        looseDocuments = documents.size,
    )
}

/** One-off events: the screen opens a source or writes the clipboard. */
sealed interface ChatEvent {
    data class OpenSource(val target: SourceTarget) : ChatEvent

    data class Copy(val text: String) : ChatEvent
}
