package com.lisdatasolutions.microrag.rag.text

/**
 * Spanish text normalizer v1, exactly as in the RAG report (§Búsqueda léxica en español): lowercase,
 * no diacritics, no stopwords and a light plural/gender stemmer; tokens with digits stay intact.
 *
 * `offline/normalizer.py` is its exact port and `offline/golden/normalizer_golden.jsonl` keeps both
 * in sync (constitution III). Any change in the output MUST bump [VERSION] on both sides, regenerate
 * the golden and update its pinned hash; indexes built with another version stop being searchable.
 */
object SpanishText {
    const val VERSION = 1

    private val STOP = setOf(
        "a","al","algo","ante","asi","como","con","contra","cual","cuales","cuando","de","del",
        "desde","donde","durante","e","el","ella","ellas","ellos","en","entre","era","es","esa",
        "esas","ese","eso","esos","esta","estas","este","esto","estos","fue","ha","han","hay",
        "la","las","le","les","lo","los","mas","me","mi","muy","ni","no","nos","o","os","para",
        "pero","por","que","quien","se","sea","ser","si","sin","sobre","su","sus","tambien",
        "te","tiene","u","un","una","uno","unos","unas","y","ya","yo","cuanto","cuanta"
    )
    private val TOKEN = Regex("[\\p{L}\\p{N}]+(?:[-_./][\\p{L}\\p{N}]+)*")
    private val MARKS = Regex("\\p{Mn}+")

    fun tokens(text: String): List<String> {
        val plain = java.text.Normalizer
            .normalize(text.lowercase(), java.text.Normalizer.Form.NFD)
            .replace(MARKS, "")
        return TOKEN.findAll(plain).map { it.value }
            .filter { it !in STOP && it.length > 1 }
            .map(::lightStem)
            .toList()
    }

    fun normalize(text: String): String = tokens(text).joinToString(" ")

    /** Simplified equivalent of Lucene's SpanishLightStemmer. */
    fun lightStem(t: String): String {
        if (t.length < 5 || t.any { it.isDigit() }) return t
        val n = t.length
        return when (t[n - 1]) {
            'o', 'a', 'e' -> t.substring(0, n - 1)
            's' -> when {
                t.endsWith("eses") -> t.substring(0, n - 2)
                t[n - 2] == 'e' && t[n - 3] == 'c' -> t.substring(0, n - 3) + "z"
                t[n - 2] in "oae" -> t.substring(0, n - 2)
                else -> t
            }
            else -> t
        }
    }

    /** FTS5 query: normalized tokens, quoted, joined with OR. */
    fun ftsQuery(userQuery: String, maxTerms: Int = 16): String =
        tokens(userQuery).distinct().take(maxTerms)
            .joinToString(" OR ") { "\"" + it.replace("\"", "\"\"") + "\"" }
}
