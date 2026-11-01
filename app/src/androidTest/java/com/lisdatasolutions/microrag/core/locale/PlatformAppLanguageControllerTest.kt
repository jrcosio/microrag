package com.lisdatasolutions.microrag.core.locale

import android.app.LocaleManager
import android.os.Build
import android.os.LocaleList
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import com.lisdatasolutions.microrag.domain.settings.AppLanguage
import com.lisdatasolutions.microrag.testing.CapturingLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * F12 T056 (research R2, P2): the real per-app language of Android 13+. The language the app had before
 * the test is put back at the end.
 */
@RunWith(AndroidJUnit4::class)
class PlatformAppLanguageControllerTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val manager = context.getSystemService(LocaleManager::class.java)
    private val controller = PlatformAppLanguageController(context, Dispatchers.IO, CapturingLogger())
    private lateinit var before: LocaleList

    @Before
    fun setUp() {
        assumeTrue(Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
        before = manager.applicationLocales
    }

    @After
    fun tearDown() {
        if (::before.isInitialized) manager.applicationLocales = before
    }

    @Test
    fun englishThenSpanish_throughTheSystemPerAppLanguage() = runBlocking<Unit> {
        assertThat(controller.recreatesItself).isTrue()

        assertThat(controller.apply(AppLanguage.ENGLISH)).isTrue()
        assertThat(manager.applicationLocales.toLanguageTags()).isEqualTo("en")
        assertThat(controller.current()).isEqualTo(AppLanguage.ENGLISH)

        assertThat(controller.apply(AppLanguage.SPANISH)).isTrue()
        assertThat(manager.applicationLocales.toLanguageTags()).isEqualTo("es")
        assertThat(controller.current()).isEqualTo(AppLanguage.SPANISH)
        assertThat(controller.readFailed).isFalse()
    }
}
