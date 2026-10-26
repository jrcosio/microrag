package com.lisdatasolutions.microrag.data.di

import androidx.work.WorkManager
import com.lisdatasolutions.microrag.BuildConfig
import com.lisdatasolutions.microrag.core.di.ApplicationScope
import com.lisdatasolutions.microrag.core.dispatchers.AppDispatchers
import com.lisdatasolutions.microrag.data.device.AndroidDeviceResources
import com.lisdatasolutions.microrag.data.device.PowerManagerThermalStatusSource
import com.lisdatasolutions.microrag.data.device.ThermalStatusSource
import com.lisdatasolutions.microrag.data.download.DefaultModelDownloadController
import com.lisdatasolutions.microrag.data.download.DownloadNotifications
import com.lisdatasolutions.microrag.data.download.FileDownloader
import com.lisdatasolutions.microrag.data.download.ModelDownloadRunner
import com.lisdatasolutions.microrag.data.download.ModelDownloader
import com.lisdatasolutions.microrag.data.indexing.ChunkEmbeddingSession
import com.lisdatasolutions.microrag.data.indexing.DataStoreIngestRunStore
import com.lisdatasolutions.microrag.data.indexing.EmbeddingSessions
import com.lisdatasolutions.microrag.data.indexing.IngestRunStore
import com.lisdatasolutions.microrag.data.ingest.DefaultImportQueueRepository
import com.lisdatasolutions.microrag.data.ingest.DocumentFileStore
import com.lisdatasolutions.microrag.data.ingest.DocumentImporter
import com.lisdatasolutions.microrag.data.ingest.DocumentSource
import com.lisdatasolutions.microrag.data.ingest.IngestRunner
import com.lisdatasolutions.microrag.data.ingest.IngestScheduler
import com.lisdatasolutions.microrag.data.ingest.PlatformPdfOpener
import com.lisdatasolutions.microrag.data.ingest.SafDocumentSource
import com.lisdatasolutions.microrag.data.ingest.WorkManagerIngestScheduler
import com.lisdatasolutions.microrag.data.models.AppDirs
import com.lisdatasolutions.microrag.data.models.ContentResolverModelFileSource
import com.lisdatasolutions.microrag.data.models.DefaultModelFileRepository
import com.lisdatasolutions.microrag.data.models.ModelFileRepository
import com.lisdatasolutions.microrag.data.onboarding.DataStoreOnboardingStore
import com.lisdatasolutions.microrag.data.rag.DefaultRagIndex
import com.lisdatasolutions.microrag.data.rag.RagSchema
import com.lisdatasolutions.microrag.domain.ingest.ImportQueueRepository
import com.lisdatasolutions.microrag.domain.model.ModelId
import com.lisdatasolutions.microrag.domain.model.NetworkKind
import com.lisdatasolutions.microrag.domain.onboarding.DeviceResources
import com.lisdatasolutions.microrag.domain.onboarding.ModelDownloadController
import com.lisdatasolutions.microrag.domain.onboarding.ModelFileSource
import com.lisdatasolutions.microrag.domain.onboarding.OnboardingStore
import com.lisdatasolutions.microrag.domain.rag.RagIndex
import com.lisdatasolutions.microrag.domain.rag.RagIndexConfig
import com.lisdatasolutions.microrag.rag.ingest.DocumentExtractor
import com.lisdatasolutions.microrag.rag.ingest.PdfOpener
import java.io.File
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

    // The local RAG index (F04): one connection on the database dispatcher, opened on first use.
    single<RagIndex> {
        val context = androidContext()
        DefaultRagIndex(
            dbFile = File(get<AppDirs>().index, "rag.db"),
            schema = { RagSchema.load(context.assets) },
            extensionPath = File(context.applicationInfo.nativeLibraryDir, "vector.so").path,
            config = RagIndexConfig.current(),
            dispatcher = get<AppDispatchers>().database,
            logger = get(),
        )
    }

    // Document import (F06): private copies and the system PDF reader.
    single { DocumentFileStore(get<AppDirs>().documents) }
    single<PdfOpener> { PlatformPdfOpener() }
    single<DocumentSource> { SafDocumentSource(androidContext().contentResolver) }
    single { DocumentImporter(get(), get(), get(), get(), get<DeviceResources>()::freeBytes, get()) }
    single<IngestScheduler> { WorkManagerIngestScheduler(WorkManager.getInstance(androidContext())) }
    single { DocumentExtractor(get()) }

    // Indexing (F07): the run and the device's heat.
    single<IngestRunStore> {
        DataStoreIngestRunStore(DataStoreIngestRunStore.create(androidContext().noBackupFilesDir, get<ApplicationScope>().scope))
    }
    single<ThermalStatusSource> { PowerManagerThermalStatusSource(androidContext()) }
    single<EmbeddingSessions> {
        val models = get<ModelFileRepository>()
        ChunkEmbeddingSession(
            coordinator = get(),
            factory = get(),
            // A process started for the work has not scanned the models folder yet.
            modelPath = { models.pathOf(ModelId.EG2_270M) ?: models.refresh().let { models.pathOf(ModelId.EG2_270M) } },
            runStore = get(),
            appVersion = BuildConfig.VERSION_CODE.toLong(),
            logger = get(),
        )
    }
    single {
        IngestRunner(
            index = get(),
            extractor = get(),
            sessions = get(),
            runStore = get(),
            thermal = get(),
            freeBytes = get<DeviceResources>()::freeBytes,
            io = get<AppDispatchers>().io,
            logger = get(),
        )
    }
    // Single: the import attempts live here, in the application scope (D-038).
    single<ImportQueueRepository> {
        DefaultImportQueueRepository(
            importer = get(),
            source = get(),
            index = get(),
            files = get(),
            scheduler = get(),
            pdf = get(),
            scope = get<ApplicationScope>().scope,
            io = get<AppDispatchers>().io,
            logger = get(),
        )
    }

    // The only network access of the app (constitution I, F03 FR-026): pinned Hugging Face files.
    single<FileDownloader> { ModelDownloader(userAgent = "MicroRAG/${BuildConfig.VERSION_NAME}") }
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
