package com.lisdatasolutions.microrag.data.chat

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.lisdatasolutions.microrag.domain.chat.ActiveAnswer
import com.lisdatasolutions.microrag.domain.chat.AnswerSource
import com.lisdatasolutions.microrag.domain.chat.ChatAvailability
import com.lisdatasolutions.microrag.domain.chat.ChatStorageException
import com.lisdatasolutions.microrag.domain.chat.ChatStorageState
import com.lisdatasolutions.microrag.domain.chat.TurnFailure
import com.lisdatasolutions.microrag.domain.chat.TurnState
import com.lisdatasolutions.microrag.domain.chat.TurnUpdate
import com.lisdatasolutions.microrag.domain.rag.IndexCompatibility
import com.lisdatasolutions.microrag.domain.retrieval.CandidateScore
import com.lisdatasolutions.microrag.domain.retrieval.ContextBlock
import com.lisdatasolutions.microrag.domain.retrieval.RetrievalOutcome
import com.lisdatasolutions.microrag.domain.retrieval.RetrieveContextUseCase
import com.lisdatasolutions.microrag.rag.answer.AnswerGoldenFiles
import com.lisdatasolutions.microrag.rag.answer.PromptBuilder
import com.lisdatasolutions.microrag.testing.FakeAnswerEngine
import com.lisdatasolutions.microrag.testing.FakeContextRetriever
import com.lisdatasolutions.microrag.testing.InMemoryChatRepository
import com.lisdatasolutions.microrag.testing.RecordingLogger
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test

/** F11 (research R6–R10, contract rules A1–A15): the chat session with fakes and virtual time. */
@OptIn(ExperimentalCoroutinesApi::class)
class DefaultChatSessionTest {

    private val repository = InMemoryChatRepository()
    private val retriever = FakeContextRetriever()
    private val engine = FakeAnswerEngine()
    private val availability = MutableStateFlow<ChatAvailability>(ChatAvailability.Ready(cpuMode = false, partialCoverage = false))
    private val logger = RecordingLogger()

    private fun TestScope.session() = DefaultChatSession(
        repository = repository, retrieve = RetrieveContextUseCase(retriever), engine = engine,
        availability = { availability }, scope = this, logger = logger, now = { testScheduler.currentTime },
    )

    private fun block(n: Int, documentId: Long = n.toLong(), chunks: List<Long> = listOf(100L + n), text: String = "Texto del bloque $n.") =
        ContextBlock(
            documentId = documentId, documentName = "Documento $n.pdf", headingPath = "Capítulo $n", page = n,
            firstOrdinal = n * 10, lastOrdinal = n * 10 + chunks.size - 1, chunkIds = chunks, text = text, tokens = 50,
        )

    private fun blocks(vararg blocks: ContextBlock, ranks: Map<Long, Int> = emptyMap()) = RetrievalOutcome.Blocks(
        blocks.toList(),
        blocks.flatMap { it.chunkIds }.mapIndexed { i, id -> CandidateScore(id, ranks[id] ?: (i + 1), 0.1, null, null, null, true) },
    )

    private fun lastTurn() = repository.turns.values.last()

    private companion object {
        /** The previous-turn header; the v4 rules also name «CONVERSACIÓN PREVIA», so the header is matched whole. */
        const val PREVIOUS_TURN = "CONVERSACIÓN PREVIA (solo como contexto de la pregunta):"
    }

    // --- US1: the main path (A1, A2, A7, A8, A14) ---------------------------------------------------

    @Test
    fun aFirstQuestion_createsTheConversation_andAnswersWithItsCitedSources() = runTest {
        retriever.outcome = blocks(block(1), block(2), block(3, chunks = listOf(301, 302)))
        engine.tokens = listOf("Según el manual [3], ", "el plazo es de 30 días [1].")
        val session = session()
        assertThat(repository.lastConversationId()).isNull()

        val id = session.ask(null, "¿Plazo?")
        advanceUntilIdle()

        assertThat(id).isNotNull()
        with(lastTurn()) {
            assertThat(conversationId).isEqualTo(id)
            assertThat(state).isEqualTo(TurnState.COMPLETED)
            assertThat(answer).isEqualTo("Según el manual [3], el plazo es de 30 días [1].")
            assertThat(promptBlocks).isEqualTo(3)
            assertThat(refusal).isFalse()
            assertThat(sources).containsExactly(
                AnswerSource(1, 3, 3, 301, 30, 31, "Documento 3.pdf", 3),
                AnswerSource(2, 1, 1, 101, 10, 10, "Documento 1.pdf", 1),
            ).inOrder()
        }
        assertThat(session.active.value).isNull()
    }

