package com.lisdatasolutions.microrag.rag.runtime

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Test

/** F07 T005: the D-036 cuts give, step by step, what offline/embedding_text.overflow_cut gives. */
class EmbeddingOverflowTest {

    @Test
    fun cutsMatchTheDesktopGolden() {
        val cases = File("../offline/golden/embedding_overflow.jsonl").readLines().filter { it.isNotBlank() }
            .map { Json.parseToJsonElement(it).jsonObject }
        assertThat(cases).hasSize(8)
        for (case in cases) {
            val name = case.getValue("case").jsonPrimitive.content
            val expected = case.getValue("steps").jsonArray.map { it.jsonPrimitive.content }
            assertThat(expected).hasSize(EmbeddingOverflow.MAX_ATTEMPTS - 1)
            var text = case.getValue("text").jsonPrimitive.content
            expected.forEachIndexed { step, want ->
                text = EmbeddingOverflow.cut(text)
                assertWithMessage("$name, cut ${step + 1}").that(text).isEqualTo(want)
            }
        }
    }

    @Test
    fun countsCodePointsNotUtf16Units() {
        // Ten code points (twelve UTF-16 units): nine are kept, then back to the space.
        assertThat(EmbeddingOverflow.cut("😀😀 abcdef")).isEqualTo("😀😀")
        assertThat(EmbeddingOverflow.cut("")).isEmpty()
    }
}
