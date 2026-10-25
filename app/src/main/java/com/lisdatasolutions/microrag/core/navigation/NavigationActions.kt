package com.lisdatasolutions.microrag.core.navigation

import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute

/** The tab whose root this destination is, or null for secondary destinations. */
fun NavDestination.asTopLevelRoot(): TopLevelDestination? =
    TopLevelDestination.entries.firstOrNull { hasRoute(it.routeClass) }

/** N6: the bottom bar shows only on tab roots and never over the keyboard (FR-017, FR-020). */
fun shouldShowBottomBar(destination: TopLevelDestination?, imeVisible: Boolean): Boolean =
    destination != null && !imeVisible

/**
 * The tab the current back stack belongs to (Home when only Home is below). Tabs are kept as a single
 * instance above Home (N1), so at most one non-Home tab root is in the back stack.
 */
fun NavController.currentTopLevel(): TopLevelDestination =
    TopLevelDestination.entries
        .filter { it != TopLevelDestination.HOME }
        .firstOrNull { hasRootInBackStack(it) }
        ?: TopLevelDestination.HOME

private fun NavController.hasRootInBackStack(destination: TopLevelDestination): Boolean = runCatching {
    when (destination) {
        TopLevelDestination.HOME -> getBackStackEntry<Home>()
        TopLevelDestination.CHAT -> getBackStackEntry<Chat>()
        TopLevelDestination.LIBRARY -> getBackStackEntry<Library>()
        TopLevelDestination.SETTINGS -> getBackStackEntry<Settings>()
    }
}.isSuccess

/**
 * N1 + N4: switch tabs keeping a single instance of each, saving and restoring their state; selecting
 * the current tab again returns to its root.
 */
fun NavController.navigateToTopLevel(destination: TopLevelDestination) {
    if (destination == currentTopLevel()) {
        popToRoot(destination)
        return
    }
    navigate(destination.route) {
        popUpTo<Home> { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

private fun NavController.popToRoot(destination: TopLevelDestination) {
    when (destination) {
        TopLevelDestination.HOME -> popBackStack<Home>(inclusive = false)
        TopLevelDestination.CHAT -> popBackStack<Chat>(inclusive = false)
        TopLevelDestination.LIBRARY -> popBackStack<Library>(inclusive = false)
        TopLevelDestination.SETTINGS -> popBackStack<Settings>(inclusive = false)
    }
}
