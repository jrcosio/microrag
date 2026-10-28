package com.lisdatasolutions.microrag.domain.rag

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** F08 research R3: names folded for the Library search. */
class DocumentNamesTest {

    @Test
    fun fold_dropsCaseAndAccents() {
        assertThat(DocumentNames.fold("Guía")).isEqualTo("guia")
        assertThat(DocumentNames.fold("CATÁLOGO")).isEqualTo("catalogo")
        assertThat(DocumentNames.fold("Ñandú")).isEqualTo("nandu")
        assertThat(DocumentNames.fold("Pingüino ÇA")).isEqualTo("pinguino ca")
    }

    @Test
    fun fold_keepsTheRestOfTheName() {
        assertThat(DocumentNames.fold("Informe_2024 (v2).pdf")).isEqualTo("informe_2024 (v2).pdf")
        assertThat(DocumentNames.fold("50% \\ total")).isEqualTo("50% \\ total")
    }

    @Test
    fun fold_isComposedOrDecomposedAlike() {
        assertThat(DocumentNames.fold("Gui\u0301a")).isEqualTo(DocumentNames.fold("Guía"))
    }

    @Test
    fun likePattern_isAFoldedSubstringWithLiteralWildcards() {
        assertThat(DocumentNames.likePattern(" Guía ")).isEqualTo("%guia%")
        assertThat(DocumentNames.likePattern("50%")).isEqualTo("%50\\%%")
        assertThat(DocumentNames.likePattern("a_b")).isEqualTo("%a\\_b%")
        assertThat(DocumentNames.likePattern("c:\\x")).isEqualTo("%c:\\\\x%")
    }
}
