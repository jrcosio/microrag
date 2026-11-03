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
import com.lisdatasolutions.microrag.domain.settings.ObserveLanguageSettingsUseCase
import com.lisdatasolutions.microrag.domain.settings.ObserveModelOverviewUseCase
import com.lisdatasolutions.microrag.domain.settings.ObserveThemeSettingsUseCase
import com.lisdatasolutions.microrag.domain.settings.ReconcileThemeUseCase
import com.lisdatasolutions.microrag.domain.settings.SaveLanguageSettingsUseCase
import com.lisdatasolutions.microrag.domain.onboarding.ReconcileDownloadsUseCase
import com.lisdatasolutions.microrag.domain.onboarding.ResolveStartDestinationUseCase
import com.lisdatasolutions.microrag.domain.onboarding.ResumeModelDownloadUseCase
import com.lisdatasolutions.microrag.domain.onboarding.RetryModelUseCase
import com.lisdatasolutions.microrag.domain.onboarding.StartModelDownloadUseCase
import com.lisdatasolutions.microrag.domain.onboarding.StartOnboardingUseCase
import com.lisdatasolutions.microrag.domain.chat.AskQuestionUseCase
import com.lisdatasolutions.microrag.domain.chat.CancelAnswerUseCase
import com.lisdatasolutions.microrag.domain.chat.ClearChatHistoryUseCase
import com.lisdatasolutions.microrag.domain.chat.ConversationExistsUseCase
import com.lisdatasolutions.microrag.domain.chat.CountConversationsUseCase
import com.lisdatasolutions.microrag.domain.chat.DeleteConversationUseCase
import com.lisdatasolutions.microrag.domain.chat.IsAnswerInProgressUseCase
import com.lisdatasolutions.microrag.domain.chat.LastConversationUseCase
import com.lisdatasolutions.microrag.domain.chat.ObserveAnswerInProgressUseCase
import com.lisdatasolutions.microrag.domain.chat.ObserveChatAvailabilityUseCase
import com.lisdatasolutions.microrag.domain.chat.ObserveConversationDeletionsUseCase
import com.lisdatasolutions.microrag.domain.chat.ObserveConversationUseCase
import com.lisdatasolutions.microrag.domain.chat.ObserveConversationsUseCase
import com.lisdatasolutions.microrag.domain.chat.ObserveHistoryClearedUseCase
import com.lisdatasolutions.microrag.domain.chat.OpenConversationUseCase
import com.lisdatasolutions.microrag.domain.chat.ResolveSourcesUseCase
import com.lisdatasolutions.microrag.domain.chat.RetryAnswerUseCase
import com.lisdatasolutions.microrag.domain.chat.StartNewConversationUseCase
import com.lisdatasolutions.microrag.domain.chat.WarmUpAnswerEngineUseCase
import com.lisdatasolutions.microrag.domain.retrieval.RetrieveContextUseCase
import com.lisdatasolutions.microrag.domain.settings.SaveThemeSettingsUseCase
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
    factoryOf(::ObserveModelOverviewUseCase)
    factoryOf(::ObserveLanguageSettingsUseCase)
    factoryOf(::SaveLanguageSettingsUseCase)
    factoryOf(::ObserveThemeSettingsUseCase)
    factoryOf(::SaveThemeSettingsUseCase)
    factoryOf(::ReconcileThemeUseCase)

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

    // Retrieval (F10).
    factoryOf(::RetrieveContextUseCase)

    // Chat (F11).
    factoryOf(::ObserveChatAvailabilityUseCase)
    factoryOf(::ObserveConversationUseCase)
    factoryOf(::AskQuestionUseCase)
    factoryOf(::CancelAnswerUseCase)
    factoryOf(::RetryAnswerUseCase)
    factoryOf(::StartNewConversationUseCase)
    factoryOf(::DeleteConversationUseCase)
    factoryOf(::LastConversationUseCase)
    factoryOf(::CountConversationsUseCase)
    factoryOf(::IsAnswerInProgressUseCase)
    factoryOf(::ClearChatHistoryUseCase)
    factoryOf(::ObserveHistoryClearedUseCase)
    factoryOf(::WarmUpAnswerEngineUseCase)
    factoryOf(::ResolveSourcesUseCase)

    // Chat history (F13).
    factoryOf(::ObserveConversationsUseCase)
    factoryOf(::OpenConversationUseCase)
    factoryOf(::ObserveConversationDeletionsUseCase)
    factoryOf(::ObserveAnswerInProgressUseCase)
    factoryOf(::ConversationExistsUseCase)
}
