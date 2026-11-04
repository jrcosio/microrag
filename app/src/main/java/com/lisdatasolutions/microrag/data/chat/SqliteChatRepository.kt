package com.lisdatasolutions.microrag.data.chat

import com.lisdatasolutions.microrag.core.logging.AppLogger
import com.lisdatasolutions.microrag.data.rag.RagConnection
import com.lisdatasolutions.microrag.data.rag.RagOpenException
import com.lisdatasolutions.microrag.domain.chat.AnswerSource
import com.lisdatasolutions.microrag.domain.chat.ChatRepository
import com.lisdatasolutions.microrag.domain.chat.ChatScope
import com.lisdatasolutions.microrag.domain.chat.ChatStorageException
import com.lisdatasolutions.microrag.domain.chat.ChatStorageState
import com.lisdatasolutions.microrag.domain.chat.ChatTurn
import com.lisdatasolutions.microrag.domain.chat.ConversationPage
import com.lisdatasolutions.microrag.domain.chat.ConversationSummary
import com.lisdatasolutions.microrag.domain.chat.HistoryLimits
import com.lisdatasolutions.microrag.domain.chat.TurnFailure
import com.lisdatasolutions.microrag.domain.chat.TurnState
import com.lisdatasolutions.microrag.domain.chat.TurnUpdate
import com.lisdatasolutions.microrag.domain.rag.DocumentRef
import com.lisdatasolutions.microrag.domain.rag.FolderRef
import java.io.File
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.withContext

/**
 * The saved conversations in their own file, `noBackupFilesDir/chat/chat.db` (F11 research R9). One
 * connection on the database dispatcher, the same single thread as the index, so the app never writes
 * twice at once. A file it cannot read, or one of a newer schema, is left exactly as it was and the
 * storage reports [ChatStorageState.UNAVAILABLE] (contract A15, C17). [migrations] is
 * [ChatMigrations.all] but in the tests of a failing step.
 */
