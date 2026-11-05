package com.lisdatasolutions.microrag.rag.runtime

import com.lisdatasolutions.microrag.core.logging.AppLogger
import com.lisdatasolutions.microrag.domain.model.ComputeBackend
import com.lisdatasolutions.microrag.domain.model.ModelCatalog
import com.lisdatasolutions.microrag.domain.model.ModelFamily
import com.lisdatasolutions.microrag.domain.model.ModelId
import java.util.concurrent.atomic.AtomicInteger
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
 * Owns every loaded engine (constitution IV): never two engines on the GPU at once, never two LLMs loaded at once
 * on any backend (F20 analyze I2: Gemma with and without vision), and an engine is never closed while a block is
 * using it.
 *
 * Embedding engines are shared (F10 research R5): several blocks can use one at once, so a query does
 * not wait for the ingest's whole run; each call already runs alone on the single embeddings thread.
 * The LLM is used by one block at a time, as its conversation has state.
 *
 * A caller never waits for another block while it holds the load lock (F07 research R4): if the
 * engine it needs, or the GPU, is in use, it waits outside and tries again, so engines that are free
 * keep working. Closing is the exception: it waits for the entry's users with the load lock taken, so
 * nobody enters the entry meanwhile, and a block that ends therefore never takes the load lock. Blocks
 * must not call [withEngine] again.
 */
class EngineCoordinator(private val logger: AppLogger) {

    private class Entry(val engine: ManagedEngine, val shared: Boolean) {
        /** Exclusive engines (the LLM): one block at a time. */
        val useLock = Mutex()

        /** Shared engines (embeddings): blocks using it now. Raised under the load lock, lowered without it. */
        val users = AtomicInteger(0)

        /** Blocks of the ingest holding it: memory callbacks leave it loaded ([releaseUnheld]). */
        val holds = AtomicInteger(0)

        val inUse: Boolean get() = if (shared) users.get() > 0 else useLock.isLocked
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

    /** Closes every engine of [model], whatever its backend or kind: before its file is deleted (F19 contract D1). */
    suspend fun release(model: ModelId) {
        loadLock.withLock { entries.keys.filter { it.model == model }.forEach { closeLocked(it) } }
    }

    suspend fun releaseAll() {
        loadLock.withLock { entries.keys.toList().forEach { closeLocked(it) } }
    }

    /** Closes every engine not held by the ingest (EngineMemoryCallbacks). */
    suspend fun releaseUnheld() {
        loadLock.withLock { entries.filterValues { it.holds.get() == 0 }.keys.toList().forEach { closeLocked(it) } }
    }

    /** Closes the generation engines (the LLM) before the ingest loads its embedder (constitution IV). */
    suspend fun releaseGenerators() {
        loadLock.withLock {
            entries.keys.filter { ModelCatalog.byId(it.model).family == ModelFamily.LLM }.forEach { closeLocked(it) }
        }
    }

    private suspend fun <T : ManagedEngine, R> use(key: EngineKey, factory: suspend () -> T, held: Boolean, block: suspend (T) -> R): R {
        val entry = acquire(key, factory, held)
        try {
            @Suppress("UNCHECKED_CAST")
            return block(entry.engine as T)
        } finally {
            // Without the load lock: a release may be waiting for this block with it taken.
            if (held) entry.holds.decrementAndGet()
            if (entry.shared) entry.users.decrementAndGet() else entry.useLock.unlock()
            changes.update { it + 1 }
        }
    }

    /** The entry for [key] in use by the caller (and held when [held]), loading it if needed. */
    private suspend fun acquire(key: EngineKey, factory: suspend () -> ManagedEngine, held: Boolean): Entry {
        while (true) {
            val seen = changes.value
            val entry = loadLock.withLock {
                val existing = entries[key]
                // Taken before the load lock is released so no release can close it in between.
                when {
                    existing != null -> existing.takeIf { it.enter() }
                    conflictsInUse(key) -> null
                    else -> load(key, factory)
                }?.also { if (held) it.holds.incrementAndGet() }
            }
            if (entry != null) return entry
            changes.first { it != seen }
        }
    }

    /** Under the load lock: a shared engine always lets one more block in, an exclusive one only when free. */
    private fun Entry.enter(): Boolean = if (shared) {
        users.incrementAndGet()
        true
    } else {
        useLock.tryLock()
    }

    /** The engines [key] cannot be loaded beside: the other GPU ones for a GPU engine, the other LLMs for an LLM. */
    private fun conflicts(key: EngineKey): List<EngineKey> = entries.keys.filter { other ->
        other != key && (
            (key.backend == ComputeBackend.GPU && other.backend == ComputeBackend.GPU) ||
                (key.isLlm() && other.isLlm())
            )
    }

    private fun conflictsInUse(key: EngineKey): Boolean = conflicts(key).any { entries.getValue(it).inUse }

    private fun EngineKey.isLlm(): Boolean = ModelCatalog.byId(model).family == ModelFamily.LLM

    /** Under the load lock: no conflicting engine is in use, so closing them does not wait. */
    private suspend fun load(key: EngineKey, factory: suspend () -> ManagedEngine): Entry {
        conflicts(key).forEach { closeLocked(it) }
        val entry = Entry(factory(), shared = ModelCatalog.byId(key.model).family == ModelFamily.EMBEDDING)
        entries[key] = entry
        entry.enter()
        publish()
        logger.i(TAG) { "engine load ${key.label()}" }
        return entry
    }

    /** Under the load lock: waits for the entry's users (none can enter meanwhile), then closes it. */
    private suspend fun closeLocked(key: EngineKey) {
        val entry = entries[key] ?: return
        withContext(NonCancellable) {
            if (entry.shared) {
                changes.first { entry.users.get() == 0 }
                closeEngine(key, entry)
            } else {
                entry.useLock.withLock { closeEngine(key, entry) }
            }
        }
        entries.remove(key)
        publish()
        logger.i(TAG) { "engine release ${key.label()}" }
    }

    private fun closeEngine(key: EngineKey, entry: Entry) {
        runCatching { entry.engine.close() }
            .onFailure { logger.w(TAG, it) { "engine close failed ${key.label()}" } }
    }

    private fun EngineKey.label(): String = if (kind == EngineKind.DEFAULT) "$model $backend" else "$model $backend $kind"

    private fun publish() {
        _loaded.value = entries.keys.toSet()
        changes.update { it + 1 }
    }

    private companion object {
        const val TAG = "MicroRAG"
    }
}
