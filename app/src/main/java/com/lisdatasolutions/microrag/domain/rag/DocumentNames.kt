package com.lisdatasolutions.microrag.domain.rag

import java.text.Normalizer
import java.util.Locale

/**
 * Document names as the Library searches them (F08 research R3): without case or accents, so «guia»
 * finds «Guía». Not [com.lisdatasolutions.microrag.rag.text.SpanishText], whose stemming would break
 * the literal substrings a user types to find a name.
 */
object DocumentNames {

    private val COMBINING_MARKS = Regex("\\p{Mn}+")

    /** NFD, combining marks removed, lower case with [Locale.ROOT]: «Ñandú» → «nandu». */
    fun fold(name: String): String =
        COMBINING_MARKS.replace(Normalizer.normalize(name, Normalizer.Form.NFD), "").lowercase(Locale.ROOT)

    /** The `LIKE … ESCAPE '\'` pattern of a folded substring of [text], with `%`, `_` and `\` literal. */
    fun likePattern(text: String): String {
        val escaped = fold(text.trim()).replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
        return "%$escaped%"
    }
}
