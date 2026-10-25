package com.lisdatasolutions.microrag.core.di

import android.app.Application
import android.content.Context
import kotlinx.coroutines.CoroutineDispatcher
import org.junit.Test
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.dsl.module
import org.koin.test.verify.verify

@OptIn(KoinExperimentalAPI::class)
class AppModulesTest {

    @Test
    fun appModules_dependencyGraphIsComplete() {
        module { includes(appModules) }.verify(
            // AppDispatchers is built by its factory, not by constructor injection.
            extraTypes = listOf(Context::class, Application::class, CoroutineDispatcher::class),
        )
    }
}
