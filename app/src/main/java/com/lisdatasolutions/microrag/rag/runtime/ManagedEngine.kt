package com.lisdatasolutions.microrag.rag.runtime

import com.lisdatasolutions.microrag.domain.model.ComputeBackend
import com.lisdatasolutions.microrag.domain.model.ModelId

/** A loaded native engine owned by [EngineCoordinator]; [close] frees its memory. */
interface ManagedEngine : AutoCloseable {
    val backend: ComputeBackend
}

data class EngineKey(val model: ModelId, val backend: ComputeBackend)

/**
 * Runs [init] on a freshly created engine and closes it if initialization fails or is cancelled,
 * so a half-loaded native engine never leaks.
 */
suspend fun <T : ManagedEngine> T.initializeOrClose(init: suspend (T) -> Unit): T {
    try {
        init(this)
    } catch (t: Throwable) {
        close()
        throw t
    }
    return this
}
