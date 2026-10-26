package com.lisdatasolutions.microrag.data.rag

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteStatement
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.driver.bundled.SQLITE_OPEN_CREATE
import androidx.sqlite.driver.bundled.SQLITE_OPEN_READWRITE
import java.io.File

/** The index file could not be opened as a RAG index; [reason] names the stage and the SQLite error. */
class RagOpenException(val reason: String, cause: Throwable? = null) : Exception(reason, cause)

/**
 * One bundled SQLite connection with SQLite-Vector loaded (F04 research R8). Not thread-safe: the
 * owner uses it only from the database dispatcher.
 */
class RagConnection private constructor(private val connection: SQLiteConnection) : AutoCloseable {

    /** Persistent journal mode; only set on files this app owns (new or validated), as it rewrites the header. */
    fun enableWal() {
        queryOne("PRAGMA journal_mode = WAL") { it.getText(0) }
    }

    /** Vector search configuration of this connection; SQLite-Vector needs it on every open. */
    fun initVector(dims: Int) {
        queryOne("SELECT vector_init('chunks', 'embedding', 'dimension=$dims,type=FLOAT32,distance=COSINE,normalized=1')") {}
    }

    /** Tables of the file, leaving out SQLite's own and [VECTOR_EXTENSION_TABLE]. */
    fun tableNames(): Set<String> =
        query(
            "SELECT name FROM sqlite_master WHERE type = 'table' AND name NOT LIKE 'sqlite_%' AND name <> ?",
            VECTOR_EXTENSION_TABLE,
        ) { it.getText(0) }.toSet()

    fun exec(sql: String, vararg args: Any?) {
        connection.prepare(sql).use { statement ->
            statement.bindAll(args)
            while (statement.step()) Unit
        }
    }

    fun <T> query(sql: String, vararg args: Any?, row: (SQLiteStatement) -> T): List<T> =
        connection.prepare(sql).use { statement ->
            statement.bindAll(args)
            buildList { while (statement.step()) add(row(statement)) }
        }

    fun <T> queryOne(sql: String, vararg args: Any?, row: (SQLiteStatement) -> T): T? =
        connection.prepare(sql).use { statement ->
            statement.bindAll(args)
            if (statement.step()) row(statement) else null
        }

    /** Rows changed by the last INSERT, UPDATE or DELETE of this connection, without trigger rows. */
    fun changes(): Int = queryOne("SELECT changes()") { it.getLong(0).toInt() } ?: 0

    /** `BEGIN IMMEDIATE` … `COMMIT`; any error rolls back and is rethrown. */
    fun <T> transaction(block: () -> T): T {
        exec("BEGIN IMMEDIATE")
        val result = try {
            block()
        } catch (t: Throwable) {
            runCatching { exec("ROLLBACK") }.onFailure { t.addSuppressed(it) }
            throw t
        }
        exec("COMMIT")
        return result
    }

    override fun close() = connection.close()

    companion object {
        const val EXTENSION_ENTRY_POINT = "sqlite3_vector_init"

        /** SQLite-Vector 1.1.2 creates this settings table in every file it is loaded into (F04 research R3). */
        const val VECTOR_EXTENSION_TABLE = "_sqliteai_vector"

        /** Opens [dbFile] read-write, creating it, with the extension and the per-connection pragmas. */
        fun open(dbFile: File, extensionPath: String): RagConnection {
            if (!File(extensionPath).isFile) throw RagOpenException("extension: vector.so not found")
            val driver = BundledSQLiteDriver().apply { addExtension(extensionPath, EXTENSION_ENTRY_POINT) }
            val connection = try {
                driver.open(dbFile.path, SQLITE_OPEN_READWRITE or SQLITE_OPEN_CREATE)
            } catch (e: Exception) {
                throw RagOpenException("open: ${e.message}", e)
            }
            return RagConnection(connection).apply {
                try {
                    exec("PRAGMA synchronous = NORMAL")
                    exec("PRAGMA temp_store = MEMORY")
                    exec("PRAGMA foreign_keys = ON")
                    // First read of the file: fails here when it is not a database.
                    tableNames()
                } catch (e: Exception) {
                    close()
                    throw RagOpenException("read: ${e.message}", e)
                }
            }
        }

        private fun SQLiteStatement.bindAll(args: Array<out Any?>) {
            args.forEachIndexed { i, arg ->
                val index = i + 1
                when (arg) {
                    null -> bindNull(index)
                    is String -> bindText(index, arg)
                    is Long -> bindLong(index, arg)
                    is Int -> bindLong(index, arg.toLong())
                    is Boolean -> bindLong(index, if (arg) 1L else 0L)
                    is Double -> bindDouble(index, arg)
                    is ByteArray -> bindBlob(index, arg)
                    else -> throw IllegalArgumentException("Unsupported SQL argument type ${arg::class.simpleName}")
                }
            }
        }
    }
}