    @Test
    fun theTurnGoesThroughRetrievingAndGenerating_andActiveCarriesTheText() = runTest {
        retriever.outcome = blocks(block(1))
        engine.tokens = listOf("Uno ", "dos [1].")
        val session = session()

        session.ask(null, "¿Plazo?")
        runCurrent()
        assertThat(lastTurn().state).isEqualTo(TurnState.RETRIEVING)
        assertThat(session.active.value!!.state).isEqualTo(TurnState.RETRIEVING)

        advanceTimeBy(retriever.delayMs + 1)
        assertThat(lastTurn().state).isEqualTo(TurnState.GENERATING)
        assertWithMessage("generating from the prompt, before the first token").that(session.active.value!!.text).isEmpty()
        assertThat(session.active.value!!.blocks.map { it.promptNumber }).containsExactly(1)

        advanceTimeBy(engine.prefillMs)
        runCurrent()
        assertThat(session.active.value!!.text).isEqualTo("Uno ")
        advanceUntilIdle()
        assertThat(lastTurn().state).isEqualTo(TurnState.COMPLETED)
    }

    @Test
    fun thePartialAnswerIsSavedAtMostOnceASecond_andAtTheEnd() = runTest {
        retriever.outcome = blocks(block(1))
        engine.tokens = List(30) { "t$it " }
        engine.delayPerTokenMs = 100
        val session = session()

        session.ask(null, "¿Plazo?")
        advanceUntilIdle()

        val answerWrites = repository.writes.count { it == "update:" }
        assertThat(answerWrites).isIn(2..3)
        assertThat(lastTurn().answer).isEqualTo(engine.tokens.joinToString(""))
    }

    @Test
    fun aSecondQuestion_whileAnswering_isRefused() = runTest {
        retriever.outcome = blocks(block(1))
        val session = session()

        val id = session.ask(null, "¿Uno?")
        runCurrent()

        assertThat(session.ask(id, "¿Dos?")).isNull()
        advanceUntilIdle()
        assertThat(repository.turns).hasSize(1)
    }

    @Test
    fun withoutReady_nothingStarts() = runTest {
        availability.value = ChatAvailability.WaitingForIndexing
        val session = session()

        assertThat(session.ask(null, "¿Plazo?")).isNull()
        advanceUntilIdle()

        assertThat(repository.turns).isEmpty()
        assertThat(retriever.requests).isEmpty()
        assertThat(engine.prompts).isEmpty()
    }

    @Test
    fun blankAndTooLongQuestions_doNothing() = runTest {
        val session = session()
        assertThat(session.ask(null, "   ")).isNull()
        assertThat(session.ask(null, "a".repeat(1001))).isNull()
        assertThat(session.ask(null, "a".repeat(1000))).isNotNull()
    }

    @Test
    fun theTurnKeepsThePartialCoverageOfTheMoment() = runTest {
        availability.value = ChatAvailability.Ready(cpuMode = false, partialCoverage = true)
        retriever.outcome = blocks(block(1))
        session().ask(null, "¿Plazo?")
        advanceUntilIdle()
        assertThat(lastTurn().partialCoverage).isTrue()
    }

    @Test
    fun theLogHasOneLineOfNumbers_andNoText() = runTest {
        retriever.outcome = blocks(block(1, text = "SECRETO-BLOQUE"))
        engine.tokens = listOf("SECRETO-RESPUESTA [1].")
        session().ask(null, "SECRETO-PREGUNTA")
        advanceUntilIdle()

        val answers = logger.lines.filter { it.startsWith("chat answer") }
        assertThat(answers).hasSize(1)
        assertThat(answers.single()).contains("outcome=completed")
        assertThat(answers.single()).contains("blocks=1 cited=1 refusal=false")
        logger.lines.forEach { assertThat(it).doesNotContain("SECRETO") }
    }

