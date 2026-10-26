package com.lisdatasolutions.microrag.core.navigation

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.lisdatasolutions.microrag.core.designsystem.component.LisdataBottomBar
import com.lisdatasolutions.microrag.core.designsystem.component.LisdataBottomBarItem
import com.lisdatasolutions.microrag.core.designsystem.theme.LisdataTheme
import com.lisdatasolutions.microrag.domain.model.StartDestination

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
            modifier = Modifier.padding(padding),
            startDestination = if (startDestination == StartDestination.HOME) Home else Welcome,
        )
    }

    // An onboarding already started reopens setup with Welcome below it, so Back still works (FR-003).
    var setupRestored by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(startDestination) {
        if (startDestination != StartDestination.SETUP_OVER_WELCOME || setupRestored || initialRoute != null) return@LaunchedEffect
        setupRestored = true
        navController.navigate(Setup)
    }

    var initialRouteHandled by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(initialRoute) {
        if (initialRoute == null || initialRouteHandled) return@LaunchedEffect
        initialRouteHandled = true
        val tab = TopLevelDestination.entries.firstOrNull { it.routeClass.isInstance(initialRoute) && it.route == initialRoute }
        if (tab != null) navController.navigateToTopLevel(tab) else navController.navigate(initialRoute)
    }
}
