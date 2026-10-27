package com.lisdatasolutions.microrag.data.indexing

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.SystemClock

/**
 * A wake-up while a run is unfinished (F07 research R2, D-041). When the process dies while the work runs
 * without a foreground service, JobScheduler keeps the job ready but only queues it at its next full
 * check, which an idle phone may not make for a long time. Starting the process is enough: WorkManager
 * schedules its unfinished work again, reconcile replaces work waiting for the system, and a newly
 * scheduled job that is ready runs at once.
 */
interface IndexingAlarm {
    /** Sets the wake-up, or moves it forward. */
    fun arm()

    fun cancel()

    companion object {
        val NONE = object : IndexingAlarm {
            override fun arm() = Unit
            override fun cancel() = Unit
        }
    }
}

/** Inexact, so it needs no special permission; the worker moves it forward while it runs. */
class AlarmManagerIndexingAlarm(private val context: Context) : IndexingAlarm {

    private val alarms = context.getSystemService(AlarmManager::class.java)

    override fun arm() = alarms.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, SystemClock.elapsedRealtime() + DELAY_MS, wakeIntent())

    override fun cancel() = alarms.cancel(wakeIntent())

    private fun wakeIntent(): PendingIntent = PendingIntent.getBroadcast(
        context,
        0,
        Intent(context, IndexingActionReceiver::class.java).setAction(IndexingActionReceiver.ACTION_WAKE),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    companion object {
        /** Never reached while the worker runs, which moves it every minute. */
        const val DELAY_MS = 15 * 60_000L
    }
}
