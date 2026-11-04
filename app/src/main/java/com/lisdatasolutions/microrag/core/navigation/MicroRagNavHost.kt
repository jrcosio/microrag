package com.lisdatasolutions.microrag.core.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.Lifecycle
import com.lisdatasolutions.microrag.domain.rag.LibraryFolderFilter
import com.lisdatasolutions.microrag.feature.chat.ChatRoute
import com.lisdatasolutions.microrag.feature.document.DocumentRoute
import com.lisdatasolutions.microrag.feature.history.HistoryRoute
import com.lisdatasolutions.microrag.feature.home.HomeRoute
import com.lisdatasolutions.microrag.feature.indexing.IndexRoute
import com.lisdatasolutions.microrag.feature.ingest.IngestRoute
import com.lisdatasolutions.microrag.feature.library.LibraryMessage
import com.lisdatasolutions.microrag.feature.library.LibraryRoute
import com.lisdatasolutions.microrag.feature.onboarding.setup.SetupRoute
import com.lisdatasolutions.microrag.feature.onboarding.welcome.WelcomeRoute
import com.lisdatasolutions.microrag.feature.settings.LanguagesRoute
import com.lisdatasolutions.microrag.feature.settings.SettingsRoute
import com.lisdatasolutions.microrag.feature.settings.ThemeRoute

/** Key of the Library entry's SavedStateHandle where the detail leaves a [LibraryMessage] (F08 B8). */
private const val LIBRARY_MESSAGE = "library_message"

/** Key of the chat entry's SavedStateHandle where Historial leaves what to show (F13 research R4). */
internal const val CHAT_REQUEST = "chat_request"

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
                onCompleted = { navController.leaveSetup() },
            )
        }
        composable<Home> { entry ->
            // F09 R4: only from a resumed Home, so a second tap while the first one navigates does nothing (FR-006).
            val open: (() -> Unit) -> () -> Unit = { navigate ->
                { if (entry.lifecycle.currentState == Lifecycle.State.RESUMED) navigate() }
            }
            HomeRoute(
                onNewChat = open { navController.openNewChat() },
                onImport = open { navController.navigate(Ingest) { launchSingleTop = true } },
                onLibrary = open { navController.navigateToTopLevel(TopLevelDestination.LIBRARY) },
                onIndex = open { navController.navigate(Index) { launchSingleTop = true } },
                onLanguages = open { navController.navigate(Languages) { launchSingleTop = true } },
                onSettings = open { navController.navigateToTopLevel(TopLevelDestination.SETTINGS) },
                onCompleteSetup = open { navController.navigate(Setup) { launchSingleTop = true } },
            )
        }
        composable<Chat> { entry ->
            // R13: a deleted document already reads «Fuente ya no disponible», so the detail's reason is dropped.
            LaunchedEffect(entry) {
                entry.savedStateHandle.getStateFlow<String?>(LIBRARY_MESSAGE, null).collect { message ->
                    if (message != null) entry.savedStateHandle[LIBRARY_MESSAGE] = null
                }
            }
            // F13 research R4: what Historial asked for, read once.
            val request by entry.savedStateHandle.getStateFlow<String?>(CHAT_REQUEST, null).collectAsStateWithLifecycle()
            val resumed: (() -> Unit) -> Unit = { navigate ->
                if (entry.lifecycle.currentState == Lifecycle.State.RESUMED) navigate()
            }
            ChatRoute(
                onBack = back,
                // R13: from a resumed chat only, so a double tap opens a single detail.
                onOpenSource = { documentId, chunkId -> resumed { navController.navigate(Document(documentId, chunkId)) } },
                onCompleteSetup = { resumed { navController.navigate(Setup) { launchSingleTop = true } } },
                onImport = { resumed { navController.navigate(Ingest) { launchSingleTop = true } } },
                onOpenIndex = { resumed { navController.navigate(Index) { launchSingleTop = true } } },
                onOpenHistory = { id -> resumed { navController.navigate(History(openConversationId = id)) { launchSingleTop = true } } },
                request = ChatRequest.parse(request),
                onRequestHandled = { entry.savedStateHandle[CHAT_REQUEST] = null },
            )
        }
        composable<History> { entry ->
            // Research R4: what to show goes back to the chat underneath, which reads it once.
            val leave: (ChatRequest) -> Unit = { request ->
                if (entry.lifecycle.currentState == Lifecycle.State.RESUMED) {
                    navController.previousBackStackEntry?.savedStateHandle?.set(CHAT_REQUEST, request.key)
                    navController.popBackStack()
                }
            }
            HistoryRoute(
                onBack = back,
                onOpenConversation = { leave(ChatRequest.Open(it)) },
                onNewChat = { leave(ChatRequest.New) },
            )
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
                // F17 K1: from a resumed root only, so a double tap opens a single folder.
                onOpenFolder = { id ->
                    if (entry.lifecycle.currentState == Lifecycle.State.RESUMED) navController.navigate(LibraryFolder(id))
                },
            )
        }
        composable<LibraryFolder> { entry ->
            val route = entry.toRoute<LibraryFolder>()
            val message by entry.savedStateHandle.getStateFlow<String?>(LIBRARY_MESSAGE, null).collectAsStateWithLifecycle()
            LibraryRoute(
                onBack = back,
                onImport = { navController.navigate(Ingest) },
                onOpenDocument = { navController.navigate(Document(documentId = it)) },
                message = message?.let(LibraryMessage::valueOf),
                onMessageShown = { entry.savedStateHandle[LIBRARY_MESSAGE] = null },
                folder = route.folderId?.let { LibraryFolderFilter.Folder(it) } ?: LibraryFolderFilter.Unfiled,
                // K2: a folder that goes takes its screen with it; the root says «Carpeta eliminada».
                onFolderGone = { deleted ->
                    if (entry.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                        if (deleted) navController.previousBackStackEntry?.savedStateHandle?.set(LIBRARY_MESSAGE, LibraryMessage.FOLDER_DELETED.name)
                        navController.popBackStack()
                    }
                },
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
        composable<Settings> {
            // G08: the Ajustes tab sits over Home, so Back returns to Home.
            SettingsRoute(
                onBack = back,
                onCompleteSetup = { navController.navigate(Setup) { launchSingleTop = true } },
                onLanguages = { navController.navigate(Languages) { launchSingleTop = true } },
                onTheme = { navController.navigate(Theme) { launchSingleTop = true } },
                onIndex = { navController.navigate(Index) { launchSingleTop = true } },
            )
        }
        // S7: Back and a save both return to where Idiomas was opened from, Ajustes or Home.
        composable<Languages> { LanguagesRoute(onBack = back) }
        // F16 U3: Back and a save both return to Ajustes; the system recreates the activity in the new theme.
        composable<Theme> { ThemeRoute(onBack = back) }
    }
}