    // --- US2: refusals (A5, A8) ---------------------------------------------------------------------

    @Test
    fun noBlocks_answersTheRefusal_withoutCallingTheModel() = runTest {
        retriever.outcome = RetrievalOutcome.NoResults
        val session = session()

        session.ask(null, "¿Algo que no está?")
        advanceTimeBy(retriever.delayMs + 1)

        with(lastTurn()) {
            assertThat(state).isEqualTo(TurnState.COMPLETED)
            assertThat(refusal).isTrue()
            assertThat(answer).isEqualTo(PromptBuilder.REFUSAL)
            assertThat(sources).isEmpty()
        }
        assertThat(engine.prompts).isEmpty()
        assertThat(testScheduler.currentTime).isLessThan(1000)
    }

    @Test
    fun theModelsRefusal_evenQuoted_isARefusalWithoutSources() = runTest {
        retriever.outcome = blocks(block(1))
        engine.tokens = listOf("«No encuentro esa información en los documentos disponibles».")
        session().ask(null, "¿Plazo?")
        advanceUntilIdle()
        assertThat(lastTurn().refusal).isTrue()
        assertThat(lastTurn().sources).isEmpty()
    }

    @Test
    fun aRefusalWithMoreText_isNotARefusal_andAnAnswerWithoutValidCitations_hasNoSources() = runTest {
        retriever.outcome = blocks(block(1))
        engine.tokens = listOf("No encuentro esa información en los documentos disponibles. Pero el plazo es de 30 días [7].")
        session().ask(null, "¿Plazo?")
        advanceUntilIdle()
        assertThat(lastTurn().refusal).isFalse()
        assertThat(lastTurn().state).isEqualTo(TurnState.COMPLETED)
        assertThat(lastTurn().sources).isEmpty()
    }

    // --- US3: stop, failures and retry (A5, A9, A10) -------------------------------------------------

    @Test
    fun cancel_whileGenerating_keepsTheTextAndItsValidCitations_inLessThanASecond() = runTest {
        retriever.outcome = blocks(block(1), block(2))
        engine.tokens = listOf("Primero [2]. ", "Segundo [1]. ", "Tercero.")
        engine.delayPerTokenMs = 5_000
        val session = session()
        session.ask(null, "¿Plazo?")
        advanceTimeBy(retriever.delayMs + engine.prefillMs + 1)
        assertThat(session.active.value!!.text).isEqualTo("Primero [2]. ")
        val stoppedAt = testScheduler.currentTime

        session.cancel()
        runCurrent()

        assertThat(testScheduler.currentTime - stoppedAt).isLessThan(1000)
        with(lastTurn()) {
            assertThat(state).isEqualTo(TurnState.CANCELLED)
            assertThat(answer).isEqualTo("Primero [2]. ")
            assertThat(sources.map { it.promptNumber }).containsExactly(2)
        }
        assertThat(engine.cancellations).isEqualTo(1)
        assertThat(session.active.value).isNull()
    }

    @Test
    fun cancel_whileRetrieving_leavesAnEmptyCancelledTurn() = runTest {
        retriever.outcome = blocks(block(1))
        retriever.delayMs = 5_000
        val session = session()
        session.ask(null, "¿Plazo?")
        runCurrent()

        session.cancel()
        runCurrent()

        assertThat(lastTurn().state).isEqualTo(TurnState.CANCELLED)
        assertThat(lastTurn().answer).isEmpty()
        assertThat(engine.prompts).isEmpty()
    }

    @Test
    fun anEngineFailure_keepsThePartialText() = runTest {
        retriever.outcome = blocks(block(1))
        engine.tokens = listOf("Parcial [1]. ", "nunca")
        engine.failAt = 1
        session().ask(null, "¿Plazo?")
        advanceUntilIdle()
        with(lastTurn()) {
            assertThat(state).isEqualTo(TurnState.FAILED)
            assertThat(failure).isEqualTo(TurnFailure.GENERATION)
            assertThat(answer).isEqualTo("Parcial [1]. ")
        }
        assertThat(logger.lines.last()).contains("error=IllegalStateException")
    }

