package com.lisdatasolutions.microrag.domain.settings

/**
 * The system's per-app night mode (F16 research R1): it is applied before the app's first window, so the
 * splash screen, the system bars and Compose follow it. The system recreates the activities.
 */
interface AppThemeController {
    /** False when the system refused it; the theme did not change then. */
    fun apply(preference: ThemePreference): Boolean
}
