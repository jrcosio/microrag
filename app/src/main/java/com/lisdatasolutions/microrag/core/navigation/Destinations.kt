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

/**
 * F13 Historial, over the chat that opened it (research R4). [openConversationId] is the conversation that
 * chat shows, marked «Abierta»; the chat does not change while Historial is on top of it.
 */
@Serializable
data class History(val openConversationId: Long? = null)

/** F17 K6: [folderId] is the destination the screen starts with, from a folder's «＋»; null is «General». */
@Serializable
data class Ingest(val folderId: Long? = null)

@Serializable
data object Library

/** F17 K2: one folder over the Library tab, without the bottom bar; [folderId] null is «General». */
@Serializable
data class LibraryFolder(val folderId: Long? = null)

/** `chunkId` is set only when arriving from a citation, to highlight the fragment (F11). */
@Serializable
data class Document(val documentId: Long, val chunkId: Long? = null)

@Serializable
data object Index

@Serializable
data object Settings

@Serializable
data object Languages

/** F16: Tema, opened from Ajustes (D-067). */
@Serializable
data object Theme
