package com.lisdatasolutions.microrag.data.device

import android.content.Context
import android.os.PowerManager
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

/** The heat levels indexing reacts to (D-039): it pauses at severe or worse and goes on below. */
enum class ThermalLevel {
    NORMAL,
    MODERATE,
    SEVERE_OR_WORSE,
    ;

    companion object {
        fun of(status: Int): ThermalLevel = when {
            status >= PowerManager.THERMAL_STATUS_SEVERE -> SEVERE_OR_WORSE
            status == PowerManager.THERMAL_STATUS_MODERATE -> MODERATE
            else -> NORMAL
        }
    }
}

/** The device's thermal status as a flow that starts with the current level (F07 research R10). */
interface ThermalStatusSource {
    val level: Flow<ThermalLevel>
}

class PowerManagerThermalStatusSource(private val context: Context) : ThermalStatusSource {

    override val level: Flow<ThermalLevel> = callbackFlow {
        val powerManager = context.getSystemService(PowerManager::class.java)
        val listener = PowerManager.OnThermalStatusChangedListener { trySend(ThermalLevel.of(it)) }
        trySend(ThermalLevel.of(powerManager.currentThermalStatus))
        powerManager.addThermalStatusListener(Runnable::run, listener)
        awaitClose { powerManager.removeThermalStatusListener(listener) }
    }.distinctUntilChanged()
}
