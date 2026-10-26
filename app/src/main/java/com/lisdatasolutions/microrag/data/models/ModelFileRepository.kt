package com.lisdatasolutions.microrag.data.models

import com.lisdatasolutions.microrag.domain.model.ComputeBackend
import com.lisdatasolutions.microrag.domain.model.ModelFileState
import com.lisdatasolutions.microrag.domain.model.ModelId
import java.io.File
import java.io.InputStream
import kotlinx.coroutines.flow.StateFlow

/** Model files in private no-backup storage. A file only becomes loadable once its hash matches. */
interface ModelFileRepository {
    val states: StateFlow<Map<ModelId, ModelFileState>>

    /** Backend that passed the on-device test, only for files that are still [ModelFileState.Verified]. */
    val preparedBackends: StateFlow<Map<ModelId, ComputeBackend>>

    /**
     * Scans the models directory without hashing. A file matching its verification record (size and
     * date) is [ModelFileState.Verified]; any other present file is [ModelFileState.Unverified].
     */
    suspend fun refresh()

    /**
     * Copies [source] into the models directory while hashing it. The file is only moved into place
     * when the hash matches; otherwise nothing is kept and [source] is left untouched.
     */
    suspend fun installVerified(id: ModelId, source: File, deleteSourceOnSuccess: Boolean): ModelFileState

    /**
     * Hashes a finished download in place and renames it into the models directory when it matches,
     * without copying (F03 research R4). A mismatching [file] is deleted.
     */
    suspend fun adopt(id: ModelId, file: File): ModelFileState

    /** Like [installVerified] for a stream (system file picker); the caller closes [input]. */
    suspend fun installFromStream(id: ModelId, input: InputStream): ModelFileState

    /** Re-hashes an installed file; a mismatching file is deleted. */
    suspend fun verify(id: ModelId): ModelFileState

    /** Records that a verified file passed the on-device test on [backend] (F03 FR-021). */
    suspend fun markPrepared(id: ModelId, backend: ComputeBackend)

    /** Path of the file only while it is [ModelFileState.Verified]. */
    fun pathOf(id: ModelId): File?
}
