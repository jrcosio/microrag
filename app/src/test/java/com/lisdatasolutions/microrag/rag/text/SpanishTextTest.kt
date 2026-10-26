package com.lisdatasolutions.microrag.rag.text

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** SpanishText v1 against the report (§Búsqueda léxica en español) and its edge rules. */
class SpanishTextTest {

    @Test
    fun reportExamples() {
        assertThat(SpanishText.normalize("clientes")).isEqualTo("client")
        assertThat(SpanishText.normalize("cliente")).isEqualTo("client")
        assertThat(SpanishText.normalize("motores")).isEqualTo("motor")
        assertThat(SpanishText.normalize("luces")).isEqualTo("luz")
        assertThat(SpanishText.normalize("R-1234")).isEqualTo("r-1234")
    }

    @Test
    fun lowercasesAndDropsDiacriticsAfterNfd() {
        assertThat(SpanishText.normalize("Canción")).isEqualTo("cancion")
        assertThat(SpanishText.normalize("AÑO")).isEqualTo("ano")
        assertThat(SpanishText.normalize("pingüino")).isEqualTo("pinguin")
    }

    @Test
    fun dropsStopwordsAndSingleCharacterTokens() {
        assertThat(SpanishText.normalize("de la que")).isEmpty()
        assertThat(SpanishText.normalize("x k z")).isEmpty()
        assertThat(SpanishText.normalize("El motor de la bomba")).isEqualTo("motor bomb")
    }

    @Test
    fun stemsOnlyLongTokensWithoutDigits() {
        assertThat(SpanishText.normalize("casa")).isEqualTo("casa")
        assertThat(SpanishText.normalize("casas")).isEqualTo("cas")
        assertThat(SpanishText.normalize("meses")).isEqualTo("mes")
        assertThat(SpanishText.normalize("abc12")).isEqualTo("abc12")
        assertThat(SpanishText.normalize("modelo2024")).isEqualTo("modelo2024")
    }

    @Test
    fun separatorsJoinOnlyBetweenAlphanumerics() {
        assertThat(SpanishText.normalize("v1.2/a")).isEqualTo("v1.2/a")
        assertThat(SpanishText.normalize("abc_def")).isEqualTo("abc_def")
        assertThat(SpanishText.normalize("abc- -abc")).isEqualTo("abc abc")
        assertThat(SpanishText.normalize("abc--def")).isEqualTo("abc def")
    }

    @Test
    fun ftsQuery_quotesDistinctTermsJoinedWithOr() {
        assertThat(SpanishText.ftsQuery("clientes y motores")).isEqualTo("\"client\" OR \"motor\"")
        assertThat(SpanishText.ftsQuery("cliente clientes")).isEqualTo("\"client\"")
        assertThat(SpanishText.ftsQuery("R-1234 \"manual\"")).isEqualTo("\"r-1234\" OR \"manual\"")
    }

    @Test
    fun ftsQuery_keepsAtMostSixteenTerms() {
        val query = (1..20).joinToString(" ") { "t%02d".format(it) }

        val terms = SpanishText.ftsQuery(query).split(" OR ")

        assertThat(terms).hasSize(16)
        assertThat(terms.first()).isEqualTo("\"t01\"")
        assertThat(terms.last()).isEqualTo("\"t16\"")
    }

    @Test
    fun ftsQuery_isEmptyWhenOnlyStopwords() {
        assertThat(SpanishText.ftsQuery("de la que")).isEmpty()
    }

    @Test
    fun versionIsOne() {
        assertThat(SpanishText.VERSION).isEqualTo(1)
    }
}
