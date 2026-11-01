package com.lisdatasolutions.microrag.core.locale

import android.app.Application
import android.app.LocaleManager
import android.content.Context
import android.os.LocaleList
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.lisdatasolutions.microrag.domain.settings.AppLanguage
import com.lisdatasolutions.microrag.testing.RecordingLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** F12 T039 (P2–P3, P18): storing the app language on Android 12 (sdk 32) and Android 13+ (sdk 36). */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class, qualifiers = "en")
class PlatformAppLanguageControllerTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val file = AppLocale.file(context)
    private val logger = RecordingLogger()
    private val controller = PlatformAppLanguageController(context, Dispatchers.Unconfined, logger)

    @After
    fun tearDown() {
        file.parentFile!!.deleteRecursively()
        AppLocale.init(context)
    }

    @Test
    @Config(sdk = [32])
    fun onAndroid12_applyWritesTheFile_andTheLanguageInMemory() = runBlocking<Unit> {
        AppLocale.init(context)
        assertThat(controller.recreatesItself).isFalse()
        assertThat(controller.current()).isEqualTo(AppLanguage.SPANISH)

        assertThat(controller.apply(AppLanguage.ENGLISH)).isTrue()

        assertThat(file.readText()).isEqualTo("en")
        assertThat(file.parentFile!!.listFiles()!!.map { it.name }).containsExactly(file.name)
        assertThat(controller.current()).isEqualTo(AppLanguage.ENGLISH)
        assertThat(AppLocale.resolve(context).language).isEqualTo("en")

        assertThat(controller.apply(AppLanguage.SPANISH)).isTrue()
        assertThat(file.readText()).isEqualTo("es")
        assertThat(controller.current()).isEqualTo(AppLanguage.SPANISH)
    }

    @Test
    @Config(sdk = [32])
    fun onAndroid12_aFailedWrite_returnsFalse_andChangesNothing() = runBlocking<Unit> {
        AppLocale.init(context)
        // A file where the folder must be: nothing can be written inside it.
        file.parentFile!!.parentFile!!.mkdirs()
        file.parentFile!!.writeText("not a folder")

        assertThat(controller.apply(AppLanguage.ENGLISH)).isFalse()

        assertThat(controller.current()).isEqualTo(AppLanguage.SPANISH)
        assertThat(file.parentFile!!.readText()).isEqualTo("not a folder")
        // P18: only the kind of failure is logged, never the value.
        assertThat(logger.lines).hasSize(1)
        assertThat(logger.lines.single()).matches("app language not stored: \\w+")
        file.parentFile!!.delete()
    }

    @Test
    @Config(sdk = [32])
    fun onAndroid12_anUnreadableFile_isReported() {
        file.mkdirs()
        AppLocale.init(context)

        assertThat(controller.readFailed).isTrue()
        assertThat(controller.current()).isEqualTo(AppLanguage.SPANISH)
    }

    @Test
    @Config(sdk = [36])
    fun onAndroid13Plus_applySetsTheSystemPerAppLanguage_andCurrentFollowsIt() = runBlocking<Unit> {
        val manager = context.getSystemService(LocaleManager::class.java)
        assertThat(controller.recreatesItself).isTrue()

        assertThat(controller.apply(AppLanguage.ENGLISH)).isTrue()
        assertThat(manager.applicationLocales.toLanguageTags()).isEqualTo("en")
        assertThat(controller.current()).isEqualTo(AppLanguage.ENGLISH)

        assertThat(controller.apply(AppLanguage.SPANISH)).isTrue()
        assertThat(manager.applicationLocales.toLanguageTags()).isEqualTo("es")

        // A change made in the system settings is read back.
        manager.applicationLocales = LocaleList.forLanguageTags("en")
        assertThat(controller.current()).isEqualTo(AppLanguage.ENGLISH)
        assertThat(controller.readFailed).isFalse()
        assertThat(file.exists()).isFalse()
    }
}
