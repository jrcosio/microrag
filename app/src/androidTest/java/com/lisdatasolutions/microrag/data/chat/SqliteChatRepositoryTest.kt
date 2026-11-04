package com.lisdatasolutions.microrag.data.chat

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import com.lisdatasolutions.microrag.data.rag.RagConnection
import com.lisdatasolutions.microrag.data.rag.RagSchema
import com.lisdatasolutions.microrag.domain.chat.AnswerSource
import com.lisdatasolutions.microrag.domain.chat.ChatScope
import com.lisdatasolutions.microrag.domain.chat.ChatStorageException
import com.lisdatasolutions.microrag.domain.chat.ChatStorageState
import com.lisdatasolutions.microrag.domain.chat.TurnFailure
import com.lisdatasolutions.microrag.domain.chat.TurnState
import com.lisdatasolutions.microrag.domain.chat.TurnUpdate
import com.lisdatasolutions.microrag.domain.rag.DocumentRef
import com.lisdatasolutions.microrag.domain.rag.FolderRef
import com.lisdatasolutions.microrag.testing.CapturingLogger
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
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

    private val logger = CapturingLogger()

    private fun repository(migrations: List<ChatMigration> = ChatMigrations.all) =
        SqliteChatRepository(dbFile, { ChatSchema.load(context.assets) }, Dispatchers.IO, logger, migrations)

    /** The released v1 schema (F11), a test asset: v1 files are built with it, never with the app's schema (F17 M7). */
    private val v1Schema: List<String> = RagSchema.split(
        InstrumentationRegistry.getInstrumentation().context.assets.open("schema/chat_schema_v1.sql").bufferedReader().use { it.readText() },
    )

    private val scope = ChatScope.Some(
        folders = setOf(FolderRef(id = 3, createdAtMs = 30), FolderRef(id = 4, createdAtMs = 40)),
        general = true,
        documents = setOf(DocumentRef(id = 7, createdAtMs = 70)),
    )

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
            assertThat(db.tableNames())
                .containsAtLeast("chat_meta", "conversations", "turns", "sources", "conversation_scope", "last_scope")
            assertThat(db.queryOne("SELECT value FROM chat_meta WHERE key = 'schema_version'") { it.getText(0) }).isEqualTo("2")
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

    /** F13 Q1 (D-065): a conversation without questions, which Historial does not list, is never the last one. */
    @Test
    fun theLastConversationIsTheOneWithTheMostRecentQuestion() = runBlocking<Unit> {
        val repo = repository()
        assertThat(repo.lastConversationId()).isNull()
        val older = repo.createConversation(now = 1)
        val newer = repo.createConversation(now = 2)
        assertThat(repo.lastConversationId()).isNull()

        repo.insertTurn(newer, "¿Uno?", partialCoverage = false, now = 3)
        assertThat(repo.lastConversationId()).isEqualTo(newer)

        repo.insertTurn(older, "¿Plazo?", partialCoverage = false, now = 4)
        assertThat(repo.lastConversationId()).isEqualTo(older)

        repo.createConversation(now = 5)
        assertThat(repo.lastConversationId()).isEqualTo(older)
    }

    @Test
    fun deletingCascadesToTurnsAndSources() = runBlocking<Unit> {
        val repo = repository()
        val kept = repo.createConversation(now = 1)
        val deleted = repo.createConversation(now = 2)
        val keptTurn = repo.insertTurn(kept, "¿Uno?", partialCoverage = false, now = 3)
        repo.replaceSources(keptTurn.id, listOf(source(2)))
        val turn = repo.insertTurn(deleted, "¿Dos?", partialCoverage = false, now = 4)
        repo.replaceSources(turn.id, listOf(source(1)))

        repo.deleteConversation(deleted)

        assertThat(repo.conversation(deleted).first()).isEmpty()
        // F13 SC-005: the other conversation keeps its turn and its source.
        assertThat(repo.conversation(kept).first().single().sources).containsExactly(source(2))
        RagConnection.open(dbFile, extensionPath = null).use { db ->
            assertThat(db.queryOne("SELECT count(*) FROM sources") { it.getLong(0) }).isEqualTo(1)
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
        // F13 Q5: the history fails as storage, never with a SQLite exception.
        assertThrows(ChatStorageException::class.java) { runBlocking { repo.conversations(limit = 50).first() } }
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

    // F13 T003 (research R1, R2; contract Q1–Q5, Q13): the history list.

    @Test
    fun historyListsConversationsWithQuestionsByLastActivity() = runBlocking<Unit> {
        val repo = repository()
        val first = repo.createConversation(now = 1)
        val second = repo.createConversation(now = 2)
        repo.createConversation(now = 3) // never asked: not listed (Q1)
        repo.insertTurn(first, "¿Cuál es el plazo de devolución?", partialCoverage = false, now = 10)
        repo.insertTurn(second, "Resume el capítulo 2", partialCoverage = false, now = 20)
        repo.insertTurn(first, "¿Y para empresas?", partialCoverage = false, now = 30)

        val page = repo.conversations(limit = 50).first()

        assertThat(page.total).isEqualTo(2)
        assertThat(page.entries.map { it.id }).containsExactly(first, second).inOrder()
        with(page.entries[0]) {
            assertThat(title).isEqualTo("¿Cuál es el plazo de devolución?")
            assertThat(lastActivityAt).isEqualTo(30)
            assertThat(questions).isEqualTo(2)
        }
        assertThat(page.entries[1].questions).isEqualTo(1)
        assertThat(page.total).isEqualTo(repo.conversationCount())
    }

    @Test
    fun historyTiesGoToTheConversationCreatedLater_andTitlesAreCut() = runBlocking<Unit> {
        val repo = repository()
        val older = repo.createConversation(now = 1)
        val newer = repo.createConversation(now = 1)
        repo.insertTurn(older, "x".repeat(1_000), partialCoverage = false, now = 5)
        repo.insertTurn(newer, "¿Plazo?", partialCoverage = false, now = 5)

        val page = repo.conversations(limit = 50).first()

        assertThat(page.entries.map { it.id }).containsExactly(newer, older).inOrder()
        assertThat(page.entries[1].title).hasLength(300)
    }

    @Test
    fun historyPagesGrowWithoutDuplicatesOrGaps() = runBlocking<Unit> {
        val repo = repository()
        val ids = (1..120).map { i -> repo.createConversation(now = i.toLong()).also { repo.insertTurn(it, "¿$i?", false, now = i.toLong()) } }

        val first = repo.conversations(limit = 50).first()
        assertThat(first.entries).hasSize(50)
        assertThat(first.total).isEqualTo(120)
        assertThat(first.hasMore).isTrue()

        // The oldest one is asked again between two blocks: it goes first, and nothing repeats or goes missing.
        repo.insertTurn(ids.first(), "¿Otra vez?", partialCoverage = false, now = 500)
        val second = repo.conversations(limit = 100).first()
        val all = repo.conversations(limit = 120).first()

        assertThat(second.entries.first().id).isEqualTo(ids.first())
        assertThat(second.entries.map { it.id }).containsNoDuplicates()
        assertThat(second.entries.map { it.id }).isEqualTo(all.entries.take(100).map { it.id })
        assertThat(all.entries.map { it.id }).containsExactlyElementsIn(ids)
        assertThat(all.hasMore).isFalse()
    }

    /** Q4: only questions and deletions read the list again; the answer checkpoints never do (research R2). */
    @Test
    fun historyIsReadAgainOnlyWhenTheListChanges() = runBlocking<Unit> {
        val repo = repository()
        val kept = repo.createConversation(now = 1)
        repo.insertTurn(kept, "¿Uno?", partialCoverage = false, now = 2)
        val pages = mutableListOf<Int>()
        val job = launch(Dispatchers.IO) { repo.conversations(limit = 50).collect { pages += it.entries.size } }
        suspend fun settle() = delay(300)
        fun reads() = logger.lines.count { it.startsWith("history page ") }
        settle()
        val start = reads()

        val other = repo.createConversation(now = 3)
        val turn = repo.insertTurn(other, "¿Dos?", partialCoverage = false, now = 4)
        settle()
        assertThat(reads()).isEqualTo(start + 1)

        repo.updateTurn(turn.id, TurnUpdate(state = TurnState.GENERATING, answer = "Parcial"))
        repo.updateTurn(turn.id, TurnUpdate(answer = "Parcial y más"))
        repo.replaceSources(turn.id, listOf(source(1)))
        repo.resetForRetry(turn.id)
        repo.markInterrupted(now = 5)
        repo.createConversation(now = 6)
        settle()
        assertThat(reads()).isEqualTo(start + 1)

        repo.deleteConversation(other)
        settle()
        repo.deleteAll()
        settle()
        job.cancel()

        assertThat(reads()).isEqualTo(start + 3)
        assertThat(pages).containsExactly(1, 2, 1, 0).inOrder()
    }

    @Test
    fun historyLogsOnlyNumbers() = runBlocking<Unit> {
        val repo = repository()
        val id = repo.createConversation(now = 1)
        repo.insertTurn(id, "¿Pregunta privada?", partialCoverage = false, now = 2)

        repo.conversations(limit = 50).first()

        val line = logger.lines.single { it.startsWith("history page ") }
        assertThat(line).matches("history page rows=1 total=1 ms=\\d+")
    }

    // F17 T005 (research R7, data-model §2): the scope of each conversation and the last one used.

    /** A v1 file as F11–F16 left it: one conversation with a turn and a source. Returns the conversation id. */
    private fun versionOneFile(): Long {
        folder.mkdirs()
        RagConnection.open(dbFile, extensionPath = null).use { db ->
            db.enableWal()
            db.transaction {
                v1Schema.forEach { db.exec(it) }
                db.exec("INSERT INTO chat_meta(key, value) VALUES ('schema_version', '1')")
                db.exec("INSERT INTO conversations(id, created_at, updated_at) VALUES (5, 10, 20)")
                db.exec(
                    "INSERT INTO turns(id, conversation_id, ordinal, question, answer, state, created_at, finished_at) " +
                        "VALUES (8, 5, 0, '¿Plazo?', '30 días [1].', 'completed', 15, 20)",
                )
                db.exec(
                    "INSERT INTO sources(turn_id, position, prompt_number, document_id, chunk_id, first_ordinal, last_ordinal, " +
                        "document_name, page) VALUES (8, 1, 1, 7, 101, 3, 4, 'Manual.pdf', 12)",
                )
            }
        }
        return 5
    }

    @Test
    fun versionOne_isMigratedWithEveryConversationOnTheWholeLibrary() = runBlocking<Unit> {
        val id = versionOneFile()
        val repo = repository()

        assertThat(repo.open()).isEqualTo(ChatStorageState.OK)

        val turn = repo.conversation(id).first().single()
        assertThat(turn.answer).isEqualTo("30 días [1].")
        assertThat(turn.sources).containsExactly(source(1).copy(promptNumber = 1, chunkId = 101))
        assertThat(repo.conversations(limit = 50).first().entries.single().lastActivityAt).isEqualTo(20)
        assertThat(repo.conversationScope(id)).isEqualTo(ChatScope.WholeLibrary)
        assertThat(repo.lastScope()).isEqualTo(ChatScope.WholeLibrary)
        RagConnection.open(dbFile, extensionPath = null).use { db ->
            assertThat(db.queryOne("SELECT value FROM chat_meta WHERE key = 'schema_version'") { it.getText(0) }).isEqualTo("2")
            assertThat(db.tableNames()).containsAtLeast("conversation_scope", "last_scope")
        }
    }

    @Test
    fun failingMigration_leavesTheVersionOneFileUntouched() = runBlocking<Unit> {
        versionOneFile()
        val before = dbFile.readBytes()
        val repo = repository(migrations = listOf(ChatMigration(from = 1, statements = listOf("NOT SQL"))))

        assertThat(repo.open()).isEqualTo(ChatStorageState.UNAVAILABLE)

        assertThat(dbFile.readBytes()).isEqualTo(before)
        RagConnection.open(dbFile, extensionPath = null).use { db ->
            assertThat(db.tableNames()).doesNotContain("conversation_scope")
        }
    }

    @Test
    fun theScopeIsSavedWithItsConversationAndRead() = runBlocking<Unit> {
        val repo = repository()
        val scoped = repo.createConversation(now = 1, scope = scope)
        val whole = repo.createConversation(now = 2)

        assertThat(repo.conversationScope(scoped)).isEqualTo(scope)
        assertThat(repo.conversationScope(whole)).isEqualTo(ChatScope.WholeLibrary)
        assertThat(repo.conversationScope(999)).isEqualTo(ChatScope.WholeLibrary)

        val loose = ChatScope.Some(folders = emptySet(), general = false, documents = setOf(DocumentRef(9, 90)))
        repo.setConversationScope(whole, loose)
        repo.setConversationScope(scoped, ChatScope.WholeLibrary)

        assertThat(repo.conversationScope(whole)).isEqualTo(loose)
        assertThat(repo.conversationScope(scoped)).isEqualTo(ChatScope.WholeLibrary)
    }

    /** «General» is one row with ref_id 0; folders and documents keep when they were created (D-065, L9). */
    @Test
    fun theRowsKeepGeneralAndTheCreationTimes() = runBlocking<Unit> {
        val repo = repository()
        val id = repo.createConversation(now = 1, scope = scope)
        repo.setLastScope(scope)

        RagConnection.open(dbFile, extensionPath = null).use { db ->
            for (table in listOf("conversation_scope", "last_scope")) {
                val rows = db.query("SELECT kind, ref_id, ref_created_at_ms FROM $table ORDER BY kind, ref_id") {
                    Triple(it.getText(0), it.getLong(1), if (it.isNull(2)) null else it.getLong(2))
                }
                assertThat(rows).containsExactly(
                    Triple("document", 7L, 70L), Triple("folder", 3L, 30L), Triple("folder", 4L, 40L), Triple("general", 0L, null),
                ).inOrder()
            }
            assertThat(db.queryOne("SELECT count(*) FROM conversation_scope WHERE conversation_id = ?", id) { it.getLong(0) })
                .isEqualTo(4)
        }
    }

    /** L4: applying a scope does not move the conversation in Historial. */
    @Test
    fun settingTheScopeKeepsTheHistoryOrder() = runBlocking<Unit> {
        val repo = repository()
        val older = repo.createConversation(now = 1)
        val newer = repo.createConversation(now = 2)
        repo.insertTurn(older, "¿Uno?", partialCoverage = false, now = 3)
        repo.insertTurn(newer, "¿Dos?", partialCoverage = false, now = 4)

        repo.setConversationScope(older, scope)

        assertThat(repo.conversations(limit = 50).first().entries.map { it.id }).containsExactly(newer, older).inOrder()
        assertThat(repo.lastConversationId()).isEqualTo(newer)
    }

    @Test
    fun theLastScopeIsReplacedAsAWhole() = runBlocking<Unit> {
        val repo = repository()
        assertThat(repo.lastScope()).isEqualTo(ChatScope.WholeLibrary)

        repo.setLastScope(scope)
        assertThat(repo.lastScope()).isEqualTo(scope)

        val general = ChatScope.Some(folders = emptySet(), general = true, documents = emptySet())
        repo.setLastScope(general)
        assertThat(repo.lastScope()).isEqualTo(general)

        repo.setLastScope(ChatScope.WholeLibrary)
        assertThat(repo.lastScope()).isEqualTo(ChatScope.WholeLibrary)
    }

    @Test
    fun deletingAConversationTakesItsScope_andDeleteAllTheLastOneToo() = runBlocking<Unit> {
        val repo = repository()
        val deleted = repo.createConversation(now = 1, scope = scope)
        val kept = repo.createConversation(now = 2, scope = scope)
        repo.setLastScope(scope)
        fun rows(table: String) = RagConnection.open(dbFile, extensionPath = null).use { db ->
            db.queryOne("SELECT count(*) FROM $table") { it.getLong(0) }
        }

        repo.deleteConversation(deleted)

        assertThat(repo.conversationScope(kept)).isEqualTo(scope)
        assertThat(rows("conversation_scope")).isEqualTo(4)
        assertThat(repo.lastScope()).isEqualTo(scope)

        repo.deleteAll()

        assertThat(rows("conversation_scope")).isEqualTo(0)
        assertThat(rows("last_scope")).isEqualTo(0)
        assertThat(repo.lastScope()).isEqualTo(ChatScope.WholeLibrary)
    }

    @Test
    fun scopeWritesFailAsStorageWhenTheFileIsUnavailable() = runBlocking<Unit> {
        folder.mkdirs()
        dbFile.writeBytes(ByteArray(4096) { 0x5A })
        val repo = repository()

        assertThat(repo.open()).isEqualTo(ChatStorageState.UNAVAILABLE)
        assertThat(repo.lastScope()).isEqualTo(ChatScope.WholeLibrary)
        assertThrows(ChatStorageException::class.java) { runBlocking { repo.setLastScope(scope) } }
        assertThrows(ChatStorageException::class.java) { runBlocking { repo.createConversation(now = 1, scope = scope) } }
    }
}
