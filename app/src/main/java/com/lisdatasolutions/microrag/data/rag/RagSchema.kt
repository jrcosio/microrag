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
    )
}
