package com.lisdatasolutions.microrag.feature.chat

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.lisdatasolutions.microrag.R
import com.lisdatasolutions.microrag.core.designsystem.model.AnswerState
import com.lisdatasolutions.microrag.domain.chat.ActiveAnswer
import com.lisdatasolutions.microrag.domain.chat.AnswerSource
import com.lisdatasolutions.microrag.domain.chat.ChatAvailability
import com.lisdatasolutions.microrag.domain.chat.ChatScope
import com.lisdatasolutions.microrag.domain.chat.ChatStorageState
import com.lisdatasolutions.microrag.domain.chat.PromptBlockRef
import com.lisdatasolutions.microrag.domain.chat.ResolvedScope
import com.lisdatasolutions.microrag.domain.chat.TurnFailure
import com.lisdatasolutions.microrag.domain.chat.TurnState
import com.lisdatasolutions.microrag.domain.chat.TurnUpdate
import com.lisdatasolutions.microrag.domain.indexing.IndexingState
import com.lisdatasolutions.microrag.domain.indexing.RunProgress
import com.lisdatasolutions.microrag.domain.rag.Folder
import com.lisdatasolutions.microrag.domain.rag.FolderRef
import com.lisdatasolutions.microrag.domain.rag.IndexCompatibility
import com.lisdatasolutions.microrag.feature.chat.scope.ScopeSummary
import com.lisdatasolutions.microrag.testing.ChatTestGraph
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** F11 T025: the chat's main path (contracts/chat-ui.md §1 and §3, research R13). */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class)
class ChatViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val graph = ChatTestGraph()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun saved(conversationId: Long? = null, newConversation: Boolean = false) =
        SavedStateHandle(mapOf("conversationId" to conversationId, "newConversation" to newConversation))

    private fun source(documentId: Long) = AnswerSource(
        position = 1, promptNumber = 1, documentId = documentId, chunkId = 186, firstOrdinal = 85, lastOrdinal = 85,
        documentName = "Manual de procesos.pdf", page = 12,
    )

    private fun block(documentId: Long) = PromptBlockRef(
        promptNumber = 1, documentId = documentId, chunkId = 186, firstOrdinal = 85, lastOrdinal = 85,
        documentName = "Manual de procesos.pdf", page = 12,
    )

    /** A conversation with one completed answer citing [documentId], asked at [at]; its id. */
    private suspend fun answered(documentId: Long, at: Long = 0): Long {
        val id = graph.chats.createConversation(at)
        val turn = graph.chats.insertTurn(id, "¿Qué plazo hay?", partialCoverage = false, now = at)
        graph.chats.updateTurn(turn.id, TurnUpdate(state = TurnState.COMPLETED, answer = "Son 30 días [1].", promptBlocks = 1, finishedAt = at + 1))
        graph.chats.replaceSources(turn.id, listOf(source(documentId)))
        return id
    }

    @Test
    fun sending_emptiesTheComposer_showsStop_andSavesTheNewConversation() = runTest(dispatcher) {
        graph.addDocument("Manual de procesos.pdf")
        val saved = saved()
        val vm = graph.chatViewModel(saved)
        advanceUntilIdle()
        assertThat(vm.state.value.ready).isTrue()
        assertThat(vm.state.value.canSend).isFalse()

        vm.onQuestionChange("¿Qué plazo hay?")
        advanceUntilIdle()
        assertThat(vm.state.value.canSend).isTrue()
        vm.onSend()
        advanceUntilIdle()

        val state = vm.state.value
        assertThat(state.question).isEmpty()
        assertThat(state.answering).isTrue()
        assertThat(state.canSend).isFalse()
        assertThat(state.turns.single().question).isEqualTo("¿Qué plazo hay?")
        assertThat(state.turns.single().status).isEqualTo(TurnStatus.SEARCHING)
        assertThat(state.conversationId).isNotNull()
        assertThat(saved.get<Long>("chat_conversation")).isEqualTo(state.conversationId)
        assertThat(graph.session.calls).containsExactly("ask")
    }

    @Test
    fun theTurns_joinWhatIsSaved_andTheAnswerInProgress() = runTest(dispatcher) {
        val document = graph.addDocument("Manual de procesos.pdf")
        val id = answered(document)
        val second = graph.chats.insertTurn(id, "¿Y quién firma?", partialCoverage = false, now = 2)
        graph.session.active.value = ActiveAnswer(id, second.id, "¿Y quién firma?", TurnState.GENERATING, text = "Lo firma [1]", blocks = listOf(block(document)))

        val vm = graph.chatViewModel(saved())
        advanceUntilIdle()

        val turns = vm.state.value.turns
        assertThat(turns.map { it.question }).containsExactly("¿Qué plazo hay?", "¿Y quién firma?").inOrder()
        assertThat(turns[0].state).isEqualTo(AnswerState.Completed)
        assertThat(turns[0].sources.single().target).isEqualTo(SourceTarget(document, 186))
        assertThat(turns[0].sources.single().citation.fragment).isEqualTo(86)
        assertThat(turns[1].state).isEqualTo(AnswerState.Streaming)
        assertThat(turns[1].status).isEqualTo(TurnStatus.GENERATING)
        assertThat(turns[1].sources).isEmpty()
        assertThat(turns[1].chipTargets).containsExactly(SourceTarget(document, 186))
        assertThat(vm.state.value.answering).isTrue()
    }

    @Test
    fun theConversation_isTheSavedOne_thenTheRoutes_thenANewOne_elseTheLast() = runTest(dispatcher) {
        val document = graph.addDocument("Manual de procesos.pdf")
        val first = answered(document, at = 0)
        val last = answered(document, at = 10)

        val tab = graph.chatViewModel(saved())
        val fromRoute = graph.chatViewModel(saved(conversationId = first))
        val restored = graph.chatViewModel(saved(conversationId = first).apply { set("chat_conversation", last); set("chat_resolved", true) })
        advanceUntilIdle()
        assertThat(tab.state.value.conversationId).isEqualTo(last)
        assertThat(fromRoute.state.value.conversationId).isEqualTo(first)
        assertThat(restored.state.value.conversationId).isEqualTo(last)
        assertThat(graph.session.calls).isEmpty()

        val fresh = graph.chatViewModel(saved(newConversation = true))
        advanceUntilIdle()
        assertThat(fresh.state.value.conversationId).isNull()
        assertThat(fresh.state.value.turns).isEmpty()
        assertThat(graph.session.calls).containsExactly("new")
    }

    @Test
    fun openingASource_sendsItsDocumentAndChunk() = runTest(dispatcher) {
        val vm = graph.chatViewModel(saved())
        val events = mutableListOf<ChatEvent>()
        backgroundScope.launch { vm.eventFlow.toList(events) }

        vm.onOpenSource(SourceTarget(7, 186))
        advanceUntilIdle()

        assertThat(events).containsExactly(ChatEvent.OpenSource(SourceTarget(7, 186)))
    }

    @Test
    fun eachChangeOfTheAnswer_isAnnouncedOnce_neverItsGrowth() = runTest(dispatcher) {
        val document = graph.addDocument("Manual de procesos.pdf")
        val vm = graph.chatViewModel(saved())
        val said = mutableListOf<ChatAnnouncement?>()
        // Not in backgroundScope: advanceUntilIdle would leave its last delivery pending.
        val listening = launch { vm.state.map { it.announcement }.distinctUntilChanged().toList(said) }
        advanceUntilIdle()

        vm.onQuestionChange("¿Quién firma?")
        vm.onSend()
        advanceUntilIdle()
        val asked = graph.session.active.value!!
        listOf("Lo firma", "Lo firma la persona", "Lo firma la persona responsable [1]").forEach { text ->
            graph.session.active.value = asked.copy(state = TurnState.GENERATING, text = text, blocks = listOf(block(document)))
            advanceUntilIdle()
        }
        graph.chats.updateTurn(asked.turnId, TurnUpdate(state = TurnState.COMPLETED, answer = "Lo firma la persona responsable [1].", promptBlocks = 1))
        graph.session.active.value = null
        advanceUntilIdle()
        listening.cancel()

        assertThat(said).containsExactly(null, ChatAnnouncement.SEARCHING, ChatAnnouncement.GENERATING, ChatAnnouncement.COMPLETED).inOrder()
    }

    @Test
    fun aConversationOpened_announcesNothing() = runTest(dispatcher) {
        answered(graph.addDocument("Manual de procesos.pdf"))
        val vm = graph.chatViewModel(saved())
        advanceUntilIdle()

        assertThat(vm.state.value.turns).hasSize(1)
        assertThat(vm.state.value.announcement).isNull()
    }

    @Test
    fun copying_writesTheText_andConfirmsIt_onlyUpToAndroid12() = runTest(dispatcher) {
        val old = graph.chatViewModel(saved(), sdkInt = 32)
        val current = graph.chatViewModel(saved(), sdkInt = 33)
        val events = mutableListOf<ChatEvent>()
        backgroundScope.launch { old.eventFlow.toList(events) }
        backgroundScope.launch { current.eventFlow.toList(events) }

        old.onCopy(1, "Son 30 días [1].")
        current.onCopy(1, "Son 30 días [1].")
        advanceTimeBy(1_000)
        assertThat(old.state.value.copiedTurnId).isEqualTo(1)
        assertThat(current.state.value.copiedTurnId).isNull()
        assertThat(events).containsExactly(ChatEvent.Copy("Son 30 días [1]."), ChatEvent.Copy("Son 30 días [1]."))

        advanceTimeBy(1_500)
        assertThat(old.state.value.copiedTurnId).isNull()
    }

    @Test
    fun theQuestion_isCutAt1000Units_withoutSplittingAPair() = runTest(dispatcher) {
        val vm = graph.chatViewModel(saved())
        vm.onQuestionChange("a".repeat(999) + "😀")
        advanceUntilIdle()

        assertThat(vm.state.value.question).isEqualTo("a".repeat(999))

        // A long paste keeps its first 1,000 units.
        vm.onQuestionChange("b".repeat(2_500))
        advanceUntilIdle()
        assertThat(vm.state.value.question).isEqualTo("b".repeat(1_000))
    }

    // US2 (T040): the refusal and an answer without citations (C1, C2).

    /** A conversation whose only turn ended COMPLETED with [answer] and [sources]; its id. */
    private suspend fun completed(answer: String, refusal: Boolean = false, sources: List<AnswerSource> = emptyList()): Long {
        val id = graph.chats.createConversation(0)
        val turn = graph.chats.insertTurn(id, "¿Plazo?", partialCoverage = false, now = 0)
        graph.chats.updateTurn(turn.id, TurnUpdate(state = TurnState.COMPLETED, answer = answer, refusal = refusal, promptBlocks = 2))
        graph.chats.replaceSources(turn.id, sources)
        return id
    }

    @Test
    fun theRefusal_hasNoContentChipsSourcesOrNotes_theScreenShowsTheSentence() = runTest(dispatcher) {
        completed("No encuentro esa información en los documentos disponibles.", refusal = true)
        val vm = graph.chatViewModel(saved())
        advanceUntilIdle()

        with(vm.state.value.turns.single()) {
            assertThat(refusal).isTrue()
            assertThat(state).isEqualTo(AnswerState.Completed)
            assertThat(content.isEmpty).isTrue()
            assertThat(sources).isEmpty()
            assertThat(chipTargets).isEmpty()
            assertThat(notes).isEmpty()
        }
    }

    @Test
    fun anAnswerWithoutValidCitations_carriesTheC2Note() = runTest(dispatcher) {
        completed("El plazo es de 30 días [7].")
        val vm = graph.chatViewModel(saved())
        advanceUntilIdle()

        with(vm.state.value.turns.single()) {
            assertThat(content.isEmpty).isFalse()
            assertThat(sources).isEmpty()
            assertThat(notes).containsExactly(R.string.chat_no_citations)
        }
    }

    @Test
    fun aBlankQuestion_cannotBeSent() = runTest(dispatcher) {
        graph.addDocument("Manual de procesos.pdf")
        val vm = graph.chatViewModel(saved())
        vm.onQuestionChange("   \n ")
        advanceUntilIdle()

        assertThat(vm.state.value.ready).isTrue()
        assertThat(vm.state.value.canSend).isFalse()
        vm.onSend()
        advanceUntilIdle()
        assertThat(graph.session.calls).isEmpty()
    }

    // US3 (T043): stop, failures, retry and copy (C7, C8, C10, U10).

    /** A conversation whose only turn ended in [state]; the ids of the conversation and the turn. */
    private suspend fun ended(state: TurnState, failure: TurnFailure? = null): Pair<Long, Long> {
        val id = graph.chats.createConversation(0)
        val turn = graph.chats.insertTurn(id, "¿Plazo?", partialCoverage = false, now = 0)
        graph.chats.updateTurn(turn.id, TurnUpdate(state = state, answer = "El plazo", failure = failure, promptBlocks = 1))
        return id to turn.id
    }

    @Test
    fun stopping_turnsStopBackIntoSend() = runTest(dispatcher) {
        graph.addDocument("Manual de procesos.pdf")
        val vm = graph.chatViewModel(saved())
        advanceUntilIdle()
        vm.onQuestionChange("¿Plazo?")
        vm.onSend()
        advanceUntilIdle()
        assertThat(vm.state.value.answering).isTrue()

        vm.onStop()
        val asked = graph.session.active.value!!
        graph.chats.updateTurn(asked.turnId, TurnUpdate(state = TurnState.CANCELLED, answer = "El plazo"))
        graph.session.active.value = null
        advanceUntilIdle()

        assertThat(graph.session.calls).containsExactly("ask", "cancel").inOrder()
        assertThat(vm.state.value.answering).isFalse()
        assertThat(vm.state.value.turns.single().state).isEqualTo(AnswerState.Cancelled)
        vm.onQuestionChange("¿Y el plazo para empresas?")
        advanceUntilIdle()
        assertThat(vm.state.value.canSend).isTrue()
    }

    @Test
    fun failures_sayWhatFailed_andRetryRepeatsTheTurn() = runTest(dispatcher) {
        graph.addDocument("Manual de procesos.pdf")
        val (index, indexTurn) = ended(TurnState.FAILED, TurnFailure.INDEX)
        val (generation, _) = ended(TurnState.FAILED, TurnFailure.GENERATION)
        val (interrupted, _) = ended(TurnState.INTERRUPTED)
        val turnOf = { id: Long ->
            graph.chatViewModel(saved(conversationId = id)).also { advanceUntilIdle() }.state.value.turns.single()
        }

        with(turnOf(index)) {
            assertThat(state).isEqualTo(AnswerState.Failed)
            assertThat(failureText).isEqualTo(R.string.chat_index_failed)
            assertThat(retryable).isTrue()
            assertThat(canRetry).isTrue()
        }
        // C7 keeps F01's sentence.
        assertThat(turnOf(generation).failureText).isNull()
        assertThat(turnOf(interrupted).failureText).isEqualTo(R.string.chat_interrupted)

        val vm = graph.chatViewModel(saved(conversationId = index))
        advanceUntilIdle()
        vm.onRetry(indexTurn)
        advanceUntilIdle()
        assertThat(graph.session.calls).containsExactly("retry:$indexTurn")
    }

    @Test
    fun retry_isOff_whileAnotherAnswerRuns_orTheChatIsNotReady() = runTest(dispatcher) {
        val (id, _) = ended(TurnState.FAILED, TurnFailure.GENERATION)
        val vm = graph.chatViewModel(saved(conversationId = id))
        advanceUntilIdle()
        // No document: EmptyLibrary.
        with(vm.state.value.turns.single()) {
            assertThat(retryable).isTrue()
            assertThat(canRetry).isFalse()
        }

        graph.addDocument("Manual de procesos.pdf")
        val running = graph.chats.insertTurn(id, "¿Otra?", partialCoverage = false, now = 1)
        graph.session.active.value = ActiveAnswer(id, running.id, "¿Otra?", TurnState.GENERATING)
        advanceUntilIdle()

        assertThat(vm.state.value.ready).isTrue()
        assertThat(vm.state.value.turns.first().canRetry).isFalse()
    }

    @Test
    fun copying_upToAndroid12_isAnnounced_once_andNotFrom13() = runTest(dispatcher) {
        val old = graph.chatViewModel(saved(), sdkInt = 31)
        val current = graph.chatViewModel(saved(), sdkInt = 33)
        advanceUntilIdle()

        old.onCopy(1, "Texto")
        current.onCopy(1, "Texto")
        advanceTimeBy(100)
        assertThat(old.state.value.announcement).isEqualTo(ChatAnnouncement.COPIED)
        assertThat(current.state.value.announcement).isNull()

        advanceTimeBy(2_000)
        assertThat(old.state.value.announcement).isNull()
    }

    // US5 (T050): the saved conversation, «⋯», sources and storage (R13, C11, C15, C16).

    @Test
    fun aNewChatEntryThatAsked_comesBackToItsConversation_whenRecreated() = runTest(dispatcher) {
        graph.addDocument("Manual de procesos.pdf")
        val saved = saved(newConversation = true)
        val vm = graph.chatViewModel(saved)
        advanceUntilIdle()
        vm.onQuestionChange("¿Plazo?")
        vm.onSend()
        advanceUntilIdle()
        val id = vm.state.value.conversationId!!

        val recreated = graph.chatViewModel(saved)
        advanceUntilIdle()

        assertThat(recreated.state.value.conversationId).isEqualTo(id)
        assertThat(recreated.state.value.turns.single().question).isEqualTo("¿Plazo?")
        // Only the first entry started a new conversation.
        assertThat(graph.session.calls).containsExactly("new", "ask").inOrder()
    }

    @Test
    fun newChat_fromTheMenu_opensAnEmptyConversation_withoutNavigating() = runTest(dispatcher) {
        answered(graph.addDocument("Manual de procesos.pdf"))
        val saved = saved()
        val vm = graph.chatViewModel(saved)
        advanceUntilIdle()
        assertThat(vm.state.value.turns).hasSize(1)

        vm.onNewChat()
        advanceUntilIdle()

        assertThat(graph.session.calls).containsExactly("new")
        assertThat(vm.state.value.conversationId).isNull()
        assertThat(vm.state.value.turns).isEmpty()
        assertThat(saved.get<Long>("chat_conversation")).isNull()
    }

    @Test
    fun deleting_asksFirst_thenDeletes_andAnnouncesIt_C15() = runTest(dispatcher) {
        val id = answered(graph.addDocument("Manual de procesos.pdf"))
        val vm = graph.chatViewModel(saved())
        advanceUntilIdle()

        vm.onDelete()
        advanceUntilIdle()
        assertThat(vm.state.value.confirmDelete).isTrue()
        vm.onDismissDelete()
        advanceUntilIdle()
        assertThat(vm.state.value.confirmDelete).isFalse()
        assertThat(graph.session.calls).isEmpty()

        vm.onDelete()
        vm.onConfirmDelete()
        advanceUntilIdle()

        assertThat(graph.session.calls).containsExactly("delete:$id")
        assertThat(vm.state.value.confirmDelete).isFalse()
        assertThat(vm.state.value.conversationId).isNull()
        assertThat(vm.state.value.turns).isEmpty()
        assertThat(vm.state.value.announcement).isEqualTo(ChatAnnouncement.DELETED)
    }

    /** F12 P11: «Borrar historial» in Ajustes empties the chat and closes its delete dialog. */
    @Test
    fun clearingTheHistory_emptiesTheChat_andClosesTheDeleteDialog() = runTest(dispatcher) {
        val id = answered(graph.addDocument("Manual de procesos.pdf"))
        val vm = graph.chatViewModel(saved())
        advanceUntilIdle()
        assertThat(vm.state.value.conversationId).isEqualTo(id)
        vm.onDelete()
        advanceUntilIdle()
        assertThat(vm.state.value.confirmDelete).isTrue()

        graph.session.deleteAll()
        advanceUntilIdle()

        assertThat(vm.state.value.conversationId).isNull()
        assertThat(vm.state.value.turns).isEmpty()
        assertThat(vm.state.value.confirmDelete).isFalse()
    }

    @Test
    fun aHistoryClearedBeforeTheChatOpened_doesNothing() = runTest(dispatcher) {
        graph.session.historyCleared.value = 3
        val id = answered(graph.addDocument("Manual de procesos.pdf"))
        val vm = graph.chatViewModel(saved())
        advanceUntilIdle()

        assertThat(vm.state.value.conversationId).isEqualTo(id)
        assertThat(vm.state.value.turns).isNotEmpty()
    }

    @Test
    fun deleting_needsASavedConversation() = runTest(dispatcher) {
        val vm = graph.chatViewModel(saved())
        advanceUntilIdle()

        vm.onDelete()
        advanceUntilIdle()

        assertThat(vm.state.value.confirmDelete).isFalse()
    }

    @Test
    fun aDeletedDocument_readsUnavailable_andABlockOfSeveralFragments_givesItsRange_C11() = runTest(dispatcher) {
        val document = graph.addDocument("Manual de procesos.pdf")
        val id = graph.chats.createConversation(0)
        // Asked after the document was imported: one created later would be another document (F13 Q15).
        val turn = graph.chats.insertTurn(id, "¿Plazo?", partialCoverage = false, now = 5_000)
        graph.chats.updateTurn(turn.id, TurnUpdate(state = TurnState.COMPLETED, answer = "Son 30 días [1][2].", promptBlocks = 2))
        graph.chats.replaceSources(
            turn.id,
            listOf(
                source(document).copy(firstOrdinal = 85, lastOrdinal = 86),
                source(99).copy(position = 2, promptNumber = 2, chunkId = 241, documentName = "Protocolo interno.pdf"),
            ),
        )
        val vm = graph.chatViewModel(saved())
        advanceUntilIdle()

        val (block, gone) = vm.state.value.turns.single().sources
        assertThat(block.citation.available).isTrue()
        assertThat(block.citation.fragment).isEqualTo(86)
        assertThat(block.citation.lastFragment).isEqualTo(87)
        assertThat(gone.citation.available).isFalse()
    }

    @Test
    fun anAnswerThatCouldNotBeSaved_showsFromMemory_withItsNote_C16() = runTest(dispatcher) {
        graph.addDocument("Manual de procesos.pdf")
        val saved = saved().apply {
            set("chat_conversation", ActiveAnswer.UNSAVED)
            set("chat_resolved", true)
        }
        graph.session.active.value = ActiveAnswer(
            ActiveAnswer.UNSAVED, ActiveAnswer.UNSAVED, "¿Plazo?", TurnState.COMPLETED,
            text = "Son 30 días [1].", blocks = listOf(block(1)), saveFailed = true,
        )
        val vm = graph.chatViewModel(saved)
        advanceUntilIdle()

        with(vm.state.value.turns.single()) {
            assertThat(question).isEqualTo("¿Plazo?")
            assertThat(state).isEqualTo(AnswerState.Completed)
            assertThat(notes).containsExactly(R.string.chat_save_failed)
            assertThat(retryable).isFalse()
        }
        // Nothing saved to delete.
        assertThat(vm.state.value.conversationId).isNull()
        vm.onDelete()
        advanceUntilIdle()
        assertThat(vm.state.value.confirmDelete).isFalse()
    }

    // US6 (T054): the availability on screen (contracts/chat-ui.md §2).

    @Test
    fun gemmaPreloads_onStart_onlyWhenReady() = runTest(dispatcher) {
        val vm = graph.chatViewModel(saved())
        advanceUntilIdle()
        vm.onStart()
        advanceUntilIdle()
        assertThat(vm.state.value.availability).isEqualTo(ChatAvailability.EmptyLibrary)
        assertThat(graph.engine.warmUps).isEqualTo(0)

        graph.addDocument("Manual de procesos.pdf")
        advanceUntilIdle()
        vm.onStart()
        advanceUntilIdle()

        assertThat(graph.engine.warmUps).isEqualTo(1)
    }

    /** Found on the phone (T069): waiting on the chat for a batch, the first answer paid Gemma's cold load. */
    @Test
    fun gemmaPreloads_whenTheBatchEndsOnScreen_butNotWhileHidden() = runTest(dispatcher) {
        graph.addDocument("Manual de procesos.pdf")
        graph.indexing.state.value = IndexingState.Active(RunProgress(1, 10, null), null, 0, pause = null, failed = emptyList())
        val vm = graph.chatViewModel(saved())
        vm.onStart()
        advanceUntilIdle()
        assertThat(graph.engine.warmUps).isEqualTo(0)

        graph.indexing.state.value = IndexingState.Idle(emptyList())
        advanceUntilIdle()
        assertThat(graph.engine.warmUps).isEqualTo(1)

        // Hidden, Gemma was released (constitution IV): a batch that ends meanwhile does not load it again.
        vm.onHidden()
        graph.indexing.state.value = IndexingState.Active(RunProgress(1, 10, null), null, 0, pause = null, failed = emptyList())
        advanceUntilIdle()
        graph.indexing.state.value = IndexingState.Idle(emptyList())
        advanceUntilIdle()
        assertThat(graph.engine.warmUps).isEqualTo(1)

        vm.onStart()
        advanceUntilIdle()
        assertThat(graph.engine.warmUps).isEqualTo(2)
    }

    @Test
    fun retryingTheAvailability_checksTheIndexAgain_C8() = runTest(dispatcher) {
        graph.addDocument("Manual de procesos.pdf")
        graph.index.setCompatibility(IndexCompatibility.Incompatible("embedding_model", "eg2", null))
        val vm = graph.chatViewModel(saved())
        advanceUntilIdle()
        assertThat(vm.state.value.availability).isEqualTo(ChatAvailability.IndexUnavailable)

        graph.index.setCompatibility(IndexCompatibility.Compatible)
        vm.onRetryAvailability()
        advanceUntilIdle()

        assertThat(vm.state.value.ready).isTrue()
    }

    @Test
    fun theComposerTurnsOn_whenTheBatchEnds_withoutLeaving() = runTest(dispatcher) {
        graph.addDocument("Manual de procesos.pdf")
        graph.indexing.state.value = IndexingState.Active(RunProgress(1, 10, null), null, 0, pause = null, failed = emptyList())
        val vm = graph.chatViewModel(saved())
        vm.onQuestionChange("¿Plazo?")
        advanceUntilIdle()
        assertThat(vm.state.value.availability).isEqualTo(ChatAvailability.WaitingForIndexing)
        assertThat(vm.state.value.canSend).isFalse()

        graph.indexing.state.value = IndexingState.Idle(emptyList())
        advanceUntilIdle()

        assertThat(vm.state.value.canSend).isTrue()
    }

    @Test
    fun anAnswerWithPartialCoverage_carriesTheC6Note() = runTest(dispatcher) {
        val document = graph.addDocument("Manual de procesos.pdf")
        val id = graph.chats.createConversation(0)
        val turn = graph.chats.insertTurn(id, "¿Plazo?", partialCoverage = true, now = 0)
        graph.chats.updateTurn(turn.id, TurnUpdate(state = TurnState.COMPLETED, answer = "Son 30 días [1].", promptBlocks = 1))
        graph.chats.replaceSources(turn.id, listOf(source(document)))
        val vm = graph.chatViewModel(saved())
        advanceUntilIdle()

        assertThat(vm.state.value.turns.single().notes).containsExactly(R.string.chat_partial_coverage)
    }

    // --- F13 (research R5, R7, R8; contracts/history-api.md Q8, Q11, Q12, Q16) ----------------------

    /** Q11: Historial's request shows that conversation; the draft stays, and the next question goes to it. */
    @Test
    fun openingFromHistory_showsThatConversation_andTheNextQuestionGoesToIt() = runTest(dispatcher) {
        val document = graph.addDocument("Manual de procesos.pdf")
        val older = answered(document, at = 5_000)
        answered(document, at = 6_000)
        val saved = saved()
        val vm = graph.chatViewModel(saved)
        advanceUntilIdle()
        vm.onQuestionChange("¿Y para empresas?")
        vm.onDelete()

        vm.onOpenConversation(older)
        advanceUntilIdle()

        assertThat(graph.session.calls).contains("open:$older")
        assertThat(vm.state.value.conversationId).isEqualTo(older)
        assertThat(saved.get<Long>("chat_conversation")).isEqualTo(older)
        assertThat(vm.state.value.confirmDelete).isFalse()
        assertThat(vm.state.value.question).isEqualTo("¿Y para empresas?")

        vm.onSend()
        advanceUntilIdle()
        assertThat(graph.chats.turns.values.last().conversationId).isEqualTo(older)
    }

    /** SC-002: every state of a turn comes back as it was saved. */
    @Test
    fun aConversationOpenedFromHistory_keepsEveryStateOfItsTurns() = runTest(dispatcher) {
        graph.addDocument("Manual de procesos.pdf")
        val id = graph.chats.createConversation(5_000)
        val states = listOf(TurnState.COMPLETED, TurnState.COMPLETED, TurnState.CANCELLED, TurnState.INTERRUPTED, TurnState.FAILED)
        states.forEachIndexed { i, state ->
            val turn = graph.chats.insertTurn(id, "¿$i?", partialCoverage = false, now = 5_000L + i)
            graph.chats.updateTurn(turn.id, TurnUpdate(state = state, answer = if (i == 1) "No encuentro esa información en los documentos disponibles." else "Texto $i", refusal = i == 1, finishedAt = 5_100L + i))
        }
        val vm = graph.chatViewModel(saved())
        advanceUntilIdle()

        vm.onOpenConversation(id)
        advanceUntilIdle()

        val turns = vm.state.value.turns
        assertThat(turns.map { it.question }).containsExactly("¿0?", "¿1?", "¿2?", "¿3?", "¿4?").inOrder()
        assertThat(turns.map { it.state }).containsExactly(
            AnswerState.Completed, AnswerState.Completed, AnswerState.Cancelled, AnswerState.Failed, AnswerState.Failed,
        ).inOrder()
        assertThat(turns[1].refusal).isTrue()
        assertThat(turns[3].failureText).isEqualTo(R.string.chat_interrupted)
        assertThat(turns[3].retryable).isTrue()
    }

    /** Q12, FR-011: the chip of a deleted document opens nothing; one whose fragment is gone still opens. */
    @Test
    fun chipsOfDeletedDocuments_openNothing() = runTest(dispatcher) {
        val kept = graph.addDocument("Manual de procesos.pdf")
        val deleted = graph.addDocument("Protocolo interno.pdf")
        val id = graph.chats.createConversation(0)
        val turn = graph.chats.insertTurn(id, "¿Plazo?", partialCoverage = false, now = 5_000)
        graph.chats.updateTurn(turn.id, TurnUpdate(state = TurnState.COMPLETED, answer = "Son 30 días [1][2].", promptBlocks = 2))
        graph.chats.replaceSources(
            turn.id,
            listOf(source(kept).copy(chunkId = 9_999), source(deleted).copy(position = 2, promptNumber = 2, chunkId = 241)),
        )
        graph.index.deleteDocument(deleted)
        val vm = graph.chatViewModel(saved(conversationId = id))
        advanceUntilIdle()

        val targets = vm.state.value.turns.single().chipTargets
        assertThat(targets).containsExactly(SourceTarget(kept, 9_999), null).inOrder()
    }

    @Test
    fun chipsOfDeletedDocuments_openNothing_alsoWhileGenerating() = runTest(dispatcher) {
        val deleted = graph.addDocument("Manual de procesos.pdf")
        graph.addDocument("Otro.pdf")
        val id = graph.chats.createConversation(0)
        val turn = graph.chats.insertTurn(id, "¿Plazo?", partialCoverage = false, now = 5_000)
        graph.chats.updateTurn(turn.id, TurnUpdate(state = TurnState.GENERATING))
        graph.session.active.value = ActiveAnswer(id, turn.id, "¿Plazo?", TurnState.GENERATING, text = "Son 30 días [1].", blocks = listOf(block(deleted)))
        val vm = graph.chatViewModel(saved(conversationId = id))
        advanceUntilIdle()
        assertThat(vm.state.value.turns.single().chipTargets).containsExactly(SourceTarget(deleted, 186))

        graph.index.deleteDocument(deleted)
        advanceUntilIdle()

        assertThat(vm.state.value.turns.single().chipTargets).containsExactly(null)
    }

    /** Q8: a conversation deleted elsewhere (Historial) leaves this chat empty; another one changes nothing. */
    @Test
    fun aDeletionOfThisConversation_emptiesTheChat_andClosesItsDialog() = runTest(dispatcher) {
        val document = graph.addDocument("Manual de procesos.pdf")
        val shown = answered(document, at = 5_000)
        val other = answered(document, at = 4_000)
        val saved = saved(conversationId = shown)
        val vm = graph.chatViewModel(saved)
        advanceUntilIdle()

        graph.session.delete(other)
        advanceUntilIdle()
        assertThat(vm.state.value.conversationId).isEqualTo(shown)

        vm.onDelete()
        graph.session.delete(shown)
        advanceUntilIdle()

        assertThat(vm.state.value.conversationId).isNull()
        assertThat(vm.state.value.turns).isEmpty()
        assertThat(vm.state.value.confirmDelete).isFalse()
        assertThat(saved.get<Long>("chat_conversation")).isNull()
    }

    /** Q16: restored with a conversation deleted while the chat was not alive, it comes back empty. */
    @Test
    fun restoredWithADeletedConversation_theChatIsEmpty_butAnUnreadableOneIsKept() = runTest(dispatcher) {
        val restored = graph.chatViewModel(saved().apply { set("chat_conversation", 42L); set("chat_resolved", true) })
        advanceUntilIdle()
        assertThat(restored.state.value.conversationId).isNull()

        graph.chats.storage = ChatStorageState.UNAVAILABLE
        val unreadable = graph.chatViewModel(saved().apply { set("chat_conversation", 42L); set("chat_resolved", true) })
        advanceUntilIdle()
        assertThat(unreadable.state.value.conversationId).isEqualTo(42L)
    }

    /** FR-014: the conversation opened from Historial is the one a recreated chat comes back to. */
    @Test
    fun theConversationOpenedFromHistory_survivesARecreation() = runTest(dispatcher) {
        val document = graph.addDocument("Manual de procesos.pdf")
        val older = answered(document, at = 5_000)
        answered(document, at = 6_000)
        val saved = saved()
        graph.chatViewModel(saved).apply {
            advanceUntilIdle()
            onOpenConversation(older)
            advanceUntilIdle()
        }

        val recreated = graph.chatViewModel(saved)
        advanceUntilIdle()

        assertThat(recreated.state.value.conversationId).isEqualTo(older)
    }

    // --- F13 US3 (research R6, SC-005) ---------------------------------------------------------------

    @Test
    fun aFailedDeletion_keepsTheConversation_andSaysSo() = runTest(dispatcher) {
        val document = graph.addDocument("Manual de procesos.pdf")
        val id = answered(document, at = 5_000)
        val vm = graph.chatViewModel(saved(conversationId = id))
        advanceUntilIdle()
        graph.chats.failWrites = true

        vm.onDelete()
        vm.onConfirmDelete()
        advanceUntilIdle()

        assertThat(vm.state.value.conversationId).isEqualTo(id)
        assertThat(vm.state.value.turns).hasSize(1)
        assertThat(vm.state.value.announcement).isEqualTo(ChatAnnouncement.DELETE_FAILED)
    }

    @Test
    fun afterItsConversationIsDeletedFromHistory_theNextQuestionStartsANewOne() = runTest(dispatcher) {
        val document = graph.addDocument("Manual de procesos.pdf")
        val id = answered(document, at = 5_000)
        val vm = graph.chatViewModel(saved(conversationId = id))
        advanceUntilIdle()

        graph.session.delete(id)
        advanceUntilIdle()
        vm.onQuestionChange("¿Otra?")
        vm.onSend()
        advanceUntilIdle()

        val asked = graph.chats.turns.values.single()
        assertThat(asked.question).isEqualTo("¿Otra?")
        assertThat(asked.conversationId).isNotEqualTo(id)
        assertThat(vm.state.value.conversationId).isEqualTo(asked.conversationId)
    }

    // F17 T036, T041 and T044 (K7–K10, contract S1–S4, FR-019–FR-022).

    private val legal = ChatScope.Some(setOf(FolderRef(1, 10)), general = false, documents = emptySet())

    private fun withLegal() {
        graph.library.folderRows.value = listOf(Folder(1, "Legal", createdAtMs = 10, documentCount = 0), Folder(2, "Motores", 20, 0))
    }

    @Test
    fun aNewChatStartsOnTheWholeLibrary_thenOnTheLastScope() = runTest(dispatcher) {
        withLegal()
        val first = graph.chatViewModel(saved(newConversation = true))
        advanceUntilIdle()
        assertThat(first.state.value.scope).isEqualTo(ScopeSummary(whole = true))

        graph.chats.setLastScope(legal)
        first.onNewChat()
        advanceUntilIdle()

        assertThat(first.state.value.scope).isEqualTo(ScopeSummary(whole = false, folderNames = listOf("Legal")))
    }

    @Test
    fun applyingAScopeToAChatWithoutQuestions_keepsIt_andTheQuestionUsesIt() = runTest(dispatcher) {
        withLegal()
        graph.addDocument("Manual de procesos.pdf")
        val saved = saved(newConversation = true)
        val vm = graph.chatViewModel(saved)
        advanceUntilIdle()

        vm.onChangeScope()
        advanceUntilIdle()
        assertThat(vm.state.value.selectingScope).isTrue()
        vm.onApplyScope(legal)
        advanceUntilIdle()

        assertThat(vm.state.value.selectingScope).isFalse()
        assertThat(vm.state.value.scope).isEqualTo(ScopeSummary(whole = false, folderNames = listOf("Legal")))
        assertThat(graph.chats.lastScope).isEqualTo(legal)
        // Rotated or restored, the chat keeps the scope it was given.
        val restored = graph.chatViewModel(saved)
        advanceUntilIdle()
        assertThat(restored.state.value.scope).isEqualTo(ScopeSummary(whole = false, folderNames = listOf("Legal")))
    }

    @Test
    fun applyingItToASavedConversationReachesTheSession() = runTest(dispatcher) {
        withLegal()
        val id = answered(documentId = 7)
        val vm = graph.chatViewModel(saved(conversationId = id))
        advanceUntilIdle()

        vm.onApplyScope(legal)
        advanceUntilIdle()

        assertThat(graph.session.calls).contains("scope:$id")
        assertThat(graph.chats.conversationScope(id)).isEqualTo(legal)
    }

    /** FR-020: from Historial, the conversation comes back with its scope; old ones on the whole library. */
    @Test
    fun aConversationOpenedFromHistoryHasItsScope() = runTest(dispatcher) {
        withLegal()
        val old = answered(documentId = 7)
        val scoped = answered(documentId = 7, at = 5)
        graph.chats.setConversationScope(scoped, legal)
        val vm = graph.chatViewModel(saved(conversationId = old))
        advanceUntilIdle()
        assertThat(vm.state.value.scope).isEqualTo(ScopeSummary(whole = true))

        vm.onOpenConversation(scoped)
        advanceUntilIdle()

        assertThat(vm.state.value.scope).isEqualTo(ScopeSummary(whole = false, folderNames = listOf("Legal")))
    }

    /** K10 and M8: what fell out is said once. */
    @Test
    fun aScopeThatChangedSaysWhat_once() = runTest(dispatcher) {
        val id = answered(documentId = 7)
        graph.chats.setConversationScope(id, legal)
        graph.session.resolve = { scope ->
            if (scope == legal) ResolvedScope(ChatScope.WholeLibrary, droppedFolders = 1, fellBackToWholeLibrary = true) else ResolvedScope(scope)
        }
        val vm = graph.chatViewModel(saved(conversationId = id))
        advanceUntilIdle()

        assertThat(vm.state.value.scopeNotice).isEqualTo(ScopeNotice.WHOLE_AGAIN)
        assertThat(vm.state.value.scope).isEqualTo(ScopeSummary(whole = true))
        vm.onScopeNoticeShown()
        advanceUntilIdle()
        assertThat(vm.state.value.scopeNotice).isNull()
    }

    /** K9: documents ready in the library, none in the scope. */
    @Test
    fun aScopeWithNothingReadyIsNothingInScope() = runTest(dispatcher) {
        withLegal()
        graph.addDocument("Manual de procesos.pdf")
        val vm = graph.chatViewModel(saved(newConversation = true))
        advanceUntilIdle()
        assertThat(vm.state.value.availability).isInstanceOf(ChatAvailability.Ready::class.java)

        vm.onApplyScope(legal)
        advanceUntilIdle()

        assertThat(vm.state.value.availability).isEqualTo(ChatAvailability.NothingInScope)
        assertThat(vm.state.value.ready).isFalse()
    }

    /** The question goes with the scope shown. */
    @Test
    fun sendingAsksWithinTheScope() = runTest(dispatcher) {
        withLegal()
        val id = graph.addDocument("Manual de procesos.pdf")
        graph.index.file(id, 1)
        val vm = graph.chatViewModel(saved(newConversation = true))
        advanceUntilIdle()
        vm.onApplyScope(legal)
        vm.onQuestionChange("¿Plazo?")
        advanceUntilIdle()

        vm.onSend()
        advanceUntilIdle()

        assertThat(graph.session.askedScopes).containsExactly(legal)
    }
}
