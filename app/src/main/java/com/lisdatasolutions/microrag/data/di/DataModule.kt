package com.lisdatasolutions.microrag.data.di

import com.lisdatasolutions.microrag.core.dispatchers.AppDispatchers
import com.lisdatasolutions.microrag.data.models.DefaultModelFileRepository
import com.lisdatasolutions.microrag.data.models.ModelFileRepository
import java.io.File
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

/** Repository implementations, database and downloads. */
val dataModule = module {
    single<ModelFileRepository> {
        DefaultModelFileRepository(
            // Constitution I: models live in no-backup private storage.
            modelsDir = File(androidContext().noBackupFilesDir, "models"),
            ioDispatcher = get<AppDispatchers>().io,
            logger = get(),
        )
    }
}
