package com.lisdatasolutions.microrag.core.di

import com.lisdatasolutions.microrag.BuildConfig
import com.lisdatasolutions.microrag.core.dispatchers.AppDispatchers
import com.lisdatasolutions.microrag.core.logging.AppLogger
import com.lisdatasolutions.microrag.core.logging.DefaultAppLogger
import org.koin.dsl.module

/** Cross-cutting services shared by every layer. */
val coreModule = module {
    single { AppDispatchers.create() }
    single<AppLogger> { DefaultAppLogger(isEnabled = BuildConfig.DEBUG) }
}
