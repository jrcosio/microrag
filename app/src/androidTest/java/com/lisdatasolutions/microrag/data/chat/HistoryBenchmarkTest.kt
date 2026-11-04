package com.lisdatasolutions.microrag.data.chat

import android.util.Log
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import com.lisdatasolutions.microrag.core.designsystem.theme.LisdataTheme
import com.lisdatasolutions.microrag.data.rag.RagConnection
import com.lisdatasolutions.microrag.domain.chat.ActiveAnswer
import com.lisdatasolutions.microrag.domain.chat.AnswerSource
import com.lisdatasolutions.microrag.domain.chat.ChatScope
import com.lisdatasolutions.microrag.domain.chat.ChatSession
import com.lisdatasolutions.microrag.domain.chat.DeleteConversationUseCase
import com.lisdatasolutions.microrag.domain.chat.HistoryLimits
import com.lisdatasolutions.microrag.domain.chat.ObserveAnswerInProgressUseCase
import com.lisdatasolutions.microrag.domain.chat.ObserveConversationsUseCase
import com.lisdatasolutions.microrag.domain.chat.ResolvedScope
import com.lisdatasolutions.microrag.domain.chat.TurnState
import com.lisdatasolutions.microrag.domain.chat.TurnUpdate
import com.lisdatasolutions.microrag.feature.history.HistoryRoute
import com.lisdatasolutions.microrag.feature.history.HistoryViewModel
import com.lisdatasolutions.microrag.testing.CapturingLogger
import java.io.File
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * F13 T038 (research R9, SC-001, SC-010): the size of 1,000 conversations of 5 questions and how long
 * Historial takes to show them. Everything lives in its own file in the cache, deleted at the end: the app's
 * `chat.db`, and on the phone the user's conversations, are never opened. One line of numbers, never text.
 */
@RunWith(AndroidJUnit4::class)
class HistoryBenchmarkTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val folder = File(context.cacheDir, "history-benchmark")
    private val dbFile = File(folder, "chat.db")

    private fun repository() = SqliteChatRepository(dbFile, { ChatSchema.load(context.assets) }, Dispatchers.IO, CapturingLogger())

    @After
    fun tearDown() {
        folder.deleteRecursively()
    }

    @Test
    fun aThousandConversations_sizeAndOpeningTime() = runBlocking<Unit> {
        folder.deleteRecursively()
        val filled = repository()
        val start = System.currentTimeMillis() - DAYS * DAY_MS
        repeat(CONVERSATIONS) { c ->
            val at = start + c * (DAYS * DAY_MS / CONVERSATIONS)
            val id = filled.createConversation(at)
            repeat(QUESTIONS) { q ->
                val turn = filled.insertTurn(id, question(c, q), partialCoverage = false, now = at + q)
                filled.updateTurn(turn.id, TurnUpdate(state = TurnState.COMPLETED, answer = ANSWER, promptBlocks = 4, finishedAt = at + q))
                filled.replaceSources(turn.id, (1..SOURCES).map { source(it) })
            }
        }
        RagConnection.open(dbFile, extensionPath = null).use { it.exec("PRAGMA wal_checkpoint(TRUNCATE)") }
        val bytes = dbFile.length() + File(dbFile.path + "-wal").length()

        // The first block, read 20 times on a repository already open.
        val firstPage = (1..READS).map {
            val started = System.nanoTime()
            val page = filled.conversations(HistoryLimits.PAGE).first()
            assertThat(page.entries).hasSize(HistoryLimits.PAGE)
            (System.nanoTime() - started) / 1_000_000.0
        }.sorted()

        // The whole list, as it is once scrolled to the end.
        val scanStarted = System.nanoTime()
        val all = filled.conversations(CONVERSATIONS).first()
        val scanMs = (System.nanoTime() - scanStarted) / 1_000_000
        assertThat(all.entries.map { it.id }).containsNoDuplicates()
        assertThat(all.entries).hasSize(CONVERSATIONS)

        // SC-001: from a repository that opens the file to the first row drawn by Historial.
        val newest = all.entries.first().title
        val drawnStarted = System.nanoTime()
        val vm = HistoryViewModel(
            savedState = SavedStateHandle(mapOf("openConversationId" to null)),
            observeConversations = ObserveConversationsUseCase(repository()),
            deleteConversation = DeleteConversationUseCase(IdleSession),
            observeAnswerInProgress = ObserveAnswerInProgressUseCase(IdleSession),
        )
        compose.setContent { LisdataTheme { HistoryRoute(onBack = {}, onOpenConversation = {}, onNewChat = {}, viewModel = vm) } }
        compose.waitUntil(TIMEOUT_MS) {
            compose.onAllNodes(hasContentDescription(newest, substring = true)).fetchSemanticsNodes().isNotEmpty()
        }
        val drawnMs = (System.nanoTime() - drawnStarted) / 1_000_000

        Log.i(
            TAG,
            "history_bench conversations=$CONVERSATIONS questions=$QUESTIONS sources=$SOURCES db_kb=${bytes / 1024} " +
                "first_page_p50_ms=${rank(firstPage, 50)} first_page_p95_ms=${rank(firstPage, 95)} " +
                "full_scan_ms=$scanMs first_rows_ms=$drawnMs",
        )
    }

    private fun rank(sorted: List<Double>, percentile: Int): String {
        val index = ((percentile / 100.0) * sorted.size).toInt().coerceIn(0, sorted.lastIndex)
        return "%.1f".format(Locale.ROOT, sorted[index])
    }

    private fun question(conversation: Int, turn: Int) = "Pregunta $turn de la conversación $conversation: " + "x".repeat(QUESTION_CHARS - 40)

    private fun source(position: Int) = AnswerSource(
        position = position, promptNumber = position, documentId = position.toLong(), chunkId = 100L + position,
        firstOrdinal = 10, lastOrdinal = 11, documentName = "Documento de prueba $position.pdf", page = 12,
    )

    /** Nothing answers here: Historial only needs to know there is no answer in progress. */
    private object IdleSession : ChatSession {
        override val active = MutableStateFlow<ActiveAnswer?>(null)
        override suspend fun ask(conversationId: Long?, question: String, scope: ChatScope): Long? = null
        override fun cancel() = Unit
        override suspend fun retry(turnId: Long): Boolean = false
        override suspend fun setScope(conversationId: Long, scope: ChatScope) = Unit
        override suspend fun scopeOf(conversationId: Long) = ResolvedScope(ChatScope.WholeLibrary)
        override suspend fun lastScope() = ResolvedScope(ChatScope.WholeLibrary)
        override suspend fun setLastScope(scope: ChatScope) = Unit
        override fun startNewConversation() = Unit
        override suspend fun openConversation(conversationId: Long) = Unit
        override suspend fun delete(conversationId: Long) = Unit
        override val deletions = MutableSharedFlow<Long>()
        override val historyCleared = MutableStateFlow(0L)
        override suspend fun deleteAll() = Unit
    }

    private companion object {
        const val TAG = "MicroRAG"
        const val CONVERSATIONS = 1_000
        const val QUESTIONS = 5
        const val SOURCES = 3
        const val DAYS = 90L
        const val DAY_MS = 86_400_000L
        const val READS = 20
        const val QUESTION_CHARS = 80
        const val TIMEOUT_MS = 10_000L

        /** About the length of an answer of the evaluation set (F05). */
        val ANSWER = "Según el documento, el procedimiento requiere tres pasos [1]. ".repeat(15).take(900)
    }
}
