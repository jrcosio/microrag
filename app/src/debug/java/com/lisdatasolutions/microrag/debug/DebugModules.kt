package com.lisdatasolutions.microrag.debug

import com.lisdatasolutions.microrag.debug.feasibility.feasibilityModule
import com.lisdatasolutions.microrag.debug.feasibility.visual.visualFeasibilityModule
import com.lisdatasolutions.microrag.debug.retrieval.retrievalDiagnosticModule
import org.koin.core.module.Module

/** Koin modules that only exist in debug builds (F02, F10 and F18 diagnostics). */
val debugModules: List<Module> = listOf(feasibilityModule, retrievalDiagnosticModule, visualFeasibilityModule)
