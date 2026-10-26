package com.lisdatasolutions.microrag.data.di

import com.lisdatasolutions.microrag.core.di.ApplicationScope
import com.lisdatasolutions.microrag.core.dispatchers.AppDispatchers
import com.lisdatasolutions.microrag.data.device.AndroidDeviceResources
import com.lisdatasolutions.microrag.data.models.AppDirs
import com.lisdatasolutions.microrag.data.models.DefaultModelFileRepository
import com.lisdatasolutions.microrag.data.models.ModelFileRepository
import com.lisdatasolutions.microrag.data.onboarding.DataStoreOnboardingStore
import com.lisdatasolutions.microrag.domain.onboarding.DeviceResources
import com.lisdatasolutions.microrag.domain.onboarding.OnboardingStore
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

/** Repository implementations, database and downloads. */
val dataModule = module {
    // Constitution I: everything lives in no-backup private storage.
    single { AppDirs(androidContext().noBackupFilesDir) }
    single<ModelFileRepository> {
        DefaultModelFileRepository(
            modelsDir = get<AppDirs>().models,
            ioDispatcher = get<AppDispatchers>().io,
            logger = get(),
        )
    }
    single<OnboardingStore> {
        DataStoreOnboardingStore(DataStoreOnboardingStore.create(androidContext().noBackupFilesDir, get<ApplicationScope>().scope))
    }
    single<DeviceResources> { AndroidDeviceResources(androidContext(), androidContext().noBackupFilesDir) }
}
