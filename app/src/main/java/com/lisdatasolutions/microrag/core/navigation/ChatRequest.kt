package com.lisdatasolutions.microrag.core.navigation

/**
 * What Historial asks the chat underneath to show (F13 research R4), kept as text in the chat entry's
 * SavedStateHandle so it survives a process death and is read once.
 */
sealed interface ChatRequest {
    val key: String

    data class Open(val conversationId: Long) : ChatRequest {
        override val key: String get() = "$OPEN$conversationId"
    }

    data object New : ChatRequest {
        override val key: String get() = NEW
    }

    companion object {
        private const val OPEN = "open:"
        private const val NEW = "new"

        fun parse(key: String?): ChatRequest? = when {
            key == null -> null
            key == NEW -> New
            key.startsWith(OPEN) -> key.removePrefix(OPEN).toLongOrNull()?.let(::Open)
            else -> null
        }
    }
}
