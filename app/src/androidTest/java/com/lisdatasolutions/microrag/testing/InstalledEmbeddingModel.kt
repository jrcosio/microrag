package com.lisdatasolutions.microrag.testing

import android.content.Context
import com.lisdatasolutions.microrag.data.models.ModelFileRepository
import com.lisdatasolutions.microrag.domain.model.ComputeBackend
import com.lisdatasolutions.microrag.domain.model.ModelCatalog
import com.lisdatasolutions.microrag.domain.model.ModelFileState
import com.lisdatasolutions.microrag.domain.model.ModelId
import java.io.File
import java.io.InputStream
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * The embedding model already installed on the device, seen as verified and prepared on the CPU, for
 * `@RequiresModels` retrieval tests (F10). Read only: it never hashes, moves, deletes or downloads a file.
 */
class InstalledEmbeddingModel(context: Context) : ModelFileRepository {

    val file = File(context.noBackupFilesDir, "models/${ModelCatalog.byId(ModelId.EG2_270M).fileName}")
    val isInstalled: Boolean get() = file.isFile

    override val states = MutableStateFlow<Map<ModelId, ModelFileState>>(
        ModelCatalog.all.associate { it.id to if (it.id == ModelId.EG2_270M && isInstalled) ModelFileState.Verified(file) else ModelFileState.Missing },
    )
    override val preparedBackends = MutableStateFlow(mapOf(ModelId.EG2_270M to ComputeBackend.CPU))

    override suspend fun refresh() = Unit

    override suspend fun installVerified(id: ModelId, source: File, deleteSourceOnSuccess: Boolean) = unsupported()

    override suspend fun adopt(id: ModelId, file: File) = unsupported()

    override suspend fun installFromStream(id: ModelId, input: InputStream) = unsupported()

    override suspend fun verify(id: ModelId) = unsupported()

    override suspend fun markPrepared(id: ModelId, backend: ComputeBackend) = Unit

    override fun pathOf(id: ModelId): File? = (states.value[id] as? ModelFileState.Verified)?.path

    private fun unsupported(): Nothing = throw UnsupportedOperationException("read-only test repository")
}