    @Test
    fun indexAndOtherRetrievalFailures_areTypedFailures() = runTest {
        val session = session()
        retriever.outcome = RetrievalOutcome.IndexUnavailable(IndexCompatibility.Unreadable("x"))
        session.ask(null, "¿Uno?")
        advanceUntilIdle()
        assertThat(lastTurn().failure).isEqualTo(TurnFailure.INDEX)

        retriever.outcome = RetrievalOutcome.EmbeddingFailed
        session.ask(lastTurn().conversationId, "¿Dos?")
        advanceUntilIdle()
        assertThat(lastTurn().failure).isEqualTo(TurnFailure.GENERATION)
    }

    @Test
    fun anEmptyAnswer_fails() = runTest {
        retriever.outcome = blocks(block(1))
        engine.tokens = listOf("  ")
        session().ask(null, "¿Plazo?")
        advanceUntilIdle()
        assertThat(lastTurn().state).isEqualTo(TurnState.FAILED)
    }

    @Test
    fun retry_repeatsTheQuestionInTheSameTurn_onlyFromFailedOrInterrupted() = runTest {
        retriever.outcome = blocks(block(1))
        engine.failAt = 0
        val session = session()
        session.ask(null, "¿Plazo?")
        advanceUntilIdle()
        val failed = lastTurn()
        assertThat(failed.state).isEqualTo(TurnState.FAILED)

        engine.failAt = null
        assertThat(session.retry(failed.id)).isTrue()
        advanceUntilIdle()

        assertThat(repository.turns).hasSize(1)
        assertThat(lastTurn().state).isEqualTo(TurnState.COMPLETED)
        assertThat(retriever.requests.last().question).isEqualTo("¿Plazo?")
        assertWithMessage("a completed turn is not retried").that(session.retry(failed.id)).isFalse()
    }

    @Test
    fun retry_needsReady_andNoOtherAnswer() = runTest {
        retriever.outcome = blocks(block(1))
        engine.failAt = 0
        val session = session()
        session.ask(null, "¿Uno?")
        advanceUntilIdle()
        val failed = lastTurn()
        engine.failAt = null

        availability.value = ChatAvailability.WaitingForIndexing
        assertThat(session.retry(failed.id)).isFalse()
        availability.value = ChatAvailability.Ready(cpuMode = false, partialCoverage = false)

        engine.delayPerTokenMs = 5_000
        engine.tokens = listOf("a", "b")
        session.ask(failed.conversationId, "¿Dos?")
        runCurrent()
        assertThat(session.retry(failed.id)).isFalse()
    }

    // --- US4: previous turn and prompt limit (A4, A6) -----------------------------------------------

    @Test
    fun aCompletedTurn_isThePreviousTurn_evenARefusal_asTheDesktop() = runTest {
        val session = session()
        retriever.outcome = RetrievalOutcome.NoResults
        val id = session.ask(null, "¿Primera?")
        advanceUntilIdle()

        retriever.outcome = blocks(block(1))
        session.ask(id, "¿Y la segunda?")
        advanceUntilIdle()

        assertThat(retriever.requests.last().previousQuestion).isEqualTo("¿Primera?")
        assertThat(engine.prompts.last()).contains("Usuario: ¿Primera?\nAsistente: ${PromptBuilder.REFUSAL}\n")
    }

    @Test
    fun aCancelledOrFailedTurn_isNotThePreviousTurn() = runTest {
        retriever.outcome = blocks(block(1))
        engine.failAt = 0
        val session = session()
        val id = session.ask(null, "¿Primera?")
        advanceUntilIdle()
        engine.failAt = null

        session.ask(id, "¿Segunda?")
        advanceUntilIdle()

        assertThat(retriever.requests.last().previousQuestion).isNull()
        assertThat(engine.prompts.last()).doesNotContain(PREVIOUS_TURN)
    }

