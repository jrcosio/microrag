package com.lisdatasolutions.microrag.rag.runtime

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
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

    // F07 research R4: the ingest holds its embedding engine; memory callbacks and other users respect it.

    @Test
    fun heldEngine_staysLoadedDuringTheBlockAndOnlyEmbeddingEnginesCanBeHeld() = runTest {
        val gate = CompletableDeferred<Unit>()
        launch { coordinator.withHeldEngine(gpuA, factory("A", GPU)) { gate.await() } }
        runCurrent()
        assertThat(coordinator.loaded.value).containsExactly(gpuA)

        val llm = runCatching { coordinator.withHeldEngine(gpuB, factory("B", GPU)) { } }
        assertThat(llm.exceptionOrNull()).isInstanceOf(IllegalArgumentException::class.java)

        gate.complete(Unit)
        advanceUntilIdle()
        assertThat(coordinator.loaded.value).containsExactly(gpuA)
    }

    @Test
    fun releaseUnheld_keepsTheHeldEngineAndClosesTheRest() = runTest {
        coordinator.withEngine(cpuB, factory("b", CPU)) { }
        val gate = CompletableDeferred<Unit>()
        launch { coordinator.withHeldEngine(cpuA, factory("a", CPU)) { gate.await() } }
        runCurrent()

        coordinator.releaseUnheld()
        assertThat(events).contains("close b")
        assertThat(events).doesNotContain("close a")
        assertThat(coordinator.loaded.value).containsExactly(cpuA)

        gate.complete(Unit)
        advanceUntilIdle()
        coordinator.releaseUnheld()
        assertThat(events).contains("close a")
    }

    @Test
    fun releaseGenerators_closesOnlyTheLlm() = runTest {
        coordinator.withEngine(gpuB, factory("B", GPU))  { }
        coordinator.withEngine(cpuA, factory("a", CPU)) { }

        coordinator.releaseGenerators()

        assertThat(events).contains("close B")
        assertThat(events).doesNotContain("close a")
        assertThat(coordinator.loaded.value).containsExactly(cpuA)
    }

    @Test
    fun anotherGpuRequest_waitsForTheHeldEngineWithoutBlockingCpuEngines() = runTest {
        val gate = CompletableDeferred<Unit>()
        launch { coordinator.withHeldEngine(gpuA, factory("A", GPU)) { gate.await() } }
        runCurrent()

        val llm = launch { coordinator.withEngine(gpuB, factory("B", GPU)) { events += "use B" } }
        runCurrent()
        assertWithMessage("the LLM waits while the ingest holds the GPU").that(events).doesNotContain("create B")

        coordinator.withEngine(cpuB, factory("b", CPU)) { events += "use b" }
        assertWithMessage("a CPU engine is not blocked meanwhile").that(events).contains("use b")

        gate.complete(Unit)
        llm.join()
        assertThat(events).containsAtLeast("close A", "create B", "use B").inOrder()
    }

    @Test
    fun sameKeyInUse_waitsOutsideTheLoadLock() = runTest {
        val gate = CompletableDeferred<Unit>()
        launch { coordinator.withHeldEngine(cpuA, factory("a", CPU)) { gate.await() } }
        runCurrent()

        val second = launch { coordinator.withEngine(cpuA, factory("a2", CPU)) { events += "use a" } }
        runCurrent()
        coordinator.withEngine(cpuB, factory("b", CPU)) { events += "use b" }
        assertThat(events).contains("use b")
        assertThat(events).doesNotContain("use a")

        gate.complete(Unit)
        second.join()
        assertThat(events).contains("use a")
        assertThat(events).doesNotContain("create a2")
    }
}
