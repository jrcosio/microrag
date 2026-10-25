package com.lisdatasolutions.microrag.rag.runtime

import android.content.ComponentCallbacks2
import android.content.res.Configuration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Frees every engine when the UI is no longer visible or memory runs out (constitution IV). Engines
 * in use are closed as soon as their current block ends ([EngineCoordinator.releaseAll]).
 */
class EngineMemoryCallbacks(
    private val coordinator: EngineCoordinator,
    private val scope: CoroutineScope,
) : ComponentCallbacks2 {

    override fun onTrimMemory(level: Int) {
        if (level >= ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN) scope.launch { coordinator.releaseAll() }
    }

    override fun onConfigurationChanged(newConfig: Configuration) = Unit

    @Deprecated("Deprecated in Android; still delivered on older releases.")
    override fun onLowMemory() {
        scope.launch { coordinator.releaseAll() }
    }
}
