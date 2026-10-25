package com.lisdatasolutions.microrag.debug.feasibility

import com.google.common.truth.Truth.assertThat
import com.lisdatasolutions.microrag.debug.feasibility.report.ResultStatus
import com.lisdatasolutions.microrag.domain.model.ComputeBackend.CPU
import com.lisdatasolutions.microrag.domain.model.ComputeBackend.GPU
import com.lisdatasolutions.microrag.domain.model.ModelId
import com.lisdatasolutions.microrag.rag.runtime.ActivationPrecision
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FeasibilitySuiteGenerationTest {

    private fun TestScope.fixture(present: Set<ModelId>) = SuiteFixture(StandardTestDispatcher(testScheduler), present)

    private val proceed: suspend (ThermalLevel) -> Boolean = { true }

    @Test
    fun generation_runsOnGpuWithOneWarmupAndThreeMeasuredRuns() = runTest {
        val f = fixture(setOf(ModelId.GEMMA4_E2B_GPU))

        f.suite.run(setOf(SuiteBlock.GENERATION), proceed)

        val (result, fp32) = f.suite.results.value
        assertThat(result.backend).isEqualTo(GPU)
        assertThat(result.status).isEqualTo(ResultStatus.COMPLETED)
        assertThat(result.activationPrecision).isNull()
        assertThat(fp32.activationPrecision).isEqualTo(ActivationPrecision.FLOAT32)
        assertThat(fp32.status).isEqualTo(ResultStatus.COMPLETED)
        assertThat(f.events.count { it == "generate GEMMA4_E2B_GPU GPU 128 null" }).isEqualTo(4)
        assertThat(f.events.count { it == "generate GEMMA4_E2B_GPU GPU 128 FLOAT32" }).isEqualTo(4)
        val metrics = result.generation!!
        assertThat(metrics.runs).hasSize(3)
        assertThat(metrics.initColdMs).isEqualTo(2000)
        assertThat(metrics.promptTokens).isEqualTo(2010)
        assertThat(metrics.ttftMs).isEqualTo(800.0)
        assertThat(metrics.ttftWallMs).isEqualTo(820)
        assertThat(metrics.prefillTokensPerSecond).isEqualTo(2500.0)
        assertThat(metrics.decodeTokensPerSecond).isEqualTo(40.0)
        assertThat(metrics.sampleOutput).isEqualTo("RESPUESTA-SECRETA con cita [1].")
        assertThat(metrics.fallbackFromGpu).isFalse()
        assertThat(f.reports.last().exitCriteria.ttftMeasured).isTrue()
    }

    @Test
    fun gpuInitFailure_fallsBackToCpuWithTheReason() = runTest {
        val f = fixture(setOf(ModelId.GEMMA4_E2B))
        f.failInit += ModelId.GEMMA4_E2B to GPU

        f.suite.run(setOf(SuiteBlock.GENERATION), proceed)

        val (gpu, gpuFp32, cpu) = f.suite.results.value
        assertThat(gpu.backend to gpu.status).isEqualTo(GPU to ResultStatus.NOT_AVAILABLE)
        assertThat(gpuFp32.status).isEqualTo(ResultStatus.NOT_AVAILABLE)
        assertThat(cpu.backend to cpu.status).isEqualTo(CPU to ResultStatus.COMPLETED)
        assertThat(cpu.generation!!.fallbackFromGpu).isTrue()
        assertThat(cpu.generation!!.fallbackReason).contains("GPU init failed")
    }

    @Test
    fun gpuGenerationFailure_alsoFallsBackToCpu() = runTest {
        val f = fixture(setOf(ModelId.GEMMA4_E2B))
        f.failGenerate += ModelId.GEMMA4_E2B to GPU

        f.suite.run(setOf(SuiteBlock.GENERATION), proceed)

        assertThat(f.suite.results.value.map { it.backend to it.status })
            .containsExactly(GPU to ResultStatus.NOT_AVAILABLE, GPU to ResultStatus.NOT_AVAILABLE, CPU to ResultStatus.COMPLETED)
            .inOrder()
    }

    @Test
    fun cpuFallbackFailure_isFailedAndTheNextFileStillRuns() = runTest {
        val f = fixture(setOf(ModelId.GEMMA4_E2B, ModelId.GEMMA4_E2B_GPU))
        f.failInit += ModelId.GEMMA4_E2B to GPU
        f.failInit += ModelId.GEMMA4_E2B to CPU

        f.suite.run(setOf(SuiteBlock.GENERATION), proceed)

        assertThat(f.suite.results.value.map { Triple(it.model, it.backend, it.status) }).containsExactly(
            Triple(ModelId.GEMMA4_E2B, GPU, ResultStatus.NOT_AVAILABLE),
            Triple(ModelId.GEMMA4_E2B, GPU, ResultStatus.NOT_AVAILABLE),
            Triple(ModelId.GEMMA4_E2B, CPU, ResultStatus.FAILED),
            Triple(ModelId.GEMMA4_E2B_GPU, GPU, ResultStatus.COMPLETED),
            Triple(ModelId.GEMMA4_E2B_GPU, GPU, ResultStatus.COMPLETED),
        ).inOrder()
    }

    @Test
    fun cancellingDuringGeneration_recordsItAsCancelled() = runTest {
        val f = fixture(setOf(ModelId.GEMMA4_E2B_GPU))
        val started = CompletableDeferred<Unit>()
        f.onGenerate = { started.complete(Unit); CompletableDeferred<Unit>().await() }

        val job = launch { f.suite.run(setOf(SuiteBlock.GENERATION), proceed) }
        started.await()
        job.cancel()
        advanceUntilIdle()

        val result = f.suite.results.value.single()
        assertThat(result.status).isEqualTo(ResultStatus.CANCELLED)
        assertThat(result.atPhase).isEqualTo(SuitePhase.GENERATION_WARMUP.name)
        assertThat(f.coordinator.loaded.value).isEmpty()
    }

    @Test
    fun promptAndAnswersNeverReachTheLogs() = runTest {
        val f = fixture(setOf(ModelId.GEMMA4_E2B_GPU, ModelId.EG2_270M))

        f.suite.run(SuiteBlock.entries.toSet(), proceed)

        assertThat(f.logs).isNotEmpty()
        f.logs.forEach { line ->
            assertThat(line).doesNotContain("SECRET")
            assertThat(line).doesNotContain("secret")
        }
    }
}
