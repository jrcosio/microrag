package com.lisdatasolutions.microrag.rag.runtime

import com.google.common.truth.Truth.assertThat
import com.lisdatasolutions.microrag.core.logging.DefaultAppLogger
import com.lisdatasolutions.microrag.domain.model.ComputeBackend
import com.lisdatasolutions.microrag.domain.model.ComputeBackend.CPU
import com.lisdatasolutions.microrag.domain.model.ComputeBackend.GPU
import com.lisdatasolutions.microrag.domain.model.ModelId
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class EngineCoordinatorTest {

    private val events = mutableListOf<String>()
    private val coordinator = EngineCoordinator(DefaultAppLogger(isEnabled = false))

    private val gpuA = EngineKey(ModelId.EG2_270M, GPU)
    private val gpuB = EngineKey(ModelId.GEMMA4_E2B_GPU, GPU)
    private val cpuA = EngineKey(ModelId.EG2_270M, CPU)
    private val cpuB = EngineKey(ModelId.EG2_440M, CPU)

    private inner class FakeEngine(val name: String, override val backend: ComputeBackend) : ManagedEngine {
        override fun close() {
            events += "close $name"
        }
    }

    private fun factory(name: String, backend: ComputeBackend): suspend () -> FakeEngine = {
        events += "create $name"
        FakeEngine(name, backend)
    }

    @Test
    fun loadingASecondGpuEngine_closesTheFirstBeforeCreatingIt() = runTest {
        coordinator.withEngine(gpuA, factory("A", GPU)) { }
        coordinator.withEngine(gpuB, factory("B", GPU)) { }

        assertThat(events).containsExactly("create A", "close A", "create B").inOrder()
        assertThat(coordinator.loaded.value).containsExactly(gpuB)
    }

    @Test
    fun loadedNeverHoldsMoreThanOneGpuEngine() = runTest {
        var maxGpu = 0
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            coordinator.loaded.collect { keys -> maxGpu = maxOf(maxGpu, keys.count { it.backend == GPU }) }
        }

        coordinator.withEngine(gpuA, factory("A", GPU)) { }
        coordinator.withEngine(cpuA, factory("a", CPU)) { }
        coordinator.withEngine(gpuB, factory("B", GPU)) { }
        coordinator.withEngine(gpuA, factory("A2", GPU)) { }
        coordinator.withEngine(cpuB, factory("b", CPU)) { }

        assertThat(maxGpu).isEqualTo(1)
        assertThat(coordinator.loaded.value).containsExactly(gpuA, cpuA, cpuB)
    }

    @Test
    fun cpuEnginesCoexistAndAreReused() = runTest {
        coordinator.withEngine(cpuA, factory("a", CPU)) { }
        coordinator.withEngine(cpuB, factory("b", CPU)) { }
        coordinator.withEngine(cpuA, factory("a-again", CPU)) { }

        assertThat(events).containsExactly("create a", "create b").inOrder()
        assertThat(coordinator.loaded.value).containsExactly(cpuA, cpuB)
    }

    @Test
    fun release_waitsForTheBlockInProgress() = runTest {
        val gate = CompletableDeferred<Unit>()
        launch { coordinator.withEngine(cpuA, factory("a", CPU)) { gate.await() } }
        runCurrent()

        launch { coordinator.release(cpuA) }
        runCurrent()
        assertThat(events).doesNotContain("close a")

        gate.complete(Unit)
        advanceUntilIdle()
        assertThat(events).contains("close a")
        assertThat(coordinator.loaded.value).isEmpty()
    }

    @Test
    fun cancellingTheBlock_keepsTheEngineLoadedUntilReleased() = runTest {
        val use = launch { coordinator.withEngine(cpuA, factory("a", CPU)) { awaitCancellation() } }
        runCurrent()

        use.cancelAndJoin()
        assertThat(use.isCancelled).isTrue()
        assertThat(coordinator.loaded.value).containsExactly(cpuA)
        assertThat(events).doesNotContain("close a")

        coordinator.release(cpuA)
        assertThat(events).contains("close a")
    }

    @Test
    fun failingFactory_leavesNoEntry() = runTest {
        val result = runCatching {
            coordinator.withEngine<FakeEngine, Unit>(gpuA, { throw IllegalStateException("boom") }) { }
        }

        assertThat(result.exceptionOrNull()).hasMessageThat().isEqualTo("boom")
        assertThat(coordinator.loaded.value).isEmpty()

        coordinator.withEngine(gpuA, factory("A", GPU)) { }
        assertThat(coordinator.loaded.value).containsExactly(gpuA)
    }

    @Test
    fun releaseAll_closesEveryEngine() = runTest {
        coordinator.withEngine(cpuA, factory("a", CPU)) { }
        coordinator.withEngine(gpuB, factory("B", GPU)) { }

        coordinator.releaseAll()

        assertThat(events).containsAtLeast("close a", "close B")
        assertThat(coordinator.loaded.value).isEmpty()
    }
}
