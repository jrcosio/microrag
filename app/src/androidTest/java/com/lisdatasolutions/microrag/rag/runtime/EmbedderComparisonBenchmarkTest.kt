package com.lisdatasolutions.microrag.rag.runtime

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lisdatasolutions.microrag.core.dispatchers.AppDispatchers
import com.lisdatasolutions.microrag.data.retrieval.EngineQueryEmbedder
import com.lisdatasolutions.microrag.debug.feasibility.RequiresModels
import com.lisdatasolutions.microrag.domain.model.ComputeBackend
import com.lisdatasolutions.microrag.domain.model.ModelId
import com.lisdatasolutions.microrag.rag.retrieval.RetrievalGoldenFiles
import com.lisdatasolutions.microrag.rag.runtime.litert.LiteRtEngineFactory
import com.lisdatasolutions.microrag.testing.CapturingLogger
import com.lisdatasolutions.microrag.testing.InstalledModels
import com.lisdatasolutions.microrag.testing.bench.BenchReport
import com.lisdatasolutions.microrag.testing.bench.BenchReport.Sample
import com.lisdatasolutions.microrag.testing.bench.DevFiles
import com.lisdatasolutions.microrag.testing.bench.DeviceConditions
import com.lisdatasolutions.microrag.testing.bench.FixedModels
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * F14 T038 (research R10 and R11, contract B2, FR-024): the query embedding with the 440M (installed with the mode on)
 * against the 270M (from `dev-models`, never installed), on the CPU, in alternate rounds so heat weighs the same on
 * both; with the RSS peak of each engine loaded alone. The questions of the parity sample, follow-ups joined as the
 * app joins them.
 */
@RequiresModels
@RunWith(AndroidJUnit4::class)
class EmbedderComparisonBenchmarkTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val models = InstalledModels(context)
    private val questions = RetrievalGoldenFiles.parity(
        instrumentation.context.assets.open(RetrievalGoldenFiles.PARITY).bufferedReader().use { it.readText() },
    )

    @Test
    fun the440mAgainstThe270m() = runBlocking<Unit> {
        val small = DevFiles.verified(context, DevFiles.EG2_270M)
        assumeTrue("the 440M is not installed or the 270M is not in dev-models", models.isReady(ModelId.EG2_440M) && small != null)
        checkNotNull(small)
        val dispatchers = AppDispatchers.create()
        val report = BenchReport(context, "embedders", sqliteVector = "-")
        report.start = DeviceConditions.snapshot(context)
        try {
            val factory = LiteRtEngineFactory(context, dispatchers)
            val engines = listOf(
                "440m" to (ModelId.EG2_440M to FixedModels(mapOf(ModelId.EG2_440M to models.pathOf(ModelId.EG2_440M)!!), mapOf(ModelId.EG2_440M to ComputeBackend.CPU))),
                "270m" to (ModelId.EG2_270M to FixedModels(mapOf(ModelId.EG2_270M to small), mapOf(ModelId.EG2_270M to ComputeBackend.CPU))),
            )
            val variants = engines.associate { (name, _) -> name to report.variant(name) }
            repeat(ROUNDS) { round ->
                val order = if (round % 2 == 0) engines else engines.reversed()
                order.forEach { (name, model) ->
                    val coordinator = EngineCoordinator(CapturingLogger())
                    try {
                        DeviceConditions.resetPeak()
                        val embedder = EngineQueryEmbedder(coordinator, factory, model.second, textEmbedder = { model.first })
                        embedder.embed(questions.first().question, null)
                        questions.forEach { q ->
                            val started = System.nanoTime()
                            embedder.embed(q.question, q.previous)
                            variants.getValue(name).add(Sample("embed", (System.nanoTime() - started) / 1e6, q.id, DeviceConditions.thermalNow(context)))
                        }
                        variants.getValue(name).extra["rssPeakMbRound$round"] = JsonPrimitive(DeviceConditions.snapshot(context).rssPeakMb ?: -1)
                    } finally {
                        coordinator.releaseAll()
                    }
                }
            }
            report.end = DeviceConditions.snapshot(context)
            report.write()
        } finally {
            dispatchers.close()
        }
    }

    private companion object {
        const val ROUNDS = 4
    }
}
