package com.lisdatasolutions.microrag.debug.feasibility

import com.google.common.truth.Truth.assertThat
import com.lisdatasolutions.microrag.debug.feasibility.report.ResultStatus
import com.lisdatasolutions.microrag.debug.feasibility.report.ScenarioKind
import com.lisdatasolutions.microrag.domain.model.ComputeBackend.CPU
import com.lisdatasolutions.microrag.domain.model.ComputeBackend.GPU
import com.lisdatasolutions.microrag.domain.model.ModelId
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FeasibilitySuiteEmbeddingTest {

    private fun TestScope.fixture(present: Set<ModelId>, corrupt: Set<ModelId> = emptySet()) =
        SuiteFixture(StandardTestDispatcher(testScheduler), present, corrupt)

    private val proceed: suspend (ThermalLevel) -> Boolean = { true }

    @Test
    fun embeddings_followTheProtocolOnBothBackends() = runTest {
        val f = fixture(setOf(ModelId.EG2_270M))

        f.suite.run(setOf(SuiteBlock.EMBEDDINGS), proceed)

        val results = f.suite.results.value
        assertThat(results.map { it.backend to it.status }).containsExactly(CPU to ResultStatus.COMPLETED, GPU to ResultStatus.COMPLETED).inOrder()
        assertThat(f.events.subList(0, 5))
            .containsExactly("metadata", "clear", "init EG2_270M CPU", "close EG2_270M CPU", "init EG2_270M CPU").inOrder()
        // Per backend: 3 warm-up + 30 measured + 1 final check queries, 3 + 30 documents.
        assertThat(f.events.count { it == "query" }).isEqualTo(2 * 34)
        assertThat(f.events.count { it == "document" }).isEqualTo(2 * 33)
        val metrics = results.first().embedding!!
        assertThat(metrics.outputDims).isEqualTo(256)
        assertThat(metrics.rawDims).isEqualTo(768)
        assertThat(metrics.l2Norm).isWithin(1e-5).of(1.0)
        assertThat(metrics.declaredMaxContextTokens).isEqualTo(8192)
        assertThat(metrics.initColdMs).isEqualTo(100)
        assertThat(f.coordinator.loaded.value).isEmpty()
    }

    @Test
    fun percentiles_comeFromTheMeasuredRepetitionsOnly() = runTest {
        val f = fixture(setOf(ModelId.EG2_270M))
        f.queryLatencyMs = { call -> call.toLong() } // warm-up calls 1-3, measured calls 4-33

        f.suite.run(setOf(SuiteBlock.EMBEDDINGS), proceed)

        val metrics = f.suite.results.value.first().embedding!!
        assertThat(metrics.queryP50Ms).isEqualTo(18.0)
        assertThat(metrics.queryP95Ms).isEqualTo(32.0)
        assertThat(metrics.documentP95Ms).isEqualTo(40.0)
    }

    @Test
    fun gpuFailure_isNotAvailableAndTheNextVariantStillRuns() = runTest {
        val f = fixture(setOf(ModelId.EG2_440M, ModelId.EG2_270M))
        f.failInit += ModelId.EG2_440M to GPU

        f.suite.run(setOf(SuiteBlock.EMBEDDINGS), proceed)

        val summary = f.suite.results.value.map { Triple(it.model, it.backend, it.status) }
        assertThat(summary).containsExactly(
            Triple(ModelId.EG2_440M, CPU, ResultStatus.COMPLETED),
            Triple(ModelId.EG2_440M, GPU, ResultStatus.NOT_AVAILABLE),
            Triple(ModelId.EG2_270M, CPU, ResultStatus.COMPLETED),
            Triple(ModelId.EG2_270M, GPU, ResultStatus.COMPLETED),
        ).inOrder()
        assertThat(f.suite.results.value[1].reason).contains("delegate unavailable")
    }

    @Test
    fun undeclaredBackend_isNotAvailableWithoutLoading() = runTest {
        val f = fixture(setOf(ModelId.EG2_270M))
        f.declared[ModelId.EG2_270M] = listOf(CPU)

        f.suite.run(setOf(SuiteBlock.EMBEDDINGS), proceed)

        assertThat(f.suite.results.value.last().status).isEqualTo(ResultStatus.NOT_AVAILABLE)
        assertThat(f.events).doesNotContain("init EG2_270M GPU")
    }

    @Test
    fun corruptFile_neverReachesTheFactory() = runTest {
        val f = fixture(present = setOf(ModelId.EG2_270M), corrupt = setOf(ModelId.EG2_270M))

        f.suite.run(setOf(SuiteBlock.EMBEDDINGS, SuiteBlock.COMPATIBILITY), proceed)

        assertThat(f.suite.results.value).isEmpty()
        assertThat(f.events.filter { it.startsWith("init") || it == "metadata" }).isEmpty()
    }

    @Test
    fun wrongVectorShape_failsTheScenario() = runTest {
        val f = fixture(setOf(ModelId.EG2_270M))
        f.embeddingDims = 255

        f.suite.run(setOf(SuiteBlock.EMBEDDINGS), proceed)

        val cpu = f.suite.results.value.first()
        assertThat(cpu.status).isEqualTo(ResultStatus.FAILED)
        assertThat(cpu.reason).contains("dims=255")
    }

    @Test
    fun compatibility_reportsCosinesAndTheThreshold() = runTest {
        val f = fixture(setOf(ModelId.EG2_270M))

        f.suite.run(setOf(SuiteBlock.EMBEDDINGS, SuiteBlock.COMPATIBILITY), proceed)
        val identical = f.suite.results.value.single { it.kind == ScenarioKind.COMPATIBILITY }.compatibility!!

        f.gpuPerturbation = 1f
        f.suite.run(setOf(SuiteBlock.COMPATIBILITY), proceed)
        val perturbed = f.suite.results.value.single().compatibility!!

        assertThat(identical.textCount).isEqualTo(20)
        assertThat(identical.cosineMin).isWithin(1e-6).of(1.0)
        assertThat(identical.compatible).isTrue()
        assertThat(perturbed.cosineMin).isLessThan(0.99)
        assertThat(perturbed.compatible).isFalse()
    }

    @Test
    fun compatibility_isSkippedWhenAnEmbeddingBackendDidNotComplete() = runTest {
        val f = fixture(setOf(ModelId.EG2_270M))
        f.failInit += ModelId.EG2_270M to GPU

        f.suite.run(setOf(SuiteBlock.EMBEDDINGS, SuiteBlock.COMPATIBILITY), proceed)

        val compat = f.suite.results.value.single { it.kind == ScenarioKind.COMPATIBILITY }
        assertThat(compat.status).isEqualTo(ResultStatus.NOT_AVAILABLE)
        assertThat(compat.backend).isNull()
    }

    @Test
    fun severeThermal_asksAndDecliningCancelsWithoutLoading() = runTest {
        val f = fixture(setOf(ModelId.EG2_270M))
        f.thermalLevel = ThermalLevel.SEVERE
        val asked = mutableListOf<ThermalLevel>()

        f.suite.run(setOf(SuiteBlock.EMBEDDINGS)) { asked += it; false }

        assertThat(asked).containsExactly(ThermalLevel.SEVERE, ThermalLevel.SEVERE)
        assertThat(f.suite.results.value.map { it.status }).containsExactly(ResultStatus.CANCELLED, ResultStatus.CANCELLED)
        assertThat(f.suite.results.value.first().reason).isEqualTo("thermal")
        assertThat(f.events.filter { it.startsWith("init") }).isEmpty()
    }

    @Test
    fun cancellingMidLoop_recordsTheCancelledPhaseAndWritesTheReport() = runTest {
        val f = fixture(setOf(ModelId.EG2_270M))
        val reached = CompletableDeferred<Unit>()
        f.onQuery = { call -> if (call == 10) { reached.complete(Unit); CompletableDeferred<Unit>().await() } }

        val job = launch { f.suite.run(setOf(SuiteBlock.EMBEDDINGS), proceed) }
        reached.await()
        job.cancel()
        advanceUntilIdle()

        val result = f.suite.results.value.single()
        assertThat(result.status).isEqualTo(ResultStatus.CANCELLED)
        assertThat(result.atPhase).isEqualTo(SuitePhase.QUERY.name)
        assertThat(f.reports.last().results).contains(result)
        assertThat(f.coordinator.loaded.value).isEmpty()
        assertThat(f.suite.progress.value).isNull()
    }
}
