package com.lisdatasolutions.microrag.domain.rag

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** F07 research R11: the title of the contextual header and of the FTS title column. */
class DocumentTitleTest {

    @Test
    fun dropsTheLastExtension() {
        assertThat(DocumentTitle.of("Manual técnico.pdf")).isEqualTo("Manual técnico")
        assertThat(DocumentTitle.of("Notas de reunión.md")).isEqualTo("Notas de reunión")
        assertThat(DocumentTitle.of("a.b.pdf")).isEqualTo("a.b")
    }

    @Test
    fun keepsNamesWithoutAnExtensionAsTheyAre() {
        assertThat(DocumentTitle.of("notas")).isEqualTo("notas")
        assertThat(DocumentTitle.of(".bashrc")).isEqualTo(".bashrc")
        assertThat(DocumentTitle.of("informe.")).isEqualTo("informe.")
    }

    @Test
    fun keepsWhatIsNotAShortAlphanumericExtension() {
        assertThat(DocumentTitle.of("datos.abcdefghijk")).isEqualTo("datos.abcdefghijk")
        assertThat(DocumentTitle.of("Versión 2.0 final")).isEqualTo("Versión 2.0 final")
        assertThat(DocumentTitle.of("copia.pdf~")).isEqualTo("copia.pdf~")
    }
}
