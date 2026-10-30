package com.lisdatasolutions.microrag.testing

import android.content.Context
import com.lisdatasolutions.microrag.data.models.ModelFileRepository
import com.lisdatasolutions.microrag.data.models.VerificationEntry
import com.lisdatasolutions.microrag.data.models.VerificationRecordStore
import com.lisdatasolutions.microrag.domain.model.ComputeBackend
import com.lisdatasolutions.microrag.domain.model.ModelCatalog
import com.lisdatasolutions.microrag.domain.model.ModelFileState
import com.lisdatasolutions.microrag.domain.model.ModelId
import java.io.File
import java.io.InputStream
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * The models the app's setup installed, read only (F11 research R15): a file is verified when the setup's
 * record still matches it (size and date, the check of every launch), and prepared on the backend the setup
 * recorded. It never hashes, moves, deletes or downloads a file, and never writes the record.
 */
class InstalledModels(context: Context) : ModelFileRepository {

    private val dir = File(context.noBackupFilesDir, "models")
    private val records = VerificationRecordStore(File(dir, RECORD_FILE)).read()

    private fun fileOf(id: ModelId) = File(dir, ModelCatalog.byId(id).fileName)

    private fun recorded(id: ModelId): VerificationEntry? {
        val file = fileOf(id)
        return records[file.name]?.takeIf { file.isFile && it.sizeBytes == file.length() && it.lastModified == file.lastModified() }
    }

    override val states = MutableStateFlow<Map<ModelId, ModelFileState>>(
        ModelCatalog.all.associate { model -> model.id to if (recorded(model.id) != null) ModelFileState.Verified(fileOf(model.id)) else ModelFileState.Missing },
    )
    override val preparedBackends = MutableStateFlow(
        ModelCatalog.all.mapNotNull { model ->
            val backend = recorded(model.id)?.preparedBackend?.let { name -> ComputeBackend.entries.firstOrNull { it.name == name } }
            backend?.let { model.id to it }
        }.toMap(),
    )

    /** Verified and prepared, as the app needs it. */
    fun isReady(id: ModelId): Boolean = states.value[id] is ModelFileState.Verified && id in preparedBackends.value

    override suspend fun refresh() = Unit

    override suspend fun installVerified(id: ModelId, source: File, deleteSourceOnSuccess: Boolean) = unsupported()

    override suspend fun adopt(id: ModelId, file: File) = unsupported()

    override suspend fun installFromStream(id: ModelId, input: InputStream) = unsupported()

    override suspend fun verify(id: ModelId) = unsupported()

    override suspend fun markPrepared(id: ModelId, backend: ComputeBackend) = Unit

    override fun pathOf(id: ModelId): File? = (states.value[id] as? ModelFileState.Verified)?.path

    private fun unsupported(): Nothing = throw UnsupportedOperationException("read-only test repository")

    private companion object {
        /** DefaultModelFileRepository's record (F03 data-model §7). */
        const val RECORD_FILE = "verification.json"
    }
}
