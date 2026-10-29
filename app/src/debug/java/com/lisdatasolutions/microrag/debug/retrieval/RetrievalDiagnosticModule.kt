package com.lisdatasolutions.microrag.debug.retrieval

import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/** Dependencies of the retrieval diagnostic (F10): the app's own use case, from the app modules. */
val retrievalDiagnosticModule = module {
    viewModel { RetrievalDiagnosticViewModel(get()) }
}
