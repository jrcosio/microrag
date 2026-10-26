package com.lisdatasolutions.microrag.core.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.lisdatasolutions.microrag.feature.onboarding.setup.SetupRoute
import com.lisdatasolutions.microrag.feature.onboarding.welcome.WelcomeRoute

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
        composable<Home> { PendingDestination("home", "F09") }
        composable<Chat> { entry ->
            val route = entry.toRoute<Chat>()
            PendingDestination("chat", "F11", args = mapOf("conversationId" to route.conversationId), onBack = back)
        }
        composable<Ingest> { PendingDestination("ingest", "F06", onBack = back) }
        composable<Library> { PendingDestination("library", "F08", onBack = back) }
        composable<Document> { entry ->
            val route = entry.toRoute<Document>()
            PendingDestination(
                "document",
                "F08",
                args = mapOf("documentId" to route.documentId, "chunkId" to route.chunkId),
                onBack = back,
            )
        }
        composable<Index> { PendingDestination("index", "F07", onBack = back) }
        composable<Settings> { PendingDestination("settings", "F12", onBack = back) }
        composable<Languages> { PendingDestination("languages", "F12", onBack = back) }
    }
}
