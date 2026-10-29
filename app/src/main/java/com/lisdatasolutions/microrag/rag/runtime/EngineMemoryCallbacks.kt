package com.lisdatasolutions.microrag.rag.runtime

import android.content.ComponentCallbacks2
import android.content.res.Configuration
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Frees the engines when the UI is no longer visible or memory runs out (constitution IV). Engines
 * in use are closed as soon as their current block ends; the one the ingest holds stays loaded, and
 * the LLM is never held, so it is always released ([EngineCoordinator.releaseUnheld], F07 research R4).
 * The [listeners] are told first, so the chat stops its answer instead of keeping Gemma busy (F11 R8).
 */
class EngineMemoryCallbacks(
    private val coordinator: EngineCoordinator,
    private val scope: CoroutineScope,
    private val listeners: List<MemoryTrimListener> = emptyList(),
) : ComponentCallbacks2 {

    override fun onTrimMemory(level: Int) {
        if (level >= ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN) release()
    }

    override fun onConfigurationChanged(newConfig: Configuration) = Unit

    @Deprecated("Deprecated in Android; still delivered on older releases.")
    override fun onLowMemory() = release()

    private fun release() {
        scope.launch {
            listeners.forEach { listener ->
                try {
                    listener.onUiHidden()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // A listener that fails must not keep the engines loaded.
                }
            }
            coordinator.releaseUnheld()
        }
    }
}