    @Test
    fun theLimit_dropsTheWorstRankedBlocksFirst_thenThePreviousTurn_neverTheLastBlock() = runTest {
        val long = "x".repeat(3_000)
        val first = block(1, text = long)
        val second = block(2, text = long)
        val third = block(3, text = long)
        retriever.outcome = blocks(first, second, third, ranks = mapOf(101L to 1, 102L to 3, 103L to 2))
        val session = session()
        session.ask(null, "¿Plazo?")
        advanceUntilIdle()

        val prompt = engine.prompts.last()
        assertThat(PromptBuilder.estimatedTokens(prompt)).isAtMost(2000)
        assertThat(prompt).contains("Documento 1.pdf")
        assertThat(prompt).contains("Documento 3.pdf")
        assertThat(prompt).doesNotContain("Documento 2.pdf")
        // The blocks left keep their order.
        assertThat(prompt.indexOf("Documento 1.pdf")).isLessThan(prompt.indexOf("Documento 3.pdf"))
        assertThat(logger.lines.last()).contains("dropped_blocks=1")

        val huge = block(9, text = "y".repeat(12_000))
        retriever.outcome = blocks(huge)
        session.ask(lastTurn().conversationId, "¿Otra?")
        advanceUntilIdle()
        assertThat(engine.prompts.last()).contains("Documento 9.pdf")
        assertThat(engine.prompts.last()).doesNotContain(PREVIOUS_TURN)
        assertThat(logger.lines.last()).contains("dropped_previous=true")
    }

    @Test
    fun aLongQuestion_afterALongTurn_dropsTheWorstBlock_thenThePreviousTurn() = runTest {
        val session = session()
        retriever.outcome = blocks(block(1))
        engine.tokens = listOf("z".repeat(2_000))
        val id = session.ask(null, "q".repeat(1_000))
        advanceUntilIdle()

        engine.tokens = listOf("Vale [1].")
        retriever.outcome = blocks(block(1, text = "x".repeat(6_500)), block(2, text = "w".repeat(2_000)), ranks = mapOf(101L to 1, 102L to 2))
        session.ask(id, "p".repeat(1_000))
        advanceUntilIdle()

        val prompt = engine.prompts.last()
        assertThat(prompt).contains("Documento 1.pdf")
        assertThat(prompt).doesNotContain("Documento 2.pdf")
        assertThat(prompt).doesNotContain(PREVIOUS_TURN)
        assertThat(logger.lines.last()).contains("dropped_blocks=1")
        assertThat(logger.lines.last()).contains("dropped_previous=true")
    }

    @Test
    fun aNewConversation_hasNoPreviousTurn() = runTest {
        retriever.outcome = blocks(block(1))
        val session = session()
        session.ask(null, "¿Primera?")
        advanceUntilIdle()

        session.ask(null, "¿Otra conversación?")
        advanceUntilIdle()

        assertThat(retriever.requests.last().previousQuestion).isNull()
        assertThat(engine.prompts.last()).doesNotContain(PREVIOUS_TURN)
    }

    /** SC-002 through the session: the previous turn read from the conversation gives the desktop's prompt. */
    @Test
    fun theGoldenFollowUps_giveTheDesktopPrompt_throughTheSession() = runTest {
        val cases = AnswerGoldenFiles.cases(File("../offline/golden/${AnswerGoldenFiles.GOLDEN}").readText()).filter { it.previous != null }
        assertThat(cases).hasSize(50)
        val session = session()

        cases.forEach { case ->
            val (question, answer) = case.previous!!
            val id = repository.createConversation(0)
            val turn = repository.insertTurn(id, question, partialCoverage = false, now = 0)
            repository.updateTurn(turn.id, TurnUpdate(state = TurnState.COMPLETED, answer = answer))
            val golden = case.blocks.mapIndexed { i, b ->
                ContextBlock(
                    documentId = i + 1L, documentName = b.documentName!!, headingPath = b.headingPath, page = b.page,
                    firstOrdinal = 0, lastOrdinal = 0, chunkIds = listOf((i + 1L) * 1_000), text = b.text, tokens = 0,
                )
            }
            retriever.outcome = blocks(*golden.toTypedArray())

            session.ask(id, case.question)
            advanceUntilIdle()

            assertWithMessage("${case.id}: previous question").that(retriever.requests.last().previousQuestion).isEqualTo(question)
            assertWithMessage("${case.id}: SHA-256 of the prompt").that(sha256(engine.prompts.last())).isEqualTo(case.promptSha256)
        }
    }

