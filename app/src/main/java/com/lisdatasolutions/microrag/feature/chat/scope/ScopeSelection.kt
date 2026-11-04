package com.lisdatasolutions.microrag.feature.chat.scope

import com.lisdatasolutions.microrag.domain.chat.ChatScope
import com.lisdatasolutions.microrag.domain.rag.DocumentRef
import com.lisdatasolutions.microrag.domain.rag.FolderRef

/**
 * What the selector has checked (K8), which may be nothing while it is being changed. It travels in the
 * SavedStateHandle as [encode] writes it: «W» is the whole library, «S|g|f:3:30,f:4:40|d:7:70» the rest.
 */
data class ScopeSelection(
    val whole: Boolean,
    val folders: Set<FolderRef> = emptySet(),
    val general: Boolean = false,
    val documents: Set<DocumentRef> = emptySet(),
) {
    val isEmpty: Boolean get() = !whole && folders.isEmpty() && !general && documents.isEmpty()

    /** Null while nothing is checked: «Aplicar» waits. */
    fun toScope(): ChatScope? = when {
        whole -> ChatScope.WholeLibrary
        isEmpty -> null
        else -> ChatScope.Some(folders, general, documents)
    }

    fun encode(): String = if (whole) {
        WHOLE
    } else {
        listOf(
            "S",
            if (general) "g" else "",
            folders.joinToString(",") { "f:${it.id}:${it.createdAtMs}" },
            documents.joinToString(",") { "d:${it.id}:${it.createdAtMs}" },
        ).joinToString("|")
    }

    companion object {
        private const val WHOLE = "W"

        fun of(scope: ChatScope): ScopeSelection = when (scope) {
            ChatScope.WholeLibrary -> ScopeSelection(whole = true)
            is ChatScope.Some -> ScopeSelection(whole = false, folders = scope.folders, general = scope.general, documents = scope.documents)
        }

        /** Null for anything it did not write: the caller starts again from the chat's scope. */
        fun decode(text: String?): ScopeSelection? {
            if (text == null) return null
            if (text == WHOLE) return ScopeSelection(whole = true)
            val parts = text.split("|")
            if (parts.size != 4 || parts[0] != "S") return null
            fun refs(part: String, prefix: String): List<Pair<Long, Long>>? = part.split(",").filter { it.isNotEmpty() }.map { item ->
                val fields = item.split(":")
                if (fields.size != 3 || fields[0] != prefix) return null
                (fields[1].toLongOrNull() ?: return null) to (fields[2].toLongOrNull() ?: return null)
            }
            val folders = refs(parts[2], "f")?.map { (id, at) -> FolderRef(id, at) } ?: return null
            val documents = refs(parts[3], "d")?.map { (id, at) -> DocumentRef(id, at) } ?: return null
            return ScopeSelection(whole = false, folders = folders.toSet(), general = parts[1] == "g", documents = documents.toSet())
        }
    }
}
