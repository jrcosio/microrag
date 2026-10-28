package com.lisdatasolutions.microrag.domain.di

import com.lisdatasolutions.microrag.domain.indexing.ObserveIndexingUseCase
import com.lisdatasolutions.microrag.domain.indexing.PauseIndexingUseCase
import com.lisdatasolutions.microrag.domain.indexing.ReconcileIndexingUseCase
import com.lisdatasolutions.microrag.domain.indexing.ResumeIndexingUseCase
import com.lisdatasolutions.microrag.domain.indexing.RetryIndexingUseCase
import com.lisdatasolutions.microrag.domain.indexing.StartIndexingRunUseCase
import com.lisdatasolutions.microrag.domain.ingest.ImportDocumentsUseCase
import com.lisdatasolutions.microrag.domain.ingest.ObserveImportQueueUseCase
import com.lisdatasolutions.microrag.domain.ingest.RemoveQueueItemUseCase
import com.lisdatasolutions.microrag.domain.ingest.RetryQueueItemUseCase
import com.lisdatasolutions.microrag.domain.ingest.StartIndexingUseCase
import com.lisdatasolutions.microrag.domain.library.DeleteDocumentUseCase
import com.lisdatasolutions.microrag.domain.library.DiscardReindexUseCase
import com.lisdatasolutions.microrag.domain.library.LoadPreviewUseCase
import com.lisdatasolutions.microrag.domain.library.ObserveDocumentUseCase
import com.lisdatasolutions.microrag.domain.library.ObserveLibraryUseCase
import com.lisdatasolutions.microrag.domain.library.ObserveLibrarySummaryUseCase
import com.lisdatasolutions.microrag.domain.library.ReindexDocumentUseCase
import com.lisdatasolutions.microrag.domain.library.RemoveFromIndexUseCase
import com.lisdatasolutions.microrag.domain.library.SetIncludedUseCase
import com.lisdatasolutions.microrag.domain.onboarding.BuildDownloadConfirmationUseCase
import com.lisdatasolutions.microrag.domain.onboarding.CompleteOnboardingUseCase
import com.lisdatasolutions.microrag.domain.onboarding.ObserveLocalAiUseCase
import com.lisdatasolutions.microrag.domain.onboarding.RefreshLocalAiUseCase
import com.lisdatasolutions.microrag.domain.onboarding.ImportModelFileUseCase
import com.lisdatasolutions.microrag.domain.onboarding.LocalModelWorkTracker
import com.lisdatasolutions.microrag.domain.onboarding.ObserveModelSetupUseCase
import com.lisdatasolutions.microrag.domain.onboarding.PauseModelDownloadUseCase
import com.lisdatasolutions.microrag.domain.onboarding.PrepareModelsUseCase
import com.lisdatasolutions.microrag.domain.onboarding.PrepareStorageUseCase
import com.lisdatasolutions.microrag.domain.onboarding.ReconcileDownloadsUseCase
import com.lisdatasolutions.microrag.domain.onboarding.ResolveStartDestinationUseCase
import com.lisdatasolutions.microrag.domain.onboarding.ResumeModelDownloadUseCase
import com.lisdatasolutions.microrag.domain.onboarding.RetryModelUseCase
import com.lisdatasolutions.microrag.domain.onboarding.StartModelDownloadUseCase
import com.lisdatasolutions.microrag.domain.onboarding.StartOnboardingUseCase
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

/** Use cases. */
val domainModule = module {
    singleOf(::LocalModelWorkTracker)
    factoryOf(::ResolveStartDestinationUseCase)
    factoryOf(::StartOnboardingUseCase)
    factoryOf(::ObserveModelSetupUseCase)
    factoryOf(::BuildDownloadConfirmationUseCase)
    factoryOf(::StartModelDownloadUseCase)
    factoryOf(::PauseModelDownloadUseCase)
    factoryOf(::ResumeModelDownloadUseCase)
    factoryOf(::ReconcileDownloadsUseCase)
    // Single: its mutex keeps one on-device test at a time across screens (FR-021).
    singleOf(::PrepareModelsUseCase)
    factoryOf(::RetryModelUseCase)
    factoryOf(::ImportModelFileUseCase)
    factoryOf(::PrepareStorageUseCase)
    factoryOf(::CompleteOnboardingUseCase)
    factoryOf(::ObserveLocalAiUseCase)
    factoryOf(::RefreshLocalAiUseCase)

    // Document import (F06).
    factoryOf(::ObserveImportQueueUseCase)
    factoryOf(::ImportDocumentsUseCase)
    factoryOf(::StartIndexingUseCase)
    factoryOf(::RetryQueueItemUseCase)
    factoryOf(::RemoveQueueItemUseCase)

    // Indexing (F07).
    factoryOf(::ObserveIndexingUseCase)
    factoryOf(::StartIndexingRunUseCase)
    factoryOf(::PauseIndexingUseCase)
    factoryOf(::ResumeIndexingUseCase)
    factoryOf(::RetryIndexingUseCase)
    factoryOf(::ReconcileIndexingUseCase)

    // Library and document detail (F08).
    factoryOf(::ObserveLibraryUseCase)
    factoryOf(::ObserveLibrarySummaryUseCase)
    factoryOf(::ObserveDocumentUseCase)
    factoryOf(::LoadPreviewUseCase)
    factoryOf(::SetIncludedUseCase)
    factoryOf(::DeleteDocumentUseCase)
    factoryOf(::RemoveFromIndexUseCase)
    factoryOf(::ReindexDocumentUseCase)
    factoryOf(::DiscardReindexUseCase)
}
