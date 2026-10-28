package com.lisdatasolutions.microrag.debug

import android.content.Intent
import com.lisdatasolutions.microrag.core.navigation.Chat
import com.lisdatasolutions.microrag.core.navigation.Document
import com.lisdatasolutions.microrag.core.navigation.Home
import com.lisdatasolutions.microrag.core.navigation.Index
import com.lisdatasolutions.microrag.core.navigation.Ingest
import com.lisdatasolutions.microrag.core.navigation.Languages
import com.lisdatasolutions.microrag.core.navigation.Library
import com.lisdatasolutions.microrag.core.navigation.Settings
import com.lisdatasolutions.microrag.core.navigation.Setup
import com.lisdatasolutions.microrag.core.navigation.Welcome

/** Debug-only: maps the route menu extras to a typed route (FR-024). */
object DebugRouting {
    const val EXTRA_ROUTE = "com.lisdatasolutions.microrag.debug.EXTRA_ROUTE"
    const val EXTRA_ID = "com.lisdatasolutions.microrag.debug.EXTRA_ID"

    /** F08: opens the document detail as a citation would (F11), with this chunk highlighted. */
    const val EXTRA_CHUNK_ID = "com.lisdatasolutions.microrag.debug.EXTRA_CHUNK_ID"

    fun initialRoute(intent: Intent?): Any? {
        val name = intent?.getStringExtra(EXTRA_ROUTE) ?: return null
        val id = intent.takeIf { it.hasExtra(EXTRA_ID) }?.getLongExtra(EXTRA_ID, 0L)
        val chunkId = intent.takeIf { it.hasExtra(EXTRA_CHUNK_ID) }?.getLongExtra(EXTRA_CHUNK_ID, 0L)
        return when (name) {
            "welcome" -> Welcome
            "setup" -> Setup
            "home" -> Home
            "chat" -> Chat(conversationId = id)
            "ingest" -> Ingest
            "library" -> Library
            "document" -> Document(documentId = id ?: -1L, chunkId = chunkId)
            "index" -> Index
            "settings" -> Settings
            "languages" -> Languages
            else -> null
        }
    }
}
