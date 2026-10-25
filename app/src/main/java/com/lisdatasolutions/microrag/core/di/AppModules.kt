package com.lisdatasolutions.microrag.core.di

import com.lisdatasolutions.microrag.data.di.dataModule
import com.lisdatasolutions.microrag.domain.di.domainModule
import com.lisdatasolutions.microrag.feature.di.featureModule
import org.koin.core.module.Module

/** Every Koin module of the app; AppModulesTest verifies the complete graph. */
val appModules: List<Module> = listOf(coreModule, domainModule, dataModule, featureModule)
