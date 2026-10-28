package com.lisdatasolutions.microrag.data.library

import com.google.common.truth.Truth.assertThat
import com.lisdatasolutions.microrag.rag.ingest.Chunker
import com.lisdatasolutions.microrag.rag.ingest.RawBlock
import org.junit.Test

/** F08 T027 (research R7): the neighbouring sentences shown around a cited chunk, on the JVM and on devices (ICU). */
class PreviewTextTest {

    @Test
    fun lastSentence_ofTheChunkBefore() {
        assertThat(PreviewText.lastSentence("Primera frase. Segunda frase. Antes de cerrar el proceso, se"))
            .isEqualTo("Antes de cerrar el proceso, se")
        assertThat(PreviewText.lastSentence("Uno. Dos termina aquí.")).isEqualTo("Dos termina aquí.")
    }

    @Test
    fun firstSentence_ofTheChunkAfter() {
        assertThat(PreviewText.firstSentence("en el sistema documental. Luego se archiva.")).isEqualTo("en el sistema documental.")
        assertThat(PreviewText.firstSentence("¿Qué pasa después? Nada más.")).isEqualTo("¿Qué pasa después?")
    }

    @Test
    fun commonAbbreviations_doNotEndASentence() {
        assertThat(PreviewText.firstSentence("Véase la pág. 12 del manual. Después, el anexo."))
            .isEqualTo("Véase la pág. 12 del manual.")
        assertThat(PreviewText.firstSentence("Según el art. Tercero de la norma. Fin."))
            .isEqualTo("Según el art. Tercero de la norma.")
        assertThat(PreviewText.lastSentence("Inicio. Lo firmó el Sr. Pérez en la pág. 4."))
            .isEqualTo("Lo firmó el Sr. Pérez en la pág. 4.")
    }

    @Test
    fun withoutFinalPunctuation_theWholeTextIsOneSentence() {
        assertThat(PreviewText.firstSentence("una línea sin punto final")).isEqualTo("una línea sin punto final")
        assertThat(PreviewText.lastSentence("una línea sin punto final")).isEqualTo("una línea sin punto final")
    }

    @Test
    fun aOneSentenceChunk_isItself() {
        assertThat(PreviewText.firstSentence("  Solo una frase.  ")).isEqualTo("Solo una frase.")
        assertThat(PreviewText.lastSentence("  Solo una frase.  ")).isEqualTo("Solo una frase.")
    }

    @Test
    fun lineBreaksEndSentences_andBlankTextGivesNothing() {
        assertThat(PreviewText.firstSentence("Título del capítulo\nEl texto sigue aquí.")).isEqualTo("Título del capítulo")
        assertThat(PreviewText.lastSentence("Párrafo uno.\n\nÚltima línea suelta")).isEqualTo("Última línea suelta")
        assertThat(PreviewText.firstSentence("  \n ")).isNull()
    }

    @Test
    fun theParagraphCarriedIntoTheNextChunk_isNotShownTwice() {
        val previous = "Uno. Dos.\n\nTres corto."
        val cited = "Tres corto.\n\nCuatro. Cinco.\n\nSeis corto."
        val next = "Seis corto.\n\nSiete. Ocho."

        assertThat(PreviewText.before(previous, cited)).isEqualTo("Dos.")
        assertThat(PreviewText.after(cited, next)).isEqualTo("Siete.")
        // Nothing carried: the plain last and first sentences.
        assertThat(PreviewText.before("Uno. Dos.", "Tres.")).isEqualTo("Dos.")
        assertThat(PreviewText.after("Tres.", "Cuatro. Cinco.")).isEqualTo("Cuatro.")
        // Only whole paragraphs count, and a chunk that is just the carried paragraph adds nothing.
        assertThat(PreviewText.before("Uno.\n\nTres", "Tres corto.\n\nCuatro.")).isEqualTo("Tres")
        assertThat(PreviewText.before("Tres corto.", "Tres corto.\n\nCuatro.")).isNull()
    }

    @Test
    fun withTheRealChunker_theContextIsNeverInTheCitedChunk() {
        val text = (1..40).joinToString("\n\n") { p ->
            if (p % 3 == 0) "Nota $p." else "Párrafo $p con varias palabras de relleno. Sigue la frase $p y termina aquí."
        }
        val chunks = Chunker(targetChars = 200, maxChars = 400).chunk(listOf(RawBlock(emptyList(), text, page = 1))).map { it.content }
        assertThat(chunks.zipWithNext().count { (a, b) -> b.startsWith(a.substringAfterLast("\n\n") + "\n\n") }).isGreaterThan(3)

        chunks.indices.drop(1).dropLast(1).forEach { i ->
            val before = PreviewText.before(chunks[i - 1], chunks[i])
            val after = PreviewText.after(chunks[i], chunks[i + 1])
            assertThat(before).isNotNull()
            assertThat(after).isNotNull()
            assertThat(chunks[i]).doesNotContain(before!!)
            assertThat(chunks[i]).doesNotContain(after!!)
        }
    }

    @Test
    fun veryLongSentences_areCutAtAWordWithAnEllipsis() {
        val long = (1..200).joinToString(" ") { "palabra$it" }

        val first = PreviewText.firstSentence(long)!!
        val last = PreviewText.lastSentence(long)!!

        assertThat(first.length).isAtMost(PreviewText.MAX_CONTEXT + 1)
        assertThat(first).startsWith("palabra1 ")
        assertThat(first).endsWith("…")
        assertThat(last.length).isAtMost(PreviewText.MAX_CONTEXT + 1)
        assertThat(last).startsWith("…")
        assertThat(last).endsWith("palabra200")
    }
}
