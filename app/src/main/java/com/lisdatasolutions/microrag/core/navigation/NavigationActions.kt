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

/** The tab the current back stack belongs to (Home when only Home is below). */
fun NavController.currentTopLevel(): TopLevelDestination =
    currentBackStack.value
        .asReversed()
        .firstNotNullOfOrNull { entry -> entry.destination.asTopLevelRoot()?.takeIf { it != TopLevelDestination.HOME } }
        ?: TopLevelDestination.HOME

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
