package com.lisdatasolutions.microrag.debug.feasibility

import com.lisdatasolutions.microrag.core.dispatchers.AppDispatchers
import com.lisdatasolutions.microrag.debug.feasibility.report.ReportSink
import com.lisdatasolutions.microrag.debug.feasibility.report.ReportWriter
import java.io.File
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.bind
import org.koin.dsl.module

/** Dependencies of the on-device feasibility diagnostics (F02). */
val feasibilityModule = module {
    single<ThermalSource> { PowerManagerThermalSource(androidContext()) }
    single { ThermalMonitor(get()) }
    single<MemoryProbe> { ProcMemoryProbe() }
    single<DeviceInfoSource> { DeviceSnapshotCollector(androidContext(), get()) }
    single {
        val externalFiles = checkNotNull(androidContext().getExternalFilesDir(null)) { "External storage unavailable" }
        DevModelImporter(File(externalFiles, "dev-models"), get(), get<AppDispatchers>().io)
    }
    single<FeasibilityTextsSource> { AssetFeasibilityTexts(androidContext(), get<AppDispatchers>().io) }
    single { ReportWriter(File(androidContext().filesDir, "reports"), get<AppDispatchers>().io) } bind ReportSink::class
    single {
        FeasibilitySuite(
            coordinator = get(),
            factory = get(),
            repository = get(),
            textsSource = get(),
            thermal = get(),
            memory = get(),
            device = get(),
            reportSink = get(),
            dispatchers = get(),
            logger = get(),
        )
    }
    viewModel { FeasibilityViewModel(get(), get(), get(), get(), get()) }
}
