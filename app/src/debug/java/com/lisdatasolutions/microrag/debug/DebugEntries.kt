package com.lisdatasolutions.microrag.debug

import com.lisdatasolutions.microrag.debug.catalog.ComponentCatalogScreen

/** Debug tools listed by [DebugLauncherActivity]. */
val debugEntries: List<DebugEntry> = listOf(
    DebugEntry("Catálogo de componentes") { onBack -> ComponentCatalogScreen(onBack) },
)
