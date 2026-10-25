package com.lisdatasolutions.microrag.core.navigation

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.lisdatasolutions.microrag.R
import com.lisdatasolutions.microrag.core.designsystem.icon.LisdataIcons
import kotlin.reflect.KClass

/** Bottom bar destinations (FR-017). */
enum class TopLevelDestination(
    val route: Any,
    val routeClass: KClass<*>,
    @DrawableRes val icon: Int,
    @StringRes val label: Int,
) {
    HOME(Home, Home::class, LisdataIcons.Home, R.string.nav_home),
    CHAT(Chat(), Chat::class, LisdataIcons.Chat, R.string.nav_chat),
    LIBRARY(Library, Library::class, LisdataIcons.Folder, R.string.nav_library),
    SETTINGS(Settings, Settings::class, LisdataIcons.Settings, R.string.nav_settings),
}
