package com.lisdatasolutions.microrag.rag.text

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import java.io.File
import java.security.MessageDigest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Test

/** SC-001 on the JVM: SpanishText gives exactly the golden produced by offline/normalizer.py (T027). */
class NormalizerGoldenTest {

    private val golden = File("../offline/golden/normalizer_golden.jsonl")

    @Test
    fun everyGoldenPairMatches() {
        val pairs = golden.readLines().filter { it.isNotBlank() }.map { line ->
            val pair = Json.parseToJsonElement(line).jsonObject
            pair.getValue("in").jsonPrimitive.content to pair.getValue("out").jsonPrimitive.content
        }

        val mismatches = pairs.filter { (input, output) -> SpanishText.normalize(input) != output }
            .map { (input, output) -> "«$input»: expected «$output», got «${SpanishText.normalize(input)}»" }

        assertThat(pairs.size).isAtLeast(300)
        assertWithMessage(mismatches.take(20).joinToString("\n")).that(mismatches).isEmpty()
    }

    /** Rule O6: regenerating the golden with other outputs requires a new [SpanishText.VERSION]. */
    @Test
    fun goldenHashIsPinnedForTheVersion() {
        val digest = MessageDigest.getInstance("SHA-256").digest(golden.readBytes()).joinToString("") { "%02x".format(it) }

        assertThat(digest).isEqualTo(GOLDEN_SHA256[SpanishText.VERSION])
    }

    private companion object {
        val GOLDEN_SHA256 = mapOf(1 to "d118b8e6ece31a64b03f1ca557e4261983b29f2ef6574d548fb13e8c5d1a10f9")
    }
}
