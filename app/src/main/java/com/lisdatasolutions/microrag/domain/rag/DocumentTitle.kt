package com.lisdatasolutions.microrag.domain.rag

/**
 * The title a document gets in its chunks' contextual header and FTS title column (F07 research R11):
 * the file name without its last extension, like the titles of the desktop corpus. The extension rule
 * is the import's (F06 `detectFormat`): 1 to 10 letters or digits, with something before the dot.
 */
object DocumentTitle {

    private const val MAX_EXTENSION = 10

    fun of(displayName: String): String {
        val dot = displayName.lastIndexOf('.')
        if (dot <= 0) return displayName
        val extension = displayName.substring(dot + 1)
        val isExtension = extension.length in 1..MAX_EXTENSION && extension.all { it.isLetterOrDigit() }
        return if (isExtension) displayName.substring(0, dot) else displayName
    }
}
