package com.lisdatasolutions.microrag.feature.history

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.lisdatasolutions.microrag.core.navigation.History
import com.lisdatasolutions.microrag.domain.chat.DeleteConversationUseCase
import com.lisdatasolutions.microrag.domain.chat.HistoryContent
import com.lisdatasolutions.microrag.domain.chat.HistoryLimits
import com.lisdatasolutions.microrag.domain.chat.ObserveAnswerInProgressUseCase
import com.lisdatasolutions.microrag.domain.chat.ObserveConversationsUseCase
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * F13 Historial (contracts/history-ui.md). The list comes in blocks of [HistoryLimits.PAGE], with the limit
 * and the conversation waiting for its confirmation in the [SavedStateHandle], so rotating or coming back
 * keeps them. Dates group in the zone read at [onStart], so a new day or zone shows when the screen does.
 */
class HistoryViewModel(
    private val savedState: SavedStateHandle,
    observeConversations: ObserveConversationsUseCase,
    private val deleteConversation: DeleteConversationUseCase,
    observeAnswerInProgress: ObserveAnswerInProgressUseCase,
    /** Only the instant: the zone comes from [zoneOf] at every start (research R3). */
    private val clock: Clock = Clock.systemUTC(),
    private val zoneOf: () -> ZoneId = ZoneId::systemDefault,
    backgroundDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {

    private val route = savedState.toRoute<History>()
    private val limit = savedState.getStateFlow(KEY_LIMIT, HistoryLimits.PAGE)
    private val confirmDelete = savedState.getStateFlow<Long?>(KEY_CONFIRM_DELETE, null)
    private val day = MutableStateFlow(today())

    private val events = Channel<HistoryEvent>(Channel.BUFFERED)
    val eventFlow: Flow<HistoryEvent> = events.receiveAsFlow()

    private val content: StateFlow<HistoryContent> = observeConversations(limit)
        .flowOn(backgroundDispatcher)
        .stateIn(viewModelScope, SharingStarted.Eagerly, HistoryContent.Loading)

    private val answering: StateFlow<Long?> = observeAnswerInProgress()
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val state: StateFlow<HistoryUiState> = combine(content, day, confirmDelete, answering) { content, day, pending, answering ->
        HistoryUiState(
            content = content.toScreen(day),
            openConversationId = route.openConversationId,
            confirmDeleteId = pending,
            confirmDeleteAnswering = pending != null && pending == answering,
            zone = day.zone,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, HistoryUiState(openConversationId = route.openConversationId, zone = day.value.zone))

    init {
        // A conversation gone while its confirmation is open (deleted elsewhere) closes the dialog.
        viewModelScope.launch {
            combine(content, confirmDelete) { content, pending -> content to pending }.collect { (content, pending) ->
                if (pending == null) return@collect
                val gone = when (content) {
                    HistoryContent.Loading -> false
                    HistoryContent.Unavailable -> true
                    is HistoryContent.Page -> content.page.entries.none { it.id == pending }
                }
                if (gone) savedState[KEY_CONFIRM_DELETE] = null
            }
        }
    }

    /** ON_START: the day and the zone are read again (research R3). */
    fun onStart() {
        day.value = today()
    }

    fun onLoadMore() {
        val shown = (content.value as? HistoryContent.Page)?.page ?: return
        if (!shown.hasMore || shown.entries.size < limit.value) return
        savedState[KEY_LIMIT] = limit.value + HistoryLimits.PAGE
    }

    fun onOpen(conversationId: Long) {
        viewModelScope.launch { events.send(HistoryEvent.Open(conversationId)) }
    }

    fun onNewChat() {
        viewModelScope.launch { events.send(HistoryEvent.NewChat) }
    }

    fun onDelete(conversationId: Long) {
        savedState[KEY_CONFIRM_DELETE] = conversationId
    }

    fun onDismissDelete() {
        savedState[KEY_CONFIRM_DELETE] = null
    }

    fun onConfirmDelete() {
        val id = confirmDelete.value ?: return
        savedState[KEY_CONFIRM_DELETE] = null
        viewModelScope.launch {
            events.send(if (deleteConversation(id)) HistoryEvent.Deleted else HistoryEvent.DeleteFailed)
        }
    }

    private fun today(): Day {
        val zone = zoneOf()
        return Day(LocalDate.now(clock.withZone(zone)), zone)
    }

    private data class Day(val date: LocalDate, val zone: ZoneId)

    private fun HistoryContent.toScreen(day: Day): HistoryScreenContent = when (this) {
        HistoryContent.Loading -> HistoryScreenContent.Loading
        HistoryContent.Unavailable -> HistoryScreenContent.Error
        is HistoryContent.Page -> if (page.entries.isEmpty()) {
            HistoryScreenContent.Empty
        } else {
            HistoryScreenContent.Sections(groupHistory(page.entries, day.date, day.zone), page.hasMore, page.entries.size)
        }
    }

    private companion object {
        const val KEY_LIMIT = "history_limit"
        const val KEY_CONFIRM_DELETE = "history_confirm_delete"
    }
}
