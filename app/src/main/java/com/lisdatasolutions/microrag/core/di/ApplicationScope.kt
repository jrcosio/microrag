package com.lisdatasolutions.microrag.core.di

import kotlinx.coroutines.CoroutineScope

/**
 * Process-lifetime scope for work that must outlive a screen: download queue progress, model
 * tests and imports (F03). Never cancelled.
 */
class ApplicationScope(val scope: CoroutineScope)
