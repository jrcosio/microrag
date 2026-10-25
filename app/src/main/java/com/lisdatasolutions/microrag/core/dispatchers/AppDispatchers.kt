package com.lisdatasolutions.microrag.core.dispatchers

import java.io.Closeable
import java.util.concurrent.Executors
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExecutorCoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher

/**
 * Coroutine dispatchers for the whole app (constitution IV). [database], [llm] and [embeddings]
 * are dedicated single-thread dispatchers with a named thread, so work on native resources
 * (SQLite connections, LiteRT-LM engines) is serialized and never runs on the main thread.
 */
class AppDispatchers(
    val main: CoroutineDispatcher,
    val io: CoroutineDispatcher,
    val default: CoroutineDispatcher,
    val database: CoroutineDispatcher,
    val llm: CoroutineDispatcher,
    val embeddings: CoroutineDispatcher,
) : Closeable {

    override fun close() {
        listOf(database, llm, embeddings).forEach { (it as? ExecutorCoroutineDispatcher)?.close() }
    }

    companion object {
        fun create(): AppDispatchers = AppDispatchers(
            main = Dispatchers.Main,
            io = Dispatchers.IO,
            default = Dispatchers.Default,
            database = singleThread("microrag-db"),
            llm = singleThread("microrag-llm"),
            embeddings = singleThread("microrag-embed"),
        )

        private fun singleThread(name: String): ExecutorCoroutineDispatcher =
            Executors.newSingleThreadExecutor { runnable ->
                Thread(runnable, name).apply { isDaemon = true }
            }.asCoroutineDispatcher()
    }
}
