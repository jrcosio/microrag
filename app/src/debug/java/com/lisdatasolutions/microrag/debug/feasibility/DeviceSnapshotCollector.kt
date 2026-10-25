package com.lisdatasolutions.microrag.debug.feasibility

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.lisdatasolutions.microrag.BuildConfig
import com.lisdatasolutions.microrag.debug.feasibility.report.DeviceSnapshot
import com.lisdatasolutions.microrag.rag.runtime.RuntimeInfo

/** Device, battery and version facts recorded with every report (research R6). */
class DeviceSnapshotCollector(
    private val context: Context,
    private val thermal: ThermalMonitor,
) {

    fun snapshot(): DeviceSnapshot {
        val memory = ActivityManager.MemoryInfo().also {
            context.getSystemService(ActivityManager::class.java).getMemoryInfo(it)
        }
        val battery = batteryIntent()
        return DeviceSnapshot(
            manufacturer = Build.MANUFACTURER,
            model = Build.MODEL,
            socManufacturer = Build.SOC_MANUFACTURER,
            socModel = Build.SOC_MODEL,
            androidRelease = Build.VERSION.RELEASE,
            sdkInt = Build.VERSION.SDK_INT,
            totalRamMb = memory.totalMem / BYTES_PER_MB,
            thermalStatus = thermal.level(),
            thermalHeadroom = thermal.headroom(),
            charging = isCharging(battery),
            batteryLevel = battery?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)?.takeIf { it >= 0 },
            batteryTempC = battery?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)
                ?.takeIf { it != Int.MIN_VALUE }
                ?.let { it / TENTHS },
            appVersion = BuildConfig.VERSION_NAME,
            liteRtLmVersion = RuntimeInfo.LITERT_LM_VERSION,
        )
    }

    fun isCharging(): Boolean = isCharging(batteryIntent())

    private fun batteryIntent(): Intent? = ContextCompat.registerReceiver(
        context,
        null,
        IntentFilter(Intent.ACTION_BATTERY_CHANGED),
        ContextCompat.RECEIVER_NOT_EXPORTED,
    )

    private fun isCharging(battery: Intent?): Boolean =
        (battery?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0) != 0

    private companion object {
        const val BYTES_PER_MB = 1024L * 1024L
        const val TENTHS = 10f
    }
}
