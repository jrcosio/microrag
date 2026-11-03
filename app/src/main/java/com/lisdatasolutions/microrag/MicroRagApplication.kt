package com.lisdatasolutions.microrag

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.work.Configuration
import androidx.work.ExperimentalConfigurationApi
import com.lisdatasolutions.microrag.core.di.ApplicationScope
import com.lisdatasolutions.microrag.core.di.appModules
import com.lisdatasolutions.microrag.core.locale.AppLocale
import com.lisdatasolutions.microrag.debug.DebugStrictMode
import com.lisdatasolutions.microrag.debug.debugModules
import com.lisdatasolutions.microrag.domain.settings.ReconcileThemeUseCase
import com.lisdatasolutions.microrag.rag.runtime.EngineCoordinator
import com.lisdatasolutions.microrag.rag.runtime.EngineMemoryCallbacks
import com.lisdatasolutions.microrag.rag.runtime.MemoryTrimListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.android.ext.android.get
import org.koin.android.ext.android.getKoin
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

    /**
     * Spanish by default for everything built from the app context (D-029). On Android 12 the stored app
     * language is read here, once and before StrictMode (F12 P3).
     */
    override fun attachBaseContext(base: Context) {
        AppLocale.init(base)
        super.attachBaseContext(AppLocale.wrap(base))
    }

    override fun onCreate() {
        super.onCreate()
        // F12 P4: after an update from Android 12, its stored choice becomes the system's per-app language.
        AppLocale.migrate(this)
        DebugStrictMode.install()
        startKoin {
            androidContext(this@MicroRagApplication)
            modules(appModules + debugModules)
        }
        // F16 P5: the theme's copy is applied again off the main thread; even its store is resolved there (L9).
        get<ApplicationScope>().scope.launch { getKoin().get<ReconcileThemeUseCase>()() }
        // Constitution IV: release engines when the UI is hidden (TRIM_MEMORY_UI_HIDDEN or above), after the
        // listeners (the chat, F11 R8) have stopped what used them.
        registerComponentCallbacks(
            EngineMemoryCallbacks(
                get<EngineCoordinator>(), CoroutineScope(SupervisorJob() + Dispatchers.Default), getKoin().getAll<MemoryTrimListener>(),
            ),
        )
    }
}

private const val WORK_MANAGER_JOB_ID_MIN = 10_000
private const val WORK_MANAGER_JOB_ID_MAX = 19_999
