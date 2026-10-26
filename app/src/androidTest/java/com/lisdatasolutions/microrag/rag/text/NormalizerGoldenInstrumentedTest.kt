package com.lisdatasolutions.microrag.rag.text

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Test
import org.junit.runner.RunWith

/**
 * SC-001 on the device, with Android's ICU (T028). The golden is a test APK asset taken from
 * offline/golden (research R6).
 */
@RunWith(AndroidJUnit4::class)
class NormalizerGoldenInstrumentedTest {

    @Test
    fun everyGoldenPairMatches() {
        val assets = InstrumentationRegistry.getInstrumentation().context.assets
        val pairs = assets.open("normalizer_golden.jsonl").bufferedReader().use { it.readLines() }
            .filter { it.isNotBlank() }
            .map { line ->
                val pair = Json.parseToJsonElement(line).jsonObject
                pair.getValue("in").jsonPrimitive.content to pair.getValue("out").jsonPrimitive.content
            }

        val mismatches = pairs.filter { (input, output) -> SpanishText.normalize(input) != output }
            .map { (input, output) -> "«$input»: expected «$output», got «${SpanishText.normalize(input)}»" }

        assertThat(pairs.size).isAtLeast(300)
        assertWithMessage(mismatches.take(20).joinToString("\n")).that(mismatches).isEmpty()
    }
}
