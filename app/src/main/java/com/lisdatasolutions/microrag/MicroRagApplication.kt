package com.lisdatasolutions.microrag

import android.app.Application
import com.lisdatasolutions.microrag.core.di.appModules
import com.lisdatasolutions.microrag.debug.debugModules
import com.lisdatasolutions.microrag.rag.runtime.EngineCoordinator
import com.lisdatasolutions.microrag.rag.runtime.EngineMemoryCallbacks
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.android.ext.android.get
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class MicroRagApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@MicroRagApplication)
            modules(appModules + debugModules)
        }
        // Constitution IV: release engines when the UI is hidden (TRIM_MEMORY_UI_HIDDEN or above).
        registerComponentCallbacks(
            EngineMemoryCallbacks(get<EngineCoordinator>(), CoroutineScope(SupervisorJob() + Dispatchers.Default)),
        )
    }
}
