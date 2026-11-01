package com.lisdatasolutions.microrag.domain.settings

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * One-shot notices the app shell announces after a recreation (F12 P7, S9). A single instance lives as
 * long as the process, so «Preferencias guardadas» survives the activity being recreated in the new
 * language and is announced with the new resources.
 */
class SettingsNotices {
    private val saved = MutableStateFlow(false)

    /** True while «Preferencias guardadas» waits to be announced. */
    val savedPending: StateFlow<Boolean> = saved.asStateFlow()

    fun announceSaved() {
        saved.value = true
    }

    /** The shell announced it; a later recreation does not repeat it. */
    fun consumeSaved() {
        saved.value = false
    }
}
