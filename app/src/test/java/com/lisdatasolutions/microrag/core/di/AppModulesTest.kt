package com.lisdatasolutions.microrag.core.di

import android.app.Application
import android.content.Context
import androidx.datastore.core.DataStore
import java.io.File
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import org.junit.Test
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.dsl.module
import org.koin.test.verify.verify

@OptIn(KoinExperimentalAPI::class)
class AppModulesTest {

    @Test
    fun appModules_dependencyGraphIsComplete() {
        module { includes(appModules) }.verify(
            // Built inside their definitions: dispatchers, scopes, folders and the DataStore.
            extraTypes = listOf(
                Context::class, Application::class, CoroutineDispatcher::class, CoroutineScope::class,
                File::class, DataStore::class, Function0::class, String::class,
            ),
        )
    }
}
