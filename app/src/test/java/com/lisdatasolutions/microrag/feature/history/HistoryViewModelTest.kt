package com.lisdatasolutions.microrag.feature.history

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.lisdatasolutions.microrag.domain.chat.ActiveAnswer
import com.lisdatasolutions.microrag.domain.chat.ChatStorageState
import com.lisdatasolutions.microrag.domain.chat.DeleteConversationUseCase
import com.lisdatasolutions.microrag.domain.chat.ObserveAnswerInProgressUseCase
import com.lisdatasolutions.microrag.domain.chat.ObserveConversationsUseCase
import com.lisdatasolutions.microrag.domain.chat.TurnState
import com.lisdatasolutions.microrag.domain.chat.TurnUpdate
import com.lisdatasolutions.microrag.testing.FakeChatSession
import com.lisdatasolutions.microrag.testing.InMemoryChatRepository
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** F13 T014 (data-model §6, contracts/history-api.md Q1–Q5): Historial's list. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class)
class HistoryViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val repository = InMemoryChatRepository()
    private val session = FakeChatSession(repository)
    private val madrid = ZoneId.of("Europe/Madrid")
    private val clock = MutableClock(at("2026-11-03T12:00"))
    private var zone = madrid

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun at(text: String): Instant = LocalDateTime.parse(text).atZone(madrid).toInstant()

    private fun viewModel(saved: SavedStateHandle = SavedStateHandle(mapOf("openConversationId" to null))) = HistoryViewModel(
        savedState = saved,
        observeConversations = ObserveConversationsUseCase(repository),
        deleteConversation = DeleteConversationUseCase(session),
        observeAnswerInProgress = ObserveAnswerInProgressUseCase(session),
        clock = clock,
        zoneOf = { zone },
        backgroundDispatcher = dispatcher,
    )

    /** A saved conversation with one question; the fake session leaves no answer in progress. */
    private suspend fun conversation(question: String, at: String): Long {
        repository.now = at(at).toEpochMilli()
        return session.ask(null, question).also { session.active.value = null }
    }

    private fun HistoryViewModel.sections() = (state.value.content as HistoryScreenContent.Sections)

    @Test
    fun itLoads_thenShowsTheSections() = runTest(dispatcher) {
        val first = conversation("¿Cuál es el plazo de devolución?", "2026-11-03T10:42")
        conversation("¿Horario?", "2026-10-12T11:00")
        val vm = viewModel()
        assertThat(vm.state.value.content).isEqualTo(HistoryScreenContent.Loading)

        advanceUntilIdle()

        val sections = vm.sections().sections
        assertThat(sections.map { it.group }).containsExactly(HistoryGroup.Today, HistoryGroup.Month(2026, java.time.Month.OCTOBER)).inOrder()
        assertThat(sections.first().entries.single().id).isEqualTo(first)
        assertThat(vm.sections().hasMore).isFalse()
    }

    @Test
    fun noConversations_isEmpty_andAnUnreadableStorage_isTheError() = runTest(dispatcher) {
        val empty = viewModel()
        advanceUntilIdle()
        assertThat(empty.state.value.content).isEqualTo(HistoryScreenContent.Empty)

        repository.storage = ChatStorageState.UNAVAILABLE
        val failing = viewModel()
        advanceUntilIdle()
        assertThat(failing.state.value.content).isEqualTo(HistoryScreenContent.Error)
    }

    @Test
    fun moreBlocks_areLoadedOnlyWhenThereAreMore_andTheLimitSurvives() = runTest(dispatcher) {
        repeat(120) { conversation("¿$it?", "2026-11-03T08:00") }
        val saved = SavedStateHandle(mapOf("openConversationId" to null))
        val vm = viewModel(saved)
        advanceUntilIdle()
        assertThat(vm.sections().loaded).isEqualTo(50)
        assertThat(vm.sections().hasMore).isTrue()

        vm.onLoadMore()
        vm.onLoadMore() // the block asked for has not arrived yet: nothing more is asked
        advanceUntilIdle()
        assertThat(vm.sections().loaded).isEqualTo(100)

        vm.onLoadMore()
        advanceUntilIdle()
        assertThat(vm.sections().loaded).isEqualTo(120)
        assertThat(vm.sections().hasMore).isFalse()
        vm.onLoadMore()
        assertThat(saved.get<Int>("history_limit")).isEqualTo(150)

        val restored = viewModel(saved)
        advanceUntilIdle()
        assertThat(restored.sections().loaded).isEqualTo(120)
    }

    /** Research R3: at every start, the instant and the zone are read again. */
    @Test
    fun onStart_readsTheDayAndTheZoneAgain() = runTest(dispatcher) {
        conversation("¿Uno?", "2026-11-03T10:00")
        val vm = viewModel()
        advanceUntilIdle()
        assertThat(vm.sections().sections.single().group).isEqualTo(HistoryGroup.Today)

        clock.now = at("2026-11-04T09:00")
        advanceUntilIdle()
        assertThat(vm.sections().sections.single().group).isEqualTo(HistoryGroup.Today)
        vm.onStart()
        advanceUntilIdle()
        assertThat(vm.sections().sections.single().group).isEqualTo(HistoryGroup.Yesterday)

        // 2026-11-04 09:00 in Madrid is already 2026-11-04 22:00 in Kiritimati: the 10:00 of the 3rd there is still yesterday.
        zone = ZoneId.of("Pacific/Kiritimati")
        vm.onStart()
        advanceUntilIdle()
        assertThat(vm.state.value.zone).isEqualTo(zone)
        assertThat(vm.sections().sections.single().group).isEqualTo(HistoryGroup.Yesterday)
    }

    @Test
    fun theOpenConversation_comesFromTheRoute() = runTest(dispatcher) {
        val vm = viewModel(SavedStateHandle(mapOf("openConversationId" to 7L)))
        advanceUntilIdle()
        assertThat(vm.state.value.openConversationId).isEqualTo(7L)
    }

    // --- US3: deleting (research R6, H6) ---------------------------------------------------------------

    @Test
    fun theBin_asksToConfirm_andThePendingIdSurvivesARecreation() = runTest(dispatcher) {
        val id = conversation("¿Uno?", "2026-11-03T10:00")
        val saved = SavedStateHandle(mapOf("openConversationId" to null))
        val vm = viewModel(saved)
        advanceUntilIdle()

        vm.onDelete(id)
        advanceUntilIdle()
        assertThat(vm.state.value.confirmDeleteId).isEqualTo(id)
        assertThat(vm.state.value.confirmDeleteAnswering).isFalse()

        val recreated = viewModel(saved)
        advanceUntilIdle()
        assertThat(recreated.state.value.confirmDeleteId).isEqualTo(id)

        recreated.onDismissDelete()
        advanceUntilIdle()
        assertThat(recreated.state.value.confirmDeleteId).isNull()
        assertThat(repository.conversationExists(id)).isTrue()
    }

    @Test
    fun theDialog_saysTheAnswerWillStop_onlyForTheConversationBeingAnswered() = runTest(dispatcher) {
        val answered = conversation("¿Uno?", "2026-11-03T10:00")
        val other = conversation("¿Dos?", "2026-11-03T11:00")
        val vm = viewModel()
        advanceUntilIdle()
        session.active.value = ActiveAnswer(answered, 1, "¿Uno?", TurnState.GENERATING)

        vm.onDelete(other)
        advanceUntilIdle()
        assertThat(vm.state.value.confirmDeleteAnswering).isFalse()
        vm.onDelete(answered)
        advanceUntilIdle()
        assertThat(vm.state.value.confirmDeleteAnswering).isTrue()
    }

    @Test
    fun confirming_deletes_andSaysSo_orSaysItFailed_keepingTheRow() = runTest(dispatcher) {
        val kept = conversation("¿Uno?", "2026-11-03T10:00")
        val deleted = conversation("¿Dos?", "2026-11-03T11:00")
        val vm = viewModel()
        val events = mutableListOf<HistoryEvent>()
        val collecting = launch { vm.eventFlow.toList(events) }
        advanceUntilIdle()

        vm.onDelete(deleted)
        vm.onConfirmDelete()
        advanceUntilIdle()
        assertThat(vm.state.value.confirmDeleteId).isNull()
        assertThat(vm.sections().sections.flatMap { it.entries }.map { it.id }).containsExactly(kept)

        repository.failWrites = true
        vm.onDelete(kept)
        vm.onConfirmDelete()
        advanceUntilIdle()
        repository.failWrites = false

        collecting.cancel()
        assertThat(events).containsExactly(HistoryEvent.Deleted, HistoryEvent.DeleteFailed).inOrder()
        assertThat(vm.sections().sections.flatMap { it.entries }.map { it.id }).containsExactly(kept)
    }

    @Test
    fun aConversationGoneWhileItsDialogIsOpen_closesTheDialog() = runTest(dispatcher) {
        val id = conversation("¿Uno?", "2026-11-03T10:00")
        val vm = viewModel()
        advanceUntilIdle()
        vm.onDelete(id)
        advanceUntilIdle()

        repository.deleteConversation(id)
        advanceUntilIdle()

        assertThat(vm.state.value.confirmDeleteId).isNull()
        assertThat(vm.state.value.content).isEqualTo(HistoryScreenContent.Empty)
    }

    // --- US4: «Nuevo chat» (FR-021) -----------------------------------------------------------------

    @Test
    fun newChat_isAnEvent_andANewConversationWithoutQuestions_isNotListed() = runTest(dispatcher) {
        repository.createConversation(at("2026-11-03T10:00").toEpochMilli())
        val vm = viewModel()
        val events = mutableListOf<HistoryEvent>()
        val collecting = launch { vm.eventFlow.toList(events) }
        advanceUntilIdle()

        vm.onNewChat()
        advanceUntilIdle()
        collecting.cancel()

        assertThat(events).containsExactly(HistoryEvent.NewChat)
        assertThat(vm.state.value.content).isEqualTo(HistoryScreenContent.Empty)
    }

    @Test
    fun opening_isAnEvent() = runTest(dispatcher) {
        val vm = viewModel()
        val events = mutableListOf<HistoryEvent>()
        val collecting = launch { vm.eventFlow.toList(events) }

        vm.onOpen(7)
        advanceUntilIdle()
        collecting.cancel()

        assertThat(events).containsExactly(HistoryEvent.Open(7))
    }

    // --- US5: a list always up to date (FR-008, FR-020, Q4) ----------------------------------------------

    @Test
    fun theListFollowsQuestionsAndDeletions() = runTest(dispatcher) {
        val older = conversation("¿Antigua?", "2026-11-02T10:00")
        val newer = conversation("¿Reciente?", "2026-11-03T11:00")
        val vm = viewModel()
        advanceUntilIdle()
        fun ids() = (vm.state.value.content as? HistoryScreenContent.Sections)?.sections?.flatMap { s -> s.entries.map { it.id } }
        assertThat(ids()).containsExactly(newer, older).inOrder()

        repository.now = at("2026-11-03T12:00").toEpochMilli()
        session.ask(older, "¿Otra pregunta?")
        session.active.value = null
        advanceUntilIdle()
        assertThat(ids()).containsExactly(older, newer).inOrder()
        assertThat(vm.sections().sections.single().group).isEqualTo(HistoryGroup.Today)

        session.delete(newer)
        advanceUntilIdle()
        assertThat(ids()).containsExactly(older)

        session.deleteAll()
        advanceUntilIdle()
        assertThat(vm.state.value.content).isEqualTo(HistoryScreenContent.Empty)
    }

    /** Q4: the checkpoints of an answer in progress never produce a new state of the list. */
    @Test
    fun theCheckpointsOfAnAnswer_doNotChangeTheList() = runTest(dispatcher) {
        val id = conversation("¿Uno?", "2026-11-03T10:00")
        val turn = repository.turns.values.single { it.conversationId == id }
        val vm = viewModel()
        advanceUntilIdle()
        val states = mutableListOf<HistoryUiState>()
        val collecting = launch { vm.state.toList(states) }
        advanceUntilIdle()

        repeat(5) { i -> repository.updateTurn(turn.id, TurnUpdate(state = TurnState.GENERATING, answer = "Parcial $i")) }
        advanceUntilIdle()
        collecting.cancel()

        assertThat(states).hasSize(1)
    }

    private class MutableClock(var now: Instant) : Clock() {
        override fun instant(): Instant = now
        override fun getZone(): ZoneId = ZoneId.of("UTC")
        override fun withZone(zone: ZoneId): Clock = object : Clock() {
            override fun instant(): Instant = now
            override fun getZone(): ZoneId = zone
            override fun withZone(zone: ZoneId): Clock = this@MutableClock.withZone(zone)
        }
    }
}
