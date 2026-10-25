package com.lisdatasolutions.microrag.debug.feasibility

import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

/** Dependencies of the on-device feasibility diagnostics (F02). */
val feasibilityModule = module {
    single<ThermalSource> { PowerManagerThermalSource(androidContext()) }
    single { ThermalMonitor(get()) }
    single<MemoryProbe> { ProcMemoryProbe() }
    single { DeviceSnapshotCollector(androidContext(), get()) }
}
