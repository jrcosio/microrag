package com.lisdatasolutions.microrag

import android.app.Application
import android.util.Log
import androidx.work.Configuration
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

class MicroRagApplication : Application(), Configuration.Provider {

    /** On-demand WorkManager (F03 research R1): only the API 31-33 download path touches it. */
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setMinimumLoggingLevel(if (BuildConfig.DEBUG) Log.INFO else Log.ERROR).build()

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
