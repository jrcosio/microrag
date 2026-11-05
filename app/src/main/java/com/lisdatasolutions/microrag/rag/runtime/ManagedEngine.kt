package com.lisdatasolutions.microrag.rag.runtime

import com.lisdatasolutions.microrag.domain.model.ComputeBackend
import com.lisdatasolutions.microrag.domain.model.ModelId

/** A loaded native engine owned by [EngineCoordinator]; [close] frees its memory. */
interface ManagedEngine : AutoCloseable {
    val backend: ComputeBackend
}

/**
 * What a model file is loaded as (F19 analyze H2): [DEFAULT] is Gemma's generator or an embedder's text side;
 * [IMAGE_EMBEDDER] is the 440M with its vision encoder, another native engine than its text one;
 * [VISION_GENERATOR] is Gemma with its vision encoder, for an answer with a page (F20 research R5).
 */
enum class EngineKind { DEFAULT, IMAGE_EMBEDDER, VISION_GENERATOR }

data class EngineKey(val model: ModelId, val backend: ComputeBackend, val kind: EngineKind = EngineKind.DEFAULT)

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
