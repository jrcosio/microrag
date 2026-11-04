package com.lisdatasolutions.microrag.debug

import android.app.Application
import android.content.Context
import androidx.datastore.core.DataStore
import androidx.lifecycle.SavedStateHandle
import com.lisdatasolutions.microrag.core.di.appModules
import com.lisdatasolutions.microrag.domain.rag.LibraryFolderFilter
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
                // Given by koinViewModel to each screen's ViewModel (F08 research R9), and its folder (F17 R5).
                SavedStateHandle::class, LibraryFolderFilter::class,
                // F17: the chat session's availability and scope resolution, built in its definition.
                Function1::class, Function2::class,
            ),
        )
    }
}
