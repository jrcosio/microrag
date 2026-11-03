package com.lisdatasolutions.microrag.core.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.lisdatasolutions.microrag.R
import com.lisdatasolutions.microrag.core.designsystem.component.LisdataBottomBar
import com.lisdatasolutions.microrag.core.designsystem.component.LisdataBottomBarItem
import com.lisdatasolutions.microrag.core.designsystem.theme.LisdataTheme
import com.lisdatasolutions.microrag.core.locale.AppLocale
import com.lisdatasolutions.microrag.domain.model.StartDestination
import com.lisdatasolutions.microrag.domain.settings.SettingsNotices
import kotlinx.coroutines.flow.first
import org.koin.compose.koinInject

/**
 * App shell: navigation graph plus the bottom bar on tab roots (FR-017, FR-020). [startDestination]
 * comes from the persisted onboarding stage (F03 research R10). [initialRoute] is only used by debug
 * tooling to open a destination directly; release builds always pass null.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MicroRagAppShell(
    modifier: Modifier = Modifier,
    startDestination: StartDestination = StartDestination.HOME,
    initialRoute: Any? = null,
    navController: NavHostController = rememberNavController(),
    /** Grows with every tap on the indexing notification that should open Index (F07 research R3). */
    openIndexing: Int = 0,
    notices: SettingsNotices = koinInject(),
) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val tabRoot = backStackEntry?.destination?.asTopLevelRoot()
    val fullBleed = backStackEntry?.destination?.hasRoute(Welcome::class) == true
    val items = TopLevelDestination.entries.map { LisdataBottomBarItem(it.icon, stringResource(it.label)) }

    Scaffold(
        modifier = modifier,
        containerColor = LisdataTheme.colors.background,
        // The welcome gradient is drawn edge to edge; the screen pads its own content (PNG 01).
        contentWindowInsets = if (fullBleed) WindowInsets(0) else WindowInsets.navigationBars,
        bottomBar = {
            if (shouldShowBottomBar(tabRoot, imeVisible = WindowInsets.isImeVisible)) {
                LisdataBottomBar(
                    items = items,
                    selectedIndex = tabRoot?.ordinal ?: 0,
                    onSelect = { index -> navController.navigateToTopLevel(TopLevelDestination.entries[index]) },
                )
            }
        },
    ) { padding ->
        MicroRagNavHost(
            navController = navController,
            // Consumed so a screen's own navigationBarsPadding() does not add the inset a second time.
            modifier = Modifier.padding(padding).consumeWindowInsets(padding),
            startDestination = if (startDestination == StartDestination.HOME) Home else Welcome,
        )
    }

    // An onboarding already started reopens setup with Welcome below it, so Back still works (FR-003).
    var setupRestored by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(startDestination) {
        if (startDestination != StartDestination.SETUP_OVER_WELCOME || setupRestored || initialRoute != null) return@LaunchedEffect
        // The NavHost sets its graph in the Scaffold's subcomposition, after this effect starts.
        navController.currentBackStackEntryFlow.first()
        setupRestored = true
        navController.navigate(Setup)
    }

    LaunchedEffect(openIndexing) {
        if (openIndexing == 0) return@LaunchedEffect
        navController.currentBackStackEntryFlow.first()
        // Back from it leads to Home, the root once onboarding is done.
        navController.navigate(Index) { launchSingleTop = true }
    }

    SettingsSavedAnnouncement(notices)

    var initialRouteHandled by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(initialRoute) {
        if (initialRoute == null || initialRouteHandled) return@LaunchedEffect
        navController.currentBackStackEntryFlow.first()
        initialRouteHandled = true
        val tab = TopLevelDestination.entries.firstOrNull { it.routeClass.isInstance(initialRoute) && it.route == initialRoute }
        when {
            tab != null -> navController.navigateToTopLevel(tab)
            // F13 research R4: Historial always sits over the chat, which receives what it opens.
            initialRoute is History -> {
                navController.navigateToTopLevel(TopLevelDestination.CHAT)
                navController.navigate(initialRoute)
            }
            else -> navController.navigate(initialRoute)
        }
    }
}

/**
 * F12 P7 (S9): «Preferencias guardadas», read politely once. It waits until this screen shows the new app
 * language, so the activity about to be recreated never says it with the old resources.
 */
@Composable
internal fun SettingsSavedAnnouncement(notices: SettingsNotices) {
    val pending by notices.savedPending.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val shown = LocalConfiguration.current.locales[0].language
    val savedText = stringResource(R.string.languages_saved)
    var announcement by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(pending, shown) {
        if (pending && shown == AppLocale.resolve(context).language) {
            announcement = savedText
            notices.consumeSaved()
        }
    }
    announcement?.let { text ->
        Box(
            modifier = Modifier.semantics {
                liveRegion = LiveRegionMode.Polite
                stateDescription = text
            },
        )
    }
}

