package com.lisdatasolutions.microrag.core.di

import com.lisdatasolutions.microrag.BuildConfig
import com.lisdatasolutions.microrag.core.dispatchers.AppDispatchers
import com.lisdatasolutions.microrag.core.logging.AppLogger
import com.lisdatasolutions.microrag.core.logging.DefaultAppLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import org.koin.dsl.module

/** Cross-cutting services shared by every layer. */
val coreModule = module {
    single { AppDispatchers.create() }
    single<AppLogger> { DefaultAppLogger(isEnabled = BuildConfig.DEBUG) }
    single { ApplicationScope(CoroutineScope(SupervisorJob() + get<AppDispatchers>().default)) }
}
