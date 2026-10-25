package com.lisdatasolutions.microrag.rag.di

import com.lisdatasolutions.microrag.rag.runtime.EngineCoordinator
import com.lisdatasolutions.microrag.rag.runtime.EngineFactory
import com.lisdatasolutions.microrag.rag.runtime.litert.LiteRtEngineFactory
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

/** On-device inference runtime: one coordinator owns every engine. */
val runtimeModule = module {
    single { EngineCoordinator(get()) }
    single<EngineFactory> { LiteRtEngineFactory(androidContext(), get()) }
}
