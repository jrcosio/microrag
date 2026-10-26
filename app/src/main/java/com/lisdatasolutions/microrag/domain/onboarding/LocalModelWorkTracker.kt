package com.lisdatasolutions.microrag.domain.onboarding

import com.lisdatasolutions.microrag.domain.model.LocalModelWork
import com.lisdatasolutions.microrag.domain.model.ModelId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** In-process state of on-device tests and imports, shown by the setup rows (data-model §5). */
class LocalModelWorkTracker {
    private val _work = MutableStateFlow<Map<ModelId, LocalModelWork>>(emptyMap())
    val work: StateFlow<Map<ModelId, LocalModelWork>> = _work.asStateFlow()

    fun set(id: ModelId, work: LocalModelWork?) = _work.update { if (work == null) it - id else it + (id to work) }
}
