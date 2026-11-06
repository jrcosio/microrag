package com.lisdatasolutions.microrag.testing.bench

import com.lisdatasolutions.microrag.data.models.ModelFileRepository
import com.lisdatasolutions.microrag.domain.model.ComputeBackend
import com.lisdatasolutions.microrag.domain.model.ModelCatalog
import com.lisdatasolutions.microrag.domain.model.ModelFileState
import com.lisdatasolutions.microrag.domain.model.ModelId
import java.io.File
import java.io.InputStream
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * F14 (D-090): the model files a bench uses, verified by the bench itself, seen by the app's engines as verified
 * and prepared. It lets the benches load the 270M from `dev-models` without installing it. Read only.
 */
class FixedModels(private val files: Map<ModelId, File>, backends: Map<ModelId, ComputeBackend>) : ModelFileRepository {

    override val states = MutableStateFlow<Map<ModelId, ModelFileState>>(
        ModelCatalog.all.associate { model -> model.id to (files[model.id]?.let { ModelFileState.Verified(it) } ?: ModelFileState.Missing) },
    )
    override val preparedBackends = MutableStateFlow(backends.filterKeys { it in files })

    override suspend fun refresh() = Unit

    override suspend fun installVerified(id: ModelId, source: File, deleteSourceOnSuccess: Boolean) = unsupported()

    override suspend fun adopt(id: ModelId, file: File) = unsupported()

    override suspend fun installFromStream(id: ModelId, input: InputStream) = unsupported()

    override suspend fun verify(id: ModelId) = unsupported()

    override suspend fun markPrepared(id: ModelId, backend: ComputeBackend) = Unit

    override fun pathOf(id: ModelId): File? = files[id]

    override suspend fun delete(id: ModelId): Boolean = unsupported()

    private fun unsupported(): Nothing = throw UnsupportedOperationException("read-only bench repository")
}
