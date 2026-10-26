package com.lisdatasolutions.microrag.domain.di

import com.lisdatasolutions.microrag.domain.ingest.ImportDocumentsUseCase
import com.lisdatasolutions.microrag.domain.ingest.ObserveImportQueueUseCase
import com.lisdatasolutions.microrag.domain.ingest.RemoveQueueItemUseCase
import com.lisdatasolutions.microrag.domain.ingest.RetryQueueItemUseCase
import com.lisdatasolutions.microrag.domain.ingest.StartIndexingUseCase
import com.lisdatasolutions.microrag.domain.onboarding.BuildDownloadConfirmationUseCase
import com.lisdatasolutions.microrag.domain.onboarding.CompleteOnboardingUseCase
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

    // Document import (F06).
    factoryOf(::ObserveImportQueueUseCase)
    factoryOf(::ImportDocumentsUseCase)
    factoryOf(::StartIndexingUseCase)
    factoryOf(::RetryQueueItemUseCase)
    factoryOf(::RemoveQueueItemUseCase)
}
