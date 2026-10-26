package com.lisdatasolutions.microrag.rag.runtime

import com.lisdatasolutions.microrag.core.logging.AppLogger
import com.lisdatasolutions.microrag.domain.model.ComputeBackend
import com.lisdatasolutions.microrag.domain.model.ModelCatalog
import com.lisdatasolutions.microrag.domain.model.ModelFamily
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Owns every loaded engine (constitution IV): never two engines on the GPU at once, and an
 * engine is never closed while a block is using it.
 *
 * Lock order is always load lock, then use lock. A caller never waits for another block while it
 * holds the load lock (F07 research R4): if the engine it needs, or the GPU, is in use, it waits
 * outside and tries again, so engines that are free keep working. Blocks must not call [withEngine]
 * again.
 */
class EngineCoordinator(private val logger: AppLogger) {

    private class Entry(val engine: ManagedEngine) {
        val useLock = Mutex()

        /** Held by the ingest: memory callbacks leave it loaded ([releaseUnheld]). */
        @Volatile
        var held = false
    }

    private val loadLock = Mutex()
    private val entries = linkedMapOf<EngineKey, Entry>()
    private val _loaded = MutableStateFlow<Set<EngineKey>>(emptySet())

    /** Bumped whenever a block ends or an engine closes, so waiting callers try again. */
    private val changes = MutableStateFlow(0L)

    val loaded: StateFlow<Set<EngineKey>> = _loaded.asStateFlow()

    /**
     * Runs [block] with the engine for [key], creating it with [factory] if needed. Loading a GPU
     * engine first closes any other GPU engine, waiting while that one is in use. The engine stays
     * loaded until [release].
     */
    suspend fun <T : ManagedEngine, R> withEngine(
        key: EngineKey,
        factory: suspend () -> T,
        block: suspend (T) -> R,
    ): R = use(key, factory, held = false, block)

    /**
     * Like [withEngine], but the engine stays held until [block] ends: [releaseUnheld] does not close it.
     * Only embedding engines can be held, so the LLM is always released when the UI hides (F07 research R4).
     */
    suspend fun <T : ManagedEngine, R> withHeldEngine(
        key: EngineKey,
        factory: suspend () -> T,
        block: suspend (T) -> R,
    ): R {
        require(ModelCatalog.byId(key.model).family == ModelFamily.EMBEDDING) { "Only embedding engines can be held: ${key.model}" }
        return use(key, factory, held = true, block)
    }

    suspend fun release(key: EngineKey) {
        loadLock.withLock { closeLocked(key) }
    }

    suspend fun releaseAll() {
        loadLock.withLock { entries.keys.toList().forEach { closeLocked(it) } }
    }

    /** Closes every engine not held by the ingest (EngineMemoryCallbacks). */
    suspend fun releaseUnheld() {
        loadLock.withLock { entries.filterValues { !it.held }.keys.toList().forEach { closeLocked(it) } }
    }

    /** Closes the generation engines (the LLM) before the ingest loads its embedder (constitution IV). */
    suspend fun releaseGenerators() {
        loadLock.withLock {
            entries.keys.filter { ModelCatalog.byId(it.model).family == ModelFamily.LLM }.forEach { closeLocked(it) }
        }
    }

    private suspend fun <T : ManagedEngine, R> use(key: EngineKey, factory: suspend () -> T, held: Boolean, block: suspend (T) -> R): R {
        val entry = acquire(key, factory)
        entry.held = held
        try {
            @Suppress("UNCHECKED_CAST")
            return block(entry.engine as T)
        } finally {
            entry.held = false
            entry.useLock.unlock()
            changes.update { it + 1 }
        }
    }

    /** The entry for [key] with its use lock taken, loading it if needed. */
    private suspend fun acquire(key: EngineKey, factory: suspend () -> ManagedEngine): Entry {
        while (true) {
            val seen = changes.value
            val entry = loadLock.withLock {
                val existing = entries[key]
                when {
                    // Taken before the load lock is released so no release can close it in between.
                    existing != null -> existing.takeIf { it.useLock.tryLock() }
                    key.backend == ComputeBackend.GPU && otherGpuInUse(key) -> null
                    else -> load(key, factory)
                }
            }
            if (entry != null) return entry
            changes.first { it != seen }
        }
    }

    private fun otherGpuInUse(key: EngineKey): Boolean =
        entries.any { (other, entry) -> other != key && other.backend == ComputeBackend.GPU && entry.useLock.isLocked }

    /** Under the load lock: no other GPU engine is in use, so closing them does not wait. */
    private suspend fun load(key: EngineKey, factory: suspend () -> ManagedEngine): Entry {
        if (key.backend == ComputeBackend.GPU) {
            entries.keys.filter { it.backend == ComputeBackend.GPU }.forEach { closeLocked(it) }
        }
        val entry = Entry(factory())
        entries[key] = entry
        entry.useLock.lock()
        publish()
        logger.i(TAG) { "engine load ${key.model} ${key.backend}" }
        return entry
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
        changes.update { it + 1 }
    }

    private companion object {
        const val TAG = "MicroRAG"
    }
}
