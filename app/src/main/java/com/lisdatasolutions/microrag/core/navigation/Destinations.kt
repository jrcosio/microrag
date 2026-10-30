package com.lisdatasolutions.microrag.core.navigation

import kotlinx.serialization.Serializable

// Logical destinations (docs/disegn_app/docs/01-navigation.md, contracts/navigation.md).
// Only identifiers travel between destinations; content is always loaded from repositories.

@Serializable
data object Welcome

@Serializable
data object Setup

@Serializable
data object Home

/**
 * The chat (F11 research R13): [conversationId] opens that conversation; [newConversation] («Nuevo chat»
 * from Home) opens an empty one; with neither, the tab opens the last conversation. The entry keeps the
 * conversation it shows in its saved state, so a restored entry comes back to it.
 */
@Serializable
data class Chat(val conversationId: Long? = null, val newConversation: Boolean = false)

@Serializable
data object Ingest

@Serializable
data object Library

/** `chunkId` is set only when arriving from a citation, to highlight the fragment (F11). */
@Serializable
data class Document(val documentId: Long, val chunkId: Long? = null)

@Serializable
data object Index

@Serializable
data object Settings

@Serializable
data object Languages
