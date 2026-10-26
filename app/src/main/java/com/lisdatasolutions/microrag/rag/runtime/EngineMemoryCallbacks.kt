package com.lisdatasolutions.microrag.rag.runtime

import android.content.ComponentCallbacks2
import android.content.res.Configuration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Frees the engines when the UI is no longer visible or memory runs out (constitution IV). Engines
 * in use are closed as soon as their current block ends; the one the ingest holds stays loaded, and
 * the LLM is never held, so it is always released ([EngineCoordinator.releaseUnheld], F07 research R4).
 */
class EngineMemoryCallbacks(
    private val coordinator: EngineCoordinator,
    private val scope: CoroutineScope,
) : ComponentCallbacks2 {

    override fun onTrimMemory(level: Int) {
        if (level >= ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN) scope.launch { coordinator.releaseUnheld() }
    }

    override fun onConfigurationChanged(newConfig: Configuration) = Unit

    @Deprecated("Deprecated in Android; still delivered on older releases.")
    override fun onLowMemory() {
        scope.launch { coordinator.releaseUnheld() }
    }
}
