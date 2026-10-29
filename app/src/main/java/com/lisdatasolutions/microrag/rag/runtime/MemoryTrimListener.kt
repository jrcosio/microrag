package com.lisdatasolutions.microrag.rag.runtime

/**
 * Told when the UI is hidden or memory runs low, before [EngineMemoryCallbacks] releases the engines
 * (F11 research R8): the chat interrupts its answer so Gemma can be released (constitution IV).
 */
interface MemoryTrimListener {
    suspend fun onUiHidden()
}