    private fun sha256(text: String): String =
        MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

    // --- US5: storage and the session's lifecycle (A12, A13, A15) -----------------------------------

    @Test
    fun atStartup_answersLeftInProgress_areInterrupted() = runTest {
        val id = repository.createConversation(1)
        val stale = repository.insertTurn(id, "¿Plazo?", partialCoverage = false, now = 2)
        repository.updateTurn(stale.id, TurnUpdate(state = TurnState.GENERATING, answer = "Parcial"))

        session()
        runCurrent()

        assertThat(repository.turn(stale.id)!!.state).isEqualTo(TurnState.INTERRUPTED)
        assertThat(repository.turn(stale.id)!!.answer).isEqualTo("Parcial")
    }

    @Test
    fun aNewConversationOrDeleting_cancelsTheAnswerInProgress() = runTest {
        retriever.outcome = blocks(block(1))
        engine.tokens = listOf("a", "b")
        engine.delayPerTokenMs = 5_000
        val session = session()
        val id = session.ask(null, "¿Uno?")!!
        advanceTimeBy(retriever.delayMs + engine.prefillMs + 1)

        session.startNewConversation()
        runCurrent()
        assertThat(lastTurn().state).isEqualTo(TurnState.CANCELLED)

        session.ask(id, "¿Dos?")
        advanceTimeBy(retriever.delayMs + engine.prefillMs + 1)
        session.delete(id)
        runCurrent()
        assertThat(repository.turns).isEmpty()
        assertThat(session.active.value).isNull()
    }

    @Test
    fun aFailedWrite_keepsTheAnswerInMemory_markedUnsaved() = runTest {
        retriever.outcome = blocks(block(1))
        engine.tokens = listOf("Respuesta [1].")
        val session = session()
        session.ask(null, "¿Plazo?")
        advanceTimeBy(retriever.delayMs + 1)

        repository.failWrites = true
        advanceUntilIdle()

        with(session.active.value!!) {
            assertThat(saveFailed).isTrue()
            assertThat(state).isEqualTo(TurnState.COMPLETED)
            assertThat(text).isEqualTo("Respuesta [1].")
        }
    }

    @Test
    fun aRepositoryRefusingEveryWrite_whileReady_stillAnswers_unsaved() = runTest {
        repository.storage = ChatStorageState.UNAVAILABLE
        retriever.outcome = blocks(block(1))
        val session = session()

        val id = session.ask(null, "¿Plazo?")
        advanceUntilIdle()

        assertThat(id).isEqualTo(ActiveAnswer.UNSAVED)
        assertThat(session.active.value!!.saveFailed).isTrue()
        assertThat(session.active.value!!.state).isEqualTo(TurnState.COMPLETED)
    }

    @Test
    fun aConversationThatCouldNotBeSaved_isCreatedAgain_withTheNextQuestion() = runTest {
        retriever.outcome = blocks(block(1))
        repository.failWrites = true
        val session = session()
        val unsaved = session.ask(null, "¿Plazo?")
        advanceUntilIdle()
        assertThat(unsaved).isEqualTo(ActiveAnswer.UNSAVED)

        repository.failWrites = false
        val id = session.ask(unsaved, "¿Y para empresas?")
        advanceUntilIdle()

        assertThat(id).isNotEqualTo(ActiveAnswer.UNSAVED)
        assertThat(lastTurn().conversationId).isEqualTo(id)
        assertThat(lastTurn().question).isEqualTo("¿Y para empresas?")
    }

    @Test
    fun withUnreadableStorage_theChatIsNotReady_andAskDoesNothing_C17() = runTest {
        repository.storage = ChatStorageState.UNAVAILABLE
        availability.value = ChatAvailability.StorageUnavailable
        retriever.outcome = blocks(block(1))
        val session = session()

        assertThat(session.ask(null, "¿Plazo?")).isNull()
        advanceUntilIdle()

        assertThat(session.active.value).isNull()
        assertThat(repository.writes).isEmpty()
        assertThat(retriever.requests).isEmpty()
        assertThat(engine.prompts).isEmpty()
    }

