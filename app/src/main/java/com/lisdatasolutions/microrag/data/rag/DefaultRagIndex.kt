package com.lisdatasolutions.microrag.data.rag

import androidx.sqlite.SQLiteStatement
import com.lisdatasolutions.microrag.core.logging.AppLogger
import com.lisdatasolutions.microrag.domain.rag.ChunkInput
import com.lisdatasolutions.microrag.domain.rag.ChunkView
import com.lisdatasolutions.microrag.domain.rag.CompatibilityDecision
import com.lisdatasolutions.microrag.domain.rag.DocumentNames
import com.lisdatasolutions.microrag.domain.rag.DocumentTitle
import com.lisdatasolutions.microrag.domain.rag.DocumentStatus
import com.lisdatasolutions.microrag.domain.rag.DuplicateDocumentException
import com.lisdatasolutions.microrag.domain.rag.IndexCompatibility
import com.lisdatasolutions.microrag.domain.rag.IndexCompatibilityChecker
import com.lisdatasolutions.microrag.domain.rag.IndexCounts
import com.lisdatasolutions.microrag.domain.rag.LibrarySummary
import com.lisdatasolutions.microrag.domain.rag.IndexStorageFullException
import com.lisdatasolutions.microrag.domain.rag.IndexUnavailableException
import com.lisdatasolutions.microrag.domain.rag.LibraryEntry
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

    private val changes = MutableStateFlow(0L)
    override val documentChanges: StateFlow<Long> = changes.asStateFlow()

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
                local_path, created_at_ms, updated_at_ms, name_norm)
            VALUES ('user', ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            document.uri, document.displayName, document.mimeType, document.contentHash, document.sizeBytes,
            document.pageCount, document.localPath, now, now, DocumentNames.fold(document.displayName),
        )
        val id = checkNotNull(db.queryOne("SELECT last_insert_rowid()") { it.getLong(0) })
        logger.i(TAG) { "rag document add id=$id" }
        notifyChanged()
        id
    }

    override suspend fun document(id: Long): StoredDocument? = withIndex { db ->
        db.queryOne("SELECT $DOCUMENT_COLUMNS FROM documents WHERE id = ?", id, row = ::readDocument)
    }

    override suspend fun documents(statuses: Set<DocumentStatus>): List<StoredDocument> {
        if (statuses.isEmpty()) return emptyList()
        return withIndex { db ->
            db.query(
                "SELECT $DOCUMENT_COLUMNS FROM documents WHERE status IN (${statuses.joinToString(",") { "?" }}) " +
                    "ORDER BY created_at_ms, id",
                *statuses.map { it.sqlValue }.toTypedArray(),
                row = ::readDocument,
            )
        }
    }

    override suspend fun libraryPage(text: String, statuses: Set<DocumentStatus>, limit: Int): List<LibraryEntry> {
        if (statuses.isEmpty() || limit <= 0) return emptyList()
        val (where, args) = libraryFilter(text, statuses)
        return withIndex { db ->
            db.query(
                "SELECT $DOCUMENT_COLUMNS, (SELECT s.status FROM documents s WHERE s.replaces_id = documents.id LIMIT 1) " +
                    "FROM documents WHERE $where ORDER BY created_at_ms DESC, id DESC LIMIT ?",
                *args, limit,
            ) { row -> LibraryEntry(readDocument(row), row.textOrNull(SHADOW_STATUS_COLUMN)?.let(DocumentStatus::fromSql)) }
        }
    }

    override suspend fun libraryCount(text: String, statuses: Set<DocumentStatus>): Int {
        if (statuses.isEmpty()) return 0
        val (where, args) = libraryFilter(text, statuses)
        return withIndex { db -> count(db, "SELECT count(*) FROM documents WHERE $where", *args) }
    }

    override suspend fun documentIdByHash(contentHash: String): Long? = withIndex { db ->
        db.queryOne("SELECT id FROM documents WHERE content_hash = ?", contentHash) { it.getLong(0) }
    }

    override suspend fun updateStatus(id: Long, status: DocumentStatus, error: String?, chunkCount: Int?, skippedPages: Int?) =
        withIndex { db ->
            db.exec(
                "UPDATE documents SET status = ?, error = ?, chunk_count = COALESCE(?, chunk_count), " +
                    "skipped_pages = COALESCE(?, skipped_pages), updated_at_ms = ? WHERE id = ?",
                status.sqlValue, error, chunkCount, skippedPages, clock(), id,
            )
            logger.i(TAG) { "rag document status id=$id ${status.sqlValue}" }
            notifyChanged()
        }

    override suspend fun setIncluded(id: Long, included: Boolean) = withIndex { db ->
        db.exec("UPDATE documents SET included = ?, updated_at_ms = ? WHERE id = ?", included, clock(), id)
        notifyChanged()
    }

    override suspend fun deleteDocument(id: Long) = withIndex { db ->
        val started = System.nanoTime()
        db.transaction {
            // ON DELETE CASCADE removes the shadow and the chunks; their chunks_ad trigger cleans the FTS index.
            db.exec("DELETE FROM documents WHERE id = ?", id)
            markQuantDirty(db)
        }
        logger.i(TAG) { "rag document delete id=$id in ${elapsedMs(started)} ms" }
        notifyChanged()
    }

    override suspend fun addReindexShadow(originalId: Long): Long = withIndex { db ->
        val shadow = db.transaction {
            val original = db.queryOne("SELECT $DOCUMENT_COLUMNS FROM documents WHERE id = ?", originalId, row = ::readDocument)
            check(original != null && original.status == DocumentStatus.DONE && original.replacesId == null) {
                "Document $originalId is not a done document"
            }
            check(count(db, "SELECT count(*) FROM documents WHERE replaces_id = ?", originalId) == 0) {
                "Document $originalId is already being reindexed"
            }
            val now = clock()
            db.exec(
                """
                INSERT INTO documents(source, uri, display_name, mime_type, content_hash, size_bytes, page_count,
                    local_path, created_at_ms, updated_at_ms, name_norm, replaces_id)
                SELECT source, uri, display_name, mime_type, content_hash || ?, size_bytes, page_count,
                    local_path, ?, ?, name_norm, id
                FROM documents WHERE id = ?
                """.trimIndent(),
                SHADOW_HASH_SUFFIX, now, now, originalId,
            )
            checkNotNull(db.queryOne("SELECT last_insert_rowid()") { it.getLong(0) })
        }
        logger.i(TAG) { "rag reindex shadow id=$originalId shadow=$shadow" }
        notifyChanged()
        shadow
    }

    override suspend fun shadowOf(originalId: Long): StoredDocument? = withIndex { db ->
        db.queryOne("SELECT $DOCUMENT_COLUMNS FROM documents WHERE replaces_id = ? LIMIT 1", originalId, row = ::readDocument)
    }

    override suspend fun publishReindex(shadowId: Long): Int? = withIndex { db ->
        val started = System.nanoTime()
        var originalId = 0L
        val published = db.transaction {
            val shadow = db.queryOne("SELECT $DOCUMENT_COLUMNS FROM documents WHERE id = ?", shadowId, row = ::readDocument)
                ?: return@transaction null
            originalId = shadow.replacesId ?: return@transaction null
            val original = db.queryOne("SELECT status FROM documents WHERE id = ?", originalId) { it.getText(0) }
            if (original != DocumentStatus.DONE.sqlValue) return@transaction null
            // The chunks keep their ids, so FTS rows and vectors stay; chunks_au only fires on the text columns.
            db.exec("DELETE FROM chunks WHERE document_id = ?", originalId)
            db.exec("UPDATE chunks SET document_id = ? WHERE document_id = ?", originalId, shadowId)
            val moved = db.changes()
            db.exec(
                "UPDATE documents SET chunk_count = ?, skipped_pages = ?, error = NULL, updated_at_ms = ? WHERE id = ?",
                moved, shadow.skippedPages, clock(), originalId,
            )
            db.exec("DELETE FROM documents WHERE id = ?", shadowId)
            markQuantDirty(db)
            moved
        }
        if (published != null) {
            logger.i(TAG) { "rag reindex published id=$originalId chunks=$published in ${elapsedMs(started)} ms" }
            notifyChanged()
        }
        published
    }

    override suspend fun removeFromIndex(id: Long): Int? = withIndex { db ->
        val started = System.nanoTime()
        val removed = db.transaction {
            val status = db.queryOne("SELECT status FROM documents WHERE id = ? AND replaces_id IS NULL", id) { it.getText(0) }
            if (status != DocumentStatus.DONE.sqlValue) return@transaction null
            db.exec("DELETE FROM documents WHERE replaces_id = ?", id)
            db.exec("DELETE FROM chunks WHERE document_id = ?", id)
            val chunks = db.changes()
            db.exec(
                "UPDATE documents SET status = ?, chunk_count = NULL, error = NULL, updated_at_ms = ? WHERE id = ?",
                DocumentStatus.OUT_OF_INDEX.sqlValue, clock(), id,
            )
            markQuantDirty(db)
            chunks
        }
        if (removed != null) {
            logger.i(TAG) { "rag out-of-index id=$id chunks=$removed in ${elapsedMs(started)} ms" }
            notifyChanged()
        }
        removed
    }

    override suspend fun storedChunkCounts(ids: Collection<Long>): Map<Long, Int> = withIndex { db ->
        val counts = ids.distinct().chunked(LOAD_BLOCK).flatMap { block ->
            db.query(
                "SELECT document_id, count(*) FROM chunks WHERE document_id IN (${block.joinToString(",") { "?" }}) GROUP BY document_id",
                *block.toTypedArray(),
            ) { it.getLong(0) to it.getLong(1).toInt() }
        }.toMap()
        ids.associateWith { counts[it] ?: 0 }
    }

    override suspend fun storedChunkContents(documentId: Long): Map<Int, String> = withIndex { db ->
        db.query("SELECT ordinal, content FROM chunks WHERE document_id = ? ORDER BY ordinal", documentId) {
            it.getLong(0).toInt() to it.getText(1)
        }.toMap(LinkedHashMap())
    }

    override suspend fun deleteChunks(documentId: Long) = withIndex { db ->
        // The chunks_ad trigger removes their FTS rows.
        val deleted = db.transaction {
            val before = chunkCount(db, documentId)
            db.exec("DELETE FROM chunks WHERE document_id = ?", documentId)
            markQuantDirty(db)
            before
        }
        logger.i(TAG) { "rag chunks doc=$documentId deleted=$deleted" }
        notifyChanged()
    }

    override suspend fun insertChunks(documentId: Long, chunks: List<ChunkInput>): Int {
        require(chunks.map { it.ordinal }.toSet().size == chunks.size) { "Repeated ordinals in the batch" }
        chunks.forEach { requireUnitVector(it.embedding) }
        return withIndex { db ->
            val name = db.queryOne("SELECT display_name FROM documents WHERE id = ?", documentId) { it.getText(0) }
            requireNotNull(name) { "Unknown document $documentId" }
            if (chunks.isEmpty()) return@withIndex 0
            val title = DocumentTitle.of(name)
            val started = System.nanoTime()
            val inserted = storageFullAware {
                db.transaction {
                    val before = chunkCount(db, documentId)
                    chunks.forEach { chunk ->
                        val header = listOf(title, chunk.headingPath).filter { !it.isNullOrBlank() }.joinToString(" › ")
                        db.exec(
                            """
                            INSERT OR IGNORE INTO chunks(document_id, ordinal, heading_path, page, content, title_norm,
                                body_norm, token_estimate, embedding)
                            VALUES (?, ?, ?, ?, ?, ?, ?, ?, vector_as_f32(?))
                            """.trimIndent(),
                            documentId, chunk.ordinal, chunk.headingPath, chunk.page, chunk.content,
                            SpanishText.normalize(header), SpanishText.normalize(chunk.content), chunk.tokenEstimate,
                            chunk.embedding.toLeBlob(),
                        )
                    }
                    markQuantDirty(db)
                    chunkCount(db, documentId) - before
                }
            }
            logger.i(TAG) { "rag chunks doc=$documentId inserted=$inserted of ${chunks.size} in ${elapsedMs(started)} ms" }
            if (inserted > 0) notifyChanged()
            inserted
        }
    }

    override suspend fun vectorSearch(query: FloatArray, k: Int): List<ScoredChunk> {
        requireUnitVector(query)
        require(k > 0) { "k must be positive" }
        return withIndex { db ->
            db.query(VECTOR_SEARCH_SQL, query.toLeBlob(), k + hiddenChunks(db), k) { ScoredChunk(it.getLong(0), it.getDouble(1)) }
        }
    }

    override suspend fun textSearch(userQuery: String, k: Int): List<Long> {
        require(k > 0) { "k must be positive" }
        val ftsQuery = SpanishText.ftsQuery(userQuery)
        return withIndex { db ->
            if (ftsQuery.isEmpty()) {
                emptyList()
            } else {
                db.query(TEXT_SEARCH_SQL, ftsQuery, k + hiddenChunks(db), k) { it.getLong(0) }
            }
        }
    }

    override suspend fun loadChunks(ids: Collection<Long>, searchableOnly: Boolean): Map<Long, ChunkView> = withIndex { db ->
        ids.distinct().chunked(LOAD_BLOCK).flatMap { block -> loadBlock(db, block, searchableOnly) }.associateBy { it.id }
    }

    override suspend fun neighbor(documentId: Long, ordinal: Int, searchableOnly: Boolean): ChunkView? = withIndex { db ->
        db.queryOne("SELECT id FROM chunks WHERE document_id = ? AND ordinal = ?", documentId, ordinal) { it.getLong(0) }
            ?.let { loadBlock(db, listOf(it), searchableOnly).singleOrNull() }
    }

    override suspend fun counts(): IndexCounts = withIndex { db ->
        IndexCounts(
            documents = count(db, "SELECT count(*) FROM documents"),
            chunks = count(db, "SELECT count(*) FROM chunks"),
            // With external content, count(*) on chunks_fts would read chunks; docsize is the index itself.
            ftsRows = count(db, "SELECT count(*) FROM chunks_fts_docsize"),
        )
    }

    // Rule N1 of the runner keeps chunk_count of a done document equal to its stored chunks, and
    // publishReindex sets it to the chunks it moves, so chunks are never read (F09 rule H5).
    override suspend fun librarySummary(): LibrarySummary = withIndex { db ->
        db.queryOne(
            "SELECT count(*), COALESCE(SUM(CASE WHEN status = ? THEN chunk_count END), 0) FROM documents WHERE replaces_id IS NULL",
            DocumentStatus.DONE.sqlValue,
        ) { LibrarySummary(documents = it.getLong(0).toInt(), indexedChunks = it.getLong(1).toInt()) } ?: LibrarySummary(0, 0)
    }

    /** The INT8 index no longer matches the chunks (D-030); F14 rebuilds it. */
    private fun markQuantDirty(db: RagConnection) {
        db.exec("UPDATE rag_meta SET value = '1' WHERE key = ?", RagMetaKeys.QUANT_DIRTY)
    }

    /** WHERE clause and arguments shared by [libraryPage] and [libraryCount]: no shadows, [statuses], the name. */
    private fun libraryFilter(text: String, statuses: Set<DocumentStatus>): Pair<String, Array<Any?>> {
        val clauses = mutableListOf("replaces_id IS NULL", "status IN (${statuses.joinToString(",") { "?" }})")
        val args = statuses.map<DocumentStatus, Any?> { it.sqlValue }.toMutableList()
        if (text.isNotBlank()) {
            clauses += "name_norm LIKE ? ESCAPE '\\'"
            args += DocumentNames.likePattern(text)
        }
        return clauses.joinToString(" AND ") to args.toTypedArray()
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
            fillNameNorm(db)
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

    /**
     * Folds the names of rows without `name_norm`: those migrated from v2 and those the offline tools
     * write (F08 research R1). Idempotent, in one transaction; migrations can only run SQL, and SQLite
     * cannot strip accents.
     */
    private fun fillNameNorm(db: RagConnection) {
        val missing = db.query("SELECT id, display_name FROM documents WHERE name_norm IS NULL") { it.getLong(0) to it.getText(1) }
        if (missing.isEmpty()) return
        db.transaction {
            missing.forEach { (id, name) -> db.exec("UPDATE documents SET name_norm = ? WHERE id = ?", DocumentNames.fold(name), id) }
        }
        logger.i(TAG) { "rag name_norm filled=${missing.size}" }
    }

    private fun loadSchema(): List<String> = try {
        schema()
    } catch (e: Exception) {
        throw RagOpenException("schema: ${e.message}", e)
    }

    /** Called on [dispatcher] after a committed write that changes documents or chunks. */
    private fun notifyChanged() {
        changes.value += 1
    }

    private fun readDocument(s: SQLiteStatement) = StoredDocument(
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
        skippedPages = s.longOrNull(14)?.toInt(),
        replacesId = s.longOrNull(15),
    )

    private fun readMeta(db: RagConnection): Map<String, String> =
        db.query("SELECT key, value FROM rag_meta") { it.getText(0) to it.getText(1) }.toMap()

    private fun loadBlock(db: RagConnection, ids: List<Long>, searchableOnly: Boolean): List<ChunkView> =
        db.query(
            """
            SELECT c.id, c.document_id, c.ordinal, c.heading_path, c.page, c.content, c.token_estimate, d.display_name
            FROM chunks c JOIN documents d ON d.id = c.document_id
            WHERE c.id IN (${ids.joinToString(",") { "?" }})${if (searchableOnly) " AND $SEARCHABLE" else ""}
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

    /**
     * *m* of research R7: stored chunks that searches must not return, of documents not done or excluded.
     * The k + m best candidates always hold the k best searchable ones.
     */
    private fun hiddenChunks(db: RagConnection): Int = count(
        db,
        "SELECT COALESCE(SUM((SELECT count(*) FROM chunks c WHERE c.document_id = d.id)), 0) FROM documents d " +
            "WHERE d.status <> 'done' OR d.included = 0",
    )

    /** Runs a write and turns SQLite's «database or disk is full» into [IndexStorageFullException]. */
    private inline fun <T> storageFullAware(block: () -> T): T = try {
        block()
    } catch (e: IllegalArgumentException) {
        throw e
    } catch (e: RuntimeException) {
        if (e.message?.let(SQLITE_FULL::containsMatchIn) == true) throw IndexStorageFullException(e) else throw e
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

    internal companion object {
        /** Documents whose chunks searches may return (F07 rule N7); `d` is the documents table. */
        private const val SEARCHABLE = "d.status = 'done' AND d.included = 1"

        /**
         * The scan runs once, materialized, then is joined and filtered (research R7). Arguments: the
         * query blob, k + m candidates and k.
         */
        const val VECTOR_SEARCH_SQL = "WITH v AS MATERIALIZED (" +
            "SELECT rowid, distance FROM vector_full_scan('chunks', 'embedding', ?, ?)) " +
            "SELECT v.rowid, v.distance FROM v JOIN chunks c ON c.id = v.rowid JOIN documents d ON d.id = c.document_id " +
            "WHERE " + SEARCHABLE + " ORDER BY v.distance LIMIT ?"

        /** Arguments: the FTS query, k + m candidates and k. */
        const val TEXT_SEARCH_SQL = "WITH f AS MATERIALIZED (" +
            "SELECT rowid, bm25(chunks_fts, 3.0, 1.0) AS score FROM chunks_fts WHERE chunks_fts MATCH ? ORDER BY score LIMIT ?) " +
            "SELECT f.rowid FROM f JOIN chunks c ON c.id = f.rowid JOIN documents d ON d.id = c.document_id " +
            "WHERE " + SEARCHABLE + " ORDER BY f.score LIMIT ?"

        /** androidx.sqlite reports «Error code: 13, message: database or disk is full». */
        private val SQLITE_FULL = Regex("""Error code: 13\b""")

        private const val TAG = "MicroRAG"
        private const val META_TABLE = "rag_meta"

        /** A shadow's hash: unique while it lives, and never equal to a file's, so F06 duplicates ignore it. */
        private const val SHADOW_HASH_SUFFIX = "#reindex"

        /** [libraryPage] reads the shadow's status after the [DOCUMENT_COLUMNS]. */
        private const val SHADOW_STATUS_COLUMN = 16
        private const val LOAD_BLOCK = 500
        private const val UNIT_NORM_TOLERANCE = 1e-3
        private const val DOCUMENT_COLUMNS = "id, display_name, content_hash, mime_type, uri, status, chunk_count, error, " +
            "included, size_bytes, page_count, local_path, created_at_ms, updated_at_ms, skipped_pages, replaces_id"
    }
}

private fun androidx.sqlite.SQLiteStatement.textOrNull(index: Int): String? = if (isNull(index)) null else getText(index)

private fun androidx.sqlite.SQLiteStatement.longOrNull(index: Int): Long? = if (isNull(index)) null else getLong(index)
