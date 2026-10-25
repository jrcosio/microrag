package com.lisdatasolutions.microrag.debug

import com.lisdatasolutions.microrag.debug.feasibility.feasibilityModule
import org.koin.core.module.Module

/** Koin modules that only exist in debug builds (F02 diagnostics). */
val debugModules: List<Module> = listOf(feasibilityModule)
