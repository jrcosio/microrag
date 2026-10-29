package com.lisdatasolutions.microrag.debug

import com.lisdatasolutions.microrag.debug.catalog.ComponentCatalogScreen
import com.lisdatasolutions.microrag.debug.feasibility.FeasibilityScreen
import com.lisdatasolutions.microrag.debug.retrieval.RetrievalDiagnosticScreen
import com.lisdatasolutions.microrag.debug.routes.RouteMenuScreen

/** Debug tools listed by [DebugLauncherActivity]. */
val debugEntries: List<DebugEntry> = listOf(
    DebugEntry("Catálogo de componentes") { onBack -> ComponentCatalogScreen(onBack) },
    DebugEntry("Menú de rutas") { onBack -> RouteMenuScreen(onBack) },
    DebugEntry("Viabilidad on-device") { onBack -> FeasibilityScreen(onBack) },
    DebugEntry("Diagnóstico de búsqueda") { onBack -> RetrievalDiagnosticScreen(onBack) },
)
