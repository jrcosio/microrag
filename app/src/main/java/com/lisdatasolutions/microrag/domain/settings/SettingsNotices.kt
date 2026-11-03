package com.lisdatasolutions.microrag.domain.settings

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * One-shot notices the app shell announces after a recreation (F12 P7, S9). A single instance lives as
 * long as the process, so «Preferencias guardadas» survives the activity being recreated in the new
 * language or theme and is announced with the new resources.
 */
class SettingsNotices {
    private val saved = MutableStateFlow(false)
    private val settle = MutableStateFlow(false)

    /** True while «Preferencias guardadas» waits to be announced. */
    val savedPending: StateFlow<Boolean> = saved.asStateFlow()

    /** True when the announcement must wait a margin first, so a theme recreation comes before it (F16 P6). */
    val settlePending: StateFlow<Boolean> = settle.asStateFlow()

    fun announceSaved(settle: Boolean = false) {
        this.settle.value = settle
        saved.value = true
    }

    /** The shell announced it; a later recreation does not repeat it. */
    fun consumeSaved() {
        saved.value = false
        settle.value = false
    }
}
