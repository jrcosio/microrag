package com.lisdatasolutions.microrag.debug.feasibility.visual

import com.lisdatasolutions.microrag.core.dispatchers.AppDispatchers
import com.lisdatasolutions.microrag.debug.feasibility.visual.report.VisualReportSink
import com.lisdatasolutions.microrag.debug.feasibility.visual.report.VisualReportWriter
import com.lisdatasolutions.microrag.rag.runtime.litert.LiteRtVisionEngineFactory
import java.io.File
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.bind
import org.koin.dsl.module

/**
 * Dependencies of the visual feasibility diagnostic (F18). Reuses F02's thermal, memory, device and importer, and
 * F19's page rasterizer from the app's modules.
 */
val visualFeasibilityModule = module {
    single<VisionEngineFactory> { LiteRtVisionEngineFactory(androidContext(), get()) }
    single {
        val externalFiles = checkNotNull(androidContext().getExternalFilesDir(null)) { "External storage unavailable" }
        DeviceSetLoader(File(externalFiles, "dev-visual"))
    }
    single { VisualReportWriter(File(androidContext().filesDir, "reports"), get<AppDispatchers>().io) } bind VisualReportSink::class
    single {
        val loader = get<DeviceSetLoader>()
        VisualSuite(
            coordinator = get(),
            textFactory = get(),
            visionFactory = get(),
            rasterizer = get(),
            repository = get(),
            deviceSetSource = { loader.load() },
            textsSource = get(),
            thermal = get(),
            memory = get(),
            device = get(),
            reportSink = get(),
            dispatchers = get(),
            logger = get(),
        )
    }
    viewModel { VisualFeasibilityViewModel(get(), get(), get(), get(), get(), get(), get<AppDispatchers>().io) }
}
