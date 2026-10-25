package com.lisdatasolutions.microrag.data.models

import com.lisdatasolutions.microrag.domain.model.ModelFileState
import com.lisdatasolutions.microrag.domain.model.ModelId
import java.io.File
import kotlinx.coroutines.flow.StateFlow

/** Model files in private no-backup storage. A file only becomes loadable once its hash matches. */
interface ModelFileRepository {
    val states: StateFlow<Map<ModelId, ModelFileState>>

    /** Scans the models directory without hashing: present files become [ModelFileState.Unverified]. */
    suspend fun refresh()

    /**
     * Copies [source] into the models directory while hashing it. The file is only moved into place
     * when the hash matches; otherwise nothing is kept and [source] is left untouched.
     */
    suspend fun installVerified(id: ModelId, source: File, deleteSourceOnSuccess: Boolean): ModelFileState

    /** Re-hashes an installed file; a mismatching file is deleted. */
    suspend fun verify(id: ModelId): ModelFileState

    /** Path of the file only while it is [ModelFileState.Verified]. */
    fun pathOf(id: ModelId): File?
}
