package com.lisdatasolutions.microrag.data.chat

import android.content.res.AssetManager
import com.lisdatasolutions.microrag.data.rag.RagSchema

/** `assets/chat_schema.sql` (F11 data-model §3), split as the index schema. */
object ChatSchema {
    const val ASSET = "chat_schema.sql"
    const val VERSION = 1
    const val VERSION_KEY = "schema_version"

    /** Blocking asset read; call it on the database dispatcher. */
    fun load(assets: AssetManager): List<String> = RagSchema.split(assets.open(ASSET).bufferedReader().use { it.readText() })
}

/** Upgrades `chat.db` from [from] to `from + 1`, inside the opening transaction, as `RagMigration`. */
class ChatMigration(val from: Int, val statements: List<String>)

/** None yet: version 1 is the first schema. */
object ChatMigrations {
    val all: List<ChatMigration> = emptyList()
}
