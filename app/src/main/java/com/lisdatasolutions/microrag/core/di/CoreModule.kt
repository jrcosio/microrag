package com.lisdatasolutions.microrag.core.di

import com.lisdatasolutions.microrag.BuildConfig
import com.lisdatasolutions.microrag.core.dispatchers.AppDispatchers
import com.lisdatasolutions.microrag.core.locale.PlatformAppLanguageController
import com.lisdatasolutions.microrag.core.logging.AppLogger
import com.lisdatasolutions.microrag.core.logging.DefaultAppLogger
import com.lisdatasolutions.microrag.core.theme.PlatformAppThemeController
import com.lisdatasolutions.microrag.domain.settings.AppLanguageController
import com.lisdatasolutions.microrag.domain.settings.AppThemeController
import com.lisdatasolutions.microrag.domain.settings.SettingsNotices
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

/** Cross-cutting services shared by every layer. */
val coreModule = module {
    single { AppDispatchers.create() }
    single<AppLogger> { DefaultAppLogger(isEnabled = BuildConfig.DEBUG) }
    single { ApplicationScope(CoroutineScope(SupervisorJob() + get<AppDispatchers>().default)) }
    // F12: the app language is needed before Koin starts (AppLocale), so it lives in core (research R2).
    single<AppLanguageController> { PlatformAppLanguageController(androidContext(), get<AppDispatchers>().io, get()) }
    single { SettingsNotices() }
    // F16: the app theme is the system's per-app night mode (research R1), next to the app language.
    single<AppThemeController> { PlatformAppThemeController(androidContext(), get()) }
}
