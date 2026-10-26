package com.lisdatasolutions.microrag.debug

import android.app.Application
import android.content.Context
import com.lisdatasolutions.microrag.core.di.appModules
import androidx.datastore.core.DataStore
import java.io.File
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import org.junit.Test
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.dsl.module
import org.koin.test.verify.verify

@OptIn(KoinExperimentalAPI::class)
class DebugModulesTest {

    @Test
    fun debugModules_dependencyGraphIsComplete() {
        module { includes(appModules + debugModules) }.verify(
            // Folders, scopes and the DataStore are built from the Context inside the definitions.
            extraTypes = listOf(
                Context::class, Application::class, CoroutineDispatcher::class, CoroutineScope::class,
                File::class, DataStore::class, Function0::class, String::class,
            ),
        )
    }
}
