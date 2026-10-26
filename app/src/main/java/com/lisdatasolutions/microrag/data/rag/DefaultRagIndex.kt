package com.lisdatasolutions.microrag.data.rag

import com.lisdatasolutions.microrag.core.logging.AppLogger
import com.lisdatasolutions.microrag.domain.rag.ChunkInput
import com.lisdatasolutions.microrag.domain.rag.ChunkView
import com.lisdatasolutions.microrag.domain.rag.CompatibilityDecision
import com.lisdatasolutions.microrag.domain.rag.DocumentStatus
import com.lisdatasolutions.microrag.domain.rag.DuplicateDocumentException
import com.lisdatasolutions.microrag.domain.rag.IndexCompatibility
import com.lisdatasolutions.microrag.domain.rag.IndexCompatibilityChecker
import com.lisdatasolutions.microrag.domain.rag.IndexCounts
import com.lisdatasolutions.microrag.domain.rag.IndexUnavailableException
import com.lisdatasolutions.microrag.domain.rag.NewDocument
import com.lisdatasolutions.microrag.domain.rag.RagIndex
import com.lisdatasolutions.microrag.domain.rag.RagIndexConfig
import com.lisdatasolutions.microrag.domain.rag.RagMetaKeys
import com.lisdatasolutions.microrag.domain.rag.ScoredChunk
import com.lisdatasolutions.microrag.domain.rag.StoredDocument
import com.lisdatasolutions.microrag.rag.runtime.EmbeddingMath
import com.lisdatasolutions.microrag.rag.text.SpanishText
import java.io.File
import kotlin.math.abs
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/**
 * [RagIndex] on one bundled SQLite connection with SQLite-Vector (F04 contracts/rag-index-api.md).
 * Everything runs on [dispatcher], a single thread (constitution IV), so operations never overlap.
 * Logs carry ids, counts and times only, never document text, queries or names (rule A10).
 */
