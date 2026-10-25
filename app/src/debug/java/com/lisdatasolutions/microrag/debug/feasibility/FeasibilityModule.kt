package com.lisdatasolutions.microrag.debug.feasibility

import com.lisdatasolutions.microrag.core.dispatchers.AppDispatchers
import java.io.File
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/** Dependencies of the on-device feasibility diagnostics (F02). */
val feasibilityModule = module {
    single<ThermalSource> { PowerManagerThermalSource(androidContext()) }
    single { ThermalMonitor(get()) }
    single<MemoryProbe> { ProcMemoryProbe() }
    single { DeviceSnapshotCollector(androidContext(), get()) }
    single {
        val externalFiles = checkNotNull(androidContext().getExternalFilesDir(null)) { "External storage unavailable" }
        DevModelImporter(File(externalFiles, "dev-models"), get(), get<AppDispatchers>().io)
    }
    viewModel { FeasibilityViewModel(get(), get()) }
}
