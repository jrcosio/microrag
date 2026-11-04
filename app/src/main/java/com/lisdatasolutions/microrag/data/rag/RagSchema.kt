package com.lisdatasolutions.microrag.data.rag

import android.content.res.AssetManager

/** The shared `assets/schema.sql` (F04 data-model §1). */
object RagSchema {
    const val ASSET = "schema.sql"

    private val COMMENT_LINE = Regex("(?m)^\\s*--.*\\n?")

    /** Blocking asset read; call it on the database dispatcher. */
    fun load(assets: AssetManager): List<String> = split(assets.open(ASSET).bufferedReader().use { it.readText() })

    /**
     * Splits on `;` followed by a line break and the next `CREATE` or `PRAGMA`, as the report does, so
     * trigger bodies (`BEGIN … END;`) stay whole. Comment lines are dropped first.
     */
    fun split(sql: String): List<String> =
        sql.replace(COMMENT_LINE, "")
            .split(Regex(";\\s*\\n(?=CREATE|PRAGMA)"))
            .map { it.trim().removeSuffix(";") }
            .filter { it.isNotEmpty() }
}

/** Upgrades an index from schema version [from] to `from + 1`, inside the opening transaction. */
class RagMigration(val from: Int, val statements: List<String>)

/**
 * Schema migrations (F04 research R7). `schema.sql` always describes the latest version for new
 * indexes; each later version adds here the statements that take the previous one to it. On open,
 * an older file runs every migration from its version up, in one transaction, and is validated
 * again. A missing step, a newer file or a failing migration leave the file untouched and the
 * index incompatible.
 */
object RagMigrations {
    val all: List<RagMigration> = listOf(
        // F06 (D-038): PDF pages without text that the extraction skipped.
        RagMigration(from = 1, statements = listOf("ALTER TABLE documents ADD COLUMN skipped_pages INTEGER")),
        // F08 (research R1): the folded name the Library searches, the reindex shadow link (null default,
        // as ALTER TABLE requires for a REFERENCES column), the Library order and the shadow lookup.
        RagMigration(
            from = 2,
            statements = listOf(
                "ALTER TABLE documents ADD COLUMN name_norm TEXT",
                "ALTER TABLE documents ADD COLUMN replaces_id INTEGER REFERENCES documents(id) ON DELETE CASCADE",
                "CREATE INDEX IF NOT EXISTS idx_documents_created ON documents(created_at_ms, id)",
                "CREATE INDEX IF NOT EXISTS idx_documents_replaces ON documents(replaces_id)",
            ),
        ),
        // F17 (research R1): one-level folders; every document stays with no folder, «General».
        RagMigration(
            from = 3,
            statements = listOf(
                "CREATE TABLE IF NOT EXISTS folders (\n" +
                    "    id              INTEGER PRIMARY KEY AUTOINCREMENT,\n" +
                    "    name            TEXT NOT NULL,\n" +
                    "    name_norm       TEXT NOT NULL UNIQUE,\n" +
                    "    created_at_ms   INTEGER NOT NULL,\n" +
                    "    updated_at_ms   INTEGER NOT NULL\n" +
                    ")",
                "ALTER TABLE documents ADD COLUMN folder_id INTEGER REFERENCES folders(id) ON DELETE SET NULL",
                "CREATE INDEX IF NOT EXISTS idx_documents_folder ON documents(folder_id, created_at_ms, id)",
            ),
        ),
    )
}
