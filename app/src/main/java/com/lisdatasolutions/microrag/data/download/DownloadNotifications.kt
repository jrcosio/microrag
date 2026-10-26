package com.lisdatasolutions.microrag.data.download

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.text.format.Formatter
import com.lisdatasolutions.microrag.MainActivity
import com.lisdatasolutions.microrag.R
import com.lisdatasolutions.microrag.domain.model.DownloadProgress
import com.lisdatasolutions.microrag.domain.model.ModelId
import com.lisdatasolutions.microrag.domain.model.percentOf

/**
 * Ongoing notification of the model download (FR-012, contracts/onboarding-ui.md): model, real
 * percentage and bytes, a «Detener» action and a tap that opens the app. Low importance, no sound.
 */
class DownloadNotifications(private val context: Context) {

    private val manager = context.getSystemService(NotificationManager::class.java)

    fun ensureChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.download_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply { description = context.getString(R.string.download_channel_description) }
        manager.createNotificationChannel(channel)
    }

    fun build(progress: DownloadProgress): Notification {
        ensureChannel()
        val builder = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_download)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(Notification.CATEGORY_PROGRESS)
            .setContentIntent(openApp())
            .addAction(
                Notification.Action.Builder(null, context.getString(R.string.action_stop), pauseIntent()).build(),
            )
        val active = progress.active
        return if (active == null || progress.totalBytes <= 0) {
            builder.setContentTitle(context.getString(R.string.download_notification_starting))
                .setProgress(0, 0, true)
                .build()
        } else {
            val percent = percentOf(progress.bytesDone, progress.totalBytes)
            builder.setContentTitle(context.getString(R.string.download_notification_title, context.getString(nameOf(active))))
                .setContentText(
                    context.getString(
                        R.string.download_notification_text,
                        percent,
                        Formatter.formatShortFileSize(context, progress.bytesDone),
                        Formatter.formatShortFileSize(context, progress.totalBytes),
                    ),
                )
                .setProgress(PROGRESS_MAX, percent * PROGRESS_MAX / 100, false)
                .build()
        }
    }

    private fun openApp(): PendingIntent = PendingIntent.getActivity(
        context,
        0,
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun pauseIntent(): PendingIntent = PendingIntent.getBroadcast(
        context,
        0,
        Intent(context, DownloadActionReceiver::class.java).setAction(DownloadActionReceiver.ACTION_PAUSE),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun nameOf(id: ModelId): Int = when (id) {
        ModelId.GEMMA4_E2B, ModelId.GEMMA4_E2B_GPU -> R.string.setup_model_gemma_name
        else -> R.string.setup_model_embedding_name
    }

    companion object {
        const val CHANNEL_ID = "model_downloads"
        const val NOTIFICATION_ID = 3001
        private const val PROGRESS_MAX = 1000

        /** The system rate-limits progress updates; once per second is enough. */
        const val UPDATE_INTERVAL_MS = 1_000L
    }
}
