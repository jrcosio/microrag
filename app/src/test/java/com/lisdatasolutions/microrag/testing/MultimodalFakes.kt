package com.lisdatasolutions.microrag.testing

import com.lisdatasolutions.microrag.domain.multimodal.MultimodalStore
import com.lisdatasolutions.microrag.domain.multimodal.StoredMultimodal
import kotlinx.coroutines.flow.MutableStateFlow

/** The mode's copy in memory (F19 contract M1); [failWrites] makes every write fail and keep the value. */
class FakeMultimodalStore(initial: StoredMultimodal = StoredMultimodal()) : MultimodalStore {
    override val state = MutableStateFlow(initial)
    var failWrites = false
    val writes = mutableListOf<StoredMultimodal>()

    override suspend fun write(transform: (StoredMultimodal) -> StoredMultimodal): Boolean {
        if (failWrites) return false
        state.value = transform(state.value).also { writes += it }
        return true
    }
}
