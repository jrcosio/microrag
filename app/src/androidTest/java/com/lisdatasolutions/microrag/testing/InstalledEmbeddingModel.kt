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
 * F19 (analyze H5): the text embedder in use, the 270M, or the 440M once the multimodal mode deleted the 270M.
 */
class InstalledEmbeddingModel(context: Context) : ModelFileRepository {

    private val modelsDir = File(context.noBackupFilesDir, "models")

    private fun fileOf(id: ModelId) = File(modelsDir, ModelCatalog.byId(id).fileName)

    /** The 270M while it is there; otherwise the 440M, whose text vectors are the 270M's (constitution 1.2.0). */
    val id: ModelId = listOf(ModelId.EG2_270M, ModelId.EG2_440M).firstOrNull { fileOf(it).isFile } ?: ModelId.EG2_270M
    val file = fileOf(id)
    val isInstalled: Boolean get() = file.isFile

    override val states = MutableStateFlow<Map<ModelId, ModelFileState>>(
        ModelCatalog.all.associate { it.id to if (it.id == id && isInstalled) ModelFileState.Verified(file) else ModelFileState.Missing },
    )
    override val preparedBackends = MutableStateFlow(mapOf(id to ComputeBackend.CPU))

    override suspend fun refresh() = Unit

    override suspend fun installVerified(id: ModelId, source: File, deleteSourceOnSuccess: Boolean) = unsupported()

    override suspend fun adopt(id: ModelId, file: File) = unsupported()

    override suspend fun installFromStream(id: ModelId, input: InputStream) = unsupported()

    override suspend fun verify(id: ModelId) = unsupported()

    override suspend fun markPrepared(id: ModelId, backend: ComputeBackend) = Unit

    override fun pathOf(id: ModelId): File? = (states.value[id] as? ModelFileState.Verified)?.path

    override suspend fun delete(id: ModelId): Boolean = unsupported()

    private fun unsupported(): Nothing = throw UnsupportedOperationException("read-only test repository")
}
