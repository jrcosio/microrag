package com.lisdatasolutions.microrag.data.chat

import android.content.res.AssetManager
import com.lisdatasolutions.microrag.data.rag.RagSchema

/** `assets/chat_schema.sql` (F11 data-model §3), split as the index schema. */
object ChatSchema {
    const val ASSET = "chat_schema.sql"
    /** 2 since F17: `conversation_scope` and `last_scope` (research R7). */
    const val VERSION = 2
    const val VERSION_KEY = "schema_version"

    /** Blocking asset read; call it on the database dispatcher. */
    fun load(assets: AssetManager): List<String> = RagSchema.split(assets.open(ASSET).bufferedReader().use { it.readText() })
}

/** Upgrades `chat.db` from [from] to `from + 1`, inside the opening transaction, as `RagMigration`. */
class ChatMigration(val from: Int, val statements: List<String>)

object ChatMigrations {
    val all: List<ChatMigration> = listOf(
        // F17 (research R7): every conversation of before stays on the whole library, with no rows.
        ChatMigration(
            from = 1,
            statements = listOf(
                "CREATE TABLE IF NOT EXISTS conversation_scope (\n" +
                    "  conversation_id    INTEGER NOT NULL REFERENCES conversations(id) ON DELETE CASCADE,\n" +
                    "  kind               TEXT NOT NULL CHECK (kind IN ('folder','general','document')),\n" +
                    "  ref_id             INTEGER NOT NULL,\n" +
                    "  ref_created_at_ms  INTEGER,\n" +
                    "  PRIMARY KEY (conversation_id, kind, ref_id)\n" +
                    ")",
                "CREATE TABLE IF NOT EXISTS last_scope (\n" +
                    "  kind               TEXT NOT NULL CHECK (kind IN ('folder','general','document')),\n" +
                    "  ref_id             INTEGER NOT NULL,\n" +
                    "  ref_created_at_ms  INTEGER,\n" +
                    "  PRIMARY KEY (kind, ref_id)\n" +
                    ")",
            ),
        ),
    )
}
