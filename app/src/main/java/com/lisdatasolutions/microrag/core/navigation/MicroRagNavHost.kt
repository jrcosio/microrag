package com.lisdatasolutions.microrag.core.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lisdatasolutions.microrag.R
import com.lisdatasolutions.microrag.feature.document.DocumentRoute
import com.lisdatasolutions.microrag.feature.indexing.IndexRoute
import com.lisdatasolutions.microrag.feature.ingest.IngestRoute
import com.lisdatasolutions.microrag.feature.library.LibraryMessage
import com.lisdatasolutions.microrag.feature.library.LibraryRoute
import com.lisdatasolutions.microrag.feature.onboarding.setup.SetupRoute
import com.lisdatasolutions.microrag.feature.onboarding.welcome.WelcomeRoute

/** Key of the Library entry's SavedStateHandle where the detail leaves a [LibraryMessage] (F08 B8). */
private const val LIBRARY_MESSAGE = "library_message"

/**
 * Navigation graph with every logical destination. Each feature replaces its placeholder without
 * changing routes or stack rules (contracts/navigation.md). No external deep links (FR-023).
 */
@Composable
fun MicroRagNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier,
    startDestination: Any = Home,
) {
    val back: () -> Unit = { navController.popBackStack() }
    NavHost(navController = navController, startDestination = startDestination, modifier = modifier) {
        composable<Welcome> { WelcomeRoute(onStart = { navController.navigate(Setup) }) }
        composable<Setup> {
            SetupRoute(
                onBack = back,
                // Onboarding done: Home becomes the root, so Back from Home leaves the app (FR-003).
                onCompleted = { navController.navigate(Home) { popUpTo<Welcome> { inclusive = true } } },
            )
        }
        composable<Home> {
            // F06 P6 and F07 P10: provisional entries to the import and indexing screens until F09 delivers Home.
            PendingDestination(
                "home",
                "F09",
                actions = listOf(
                    PendingAction(stringResource(R.string.ingest_provisional_entry)) { navController.navigate(Ingest) },
                    PendingAction(stringResource(R.string.index_provisional_entry)) { navController.navigate(Index) },
                ),
            )
        }
        composable<Chat> { entry ->
            val route = entry.toRoute<Chat>()
            PendingDestination("chat", "F11", args = mapOf("conversationId" to route.conversationId), onBack = back)
        }
        composable<Ingest> { IngestRoute(onBack = back, onStartIndexing = { navController.navigate(Index) }) }
        composable<Library> { entry ->
            // B8: what the detail left when it closed itself (F08 research R8).
            val message by entry.savedStateHandle.getStateFlow<String?>(LIBRARY_MESSAGE, null).collectAsStateWithLifecycle()
            // G08: the Library is a tab root over Home, so Back returns to Home.
            LibraryRoute(
                onBack = back,
                onImport = { navController.navigate(Ingest) },
                onOpenDocument = { navController.navigate(Document(documentId = it)) },
                message = message?.let(LibraryMessage::valueOf),
                onMessageShown = { entry.savedStateHandle[LIBRARY_MESSAGE] = null },
            )
        }
        composable<Document> {
            // R8: back to where it was opened from (the Library, or a citation), leaving the reason behind.
            val leave: (LibraryMessage) -> Unit = { message ->
                navController.previousBackStackEntry?.savedStateHandle?.set(LIBRARY_MESSAGE, message.name)
                navController.popBackStack()
            }
            DocumentRoute(
                onBack = back,
                onGone = { leave(LibraryMessage.DOCUMENT_GONE) },
                onDeleted = { leave(LibraryMessage.DOCUMENT_DELETED) },
                onOpenIndex = { navController.navigate(Index) },
            )
        }
        composable<Index> { IndexRoute(onBack = back, onPrepareModels = { navController.navigate(Setup) }) }
        composable<Settings> { PendingDestination("settings", "F12", onBack = back) }
        composable<Languages> { PendingDestination("languages", "F12", onBack = back) }
    }
}
