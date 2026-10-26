package com.lisdatasolutions.microrag.rag.ingest

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.lisdatasolutions.microrag.domain.rag.RagParameters
import java.io.File
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Test

/** SC-001 on the JVM: the report's Chunker gives the F05 golden to the character (FR-021). */
class ChunkerGoldenTest {

    @Test
    fun everyCaseMatchesTheGolden() {
        val cases = GoldenFiles.cases(File("../offline/golden/chunker_golden.jsonl").readText())
        var chunks = 0
        val mismatches = cases.filter { case ->
            val target = case.getValue("target_chars").jsonPrimitive.int
            val max = case.getValue("max_chars").jsonPrimitive.int
            assertThat(target to max).isEqualTo(RagParameters.CHUNK_TARGET_CHARS to RagParameters.CHUNK_MAX_CHARS)
            val expected = GoldenFiles.chunks(case.getValue("chunks"))
            chunks += expected.size
            Chunker(target, max).chunk(GoldenFiles.blocks(case.getValue("blocks"))) != expected
        }.map { it.getValue("case").jsonPrimitive.content }

        assertThat(cases).hasSize(27)
        assertThat(chunks).isEqualTo(162)
        assertWithMessage("cases differing from the golden: $mismatches").that(mismatches).isEmpty()
    }
}
