package com.lisdatasolutions.microrag.domain.di

import com.lisdatasolutions.microrag.domain.onboarding.ResolveStartDestinationUseCase
import com.lisdatasolutions.microrag.domain.onboarding.StartOnboardingUseCase
import org.koin.core.module.dsl.factoryOf
import org.koin.dsl.module

/** Use cases. */
val domainModule = module {
    factoryOf(::ResolveStartDestinationUseCase)
    factoryOf(::StartOnboardingUseCase)
}
