package com.lisdatasolutions.microrag.domain.di

import com.lisdatasolutions.microrag.domain.onboarding.BuildDownloadConfirmationUseCase
import com.lisdatasolutions.microrag.domain.onboarding.LocalModelWorkTracker
import com.lisdatasolutions.microrag.domain.onboarding.ObserveModelSetupUseCase
import com.lisdatasolutions.microrag.domain.onboarding.PauseModelDownloadUseCase
import com.lisdatasolutions.microrag.domain.onboarding.ResolveStartDestinationUseCase
import com.lisdatasolutions.microrag.domain.onboarding.ResumeModelDownloadUseCase
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
}
