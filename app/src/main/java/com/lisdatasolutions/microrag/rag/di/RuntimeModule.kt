package com.lisdatasolutions.microrag.rag.di

import com.lisdatasolutions.microrag.rag.runtime.EngineCoordinator
import com.lisdatasolutions.microrag.rag.runtime.EngineFactory
import com.lisdatasolutions.microrag.rag.runtime.ImageEmbedderFactory
import com.lisdatasolutions.microrag.rag.runtime.ModelCompatibility
import com.lisdatasolutions.microrag.rag.runtime.ModelCompatibilityChecker
import com.lisdatasolutions.microrag.rag.runtime.litert.LiteRtEngineFactory
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.binds
import org.koin.dsl.module

/** On-device inference runtime: one coordinator owns every engine. */
val runtimeModule = module {
    single { EngineCoordinator(get()) }
    // F19: the same factory also loads the 440M's image engine (research R7).
    single { LiteRtEngineFactory(androidContext(), get()) } binds arrayOf(EngineFactory::class, ImageEmbedderFactory::class)
    single<ModelCompatibility> { ModelCompatibilityChecker(get(), get(), get()) }
}
