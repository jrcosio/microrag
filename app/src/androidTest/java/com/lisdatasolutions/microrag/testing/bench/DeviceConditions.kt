package com.lisdatasolutions.microrag.testing.bench

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.PowerManager
import java.io.File
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * F14 (research R4, contract B1): what a measure records about the phone, once per sample and at the start and end
 * of a bench. Numbers only: nothing of the user and no text of prompts or answers.
 */
data class DeviceConditions(
    val thermal: String,
    val headroom: Float?,
    val batteryPercent: Int?,
    val batteryTempC: Float?,
    val charging: Boolean?,
    val rssMb: Long?,
    val rssPeakMb: Long?,
    val heapMb: Long,
    val dbBytes: Long?,
) {
    fun toJson(): JsonObject = buildJsonObject {
        put("thermal", thermal)
        headroom?.let { put("headroom", it) }
        batteryPercent?.let { put("battery", it) }
        batteryTempC?.let { put("batteryTempC", it) }
        charging?.let { put("charging", it) }
        rssMb?.let { put("rssMb", it) }
        rssPeakMb?.let { put("rssPeakMb", it) }
        put("heapMb", heapMb)
        dbBytes?.let { put("dbBytes", it) }
    }

    companion object {
        /** A snapshot now; [db] adds its file size with the WAL. */
        fun snapshot(context: Context, db: File? = null): DeviceConditions {
            val power = context.getSystemService(PowerManager::class.java)
            val battery = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val level = battery?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = battery?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
            val temp = battery?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE) ?: Int.MIN_VALUE
            val plugged = battery?.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1) ?: -1
            val runtime = Runtime.getRuntime()
            return DeviceConditions(
                thermal = thermalName(power.currentThermalStatus),
                headroom = power.getThermalHeadroom(HEADROOM_FORECAST_SECONDS).takeUnless { it.isNaN() },
                batteryPercent = if (level >= 0 && scale > 0) level * PERCENT / scale else null,
                batteryTempC = if (temp != Int.MIN_VALUE) temp / TENTHS else null,
                charging = if (plugged >= 0) plugged != 0 else null,
                rssMb = statusMb("VmRSS:"),
                rssPeakMb = statusMb("VmHWM:"),
                heapMb = (runtime.totalMemory() - runtime.freeMemory()) / BYTES_PER_MB,
                dbBytes = db?.let { file -> listOf(file, File(file.path + "-wal")).filter { it.isFile }.sumOf { it.length() } },
            )
        }

        /** Only the thermal status, cheap enough for every sample. */
        fun thermalNow(context: Context): String = thermalName(context.getSystemService(PowerManager::class.java).currentThermalStatus)

        /** PowerManager's status as a name; `SEVERE` and above stop a sustained session (research R5). */
        fun thermalName(status: Int): String = when (status) {
            PowerManager.THERMAL_STATUS_NONE -> "NONE"
            PowerManager.THERMAL_STATUS_LIGHT -> "LIGHT"
            PowerManager.THERMAL_STATUS_MODERATE -> "MODERATE"
            PowerManager.THERMAL_STATUS_SEVERE -> "SEVERE"
            PowerManager.THERMAL_STATUS_CRITICAL -> "CRITICAL"
            PowerManager.THERMAL_STATUS_EMERGENCY -> "EMERGENCY"
            PowerManager.THERMAL_STATUS_SHUTDOWN -> "SHUTDOWN"
            else -> "UNKNOWN"
        }

        fun isSevere(thermal: String): Boolean = thermal in setOf("SEVERE", "CRITICAL", "EMERGENCY", "SHUTDOWN")

        /** Resets VmHWM to the current RSS (proc(5), clear_refs), as F02's memory probe. */
        fun resetPeak(): Boolean = runCatching { File("/proc/self/clear_refs").writeText("5"); true }.getOrDefault(false)

        private fun statusMb(key: String): Long? = runCatching {
            File("/proc/self/status").useLines { lines ->
                lines.firstOrNull { it.startsWith(key) }?.split(Regex("\\s+"))?.getOrNull(1)?.toLong()?.div(KB_PER_MB)
            }
        }.getOrNull()

        private const val HEADROOM_FORECAST_SECONDS = 0
        private const val PERCENT = 100
        private const val TENTHS = 10f
        private const val KB_PER_MB = 1024L
        private const val BYTES_PER_MB = 1024L * 1024L
    }
}
