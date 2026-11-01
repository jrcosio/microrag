package com.lisdatasolutions.microrag.core.locale

import android.app.Application
import android.app.LocaleManager
import android.content.Context
import android.os.LocaleList
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.lisdatasolutions.microrag.R
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** F12 T038 (research R2, P1–P5): the app language on Android 12 (sdk 32) and Android 13+ (sdk 36). */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class, qualifiers = "en")
class AppLocaleTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val file = AppLocale.file(context)

    @After
    fun tearDown() {
        file.deleteRecursively()
        AppLocale.init(context)
    }

    private fun systemPerApp(tags: String) {
        context.getSystemService(LocaleManager::class.java).applicationLocales = LocaleList.forLanguageTags(tags)
    }

    private fun systemPerApp() = context.getSystemService(LocaleManager::class.java).applicationLocales.toLanguageTags()

    @Test
    @Config(sdk = [32])
    fun spanishByDefault_evenWithTheSystemInEnglish_onAndroid12() {
        AppLocale.init(context)

        assertThat(AppLocale.resolve(context).language).isEqualTo("es")
        assertThat(AppLocale.readFailed).isFalse()
    }

    @Test
    @Config(sdk = [36])
    fun spanishByDefault_evenWithTheSystemInEnglish_onAndroid13Plus() {
        assertThat(AppLocale.resolve(context).language).isEqualTo("es")
    }

    @Test
    @Config(sdk = [32])
    fun onAndroid12_theFileIsReadAtInit() {
        file.parentFile!!.mkdirs()
        file.writeText("en")

        AppLocale.init(context)

        assertThat(AppLocale.resolve(context).language).isEqualTo("en")
    }

    @Test
    @Config(sdk = [32])
    fun onAndroid12_anUnreadableFile_givesSpanish_andSaysSo() {
        file.mkdirs()

        AppLocale.init(context)

        assertThat(AppLocale.resolve(context).language).isEqualTo("es")
        assertThat(AppLocale.readFailed).isTrue()
    }

    @Test
    @Config(sdk = [36])
    fun onAndroid13Plus_theSystemPerAppLanguageCounts() {
        systemPerApp("en")

        assertThat(AppLocale.resolve(context).language).isEqualTo("en")
    }

    @Test
    @Config(sdk = [36])
    fun theFileLeftByAndroid12_isAppliedOnce_andDeleted() {
        file.parentFile!!.mkdirs()
        file.writeText("en")

        AppLocale.migrate(context)
        assertThat(systemPerApp()).isEqualTo("en")
        assertThat(file.exists()).isFalse()

        systemPerApp("es")
        AppLocale.migrate(context)
        assertThat(systemPerApp()).isEqualTo("es")
    }

    @Test
    @Config(sdk = [36])
    fun aChoiceMadeInTheSystem_winsOverAnOldFile() {
        systemPerApp("es")
        file.parentFile!!.mkdirs()
        file.writeText("en")

        AppLocale.migrate(context)

        assertThat(systemPerApp()).isEqualTo("es")
        assertThat(file.exists()).isFalse()
    }

    @Test
    @Config(sdk = [36])
    fun localizedTexts_followTheCurrentLanguage_onAndroid13Plus() {
        assertThat(AppLocale.localized(context).getString(R.string.settings_title)).isEqualTo("Ajustes")

        systemPerApp("en")

        assertThat(AppLocale.localized(context).getString(R.string.settings_title)).isEqualTo("Settings")
    }

    @Test
    @Config(sdk = [32])
    fun localizedTexts_followTheCurrentLanguage_onAndroid12() {
        AppLocale.init(context)
        assertThat(AppLocale.localized(context).getString(R.string.settings_title)).isEqualTo("Ajustes")

        file.parentFile!!.mkdirs()
        file.writeText("en")
        AppLocale.init(context)

        assertThat(AppLocale.localized(context).getString(R.string.settings_title)).isEqualTo("Settings")
    }
}