    // --- US6: the app goes to the background (A11) --------------------------------------------------

    @Test
    fun uiHidden_interruptsTheAnswer_keepingItsTextAndCitations() = runTest {
        retriever.outcome = blocks(block(1))
        engine.tokens = listOf("Primero [1]. ", "Segundo.")
        engine.delayPerTokenMs = 5_000
        val session = session()
        session.ask(null, "¿Plazo?")
        advanceTimeBy(retriever.delayMs + engine.prefillMs + 1)

        session.onUiHidden()

        with(lastTurn()) {
            assertThat(state).isEqualTo(TurnState.INTERRUPTED)
            assertThat(answer).isEqualTo("Primero [1]. ")
            assertThat(sources.map { it.promptNumber }).containsExactly(1)
        }
        assertThat(session.retry(lastTurn().id)).isTrue()
    }

    // --- F12 US4: «Borrar historial de chat» (P9, P10) ----------------------------------------------

    @Test
    fun deleteAll_stopsTheAnswerInProgress_waits_andDeletesEveryConversation() = runTest {
        retriever.outcome = blocks(block(1))
        val session = session()
        session.ask(null, "¿Uno?")
        advanceUntilIdle()
        engine.tokens = listOf("a", "b")
        engine.delayPerTokenMs = 5_000
        session.ask(null, "¿Dos?")
        advanceTimeBy(retriever.delayMs + engine.prefillMs + 1)
        assertThat(session.active.value!!.state).isEqualTo(TurnState.GENERATING)

        session.deleteAll()

        assertThat(engine.generating).isFalse()
        assertThat(repository.turns).isEmpty()
        assertThat(repository.lastConversationId()).isNull()
        assertThat(session.active.value).isNull()
        assertThat(session.historyCleared.value).isEqualTo(1)
    }

    @Test
    fun aStorageError_propagates_andTheCounterStays() = runTest {
        retriever.outcome = blocks(block(1))
        val session = session()
        session.ask(null, "¿Uno?")
        advanceUntilIdle()
        repository.failWrites = true

        val error = runCatching { session.deleteAll() }.exceptionOrNull()

        assertThat(error).isInstanceOf(ChatStorageException::class.java)
        assertThat(session.historyCleared.value).isEqualTo(0)
        assertThat(repository.turns).hasSize(1)
    }

    @Test
    fun noQuestionStartsBetweenTheStopAndTheDeletion() = runTest {
        retriever.outcome = blocks(block(1))
        engine.tokens = listOf("a", "b")
        engine.delayPerTokenMs = 5_000
        val session = session()
        session.ask(null, "¿Uno?")
        advanceTimeBy(retriever.delayMs + engine.prefillMs + 1)

        val deleting = launch { session.deleteAll() }
        val asking = async { session.ask(null, "¿Dos?") }
        advanceUntilIdle()

        assertThat(deleting.isCompleted).isTrue()
        val id = asking.await()
        // The question waited for the deletion: it is the only turn left, in a new conversation.
        assertThat(repository.turns.values.map { it.question }).containsExactly("¿Dos?")
        assertThat(lastTurn().conversationId).isEqualTo(id)
        assertThat(lastTurn().state).isEqualTo(TurnState.COMPLETED)
    }

    @Test
    fun askingInADeletedConversation_startsANewOne_andTheAnswerIsSaved() = runTest {
        retriever.outcome = blocks(block(1))
        val session = session()
        val deleted = session.ask(null, "¿Uno?")!!
        advanceUntilIdle()
        session.deleteAll()

        val id = session.ask(deleted, "¿Dos?")
        advanceUntilIdle()

        assertThat(id).isNotEqualTo(deleted)
        assertThat(id).isNotEqualTo(ActiveAnswer.UNSAVED)
        with(lastTurn()) {
            assertThat(conversationId).isEqualTo(id)
            assertThat(question).isEqualTo("¿Dos?")
            assertThat(state).isEqualTo(TurnState.COMPLETED)
        }
        assertThat(session.active.value).isNull()
    }
}
