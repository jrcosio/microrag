package com.lisdatasolutions.microrag.data.indexing

import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.lisdatasolutions.microrag.MainActivity
import com.lisdatasolutions.microrag.domain.ingest.IngestProgress
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** F07 T037 (research R3, proposal P11): channel, texts without names, progress, action and tap. */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class, qualifiers = "es")
class IndexingNotificationsTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val notifications = IndexingNotifications(context)

    private fun Notification.text() = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()

    @Test
    fun aLowImportanceChannelOfItsOwn() {
        notifications.ensureChannel()

        val channel = context.getSystemService(NotificationManager::class.java).getNotificationChannel(IndexingNotifications.CHANNEL_ID)
        assertThat(channel.importance).isEqualTo(NotificationManager.IMPORTANCE_LOW)
    }

    @Test
    fun realProgressWithoutFileNames() {
        val embedding = notifications.build(IngestProgress(5, IngestProgress.Stage.EMBEDDING, chunksDone = 1_250, chunksTotal = 4_300))

        assertThat(embedding.extras.getCharSequence(Notification.EXTRA_TITLE).toString()).isEqualTo("Indexando documentos")
        assertThat(embedding.text()).isEqualTo("1.250 de 4.300 fragmentos")
        assertThat(embedding.extras.getInt(Notification.EXTRA_PROGRESS)).isEqualTo(1_250)
        assertThat(embedding.extras.getInt(Notification.EXTRA_PROGRESS_MAX)).isEqualTo(4_300)
        assertThat(embedding.extras.getBoolean(Notification.EXTRA_PROGRESS_INDETERMINATE)).isFalse()

        val preparing = notifications.build(null)
        assertThat(preparing.text()).isEqualTo("Preparando documentos")
        assertThat(preparing.extras.getBoolean(Notification.EXTRA_PROGRESS_INDETERMINATE)).isTrue()

        val hot = notifications.build(IngestProgress(5, IngestProgress.Stage.EMBEDDING, waiting = IngestProgress.WaitReason.THERMAL))
        assertThat(hot.text()).isEqualTo("Pausa por temperatura")
    }

    @Test
    fun everyNotification_expiresUnlessShownAgain() {
        // Also the one handed to WorkManager: Android ignores the expiry while it is a foreground service's.
        val notification = notifications.build(IngestProgress(5, IngestProgress.Stage.EMBEDDING, chunksDone = 64, chunksTotal = 430))
        assertThat(notification.timeoutAfter).isEqualTo(IndexingNotifications.TIMEOUT_MS)

        notifications.notify(notification)

        val posted = shadowOf(context.getSystemService(NotificationManager::class.java)).getNotification(IndexingNotifications.NOTIFICATION_ID)
        assertThat(posted.timeoutAfter).isEqualTo(IndexingNotifications.TIMEOUT_MS)
        assertThat(posted.text()).isEqualTo("64 de 430 fragmentos")
    }

    @Test
    fun pauseActionAndTap_areExplicitAndImmutable() {
        val notification = notifications.build(null)

        val pause = notification.actions.single()
        assertThat(pause.title.toString()).isEqualTo("Pausar")
        val pauseIntent = shadowOf(pause.actionIntent)
        assertThat(pauseIntent.isBroadcast).isTrue()
        assertThat(pauseIntent.isImmutable).isTrue()
        assertThat(pauseIntent.savedIntent.component?.className).isEqualTo(IndexingActionReceiver::class.java.name)
        assertThat(pauseIntent.savedIntent.action).isEqualTo(IndexingActionReceiver.ACTION_PAUSE)

        val tap = shadowOf(notification.contentIntent)
        assertThat(tap.isActivity).isTrue()
        assertThat(tap.isImmutable).isTrue()
        assertThat(tap.savedIntent.component?.className).isEqualTo(MainActivity::class.java.name)
        assertThat(tap.savedIntent.action).isEqualTo(MainActivity.ACTION_OPEN_INDEXING)
    }
}
