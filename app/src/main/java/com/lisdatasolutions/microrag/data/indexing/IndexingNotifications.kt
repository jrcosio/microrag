package com.lisdatasolutions.microrag.data.indexing

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.lisdatasolutions.microrag.MainActivity
import com.lisdatasolutions.microrag.R
import com.lisdatasolutions.microrag.domain.ingest.IngestProgress
import java.text.NumberFormat

/**
 * The indexing notification (F07 research R3, proposal P11): real progress of the whole run, the same
 * figures as the Index screen (D-042), a «Pausar» action and a tap that opens the Index screen. No file
 * names: it shows on the lock screen (constitution I). Low importance, no sound.
 */
class IndexingNotifications(private val context: Context) {

    private val manager = context.getSystemService(NotificationManager::class.java)

    fun ensureChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.index_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply { description = context.getString(R.string.index_channel_description) }
        manager.createNotificationChannel(channel)
    }

    /** [progress] is null before the first document starts. */
    fun build(progress: IngestProgress?): Notification {
        ensureChannel()
        val builder = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_database)
            .setContentTitle(context.getString(R.string.index_notification_title))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(Notification.CATEGORY_PROGRESS)
            // Android never times out a foreground service's notification; any other one goes by itself (D-041).
            .setTimeoutAfter(TIMEOUT_MS)
            .setContentIntent(openIndex())
            .addAction(Notification.Action.Builder(null, context.getString(R.string.index_notification_pause), pauseIntent()).build())
        val (done, total) = when {
            progress?.runChunksTotal != null -> progress.runChunksDone to progress.runChunksTotal
            else -> progress?.chunksDone to progress?.chunksTotal
        }
        return when {
            progress?.waiting == IngestProgress.WaitReason.THERMAL ->
                builder.setContentText(context.getString(R.string.index_paused_thermal)).setProgress(0, 0, false)
            done != null && total != null && total > 0 ->
                builder.setContentText(context.getString(R.string.index_notification_chunks, done.grouped(), total.grouped()))
                    .setProgress(total, done.coerceAtMost(total), false)
            else -> builder.setContentText(context.getString(R.string.index_preparing)).setProgress(0, 0, true)
        }.build()
    }

    /** Without a foreground service (research R1), the same notification as a plain one. */
    fun notify(notification: Notification) = manager.notify(NOTIFICATION_ID, notification)

    fun cancel() = manager.cancel(NOTIFICATION_ID)

    private fun openIndex(): PendingIntent = PendingIntent.getActivity(
        context,
        0,
        Intent(context, MainActivity::class.java)
            .setAction(MainActivity.ACTION_OPEN_INDEXING)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun pauseIntent(): PendingIntent = PendingIntent.getBroadcast(
        context,
        0,
        Intent(context, IndexingActionReceiver::class.java).setAction(IndexingActionReceiver.ACTION_PAUSE),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun Int.grouped(): String = NumberFormat.getIntegerInstance().format(this)

    companion object {
        const val CHANNEL_ID = "indexing"

        /** Apart from the download notification (3001). */
        const val NOTIFICATION_ID = 3002

        /** The system rate-limits progress updates; once per second is enough. */
        const val UPDATE_INTERVAL_MS = 1_000L

        /**
         * The worker shows it at least once a minute while it runs, so it only expires once the process
         * has died. Also when WorkManager posts it: its service can fail to reach the foreground and
         * still post it as a plain one (seen after a reboot).
         */
        const val TIMEOUT_MS = 3 * 60_000L
    }
}
