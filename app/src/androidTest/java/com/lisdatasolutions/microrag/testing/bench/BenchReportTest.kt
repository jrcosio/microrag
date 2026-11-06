package com.lisdatasolutions.microrag.testing.bench

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import com.lisdatasolutions.microrag.rag.runtime.RuntimeInfo
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith

/** F14 T007 (contract B1, data-model §4): the bench report a phone run leaves for `device-report`, without models. */
@RunWith(AndroidJUnit4::class)
class BenchReportTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val written = mutableListOf<java.io.File>()

    @After
    fun tearDown() {
        written.forEach { it.delete() }
    }

    @Test
    fun theReport_hasTheRuntimes_theConditions_andNearestRankPercentiles() {
        val report = BenchReport(context, "selftest", sqliteVector = "1.2.0")
        report.start = DeviceConditions.snapshot(context)
        val variant = report.variant("f32", mapOf("bank" to "tiny"))
        (1..20).forEach { variant.add(BenchReport.Sample(phase = "vector", ms = it.toDouble(), thermal = "NONE")) }
        report.end = DeviceConditions.snapshot(context)

        val file = report.write().also { written += it }
        val json = Json.parseToJsonElement(file.readText()) as JsonObject

        assertThat(file.name).startsWith("f14-selftest-")
        assertThat(json["runtimes"]!!.jsonObject["litertlm"]!!.jsonPrimitive.content).isEqualTo(RuntimeInfo.LITERT_LM_VERSION)
        assertThat(json["runtimes"]!!.jsonObject["sqliteVector"]!!.jsonPrimitive.content).isEqualTo("1.2.0")
        assertThat(json["conditions"]!!.jsonObject.keys).containsExactly("start", "end")
        val summary = json["variants"]!!.jsonArray.single().jsonObject["summary"]!!.jsonObject["vector"]!!.jsonObject
        // Nearest rank over 1..20: p50 is the 10th, p95 the 19th and p99 the 20th smallest.
        assertThat(summary["p50"]!!.jsonPrimitive.double).isEqualTo(10.0)
        assertThat(summary["p95"]!!.jsonPrimitive.double).isEqualTo(19.0)
        assertThat(summary["p99"]!!.jsonPrimitive.double).isEqualTo(20.0)
        assertThat(json["status"]!!.jsonPrimitive.content).isEqualTo(BenchReport.STATUS_COMPLETED)
    }

    @Test
    fun theConditions_alwaysHaveAThermalStatusAndTheHeap() {
        val conditions = DeviceConditions.snapshot(context)

        assertThat(conditions.thermal).isNotEmpty()
        assertThat(conditions.heapMb).isAtLeast(0L)
        assertThat(DeviceConditions.isSevere("SEVERE")).isTrue()
        assertThat(DeviceConditions.isSevere("MODERATE")).isFalse()
    }
}
