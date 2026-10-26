package com.lisdatasolutions.microrag

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.work.Configuration
import androidx.work.ExperimentalConfigurationApi
import com.lisdatasolutions.microrag.core.di.appModules
import com.lisdatasolutions.microrag.core.locale.AppLocale
import com.lisdatasolutions.microrag.debug.DebugStrictMode
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

    /** On-demand WorkManager (F03 research R1): the API 31-33 download path and the ingest (F06, F07). */
    @OptIn(ExperimentalConfigurationApi::class)
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setMinimumLoggingLevel(if (BuildConfig.DEBUG) Log.INFO else Log.ERROR)
            // Keeps WorkManager's JobScheduler ids away from the download job (3001).
            .setJobSchedulerJobIdRange(WORK_MANAGER_JOB_ID_MIN, WORK_MANAGER_JOB_ID_MAX)
            // Every worker runs inside a JobScheduler job: the in-process scheduler could keep a worker
            // running after its job was cancelled, and Android froze that process (F07 research R2).
            .setGreedySchedulerEnabled(false)
            .build()

    /** Spanish by default for everything built from the app context, such as notifications (D-029). */
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(AppLocale.wrap(base))
    }

    override fun onCreate() {
        super.onCreate()
        DebugStrictMode.install()
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

private const val WORK_MANAGER_JOB_ID_MIN = 10_000
private const val WORK_MANAGER_JOB_ID_MAX = 19_999
