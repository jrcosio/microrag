package com.lisdatasolutions.microrag.debug.feasibility

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import com.lisdatasolutions.microrag.data.models.ModelFileRepository
import com.lisdatasolutions.microrag.debug.feasibility.report.FeasibilityReport
import com.lisdatasolutions.microrag.domain.model.ModelCatalog
import com.lisdatasolutions.microrag.domain.model.ModelFileState
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext

/**
 * Unattended phase-0 battery on the reference phone (quickstart §5, option B):
 * `am instrument -w -e class …FeasibilityBenchmarkTest [-e blocks EMBEDDINGS,COMPATIBILITY,GENERATION]`.
 * Skips itself when the five verified models are not installed (e.g. on the emulator).
 */
@LargeTest
@RequiresModels
@RunWith(AndroidJUnit4::class)
class FeasibilityBenchmarkTest {

    private val koin = GlobalContext.get()

    @Test
    fun fullBattery_writesTheReport() = runBlocking<Unit> {
        val repository = koin.get<ModelFileRepository>()
        repository.refresh()
        val verified = ModelCatalog.all.count { file ->
            repository.states.value[file.id] !is ModelFileState.Missing && repository.verify(file.id) is ModelFileState.Verified
        }
        assumeTrue("Only $verified of ${ModelCatalog.all.size} models are installed and verified", verified == ModelCatalog.all.size)

        val blocks = InstrumentationRegistry.getArguments().getString("blocks")
            ?.split(',')?.map { SuiteBlock.valueOf(it.trim()) }?.toSet()
            ?: SuiteBlock.entries.toSet()
        val suite = koin.get<FeasibilitySuite>()

        // Unattended: if the phone is still SEVERE after the cool-down wait, that scenario is skipped.
        val report = suite.run(blocks) { false }

        assertThat(report.schemaVersion).isEqualTo(FeasibilityReport.SCHEMA_VERSION)
        val file = checkNotNull(suite.lastReport.value)
        assertThat(file.isFile).isTrue()
        Log.i("MicroRAG", "benchmark report: ${file.path} (${report.results.size} results)")
    }
}
