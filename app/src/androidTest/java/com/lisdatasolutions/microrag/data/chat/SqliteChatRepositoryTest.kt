package com.lisdatasolutions.microrag.data.chat

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import com.lisdatasolutions.microrag.data.rag.RagConnection
import com.lisdatasolutions.microrag.domain.chat.AnswerSource
import com.lisdatasolutions.microrag.domain.chat.ChatStorageException
import com.lisdatasolutions.microrag.domain.chat.ChatStorageState
import com.lisdatasolutions.microrag.domain.chat.TurnFailure
import com.lisdatasolutions.microrag.domain.chat.TurnState
import com.lisdatasolutions.microrag.domain.chat.TurnUpdate
import com.lisdatasolutions.microrag.testing.CapturingLogger
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith

/**
 * F11 T010 (research R9, data-model §3, contract A15): `chat.db` on real bundled SQLite, in a temporary
 * file of the app cache.
 */
@RunWith(AndroidJUnit4::class)
class SqliteChatRepositoryTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val folder = File(context.cacheDir, "chat-test-${System.nanoTime()}")
    private val dbFile = File(folder, "chat.db")

    private fun repository() = SqliteChatRepository(dbFile, { ChatSchema.load(context.assets) }, Dispatchers.IO, CapturingLogger())

    @After
    fun tearDown() {
        folder.deleteRecursively()
    }

    private fun source(position: Int, documentId: Long = 7L) = AnswerSource(
        position = position, promptNumber = position + 1, documentId = documentId, chunkId = 100L + position,
        firstOrdinal = 3, lastOrdinal = 4, documentName = "Manual.pdf", page = 12,
    )

    @Test
    fun aNewFileGetsTheSchemaAndItsVersion() = runBlocking<Unit> {
        assertThat(repository().open()).isEqualTo(ChatStorageState.OK)

        RagConnection.open(dbFile, extensionPath = null).use { db ->
            assertThat(db.tableNames()).containsAtLeast("chat_meta", "conversations", "turns", "sources")
            assertThat(db.queryOne("SELECT value FROM chat_meta WHERE key = 'schema_version'") { it.getText(0) }).isEqualTo("1")
        }
    }

    @Test
    fun turnsKeepTheirOrderUpdatesAndSources() = runBlocking<Unit> {
        val repo = repository()
        val id = repo.createConversation(now = 1_000)
        val first = repo.insertTurn(id, "¿Plazo?", partialCoverage = true, now = 1_100)
        val second = repo.insertTurn(id, "¿Y para empresas?", partialCoverage = false, now = 1_200)
        assertThat(listOf(first.ordinal, second.ordinal)).containsExactly(0, 1).inOrder()

        repo.updateTurn(first.id, TurnUpdate(state = TurnState.COMPLETED, answer = "30 días [2].", promptBlocks = 4, finishedAt = 1_150))
        repo.replaceSources(first.id, listOf(source(1), source(2, documentId = 999)))
        repo.updateTurn(second.id, TurnUpdate(state = TurnState.FAILED, failure = TurnFailure.INDEX, finishedAt = 1_250))

        val turns = repo.conversation(id).first()
        assertThat(turns.map { it.question }).containsExactly("¿Plazo?", "¿Y para empresas?").inOrder()
        with(turns[0]) {
            assertThat(state).isEqualTo(TurnState.COMPLETED)
            assertThat(answer).isEqualTo("30 días [2].")
            assertThat(partialCoverage).isTrue()
            assertThat(promptBlocks).isEqualTo(4)
            assertThat(finishedAt).isEqualTo(1_150)
            // The second source names a document that does not exist: sources outlive their documents (FR-027).
            assertThat(sources).containsExactly(source(1), source(2, documentId = 999)).inOrder()
        }
        assertThat(turns[1].failure).isEqualTo(TurnFailure.INDEX)

        repo.resetForRetry(second.id)
        with(repo.conversation(id).first()[1]) {
            assertThat(state).isEqualTo(TurnState.RETRIEVING)
            assertThat(failure).isNull()
            assertThat(answer).isEmpty()
            assertThat(finishedAt).isNull()
        }
    }

    @Test
    fun theConversationFlowEmitsAfterEachWrite() = runBlocking<Unit> {
        val repo = repository()
        val id = repo.createConversation(now = 1)
        val flow = repo.conversation(id)
        assertThat(flow.first()).isEmpty()

        repo.insertTurn(id, "¿Plazo?", partialCoverage = false, now = 2)

        assertThat(withTimeout(5_000) { flow.first { it.size == 1 } }.single().question).isEqualTo("¿Plazo?")
    }

    @Test
    fun theLastConversationIsTheOneWithTheMostRecentQuestion() = runBlocking<Unit> {
        val repo = repository()
        assertThat(repo.lastConversationId()).isNull()
        val older = repo.createConversation(now = 1)
        val newer = repo.createConversation(now = 2)
        assertThat(repo.lastConversationId()).isEqualTo(newer)

        repo.insertTurn(older, "¿Plazo?", partialCoverage = false, now = 3)

        assertThat(repo.lastConversationId()).isEqualTo(older)
    }

    @Test
    fun deletingCascadesToTurnsAndSources() = runBlocking<Unit> {
        val repo = repository()
        val kept = repo.createConversation(now = 1)
        val deleted = repo.createConversation(now = 2)
        repo.insertTurn(kept, "¿Uno?", partialCoverage = false, now = 3)
        val turn = repo.insertTurn(deleted, "¿Dos?", partialCoverage = false, now = 4)
        repo.replaceSources(turn.id, listOf(source(1)))

        repo.deleteConversation(deleted)

        assertThat(repo.conversation(deleted).first()).isEmpty()
        assertThat(repo.conversation(kept).first()).hasSize(1)
        RagConnection.open(dbFile, extensionPath = null).use { db ->
            assertThat(db.queryOne("SELECT count(*) FROM sources") { it.getLong(0) }).isEqualTo(0)
        }

        repo.deleteAll()
        assertThat(repo.lastConversationId()).isNull()
    }

    /** F12 T030 (P12, research R10): what «Borrar historial de chat» relies on. */
    @Test
    fun countExistenceAndDeleteAll_leaveTheIndexAlone() = runBlocking<Unit> {
        val repo = repository()
        val withoutTurns = repo.createConversation(now = 1)
        val withTurn = repo.createConversation(now = 2)
        val turn = repo.insertTurn(withTurn, "¿Uno?", partialCoverage = false, now = 3)
        repo.replaceSources(turn.id, listOf(source(1)))
        // The index is another file next to it (D-054): deleting the history never touches it.
        val index = File(folder, "rag.db").apply { writeBytes(byteArrayOf(1, 2, 3)) }

        assertThat(repo.conversationCount()).isEqualTo(1)
        assertThat(repo.conversationExists(withTurn)).isTrue()
        assertThat(repo.conversationExists(withoutTurns)).isTrue()
        assertThat(repo.conversationExists(999)).isFalse()

        repo.deleteAll()

        assertThat(repo.conversationCount()).isEqualTo(0)
        assertThat(repo.conversationExists(withTurn)).isFalse()
        RagConnection.open(dbFile, extensionPath = null).use { db ->
            for (table in listOf("conversations", "turns", "sources")) {
                assertThat(db.queryOne("SELECT count(*) FROM $table") { it.getLong(0) }).isEqualTo(0)
            }
        }
        assertThat(index.readBytes()).isEqualTo(byteArrayOf(1, 2, 3))
    }

    @Test
    fun onlyAnswersInProgressBecomeInterrupted() = runBlocking<Unit> {
        val repo = repository()
        val id = repo.createConversation(now = 1)
        val retrieving = repo.insertTurn(id, "¿Uno?", partialCoverage = false, now = 2)
        val generating = repo.insertTurn(id, "¿Dos?", partialCoverage = false, now = 3)
        val done = repo.insertTurn(id, "¿Tres?", partialCoverage = false, now = 4)
        repo.updateTurn(generating.id, TurnUpdate(state = TurnState.GENERATING, answer = "Parcial"))
        repo.updateTurn(done.id, TurnUpdate(state = TurnState.COMPLETED, answer = "Hecho", finishedAt = 5))

        assertThat(repo.markInterrupted(now = 9)).isEqualTo(2)

        val turns = repo.conversation(id).first().associateBy { it.id }
        assertThat(turns.getValue(retrieving.id).state).isEqualTo(TurnState.INTERRUPTED)
        assertThat(turns.getValue(generating.id).state).isEqualTo(TurnState.INTERRUPTED)
        assertThat(turns.getValue(generating.id).answer).isEqualTo("Parcial")
        assertThat(turns.getValue(generating.id).finishedAt).isEqualTo(9)
        assertThat(turns.getValue(done.id).state).isEqualTo(TurnState.COMPLETED)
    }

    @Test
    fun aNewerFileIsLeftUntouchedAndUnavailable() = runBlocking<Unit> {
        folder.mkdirs()
        RagConnection.open(dbFile, extensionPath = null).use { db ->
            db.exec("CREATE TABLE chat_meta (key TEXT PRIMARY KEY, value TEXT NOT NULL)")
            db.exec("INSERT INTO chat_meta(key, value) VALUES ('schema_version', '99')")
        }
        val before = dbFile.readBytes()
        val repo = repository()

        assertThat(repo.open()).isEqualTo(ChatStorageState.UNAVAILABLE)
        assertThat(repo.storageState.value).isEqualTo(ChatStorageState.UNAVAILABLE)
        assertThat(repo.lastConversationId()).isNull()
        assertThrows(ChatStorageException::class.java) { runBlocking { repo.createConversation(now = 1) } }
        assertThat(dbFile.readBytes()).isEqualTo(before)
    }

    @Test
    fun aDamagedFileIsLeftUntouchedAndUnavailable() = runBlocking<Unit> {
        folder.mkdirs()
        dbFile.writeBytes(ByteArray(4096) { 0x5A })
        val before = dbFile.readBytes()
        val repo = repository()

        assertThat(repo.open()).isEqualTo(ChatStorageState.UNAVAILABLE)
        assertThrows(ChatStorageException::class.java) { runBlocking { repo.insertTurn(1, "¿Uno?", false, now = 1) } }
        assertThat(dbFile.readBytes()).isEqualTo(before)
    }
}
