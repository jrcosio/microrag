package com.lisdatasolutions.microrag.feature.di

import com.lisdatasolutions.microrag.feature.chat.ChatViewModel
import com.lisdatasolutions.microrag.feature.document.DocumentViewModel
import com.lisdatasolutions.microrag.feature.history.HistoryViewModel
import com.lisdatasolutions.microrag.feature.indexing.IndexViewModel
import com.lisdatasolutions.microrag.feature.ingest.IngestViewModel
import com.lisdatasolutions.microrag.feature.folders.FoldersViewModel
import com.lisdatasolutions.microrag.feature.library.LibraryViewModel
import com.lisdatasolutions.microrag.feature.onboarding.setup.SetupViewModel
import com.lisdatasolutions.microrag.feature.onboarding.welcome.WelcomeViewModel
import com.lisdatasolutions.microrag.feature.home.HomeViewModel
import com.lisdatasolutions.microrag.feature.settings.LanguagesViewModel
import com.lisdatasolutions.microrag.feature.settings.SettingsViewModel
import com.lisdatasolutions.microrag.feature.settings.ThemeViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

/** Screen ViewModels (`viewModelOf`). */
val featureModule = module {
    viewModelOf(::WelcomeViewModel)
    viewModel {
        SetupViewModel(get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get())
    }
    viewModel { IngestViewModel(get(), get(), get(), get(), get(), get(), get()) }
    viewModel { IndexViewModel(get(), get(), get(), get()) }
    // F17 (research R5): the folder comes from the route, as a parameter; none is the root.
    viewModel { params -> LibraryViewModel(get(), get(), get(), folder = params.getOrNull()) }
    viewModel { FoldersViewModel(get(), get(), get(), get(), get()) }
    viewModel { HomeViewModel(get(), get(), get(), get(), get()) }
    viewModel { DocumentViewModel(get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get()) }
    viewModel { ChatViewModel(get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get()) }
    viewModel { SettingsViewModel(get(), get(), get(), get(), get(), get(), get(), get()) }
    viewModelOf(::LanguagesViewModel)
    viewModelOf(::ThemeViewModel)
    viewModel { HistoryViewModel(get(), get(), get(), get()) }
}
