package com.lisdatasolutions.microrag

import android.app.Application
import com.lisdatasolutions.microrag.core.di.appModules
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class MicroRagApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@MicroRagApplication)
            modules(appModules)
        }
    }
}
