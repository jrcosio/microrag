package com.lisdatasolutions.microrag.domain.onboarding

import com.lisdatasolutions.microrag.domain.model.NetworkKind
import kotlinx.coroutines.flow.Flow

/** Device facts the onboarding shows or checks: memory, free space and connectivity. */
interface DeviceResources {
    fun totalRamBytes(): Long

    /** Free bytes on the volume of the app's no-backup storage. */
    fun freeBytes(): Long

    fun currentNetwork(): NetworkKind

    /** Whether the default network can reach the Internet; updates as connectivity changes. */
    val hasInternet: Flow<Boolean>

    companion object {
        /** Report: warn below about 7.5 GB of RAM (constitution IV, F03 research R11). Decimal bytes. */
        const val LOW_RAM_THRESHOLD_BYTES = 7_500_000_000L
    }
}
