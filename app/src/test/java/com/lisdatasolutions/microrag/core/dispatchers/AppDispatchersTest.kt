package com.lisdatasolutions.microrag.core.dispatchers

import com.google.common.truth.Truth.assertThat
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Test

class AppDispatchersTest {

    private val dispatchers = AppDispatchers.create()

    @After
    fun tearDown() = dispatchers.close()

    @Test
    fun dedicatedDispatchers_runOnTheirNamedThread() = runBlocking {
        // The coroutines debug agent may append " @coroutine#N" to the thread name.
        assertThat(withContext(dispatchers.database) { threadName() }).isEqualTo("microrag-db")
        assertThat(withContext(dispatchers.llm) { threadName() }).isEqualTo("microrag-llm")
        assertThat(withContext(dispatchers.embeddings) { threadName() }).isEqualTo("microrag-embed")
    }

    @Test
    fun dedicatedDispatcher_neverRunsTwoTasksConcurrently() = runBlocking {
        val active = AtomicInteger(0)
        val maxActive = AtomicInteger(0)
        coroutineScope {
            repeat(TASKS) {
                launch(dispatchers.database) {
                    val running = active.incrementAndGet()
                    maxActive.accumulateAndGet(running, ::maxOf)
                    Thread.sleep(2)
                    active.decrementAndGet()
                }
            }
        }
        assertThat(maxActive.get()).isEqualTo(1)
    }

    private fun threadName() = Thread.currentThread().name.substringBefore(" @")

    private companion object {
        const val TASKS = 20
    }
}
