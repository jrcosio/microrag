package com.lisdatasolutions.microrag.rag.runtime

import com.lisdatasolutions.microrag.core.logging.AppLogger
import com.lisdatasolutions.microrag.domain.model.ComputeBackend
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Owns every loaded engine (constitution IV): never two engines on the GPU at once, and an
 * engine is never closed while a block is using it.
 *
 * Lock order is always load lock, then use lock. Blocks must not call [withEngine] again.
 */
class EngineCoordinator(private val logger: AppLogger) {

    private class Entry(val engine: ManagedEngine) {
        val useLock = Mutex()
    }

    private val loadLock = Mutex()
    private val entries = linkedMapOf<EngineKey, Entry>()
    private val _loaded = MutableStateFlow<Set<EngineKey>>(emptySet())

    val loaded: StateFlow<Set<EngineKey>> = _loaded.asStateFlow()

    /**
     * Runs [block] with the engine for [key], creating it with [factory] if needed. Loading a GPU
     * engine first closes any other GPU engine. The engine stays loaded until [release].
     */
    suspend fun <T : ManagedEngine, R> withEngine(
        key: EngineKey,
        factory: suspend () -> T,
        block: suspend (T) -> R,
    ): R {
        val entry = loadLock.withLock {
            val entry = entries[key] ?: run {
                if (key.backend == ComputeBackend.GPU) {
                    entries.keys.filter { it.backend == ComputeBackend.GPU }.forEach { closeLocked(it) }
                }
                val engine = factory()
                Entry(engine).also {
                    entries[key] = it
                    publish()
                    logger.i(TAG) { "engine load ${key.model} ${key.backend}" }
                }
            }
            // Taken before the load lock is released so no release can close it in between.
            entry.useLock.lock()
            entry
        }
        try {
            @Suppress("UNCHECKED_CAST")
            return block(entry.engine as T)
        } finally {
            entry.useLock.unlock()
        }
    }

    suspend fun release(key: EngineKey) {
        loadLock.withLock { closeLocked(key) }
    }

    suspend fun releaseAll() {
        loadLock.withLock { entries.keys.toList().forEach { closeLocked(it) } }
    }

    private suspend fun closeLocked(key: EngineKey) {
        val entry = entries[key] ?: return
        withContext(NonCancellable) {
            entry.useLock.withLock {
                runCatching { entry.engine.close() }
                    .onFailure { logger.w(TAG, it) { "engine close failed ${key.model} ${key.backend}" } }
            }
        }
        entries.remove(key)
        publish()
        logger.i(TAG) { "engine release ${key.model} ${key.backend}" }
    }

    private fun publish() {
        _loaded.value = entries.keys.toSet()
    }

    private companion object {
        const val TAG = "MicroRAG"
    }
}
