package com.lisdatasolutions.microrag.domain.chat

import com.google.common.truth.Truth.assertThat
import com.lisdatasolutions.microrag.testing.FakeChatSession
import com.lisdatasolutions.microrag.testing.InMemoryChatRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test

/** F13 T005 (contracts/history-api.md Q5, Q7, Q9, Q16): the use cases of Historial. */
@OptIn(ExperimentalCoroutinesApi::class)
class HistoryUseCasesTest {

    private val repository = InMemoryChatRepository()
    private val session = FakeChatSession(repository)

    private suspend fun conversation(question: String, now: Long): Long {
        repository.now = now
        return session.ask(null, question)
    }

    @Test
    fun theListStartsLoading_thenGivesThePage_withNormalizedTitles() = runTest(UnconfinedTestDispatcher()) {
        val id = conversation("  ¿Cuál es\n\tel plazo   de devolución?  ", now = 10)
        val seen = mutableListOf<HistoryContent>()
        val job = launch { ObserveConversationsUseCase(repository)(MutableStateFlow(HistoryLimits.PAGE)).toList(seen) }
        runCurrent()
        job.cancel()

        assertThat(seen.first()).isEqualTo(HistoryContent.Loading)
        val page = (seen.last() as HistoryContent.Page).page
        assertThat(page.total).isEqualTo(1)
        assertThat(page.entries.single().id).isEqualTo(id)
        assertThat(page.entries.single().title).isEqualTo("¿Cuál es el plazo de devolución?")
    }

    @Test
    fun aNewLimit_readsAgain() = runTest(UnconfinedTestDispatcher()) {
        repeat(3) { conversation("¿$it?", now = it.toLong()) }
        val limit = MutableStateFlow(2)
        val seen = mutableListOf<HistoryContent>()
        val job = launch { ObserveConversationsUseCase(repository)(limit).toList(seen) }
        runCurrent()
        assertThat((seen.last() as HistoryContent.Page).page.entries).hasSize(2)

        limit.value = 4
        runCurrent()
        job.cancel()

        with((seen.last() as HistoryContent.Page).page) {
            assertThat(entries).hasSize(3)
            assertThat(hasMore).isFalse()
        }
    }

    @Test
    fun anUnreadableStorage_givesUnavailable_withoutThrowing() = runTest(UnconfinedTestDispatcher()) {
        repository.storage = ChatStorageState.UNAVAILABLE
        val seen = mutableListOf<HistoryContent>()
        val job = launch { ObserveConversationsUseCase(repository)(MutableStateFlow(HistoryLimits.PAGE)).toList(seen) }
        runCurrent()
        job.cancel()

        assertThat(seen).containsExactly(HistoryContent.Loading, HistoryContent.Unavailable).inOrder()
    }

    @Test
    fun theAnswerInProgress_givesItsConversation_onlyWhileRetrievingOrGenerating() = runTest(UnconfinedTestDispatcher()) {
        val seen = mutableListOf<Long?>()
        val job = launch { ObserveAnswerInProgressUseCase(session)().toList(seen) }
        session.active.value = ActiveAnswer(conversationId = 4, turnId = 9, question = "¿Uno?", state = TurnState.RETRIEVING)
        session.active.value = session.active.value!!.copy(state = TurnState.GENERATING, text = "a")
        session.active.value = session.active.value!!.copy(state = TurnState.COMPLETED, saveFailed = true)
        session.active.value = null
        runCurrent()
        job.cancel()

        assertThat(seen).containsExactly(null, 4L, null).inOrder()
    }

    @Test
    fun deleting_saysFalseWhenTheStorageRefuses_withoutThrowing() = runTest {
        val kept = conversation("¿Uno?", now = 1)
        val deleted = conversation("¿Dos?", now = 2)
        val delete = DeleteConversationUseCase(session)

        assertThat(delete(deleted)).isTrue()
        repository.failWrites = true
        assertThat(delete(kept)).isFalse()
        repository.failWrites = false
        assertThat(repository.conversationExists(kept)).isTrue()
    }

    /** Q16: a conversation that cannot be checked is kept, so a read failure never empties the chat. */
    @Test
    fun existence_isTrueWhenTheStorageCannotBeRead() = runTest {
        val id = conversation("¿Uno?", now = 1)
        val exists = ConversationExistsUseCase(repository)

        assertThat(exists(id)).isTrue()
        assertThat(exists(id + 100)).isFalse()
        repository.storage = ChatStorageState.UNAVAILABLE
        assertThat(exists(id + 100)).isTrue()
    }
}
