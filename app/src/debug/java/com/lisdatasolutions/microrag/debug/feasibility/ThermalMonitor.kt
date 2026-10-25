package com.lisdatasolutions.microrag.debug.feasibility

import android.content.Context
import android.os.PowerManager
import kotlinx.coroutines.delay

/** Mirrors `PowerManager.THERMAL_STATUS_*`, in severity order. */
enum class ThermalLevel { NONE, LIGHT, MODERATE, SEVERE, CRITICAL, EMERGENCY, SHUTDOWN }

interface ThermalSource {
    fun level(): ThermalLevel

    /** Forecast headroom (1.0 = throttling threshold) in 10 s, or null if unsupported. */
    fun headroom(): Float?
}

class PowerManagerThermalSource(context: Context) : ThermalSource {
    private val powerManager = context.getSystemService(PowerManager::class.java)

    override fun level(): ThermalLevel =
        ThermalLevel.entries.getOrElse(powerManager.currentThermalStatus) { ThermalLevel.SHUTDOWN }

    override fun headroom(): Float? =
        powerManager.getThermalHeadroom(HEADROOM_FORECAST_SECONDS).takeUnless { it.isNaN() }

    private companion object {
        const val HEADROOM_FORECAST_SECONDS = 10
    }
}

/** Waits between measurement blocks so throttling does not distort the numbers (research R6). */
class ThermalMonitor(private val source: ThermalSource) {

    fun level(): ThermalLevel = source.level()

    fun headroom(): Float? = source.headroom()

    /** Polls until the device is NONE or LIGHT, or [maxWaitMs] elapses; returns the last level. */
    suspend fun awaitCool(maxWaitMs: Long = MAX_WAIT_MS, pollMs: Long = POLL_MS): ThermalLevel {
        var waited = 0L
        var level = source.level()
        while (!level.isCool() && waited < maxWaitMs) {
            delay(pollMs)
            waited += pollMs
            level = source.level()
        }
        return level
    }

    companion object {
        const val MAX_WAIT_MS = 5 * 60 * 1000L
        const val POLL_MS = 5_000L
    }
}

fun ThermalLevel.isCool(): Boolean = this <= ThermalLevel.LIGHT

/** SEVERE or worse: measuring would mostly measure throttling, so the user must confirm (FR-015). */
fun ThermalLevel.needsConfirmation(): Boolean = this >= ThermalLevel.SEVERE
