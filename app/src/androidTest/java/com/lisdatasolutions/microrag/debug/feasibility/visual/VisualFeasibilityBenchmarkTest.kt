package com.lisdatasolutions.microrag.debug.feasibility.visual

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import com.lisdatasolutions.microrag.data.models.ModelFileRepository
import com.lisdatasolutions.microrag.debug.feasibility.RequiresModels
import com.lisdatasolutions.microrag.debug.feasibility.visual.report.VisualReport
import com.lisdatasolutions.microrag.domain.model.ModelFileState
import java.util.Locale
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext

/**
 * Unattended F18 measurements on the reference phone (quickstart §5):
 * `am instrument -w -r -e class …VisualFeasibilityBenchmarkTest -e blocks GEMMA_INFO,… [-e candidates mezcla-p1-b280-t0,…]`.
 * Skips itself without the three verified models or the device set (e.g. on the emulator). Logcat gets one
 * line of numbers per scenario; answers and vectors stay in the report (FR-018).
 */
@LargeTest
@RequiresModels
@RunWith(AndroidJUnit4::class)
class VisualFeasibilityBenchmarkTest {

    private val koin = GlobalContext.get()

    @Test
    fun blocks_writeTheReport() = runBlocking<Unit> {
        val repository = koin.get<ModelFileRepository>()
        repository.refresh()
        val verified = VISUAL_MODELS.count { id ->
            repository.states.value[id] !is ModelFileState.Missing && repository.verify(id) is ModelFileState.Verified
        }
        assumeTrue("Only $verified of ${VISUAL_MODELS.size} models are installed and verified", verified == VISUAL_MODELS.size)
        val loader = koin.get<DeviceSetLoader>()
        assumeTrue("The device set is not in dev-visual", loader.load().present)

        val arguments = InstrumentationRegistry.getArguments()
        val blocks = arguments.getString("blocks")?.split(',')?.map { VisualBlock.valueOf(it.trim()) }?.toSet()
            ?: VisualBlock.entries.toSet()
        val candidates = arguments.getString("candidates")?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty()
        val suite = koin.get<VisualSuite>()

        // Unattended: if the phone is still SEVERE after the cool-down wait, that scenario is skipped.
        val report = suite.run(blocks, confirmHot = { false }, candidates = candidates)

        assertThat(report.schemaVersion).isEqualTo(VisualReport.SCHEMA_VERSION)
        report.scenarios.forEach { s ->
            val m = s.metrics
            Log.i(
                TAG,
                String.format(
                    Locale.ROOT,
                    "visual-benchmark kind=%s model=%s backend=%s stage=%s combination=%s tokens=%s px=%s format=%s status=%s p50=%s p95=%s prefill=%s",
                    s.kind, s.model, s.backend, s.params.stage, s.params.combination, s.params.tokensPerImage, s.params.px,
                    s.params.format, s.status, m?.p50Ms, m?.p95Ms, m?.prefillTokens,
                ),
            )
        }
        val file = checkNotNull(suite.lastReport.value)
        assertThat(file.isFile).isTrue()
        Log.i(TAG, "visual-benchmark report: ${file.path} (${report.scenarios.size} scenarios)")
    }

    private companion object {
        const val TAG = "MicroRAG"
    }
}
