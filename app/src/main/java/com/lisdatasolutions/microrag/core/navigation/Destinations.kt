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

/** `conversationId == null` starts a new conversation. */
@Serializable
data class Chat(val conversationId: Long? = null)

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
