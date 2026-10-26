package com.lisdatasolutions.microrag.data.indexing

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/** [IngestRunStore] in memory, for JVM and instrumented tests. */
class InMemoryIngestRunStore(initial: IngestRun = IngestRun()) : IngestRunStore {

    private val state = MutableStateFlow(initial)
    override val run: StateFlow<IngestRun> = state

    @Volatile
    private var check: String? = null

    override suspend fun edit(transform: (IngestRun) -> IngestRun): IngestRun {
        state.update(transform)
        return state.value
    }

    override suspend fun gpuCheck(): String? = check

    override suspend fun setGpuCheck(value: String) {
        check = value
    }
}
