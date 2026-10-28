package com.lisdatasolutions.microrag.data.library

import com.google.common.truth.Truth.assertThat
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
