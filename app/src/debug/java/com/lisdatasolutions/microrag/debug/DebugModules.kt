package com.lisdatasolutions.microrag.debug

import com.lisdatasolutions.microrag.debug.feasibility.feasibilityModule
import com.lisdatasolutions.microrag.debug.retrieval.retrievalDiagnosticModule
import org.koin.core.module.Module

/** Koin modules that only exist in debug builds (F02 and F10 diagnostics). */
val debugModules: List<Module> = listOf(feasibilityModule, retrievalDiagnosticModule)
