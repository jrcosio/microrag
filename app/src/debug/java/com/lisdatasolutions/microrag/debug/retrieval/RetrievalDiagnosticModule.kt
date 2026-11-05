package com.lisdatasolutions.microrag.debug.retrieval

import com.lisdatasolutions.microrag.domain.multimodal.MultimodalStage
import com.lisdatasolutions.microrag.domain.multimodal.MultimodalStore
import com.lisdatasolutions.microrag.domain.rag.RagIndex
import kotlinx.coroutines.flow.first
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/** Dependencies of the retrieval diagnostic (F10): the app's own use case, from the app modules; F20, the mode. */
val retrievalDiagnosticModule = module {
    viewModel {
        RetrievalDiagnosticViewModel(get()) {
            get<MultimodalStore>().state.first().stage == MultimodalStage.ON && get<RagIndex>().pagesUsable.value
        }
    }
}
