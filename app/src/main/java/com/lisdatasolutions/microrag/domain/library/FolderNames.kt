package com.lisdatasolutions.microrag.domain.library

import com.lisdatasolutions.microrag.domain.rag.DocumentNames

/** FR-003: a folder name is trimmed, 1 to [MAX_LENGTH] characters, and never «General» in any form (D-072). */
object FolderNames {
    const val MAX_LENGTH = 40

    private val RESERVED = setOf(DocumentNames.fold("General"))

    /** Folded as the Library searches names (F08 R3), so «LEGAL» and «Légal» are the same folder. */
    fun check(input: String): FolderNameCheck {
        val name = input.trim()
        val norm = DocumentNames.fold(name)
        return when {
            name.isEmpty() -> FolderNameCheck.Empty
            name.codePointCount(0, name.length) > MAX_LENGTH -> FolderNameCheck.TooLong
            norm in RESERVED -> FolderNameCheck.Reserved
            else -> FolderNameCheck.Valid(name, norm)
        }
    }
}
