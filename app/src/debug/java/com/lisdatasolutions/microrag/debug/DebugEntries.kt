package com.lisdatasolutions.microrag.debug

import com.lisdatasolutions.microrag.debug.catalog.ComponentCatalogScreen
import com.lisdatasolutions.microrag.debug.routes.RouteMenuScreen

/** Debug tools listed by [DebugLauncherActivity]. */
val debugEntries: List<DebugEntry> = listOf(
    DebugEntry("Catálogo de componentes") { onBack -> ComponentCatalogScreen(onBack) },
    DebugEntry("Menú de rutas") { onBack -> RouteMenuScreen(onBack) },
)
