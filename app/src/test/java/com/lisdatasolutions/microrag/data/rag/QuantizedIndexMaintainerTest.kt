package com.lisdatasolutions.microrag.data.rag

import com.google.common.truth.Truth.assertThat
import com.lisdatasolutions.microrag.data.rag.QuantizedIndexMaintainer.Reason
import com.lisdatasolutions.microrag.testing.FakeRagIndex
import com.lisdatasolutions.microrag.testing.RecordingLogger
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test

/** F14 T013 (research R3, contract I4): when the INT8 copy is rebuilt and preloaded. */
@OptIn(ExperimentalCoroutinesApi::class)
class QuantizedIndexMaintainerTest {

    private val index = FakeRagIndex()
    private val logger = RecordingLogger()
    private var running = false

    private fun kotlinx.coroutines.test.TestScope.maintainer(minChunks: Int = 100) = QuantizedIndexMaintainer(
        index = index,
        ingest = IngestActivity { running },
        scope = backgroundScope,
        logger = logger,
        minChunks = { minChunks },
        minPages = { minChunks / 2 },
        debounceMs = DEBOUNCE,
    )

    @Test
    fun requestsCloseTogether_refreshOnce() = runTest {
        val maintainer = maintainer()

        maintainer.request(Reason.LIBRARY_CHANGED)
        advanceTimeBy(DEBOUNCE / 2)
        maintainer.request(Reason.LIBRARY_CHANGED)
        advanceTimeBy(DEBOUNCE / 2)
        maintainer.request(Reason.INGEST_FINISHED)
        advanceTimeBy(DEBOUNCE + 1)
        runCurrent()

        assertThat(index.quantizedRefreshes).containsExactly(100)
    }

    @Test
    fun aRunningBatch_waitsForItsEnd() = runTest {
        val maintainer = maintainer()
        running = true

        maintainer.request(Reason.LIBRARY_CHANGED)
        advanceTimeBy(DEBOUNCE + 1)
        runCurrent()
        running = false
        maintainer.request(Reason.INGEST_FINISHED)
        advanceTimeBy(DEBOUNCE + 1)
        runCurrent()

        assertThat(index.quantizedRefreshes).hasSize(1)
    }

    @Test
    fun theStart_refreshesWithTheMinimum() = runTest {
        val maintainer = maintainer(minChunks = 10_000)

        maintainer.request(Reason.STARTUP)
        advanceTimeBy(DEBOUNCE + 1)
        runCurrent()

        assertThat(index.quantizedRefreshes).containsExactly(10_000)
        assertThat(index.quantizedPageMinimums).containsExactly(5_000)
        assertThat(logger.lines.single()).contains("reason=STARTUP")
    }

    @Test
    fun aFailedRefresh_isLogged_andTheNextRequestStillRuns() = runTest {
        val maintainer = maintainer()
        index.failNextQuantizedRefresh = true

        maintainer.request(Reason.LIBRARY_CHANGED)
        advanceTimeBy(DEBOUNCE + 1)
        runCurrent()
        maintainer.request(Reason.LIBRARY_CHANGED)
        advanceTimeBy(DEBOUNCE + 1)
        runCurrent()

        assertThat(index.quantizedRefreshes).hasSize(2)
        assertThat(logger.lines.first()).contains("failed")
    }

    private companion object {
        const val DEBOUNCE = 2_000L
    }
}
