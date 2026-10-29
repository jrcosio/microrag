package com.lisdatasolutions.microrag.rag.runtime

import android.content.ComponentCallbacks2
import com.google.common.truth.Truth.assertThat
import com.lisdatasolutions.microrag.core.logging.DefaultAppLogger
import com.lisdatasolutions.microrag.domain.model.ComputeBackend
import com.lisdatasolutions.microrag.domain.model.ModelId
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class EngineMemoryCallbacksTest {

    private val coordinator = EngineCoordinator(DefaultAppLogger(isEnabled = false))
    private val key = EngineKey(ModelId.GEMMA4_E2B_GPU, ComputeBackend.GPU)

    private suspend fun load() = coordinator.withEngine(key, {
        object : ManagedEngine {
            override val backend = ComputeBackend.GPU
            override fun close() = Unit
        }
    }) { }

    private suspend fun TestScope.trim(level: Int) {
        load()
        EngineMemoryCallbacks(coordinator, this).onTrimMemory(level)
        advanceUntilIdle()
    }

    @Test
    fun uiHiddenAndAbove_releaseEveryEngineNotHeld() = runTest {
        trim(ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN)
        assertThat(coordinator.loaded.value).isEmpty()

        trim(TRIM_MEMORY_COMPLETE)
        assertThat(coordinator.loaded.value).isEmpty()
    }

    @Test
    fun runningLevels_keepEnginesLoaded() = runTest {
        trim(TRIM_MEMORY_RUNNING_CRITICAL)
        assertThat(coordinator.loaded.value).containsExactly(key)
    }

    @Test
    fun uiHidden_keepsTheEngineHeldByTheIngest() = runTest {
        val embedding = EngineKey(ModelId.EG2_270M, ComputeBackend.GPU)
        val gate = CompletableDeferred<Unit>()
        launch {
            coordinator.withHeldEngine(embedding, {
                object : ManagedEngine {
                    override val backend = ComputeBackend.GPU
                    override fun close() = Unit
                }
            }) { gate.await() }
        }
        runCurrent()

        EngineMemoryCallbacks(coordinator, this).onTrimMemory(ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN)
        runCurrent()
        assertThat(coordinator.loaded.value).containsExactly(embedding)

        gate.complete(Unit)
        advanceUntilIdle()
    }

    /** F11 T014 (research R8, contract A11): the chat interrupts its answer before Gemma is released. */
    private class RecordingListener(private val events: MutableList<String>, private val fail: Boolean = false) : MemoryTrimListener {
        override suspend fun onUiHidden() {
            events += "listener loaded=${loadedSnapshot?.invoke()}"
            if (fail) error("listener failed")
        }

        var loadedSnapshot: (() -> Int)? = null
    }

    @Test
    fun uiHiddenAndLowMemory_warnTheListenersBeforeReleasing() = runTest {
        listOf(ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN, TRIM_MEMORY_COMPLETE, LOW_MEMORY).forEach { level ->
            load()
            val events = mutableListOf<String>()
            val listener = RecordingListener(events).apply { loadedSnapshot = { coordinator.loaded.value.size } }
            val callbacks = EngineMemoryCallbacks(coordinator, this, listOf(listener))
            if (level == LOW_MEMORY) {
                @Suppress("DEPRECATION")
                callbacks.onLowMemory()
            } else {
                callbacks.onTrimMemory(level)
            }
            advanceUntilIdle()

            assertThat(events).containsExactly("listener loaded=1")
            assertThat(coordinator.loaded.value).isEmpty()
        }
    }

    @Test
    fun aFailingListener_doesNotStopTheRelease() = runTest {
        load()
        val events = mutableListOf<String>()
        val listeners = listOf(RecordingListener(events, fail = true), RecordingListener(events))

        EngineMemoryCallbacks(coordinator, this, listeners).onTrimMemory(ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN)
        advanceUntilIdle()

        assertThat(events).hasSize(2)
        assertThat(coordinator.loaded.value).isEmpty()
    }

    @Test
    fun runningLevels_doNotWarnTheListeners() = runTest {
        load()
        val events = mutableListOf<String>()

        EngineMemoryCallbacks(coordinator, this, listOf(RecordingListener(events))).onTrimMemory(TRIM_MEMORY_RUNNING_CRITICAL)
        advanceUntilIdle()

        assertThat(events).isEmpty()
        assertThat(coordinator.loaded.value).containsExactly(key)
    }

    private companion object {
        /** Marks onLowMemory in the level loop of the listener test; not a platform value. */
        const val LOW_MEMORY = -1

        // Values of the platform constants deprecated in API 34.
        const val TRIM_MEMORY_RUNNING_CRITICAL = 15
        const val TRIM_MEMORY_COMPLETE = 80
    }
}
