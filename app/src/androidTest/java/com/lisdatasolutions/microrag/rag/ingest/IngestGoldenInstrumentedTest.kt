package com.lisdatasolutions.microrag.rag.ingest

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Test
import org.junit.runner.RunWith

/**
 * SC-001 and SC-002 on the device, with ART's Unicode tables (contracts/extraction-golden.md): the
 * golden files are test APK assets taken from offline/golden.
 */
@RunWith(AndroidJUnit4::class)
class IngestGoldenInstrumentedTest {

    private fun asset(name: String) =
        InstrumentationRegistry.getInstrumentation().context.assets.open(name).bufferedReader().use { it.readText() }

    @Test
    fun extractionMatchesTheDesktop() {
        val cases = GoldenFiles.cases(asset("extraction_golden.jsonl"))
        val mismatches = cases.filter { GoldenFiles.extract(it) != GoldenFiles.expected(it) }
            .map { it.getValue("case").jsonPrimitive.content }

        assertThat(cases.size).isAtLeast(120)
        assertWithMessage("cases differing from Python: $mismatches").that(mismatches).isEmpty()
    }

    @Test
    fun chunkingMatchesTheGolden() {
        val cases = GoldenFiles.cases(asset("chunker_golden.jsonl"))
        val mismatches = cases.filter { case ->
            Chunker(case.getValue("target_chars").jsonPrimitive.int, case.getValue("max_chars").jsonPrimitive.int)
                .chunk(GoldenFiles.blocks(case.getValue("blocks"))) != GoldenFiles.chunks(case.getValue("chunks"))
        }.map { it.getValue("case").jsonPrimitive.content }

        assertThat(cases).hasSize(27)
        assertWithMessage("cases differing from the golden: $mismatches").that(mismatches).isEmpty()
    }
}
