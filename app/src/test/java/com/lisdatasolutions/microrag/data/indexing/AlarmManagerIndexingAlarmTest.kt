package com.lisdatasolutions.microrag.data.indexing

import android.app.AlarmManager
import android.app.Application
import android.content.Context
import android.os.SystemClock
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import java.time.Duration
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSystemClock

/** D-041 (research R2): one inexact wake-up to our receiver, moved forward while the run goes on. */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class)
class AlarmManagerIndexingAlarmTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val alarms = shadowOf(context.getSystemService(AlarmManager::class.java))
    private val alarm = AlarmManagerIndexingAlarm(context)

    @Test
    fun armingAgain_movesTheOnlyWakeUpForward() {
        alarm.arm()
        ShadowSystemClock.advanceBy(Duration.ofMinutes(1))
        alarm.arm()

        val scheduled = alarms.scheduledAlarms.single()
        assertThat(scheduled.type).isEqualTo(AlarmManager.ELAPSED_REALTIME_WAKEUP)
        assertThat(scheduled.triggerAtMs).isEqualTo(SystemClock.elapsedRealtime() + AlarmManagerIndexingAlarm.DELAY_MS)
        assertThat(scheduled.isAllowWhileIdle).isFalse()
        val wake = shadowOf(scheduled.operation)
        assertThat(wake.isBroadcast).isTrue()
        assertThat(wake.isImmutable).isTrue()
        assertThat(wake.savedIntent.component?.className).isEqualTo(IndexingActionReceiver::class.java.name)
        assertThat(wake.savedIntent.action).isEqualTo(IndexingActionReceiver.ACTION_WAKE)
    }

    @Test
    fun cancel_removesIt() {
        alarm.arm()

        alarm.cancel()

        assertThat(alarms.scheduledAlarms).isEmpty()
    }
}