class SqliteChatRepository(
    private val dbFile: File,
    private val schema: () -> List<String>,
    private val dispatcher: CoroutineDispatcher,
    private val logger: AppLogger,
    private val migrations: List<ChatMigration> = ChatMigrations.all,
) : ChatRepository {

    private val state = MutableStateFlow<ChatStorageState?>(null)
    override val storageState: StateFlow<ChatStorageState?> = state.asStateFlow()

    /** Bumped after every committed write; the flows read again on each value. */
    private val changes = MutableStateFlow(0L)

    /** Bumped only by the writes that change the history list: a question, a deletion (F13 research R2). */
    private val listChanges = MutableStateFlow(0L)

    private var connection: RagConnection? = null

    override suspend fun open(): ChatStorageState = withContext(dispatcher) { ensureOpen() }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun conversation(id: Long): Flow<List<ChatTurn>> =
        changes.mapLatest { read { db -> turns(db, id) } ?: emptyList() }.distinctUntilChanged()

    override suspend fun turn(id: Long): ChatTurn? = read { db ->
        val conversationId = db.queryOne("SELECT conversation_id FROM turns WHERE id = ?", id) { it.getLong(0) } ?: return@read null
        turns(db, conversationId).firstOrNull { it.id == id }
    }

    override suspend fun lastConversationId(): Long? = read { db ->
        db.queryOne("SELECT c.id FROM conversations c WHERE $HAS_QUESTIONS ORDER BY c.updated_at DESC, c.id DESC LIMIT 1") { it.getLong(0) }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun conversations(limit: Int): Flow<ConversationPage> =
        listChanges.mapLatest { page(limit) }.distinctUntilChanged()

    /** One read for the rows and their total, so both see the same file (F13 research R1). */
    private suspend fun page(limit: Int): ConversationPage {
        val started = System.nanoTime()
        val page = try {
            read { db ->
                val entries = db.query(
                    "SELECT c.id, c.updated_at, " +
                        "(SELECT substr(t.question, 1, ${HistoryLimits.TITLE_CHARS}) FROM turns t WHERE t.conversation_id = c.id ORDER BY t.ordinal LIMIT 1), " +
                        "(SELECT count(*) FROM turns t WHERE t.conversation_id = c.id) " +
                        "FROM conversations c WHERE $HAS_QUESTIONS ORDER BY c.updated_at DESC, c.id DESC LIMIT ?",
                    limit,
                ) { ConversationSummary(id = it.getLong(0), title = it.getText(2), lastActivityAt = it.getLong(1), questions = it.getLong(3).toInt()) }
                val total = db.queryOne("SELECT count(DISTINCT conversation_id) FROM turns") { it.getLong(0).toInt() } ?: 0
                ConversationPage(entries, total)
            } ?: throw ChatStorageException("chat storage unavailable")
        } catch (e: ChatStorageException) {
            throw e
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.w(TAG, e) { "history read failed: ${e.javaClass.simpleName}" }
            throw ChatStorageException("chat db read failed", e)
        }
        logger.i(TAG) { "history page rows=${page.entries.size} total=${page.total} ms=${(System.nanoTime() - started) / 1_000_000}" }
        return page
    }

    override suspend fun createConversation(now: Long, scope: ChatScope): Long = write { db ->
        db.transaction {
            db.exec("INSERT INTO conversations(created_at, updated_at) VALUES (?, ?)", now, now)
            lastRowId(db).also { id -> scopeRows(scope).forEach { db.exec(INSERT_CONVERSATION_SCOPE, id, it.kind, it.refId, it.createdAtMs) } }
        }
    }

    override suspend fun insertTurn(conversationId: Long, question: String, partialCoverage: Boolean, now: Long): ChatTurn =
        write(listChanged = true) { db ->
            db.transaction {
                val ordinal = db.queryOne(
                    "SELECT COALESCE(MAX(ordinal) + 1, 0) FROM turns WHERE conversation_id = ?", conversationId,
                ) { it.getLong(0).toInt() } ?: 0
                db.exec(
                    "INSERT INTO turns(conversation_id, ordinal, question, state, partial_coverage, created_at) VALUES (?, ?, ?, ?, ?, ?)",
                    conversationId, ordinal, question, TurnState.RETRIEVING.sql, partialCoverage, now,
                )
                val id = lastRowId(db)
                db.exec("UPDATE conversations SET updated_at = ? WHERE id = ?", now, conversationId)
                ChatTurn(
                    id = id, conversationId = conversationId, ordinal = ordinal, question = question, answer = "",
                    state = TurnState.RETRIEVING, failure = null, refusal = false, partialCoverage = partialCoverage,
                    promptBlocks = 0, sources = emptyList(), createdAt = now, finishedAt = null,
                )
            }
        }

    override suspend fun updateTurn(turnId: Long, update: TurnUpdate) {
        val columns = buildList<Pair<String, Any>> {
            update.state?.let { add("state" to it.sql) }
            update.answer?.let { add("answer" to it) }
            update.failure?.let { add("failure" to it.sql) }
            update.refusal?.let { add("refusal" to it) }
            update.promptBlocks?.let { add("prompt_blocks" to it) }
            update.finishedAt?.let { add("finished_at" to it) }
        }
        if (columns.isEmpty()) return
        write { db ->
            db.exec(
                "UPDATE turns SET ${columns.joinToString { "${it.first} = ?" }} WHERE id = ?",
                *columns.map { it.second }.toTypedArray(), turnId,
            )
        }
    }

    override suspend fun replaceSources(turnId: Long, sources: List<AnswerSource>) = write { db ->
        db.transaction {
            db.exec("DELETE FROM sources WHERE turn_id = ?", turnId)
            sources.forEach { s ->
                db.exec(
                    "INSERT INTO sources(turn_id, position, prompt_number, document_id, chunk_id, first_ordinal, last_ordinal, " +
                        "document_name, page) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    turnId, s.position, s.promptNumber, s.documentId, s.chunkId, s.firstOrdinal, s.lastOrdinal, s.documentName, s.page,
                )
            }
        }
    }

    override suspend fun resetForRetry(turnId: Long) = write { db ->
        db.transaction {
            db.exec(
                "UPDATE turns SET state = ?, answer = '', failure = NULL, refusal = 0, prompt_blocks = 0, finished_at = NULL WHERE id = ?",
                TurnState.RETRIEVING.sql, turnId,
            )
            db.exec("DELETE FROM sources WHERE turn_id = ?", turnId)
        }
    }

    override suspend fun markInterrupted(now: Long): Int = write { db ->
        db.exec(
            "UPDATE turns SET state = ?, finished_at = ? WHERE state IN (?, ?)",
            TurnState.INTERRUPTED.sql, now, TurnState.RETRIEVING.sql, TurnState.GENERATING.sql,
        )
        db.changes()
    }

    override suspend fun deleteConversation(id: Long) = write(listChanged = true) { db ->
        db.exec("DELETE FROM conversations WHERE id = ?", id)
    }

    override suspend fun deleteAll() = write(listChanged = true) { db ->
        db.transaction {
            db.exec("DELETE FROM conversations")
            db.exec("DELETE FROM last_scope")
        }
    }

    override suspend fun conversationScope(id: Long): ChatScope = read { db ->
        readScope(db, "SELECT kind, ref_id, ref_created_at_ms FROM conversation_scope WHERE conversation_id = ?", id)
    } ?: ChatScope.WholeLibrary

    override suspend fun setConversationScope(id: Long, scope: ChatScope) = write { db ->
        db.transaction {
            db.exec("DELETE FROM conversation_scope WHERE conversation_id = ?", id)
            scopeRows(scope).forEach { db.exec(INSERT_CONVERSATION_SCOPE, id, it.kind, it.refId, it.createdAtMs) }
        }
    }

    override suspend fun lastScope(): ChatScope = read { db ->
        readScope(db, "SELECT kind, ref_id, ref_created_at_ms FROM last_scope")
    } ?: ChatScope.WholeLibrary

    override suspend fun setLastScope(scope: ChatScope) = write { db ->
        db.transaction {
            db.exec("DELETE FROM last_scope")
            scopeRows(scope).forEach {
                db.exec("INSERT INTO last_scope(kind, ref_id, ref_created_at_ms) VALUES (?, ?, ?)", it.kind, it.refId, it.createdAtMs)
            }
        }
    }

    override suspend fun conversationCount(): Int = read { db ->
        db.queryOne("SELECT count(DISTINCT conversation_id) FROM turns") { it.getLong(0) }?.toInt() ?: 0
    } ?: throw ChatStorageException("chat storage unavailable")

    override suspend fun conversationExists(id: Long): Boolean = read { db ->
        db.queryOne("SELECT 1 FROM conversations WHERE id = ?", id) { true } ?: false
    } ?: throw ChatStorageException("chat storage unavailable")

    private fun turns(db: RagConnection, conversationId: Long): List<ChatTurn> {
        val sources = db.query(
            "SELECT s.turn_id, s.position, s.prompt_number, s.document_id, s.chunk_id, s.first_ordinal, s.last_ordinal, " +
                "s.document_name, s.page FROM sources s JOIN turns t ON t.id = s.turn_id WHERE t.conversation_id = ? " +
                "ORDER BY s.turn_id, s.position",
            conversationId,
        ) {
            it.getLong(0) to AnswerSource(
                position = it.getLong(1).toInt(), promptNumber = it.getLong(2).toInt(), documentId = it.getLong(3),
                chunkId = it.getLong(4), firstOrdinal = it.getLong(5).toInt(), lastOrdinal = it.getLong(6).toInt(),
                documentName = it.getText(7), page = if (it.isNull(8)) null else it.getLong(8).toInt(),
            )
        }.groupBy({ it.first }, { it.second })
        return db.query(
            "SELECT id, ordinal, question, answer, state, failure, refusal, partial_coverage, prompt_blocks, created_at, finished_at " +
                "FROM turns WHERE conversation_id = ? ORDER BY ordinal",
            conversationId,
        ) {
            val id = it.getLong(0)
            ChatTurn(
                id = id, conversationId = conversationId, ordinal = it.getLong(1).toInt(), question = it.getText(2),
                answer = it.getText(3), state = turnState(it.getText(4)),
                failure = if (it.isNull(5)) null else turnFailure(it.getText(5)),
                refusal = it.getLong(6) != 0L, partialCoverage = it.getLong(7) != 0L, promptBlocks = it.getLong(8).toInt(),
                sources = sources[id].orEmpty(), createdAt = it.getLong(9), finishedAt = if (it.isNull(10)) null else it.getLong(10),
            )
        }
    }

    /** Null when the storage is unavailable: readers show nothing, the availability says why (C17). */
    private suspend fun <T> read(block: (RagConnection) -> T): T? = withContext(dispatcher) {
        if (ensureOpen() != ChatStorageState.OK) return@withContext null
        block(checkNotNull(connection))
    }

    /**
     * Throws [ChatStorageException] when the storage is unavailable or the write fails (contract A15).
     * [listChanged] marks the writes that change the history list.
     */
    private suspend fun <T> write(listChanged: Boolean = false, block: (RagConnection) -> T): T = withContext(dispatcher) {
        if (ensureOpen() != ChatStorageState.OK) throw ChatStorageException("chat storage unavailable")
        val result = try {
            block(checkNotNull(connection))
        } catch (e: Exception) {
            logger.w(TAG, e) { "chat db write failed: ${e.javaClass.simpleName}" }
            throw ChatStorageException("chat db write failed", e)
        }
        changes.value += 1
        if (listChanged) listChanges.value += 1
        result
    }

    private fun ensureOpen(): ChatStorageState {
        state.value?.let { return it }
        val started = System.nanoTime()
        val result = try {
            openConnection()
        } catch (e: Exception) {
            logger.w(TAG, e) { "chat db unavailable: ${e.javaClass.simpleName}" }
            ChatStorageState.UNAVAILABLE
        }
        state.value = result
        logger.i(TAG) { "chat db open: $result in ${(System.nanoTime() - started) / 1_000_000} ms" }
        return result
    }

    private fun openConnection(): ChatStorageState {
        dbFile.parentFile?.mkdirs()
        val db = RagConnection.open(dbFile, extensionPath = null)
        val result = try {
            prepare(db)
        } catch (e: Exception) {
            db.close()
            throw e as? RagOpenException ?: RagOpenException("prepare: ${e.message}", e)
        }
        if (result == ChatStorageState.OK) connection = db else db.close()
        return result
    }

    /** The version is read before anything that may write, so a newer file stays as it was. */
    private fun prepare(db: RagConnection): ChatStorageState {
        val tables = db.tableNames()
        if (tables.isEmpty()) {
            val statements = schema()
            db.enableWal()
            db.transaction {
                statements.forEach { db.exec(it) }
                db.exec("INSERT INTO chat_meta(key, value) VALUES (?, ?)", ChatSchema.VERSION_KEY, ChatSchema.VERSION.toString())
            }
            return ChatStorageState.OK
        }
        val version = if (META_TABLE in tables) {
            db.queryOne("SELECT value FROM chat_meta WHERE key = ?", ChatSchema.VERSION_KEY) { it.getText(0).toIntOrNull() }
        } else {
            null
        }
        val steps = migrations.filter { version != null && it.from >= version }.sortedBy { it.from }
        // A newer file is never reachable: there is no step down, and an empty range would match no steps.
        val reachable = version != null && version <= ChatSchema.VERSION &&
            (version == ChatSchema.VERSION || steps.map { it.from } == (version until ChatSchema.VERSION).toList())
        if (!reachable) return ChatStorageState.UNAVAILABLE
        db.enableWal()
        if (version != ChatSchema.VERSION) {
            db.transaction {
                steps.forEach { step -> step.statements.forEach { db.exec(it) } }
                db.exec("UPDATE chat_meta SET value = ? WHERE key = ?", ChatSchema.VERSION.toString(), ChatSchema.VERSION_KEY)
            }
        }
        schema().forEach { db.exec(it) }
        return ChatStorageState.OK
    }

    private fun lastRowId(db: RagConnection): Long = checkNotNull(db.queryOne("SELECT last_insert_rowid()") { it.getLong(0) })

    /** One row of `conversation_scope` or `last_scope` (research R7): «General» has ref_id 0 and no time. */
    private class ScopeRow(val kind: String, val refId: Long, val createdAtMs: Long?)

    private fun readScope(db: RagConnection, sql: String, vararg args: Any?): ChatScope =
        scopeOf(db.query(sql, *args) { ScopeRow(it.getText(0), it.getLong(1), if (it.isNull(2)) null else it.getLong(2)) })

    private fun scopeRows(scope: ChatScope): List<ScopeRow> = when (scope) {
        ChatScope.WholeLibrary -> emptyList()
        is ChatScope.Some -> buildList {
            scope.folders.forEach { add(ScopeRow(KIND_FOLDER, it.id, it.createdAtMs)) }
            if (scope.general) add(ScopeRow(KIND_GENERAL, 0, null))
            scope.documents.forEach { add(ScopeRow(KIND_DOCUMENT, it.id, it.createdAtMs)) }
        }
    }

    private fun scopeOf(rows: List<ScopeRow>): ChatScope {
        if (rows.isEmpty()) return ChatScope.WholeLibrary
        return ChatScope.Some(
            folders = rows.filter { it.kind == KIND_FOLDER }.map { FolderRef(it.refId, it.createdAtMs ?: 0) }.toSet(),
            general = rows.any { it.kind == KIND_GENERAL },
            documents = rows.filter { it.kind == KIND_DOCUMENT }.map { DocumentRef(it.refId, it.createdAtMs ?: 0) }.toSet(),
        )
    }

    private companion object {
        const val TAG = "MicroRAG"
        const val META_TABLE = "chat_meta"

        const val KIND_FOLDER = "folder"
        const val KIND_GENERAL = "general"
        const val KIND_DOCUMENT = "document"
        const val INSERT_CONVERSATION_SCOPE =
            "INSERT INTO conversation_scope(conversation_id, kind, ref_id, ref_created_at_ms) VALUES (?, ?, ?, ?)"

        /** Only conversations with at least one question are listed or opened as the last one (F13 Q1). */
        const val HAS_QUESTIONS = "EXISTS (SELECT 1 FROM turns t WHERE t.conversation_id = c.id)"

        val TurnState.sql: String get() = name.lowercase()
        val TurnFailure.sql: String get() = name.lowercase()

        fun turnState(value: String): TurnState = TurnState.valueOf(value.uppercase())
        fun turnFailure(value: String): TurnFailure = TurnFailure.valueOf(value.uppercase())
    }
}
