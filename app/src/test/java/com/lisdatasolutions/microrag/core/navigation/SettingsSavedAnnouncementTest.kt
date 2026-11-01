package com.lisdatasolutions.microrag.core.navigation

import android.app.Application
import android.app.LocaleManager
import android.content.Context
import android.os.LocaleList
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.lisdatasolutions.microrag.core.designsystem.theme.LisdataTheme
import com.lisdatasolutions.microrag.domain.settings.SettingsNotices
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * F12 T053 (P7, S9): «Preferencias guardadas» is announced politely by the shell, once, and only when the
 * screen already shows the new language, so never with the resources of the activity about to be recreated.
 */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class, sdk = [36])
class SettingsSavedAnnouncementTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val notices = SettingsNotices()

    @After
    fun tearDown() {
        context.getSystemService(LocaleManager::class.java).applicationLocales = LocaleList.getEmptyLocaleList()
    }

    private fun announcements(text: String) = compose.onAllNodes(
        SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite) and
            SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, text),
    )

    /** Bumped to compose the announcement again from scratch, as a recreated activity does. */
    private var generation by mutableIntStateOf(0)

    private fun show() = compose.setContent { LisdataTheme { key(generation) { SettingsSavedAnnouncement(notices) } } }

    @Test
    @Config(qualifiers = "es")
    fun theScreenStillInTheOldLanguage_waits() {
        context.getSystemService(LocaleManager::class.java).applicationLocales = LocaleList.forLanguageTags("en")
        notices.announceSaved()

        show()

        announcements("Preferences saved").assertCountEquals(0)
        announcements("Preferencias guardadas").assertCountEquals(0)
        assertThat(notices.savedPending.value).isTrue()
    }

    @Test
    @Config(qualifiers = "en")
    fun theRecreatedScreen_announcesItInTheNewLanguage_once() {
        context.getSystemService(LocaleManager::class.java).applicationLocales = LocaleList.forLanguageTags("en")
        notices.announceSaved()

        show()

        announcements("Preferences saved").assertCountEquals(1)
        assertThat(notices.savedPending.value).isFalse()

        generation++
        compose.waitForIdle()

        announcements("Preferences saved").assertCountEquals(0)
    }

    @Test
    @Config(qualifiers = "es")
    fun backToSpanish_isAnnouncedInSpanish() {
        notices.announceSaved()

        show()

        announcements("Preferencias guardadas").assertCountEquals(1)
    }
}