class DefaultRagIndex(
    private val dbFile: File,
    private val schema: () -> List<String>,
    private val extensionPath: String,
    private val config: RagIndexConfig,
    private val dispatcher: CoroutineDispatcher,
    private val logger: AppLogger,
    private val migrations: List<RagMigration> = RagMigrations.all,
    private val clock: () -> Long = System::currentTimeMillis,
) : RagIndex {

    private val state = MutableStateFlow<IndexCompatibility>(IndexCompatibility.Closed)
    override val compatibility: StateFlow<IndexCompatibility> = state.asStateFlow()

    /** Open only while the index is compatible; touched on [dispatcher] only. */
    private var connection: RagConnection? = null

    override suspend fun open(): IndexCompatibility = withContext(dispatcher) { ensureOpen() }

    override suspend fun close() {
        withContext(dispatcher) {
            connection?.close()
            connection = null
            state.value = IndexCompatibility.Closed
        }
    }

    override suspend fun meta(): Map<String, String> = withIndex { readMeta(it) }

    override suspend fun addDocument(document: NewDocument): Long = withIndex { db ->
        db.queryOne("SELECT id FROM documents WHERE content_hash = ?", document.contentHash) { it.getLong(0) }
            ?.let { throw DuplicateDocumentException(it) }
        val now = clock()
        db.exec(
            """
            INSERT INTO documents(source, uri, display_name, mime_type, content_hash, size_bytes, page_count,
                local_path, created_at_ms, updated_at_ms)
            VALUES ('user', ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            document.uri, document.displayName, document.mimeType, document.contentHash, document.sizeBytes,
            document.pageCount, document.localPath, now, now,
        )
        val id = checkNotNull(db.queryOne("SELECT last_insert_rowid()") { it.getLong(0) })
        logger.i(TAG) { "rag document add id=$id" }
        id
    }

    override suspend fun document(id: Long): StoredDocument? = withIndex { db ->
        db.queryOne(
            """
            SELECT id, display_name, content_hash, mime_type, uri, status, chunk_count, error, included,
                size_bytes, page_count, local_path, created_at_ms, updated_at_ms
            FROM documents WHERE id = ?
            """.trimIndent(),
            id,
        ) { s ->
            StoredDocument(
                id = s.getLong(0),
                displayName = s.getText(1),
                contentHash = s.getText(2),
                mimeType = s.textOrNull(3),
                uri = s.textOrNull(4),
                status = DocumentStatus.fromSql(s.getText(5)),
                chunkCount = s.longOrNull(6)?.toInt(),
                error = s.textOrNull(7),
                included = s.getLong(8) == 1L,
                sizeBytes = s.longOrNull(9),
                pageCount = s.longOrNull(10)?.toInt(),
                localPath = s.textOrNull(11),
                createdAtMs = s.getLong(12),
                updatedAtMs = s.getLong(13),
            )
        }
    }

    override suspend fun updateStatus(id: Long, status: DocumentStatus, error: String?, chunkCount: Int?) =
        withIndex { db ->
            db.exec(
                "UPDATE documents SET status = ?, error = ?, chunk_count = COALESCE(?, chunk_count), updated_at_ms = ? WHERE id = ?",
                status.sqlValue, error, chunkCount, clock(), id,
            )
            logger.i(TAG) { "rag document status id=$id ${status.sqlValue}" }
        }

    override suspend fun setIncluded(id: Long, included: Boolean) = withIndex { db ->
        db.exec("UPDATE documents SET included = ?, updated_at_ms = ? WHERE id = ?", included, clock(), id)
    }

    override suspend fun deleteDocument(id: Long) = withIndex { db ->
        // ON DELETE CASCADE removes the chunks; their chunks_ad trigger cleans the FTS index.
        db.exec("DELETE FROM documents WHERE id = ?", id)
        logger.i(TAG) { "rag document delete id=$id" }
    }

    override suspend fun insertChunks(documentId: Long, chunks: List<ChunkInput>): Int {
        require(chunks.map { it.ordinal }.toSet().size == chunks.size) { "Repeated ordinals in the batch" }
        chunks.forEach { requireUnitVector(it.embedding) }
        return withIndex { db ->
            val name = db.queryOne("SELECT display_name FROM documents WHERE id = ?", documentId) { it.getText(0) }
            requireNotNull(name) { "Unknown document $documentId" }
            if (chunks.isEmpty()) return@withIndex 0
            val started = System.nanoTime()
            val inserted = db.transaction {
                val before = chunkCount(db, documentId)
                chunks.forEach { chunk ->
                    val title = listOf(name, chunk.headingPath).filter { !it.isNullOrBlank() }.joinToString(" › ")
                    db.exec(
                        """
                        INSERT OR IGNORE INTO chunks(document_id, ordinal, heading_path, page, content, title_norm,
                            body_norm, token_estimate, embedding)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, vector_as_f32(?))
                        """.trimIndent(),
                        documentId, chunk.ordinal, chunk.headingPath, chunk.page, chunk.content,
                        SpanishText.normalize(title), SpanishText.normalize(chunk.content), chunk.tokenEstimate,
                        chunk.embedding.toLeBlob(),
                    )
                }
                db.exec("UPDATE rag_meta SET value = '1' WHERE key = ?", RagMetaKeys.QUANT_DIRTY)
                chunkCount(db, documentId) - before
            }
            logger.i(TAG) { "rag chunks doc=$documentId inserted=$inserted of ${chunks.size} in ${elapsedMs(started)} ms" }
            inserted
        }
    }

    override suspend fun vectorSearch(query: FloatArray, k: Int): List<ScoredChunk> {
        requireUnitVector(query)
        require(k > 0) { "k must be positive" }
        return withIndex { db ->
            db.query(
                "SELECT rowid, distance FROM vector_full_scan('chunks', 'embedding', ?, ?) ORDER BY distance",
                query.toLeBlob(), k,
            ) { ScoredChunk(it.getLong(0), it.getDouble(1)) }
        }
    }

    override suspend fun textSearch(userQuery: String, k: Int): List<Long> {
        require(k > 0) { "k must be positive" }
        val ftsQuery = SpanishText.ftsQuery(userQuery)
        return withIndex { db ->
            if (ftsQuery.isEmpty()) {
                emptyList()
            } else {
                db.query(
                    "SELECT rowid FROM chunks_fts WHERE chunks_fts MATCH ? ORDER BY bm25(chunks_fts, 3.0, 1.0) LIMIT ?",
                    ftsQuery, k,
                ) { it.getLong(0) }
            }
        }
    }

    override suspend fun loadChunks(ids: Collection<Long>): Map<Long, ChunkView> = withIndex { db ->
        ids.distinct().chunked(LOAD_BLOCK).flatMap { block -> loadBlock(db, block) }.associateBy { it.id }
    }

    override suspend fun neighbor(documentId: Long, ordinal: Int): ChunkView? = withIndex { db ->
        db.queryOne("SELECT id FROM chunks WHERE document_id = ? AND ordinal = ?", documentId, ordinal) { it.getLong(0) }
            ?.let { loadBlock(db, listOf(it)).singleOrNull() }
    }

    override suspend fun counts(): IndexCounts = withIndex { db ->
        IndexCounts(
            documents = count(db, "SELECT count(*) FROM documents"),
            chunks = count(db, "SELECT count(*) FROM chunks"),
            // With external content, count(*) on chunks_fts would read chunks; docsize is the index itself.
            ftsRows = count(db, "SELECT count(*) FROM chunks_fts_docsize"),
        )
    }

    /** Runs [block] on the open, compatible connection; otherwise throws without touching the file (rule A2). */
    private suspend fun <T> withIndex(block: (RagConnection) -> T): T = withContext(dispatcher) {
        val current = ensureOpen()
        val db = connection
        if (current != IndexCompatibility.Compatible || db == null) throw IndexUnavailableException(current)
        block(db)
    }

    private fun ensureOpen(): IndexCompatibility {
        if (state.value != IndexCompatibility.Closed) return state.value
        val started = System.nanoTime()
        val result = try {
            openConnection()
        } catch (e: RagOpenException) {
            logger.w(TAG, e) { "rag index unreadable: ${e.reason}" }
            IndexCompatibility.Unreadable(e.reason)
        }
        state.value = result
        logger.i(TAG) { "rag index open: $result in ${elapsedMs(started)} ms" }
        return result
    }

    private fun openConnection(): IndexCompatibility {
        dbFile.parentFile?.mkdirs()
        val db = RagConnection.open(dbFile, extensionPath)
        val result = try {
            prepare(db)
        } catch (e: Exception) {
            db.close()
            throw e as? RagOpenException ?: RagOpenException("prepare: ${e.message}", e)
        }
        if (result == IndexCompatibility.Compatible) connection = db else db.close()
        return result
    }

    /**
     * Pragmas, `rag_meta` validation, schema and `vector_init`. The metadata is checked before anything
     * that may write (journal mode, schema), so an incompatible file is left exactly as it was.
     */
    private fun prepare(db: RagConnection): IndexCompatibility {
        val tables = db.tableNames()
        if (tables.isEmpty()) {
            createIndex(db)
        } else {
            val meta = if (META_TABLE in tables) readMeta(db) else emptyMap()
            when (val decision = IndexCompatibilityChecker.check(config, meta, migrations.map { it.from }.toSet())) {
                CompatibilityDecision.Compatible -> applySchema(db)
                is CompatibilityDecision.NeedsMigration -> migrate(db, decision.from)?.let { return it }
                is CompatibilityDecision.Incompatible -> return decision.toState()
            }
        }
        try {
            db.initVector(config.dims)
        } catch (e: Exception) {
            throw RagOpenException("vector_init: ${e.message}", e)
        }
        return IndexCompatibility.Compatible
    }

    /** A file without tables becomes a new index: schema and `rag_meta` in one transaction. */
    private fun createIndex(db: RagConnection) {
        val statements = loadSchema()
        db.enableWal()
        db.transaction {
            statements.forEach { db.exec(it) }
            config.toMeta().forEach { (key, value) -> db.exec("INSERT INTO rag_meta(key, value) VALUES (?, ?)", key, value) }
        }
    }

    /** `IF NOT EXISTS` statements: a no-op on a complete file, as the report runs them on every open. */
    private fun applySchema(db: RagConnection) {
        val statements = loadSchema()
        db.enableWal()
        statements.forEach { db.exec(it) }
    }

    /** Returns null once migrated and compatible, or the state to publish when the file stays as it was. */
    private fun migrate(db: RagConnection, from: Int): IndexCompatibility? {
        val steps = migrations.filter { it.from >= from }.sortedBy { it.from }
        val failed = runCatching {
            db.transaction {
                steps.forEach { step -> step.statements.forEach { db.exec(it) } }
                db.exec(
                    "UPDATE rag_meta SET value = ? WHERE key = ?",
                    config.schemaVersion.toString(), RagMetaKeys.SCHEMA_VERSION,
                )
            }
        }.exceptionOrNull()
        if (failed != null) {
            logger.w(TAG, failed) { "rag migration from schema $from failed" }
            return IndexCompatibility.Incompatible(RagMetaKeys.SCHEMA_VERSION, config.schemaVersion.toString(), from.toString())
        }
        logger.i(TAG) { "rag migrated schema $from -> ${config.schemaVersion}" }
        val decision = IndexCompatibilityChecker.check(config, readMeta(db), emptySet())
        if (decision != CompatibilityDecision.Compatible) {
            return (decision as? CompatibilityDecision.Incompatible)?.toState()
                ?: IndexCompatibility.Incompatible(RagMetaKeys.SCHEMA_VERSION, config.schemaVersion.toString(), from.toString())
        }
        applySchema(db)
        return null
    }

    private fun loadSchema(): List<String> = try {
        schema()
    } catch (e: Exception) {
        throw RagOpenException("schema: ${e.message}", e)
    }

    private fun readMeta(db: RagConnection): Map<String, String> =
        db.query("SELECT key, value FROM rag_meta") { it.getText(0) to it.getText(1) }.toMap()

    private fun loadBlock(db: RagConnection, ids: List<Long>): List<ChunkView> =
        db.query(
            """
            SELECT c.id, c.document_id, c.ordinal, c.heading_path, c.page, c.content, c.token_estimate, d.display_name
            FROM chunks c JOIN documents d ON d.id = c.document_id
            WHERE c.id IN (${ids.joinToString(",") { "?" }})
            """.trimIndent(),
            *ids.toTypedArray(),
        ) { s ->
            ChunkView(
                id = s.getLong(0),
                documentId = s.getLong(1),
                ordinal = s.getLong(2).toInt(),
                headingPath = s.textOrNull(3),
                page = s.longOrNull(4)?.toInt(),
                content = s.getText(5),
                tokenEstimate = s.getLong(6).toInt(),
                documentName = s.getText(7),
            )
        }

    private fun chunkCount(db: RagConnection, documentId: Long): Int =
        count(db, "SELECT count(*) FROM chunks WHERE document_id = ?", documentId)

    private fun count(db: RagConnection, sql: String, vararg args: Any?): Int =
        db.queryOne(sql, *args) { it.getLong(0).toInt() } ?: 0

    /** `vector_init` declares `normalized=1`, so distances are only right for unit vectors. */
    private fun requireUnitVector(vector: FloatArray) {
        require(vector.size == config.dims) { "Vector has ${vector.size} components, expected ${config.dims}" }
        val norm = EmbeddingMath.l2Norm(vector)
        require(abs(norm - 1.0) <= UNIT_NORM_TOLERANCE) { "Vector is not unit length (norm $norm)" }
    }

    private fun CompatibilityDecision.Incompatible.toState() = IndexCompatibility.Incompatible(field, expected, found)

    private fun elapsedMs(startedNanos: Long): Long = (System.nanoTime() - startedNanos) / 1_000_000

    private companion object {
        const val TAG = "MicroRAG"
        const val META_TABLE = "rag_meta"
        const val LOAD_BLOCK = 500
        const val UNIT_NORM_TOLERANCE = 1e-3
    }
}

private fun androidx.sqlite.SQLiteStatement.textOrNull(index: Int): String? = if (isNull(index)) null else getText(index)

private fun androidx.sqlite.SQLiteStatement.longOrNull(index: Int): Long? = if (isNull(index)) null else getLong(index)
