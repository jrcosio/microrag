package com.lisdatasolutions.microrag.rag.runtime

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class EmbeddingPrefixesTest {

    @Test
    fun prefixes_matchTheLiteRtLmDocumentationExactly() {
        assertThat(EmbeddingPrefixes.DOCUMENT).isEqualTo("task: search result | text: ")
        assertThat(EmbeddingPrefixes.QUERY).isEqualTo("task: search query | text: ")
    }

    @Test
    fun query_trimsTheTextBeforePrefixing() {
        assertThat(EmbeddingPrefixes.query("  hola \n")).isEqualTo("task: search query | text: hola")
    }

    @Test
    fun document_joinsTrimmedHeaderAndContent() {
        assertThat(EmbeddingPrefixes.document(content = " Texto del bloque. ", header = " Informe › Embeddings "))
            .isEqualTo("task: search result | text: Informe › Embeddings\nTexto del bloque.")
    }

    @Test
    fun document_withoutHeaderHasNoLeadingNewline() {
        assertThat(EmbeddingPrefixes.document(content = "Texto", header = "  "))
            .isEqualTo("task: search result | text: Texto")
    }
}
