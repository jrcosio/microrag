package com.lisdatasolutions.microrag.feature.di

import com.lisdatasolutions.microrag.feature.onboarding.welcome.WelcomeViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

/** Screen ViewModels (`viewModelOf`). */
val featureModule = module {
    viewModelOf(::WelcomeViewModel)
}
