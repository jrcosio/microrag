package com.lisdatasolutions.microrag.data.di

import com.lisdatasolutions.microrag.BuildConfig
import com.lisdatasolutions.microrag.core.di.ApplicationScope
import com.lisdatasolutions.microrag.core.dispatchers.AppDispatchers
import com.lisdatasolutions.microrag.data.device.AndroidDeviceResources
import com.lisdatasolutions.microrag.data.download.DefaultModelDownloadController
import com.lisdatasolutions.microrag.data.download.DownloadNotifications
import com.lisdatasolutions.microrag.data.download.ModelDownloadRunner
import com.lisdatasolutions.microrag.data.download.ModelDownloader
import com.lisdatasolutions.microrag.data.models.AppDirs
import com.lisdatasolutions.microrag.data.models.ContentResolverModelFileSource
import com.lisdatasolutions.microrag.data.models.DefaultModelFileRepository
import com.lisdatasolutions.microrag.data.models.ModelFileRepository
import com.lisdatasolutions.microrag.data.onboarding.DataStoreOnboardingStore
import com.lisdatasolutions.microrag.domain.model.NetworkKind
import com.lisdatasolutions.microrag.domain.onboarding.DeviceResources
import com.lisdatasolutions.microrag.domain.onboarding.ModelDownloadController
import com.lisdatasolutions.microrag.domain.onboarding.ModelFileSource
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
    single<DeviceResources> { AndroidDeviceResources(androidContext(), get(), androidContext().noBackupFilesDir) }
    single<ModelFileSource> { ContentResolverModelFileSource(androidContext().contentResolver) }

    // The only network access of the app (constitution I, F03 FR-026): pinned Hugging Face files.
    single { ModelDownloader(userAgent = "MicroRAG/${BuildConfig.VERSION_NAME}") }
    single {
        val resources = get<DeviceResources>()
        ModelDownloadRunner(
            downloader = get(),
            repository = get(),
            store = get(),
            dirs = get(),
            freeBytes = resources::freeBytes,
            hasInternet = { resources.currentNetwork() != NetworkKind.NONE },
            logger = get(),
        )
    }
    single { DownloadNotifications(androidContext()) }
    single<ModelDownloadController> { DefaultModelDownloadController(androidContext(), get(), get(), get()) }
}
