package com.lisdatasolutions.microrag.core.locale

import android.app.LocaleManager
import android.content.Context
import android.os.Build
import android.os.LocaleList
import com.lisdatasolutions.microrag.core.logging.AppLogger
import com.lisdatasolutions.microrag.domain.settings.AppLanguage
import com.lisdatasolutions.microrag.domain.settings.AppLanguageController
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/**
 * The app language on the platform, without AppCompat (F12 research R2, P1–P4):
 * - Android 13+: `LocaleManager.setApplicationLocales` with an explicit `es` or `en`; the system keeps it
 *   and recreates the activities;
 * - Android 12: the private file of [AppLocale], written atomically on [io]; the screen recreates itself.
 * Only the kind of a failure is logged, never the language (P18).
 */
class PlatformAppLanguageController(
    private val context: Context,
    private val io: CoroutineDispatcher,
    private val logger: AppLogger,
) : AppLanguageController {

    override fun current(): AppLanguage = AppLanguage.fromTag(AppLocale.resolve(context).language)

    override val readFailed: Boolean get() = AppLocale.readFailed

    override val recreatesItself: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    override suspend fun apply(language: AppLanguage): Boolean {
        if (recreatesItself) {
            context.getSystemService(LocaleManager::class.java).applicationLocales = LocaleList.forLanguageTags(language.tag)
            return true
        }
        return withContext(io) {
            val file = AppLocale.file(context)
            val temporary = File(file.parentFile, "${file.name}.tmp")
            try {
                file.parentFile?.mkdirs()
                temporary.writeText(language.tag)
                if (!temporary.renameTo(file)) throw IOException("rename failed")
                AppLocale.remember(language.tag)
                true
            } catch (e: IOException) {
                temporary.delete()
                logger.w(TAG, e) { "app language not stored: ${e.javaClass.simpleName}" }
                false
            }
        }
    }

    private companion object {
        const val TAG = "MicroRAG"
    }
}
