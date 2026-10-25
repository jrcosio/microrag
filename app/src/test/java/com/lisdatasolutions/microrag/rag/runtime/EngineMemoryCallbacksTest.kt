package com.lisdatasolutions.microrag.rag.runtime

import android.content.ComponentCallbacks2
import com.google.common.truth.Truth.assertThat
import com.lisdatasolutions.microrag.core.logging.DefaultAppLogger
import com.lisdatasolutions.microrag.domain.model.ComputeBackend
import com.lisdatasolutions.microrag.domain.model.ModelId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
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
    fun uiHiddenAndAbove_releaseEveryEngine() = runTest {
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

    private companion object {
        // Values of the platform constants deprecated in API 34.
        const val TRIM_MEMORY_RUNNING_CRITICAL = 15
        const val TRIM_MEMORY_COMPLETE = 80
    }
}
