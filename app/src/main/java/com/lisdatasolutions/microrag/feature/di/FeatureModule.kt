package com.lisdatasolutions.microrag.feature.di

import com.lisdatasolutions.microrag.feature.document.DocumentViewModel
import com.lisdatasolutions.microrag.feature.indexing.IndexViewModel
import com.lisdatasolutions.microrag.feature.ingest.IngestViewModel
import com.lisdatasolutions.microrag.feature.library.LibraryViewModel
import com.lisdatasolutions.microrag.feature.onboarding.setup.SetupViewModel
import com.lisdatasolutions.microrag.feature.onboarding.welcome.WelcomeViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

/** Screen ViewModels (`viewModelOf`). */
val featureModule = module {
    viewModelOf(::WelcomeViewModel)
    viewModel {
        SetupViewModel(get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get())
    }
    viewModel { IngestViewModel(get(), get(), get(), get(), get()) }
    viewModel { IndexViewModel(get(), get(), get(), get()) }
    viewModel { LibraryViewModel(get(), get(), get()) }
    viewModel { DocumentViewModel(get(), get(), get(), get(), get(), get(), get(), get(), get()) }
}
