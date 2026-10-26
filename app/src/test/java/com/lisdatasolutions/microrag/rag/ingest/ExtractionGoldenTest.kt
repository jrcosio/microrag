package com.lisdatasolutions.microrag.rag.ingest

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import java.io.File
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Test

/** SC-002 on the JVM: the app extracts as offline/eval/extract.py (contracts/extraction-golden.md). */
class ExtractionGoldenTest {

    @Test
    fun everyCaseMatchesTheDesktopExtractor() {
        val cases = GoldenFiles.cases(File("../offline/golden/extraction_golden.jsonl").readText())
        val mismatches = cases.filter { GoldenFiles.extract(it) != GoldenFiles.expected(it) }
            .map { it.getValue("case").jsonPrimitive.content }

        assertThat(cases.size).isAtLeast(120)
        assertWithMessage("cases differing from Python: $mismatches").that(mismatches).isEmpty()
    }
}
