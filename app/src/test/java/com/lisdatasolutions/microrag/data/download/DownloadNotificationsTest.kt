package com.lisdatasolutions.microrag.data.download

import android.app.Application
import android.app.LocaleManager
import android.app.Notification
import android.content.Context
import android.os.LocaleList
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.lisdatasolutions.microrag.domain.model.DownloadProgress
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** F12 T040 (P5): the download notification follows a new app language without a restart. */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class, qualifiers = "es", sdk = [36])
class DownloadNotificationsTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val notifications = DownloadNotifications(context)
    private val manager = context.getSystemService(LocaleManager::class.java)

    @After
    fun tearDown() {
        manager.applicationLocales = LocaleList.getEmptyLocaleList()
    }

    private fun Notification.title() = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()

    @Test
    fun theNextNotification_followsTheAppLanguage() {
        assertThat(notifications.build(DownloadProgress()).title()).isEqualTo("Preparando la descarga")

        manager.applicationLocales = LocaleList.forLanguageTags("en")

        assertThat(notifications.build(DownloadProgress()).title()).isEqualTo("Preparing the download")
    }
}
