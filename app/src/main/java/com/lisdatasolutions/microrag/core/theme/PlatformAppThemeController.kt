package com.lisdatasolutions.microrag.core.theme

import android.app.UiModeManager
import android.content.Context
import com.lisdatasolutions.microrag.core.logging.AppLogger
import com.lisdatasolutions.microrag.domain.settings.AppThemeController
import com.lisdatasolutions.microrag.domain.settings.ThemePreference

/**
 * The app theme on the platform, without AppCompat (F16 research R1, theme-api P4): the system's per-app
 * night mode (API 31), which it keeps and applies before the app's first window. SYSTEM is
 * `MODE_NIGHT_AUTO`, which imposes nothing. Only the kind of a failure is logged, never the theme (P7).
 */
class PlatformAppThemeController(
    private val context: Context,
    private val logger: AppLogger,
) : AppThemeController {

    override fun apply(preference: ThemePreference): Boolean = try {
        context.getSystemService(UiModeManager::class.java).setApplicationNightMode(preference.nightMode())
        true
    } catch (e: RuntimeException) {
        logger.w(TAG, e) { "theme not applied: ${e.javaClass.simpleName}" }
        false
    }

    private fun ThemePreference.nightMode(): Int = when (this) {
        ThemePreference.SYSTEM -> UiModeManager.MODE_NIGHT_AUTO
        ThemePreference.LIGHT -> UiModeManager.MODE_NIGHT_NO
        ThemePreference.DARK -> UiModeManager.MODE_NIGHT_YES
    }

    private companion object {
        const val TAG = "MicroRAG"
    }
}
