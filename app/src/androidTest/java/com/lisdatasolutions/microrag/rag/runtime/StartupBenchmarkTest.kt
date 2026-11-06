package com.lisdatasolutions.microrag.rag.runtime

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lisdatasolutions.microrag.core.dispatchers.AppDispatchers
import com.lisdatasolutions.microrag.debug.feasibility.RequiresModels
import com.lisdatasolutions.microrag.domain.model.ComputeBackend
import com.lisdatasolutions.microrag.domain.model.ModelCatalog
import com.lisdatasolutions.microrag.domain.model.ModelId
import com.lisdatasolutions.microrag.domain.rag.RagParameters
import com.lisdatasolutions.microrag.rag.runtime.litert.LiteRtEngineFactory
import com.lisdatasolutions.microrag.testing.InstalledModels
import com.lisdatasolutions.microrag.testing.bench.BenchReport
import com.lisdatasolutions.microrag.testing.bench.BenchReport.Sample
import com.lisdatasolutions.microrag.testing.bench.DevFiles
import com.lisdatasolutions.microrag.testing.bench.DeviceConditions
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * F14 T025 (research R14, contract B2, FR-033): the start of every engine the app loads, cold and with its compiled
 * cache. The cold load runs with a cache folder of this test, emptied before each one, so the app's own cache
 * (`cache/litertlm`) is never touched. Gemma with and without vision, the 440M, and the 270M from `dev-models`.
 */
@RequiresModels
@RunWith(AndroidJUnit4::class)
class StartupBenchmarkTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val models = InstalledModels(context)

    @Test
    fun coldAndCachedStarts() = runBlocking<Unit> {
        assumeTrue("Gemma is not installed and prepared", models.isReady(ModelId.GEMMA4_E2B))
        val dispatchers = AppDispatchers.create()
        val report = BenchReport(context, "startup", sqliteVector = "-")
        report.start = DeviceConditions.snapshot(context)
        val scratch = File(context.cacheDir, "startup-benchmark").apply { deleteRecursively() }
        try {
            val factory = LiteRtEngineFactory(context, dispatchers, cacheDirOverride = scratch)
            val gemma = ModelCatalog.byId(ModelId.GEMMA4_E2B)
            val gemmaPath = models.pathOf(ModelId.GEMMA4_E2B)!!
            val backend = models.preparedBackends.value.getValue(ModelId.GEMMA4_E2B)
            val text = GenerationConfig(RagParameters.LLM_MAX_NUM_TOKENS, RagParameters.MAX_OUTPUT_TOKENS, greedy = true)
            val vision = text.copy(maxNumTokens = RagParameters.LLM_VISUAL_MAX_NUM_TOKENS, visionBackend = ComputeBackend.CPU,
                maxNumImages = RagParameters.LLM_MAX_NUM_IMAGES)
            measure(report, "gemma-text", scratch) { factory.generator(gemma, gemmaPath, backend, text).also { it.initialize() }.close() }
            measure(report, "gemma-vision", scratch) { factory.generator(gemma, gemmaPath, backend, vision).also { it.initialize() }.close() }
            models.pathOf(ModelId.EG2_440M)?.let { path ->
                measure(report, "eg2-440m", scratch) { factory.embedder(ModelCatalog.byId(ModelId.EG2_440M), path, ComputeBackend.CPU).also { it.initialize() }.close() }
            }
            DevFiles.verified(context, DevFiles.EG2_270M)?.let { path ->
                measure(report, "eg2-270m", scratch) { factory.embedder(ModelCatalog.byId(ModelId.EG2_270M), path, ComputeBackend.CPU).also { it.initialize() }.close() }
            }
            report.end = DeviceConditions.snapshot(context)
            report.write()
        } finally {
            scratch.deleteRecursively()
            dispatchers.close()
        }
    }

    /** [load] twice from an empty cache (cold) and twice more with the cache it left (cached). */
    private suspend fun measure(report: BenchReport, name: String, scratch: File, load: suspend () -> Unit) {
        val variant = report.variant(name)
        repeat(REPEATS) {
            scratch.deleteRecursively()
            variant.add(Sample("cold", timed(load), thermal = DeviceConditions.thermalNow(context)))
            variant.add(Sample("cached", timed(load), thermal = DeviceConditions.thermalNow(context)))
        }
    }

    private suspend fun timed(load: suspend () -> Unit): Double {
        val started = System.nanoTime()
        load()
        return (System.nanoTime() - started) / 1e6
    }

    private companion object {
        const val REPEATS = 2
    }
}
