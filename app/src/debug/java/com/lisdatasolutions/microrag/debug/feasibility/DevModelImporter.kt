package com.lisdatasolutions.microrag.debug.feasibility

import com.lisdatasolutions.microrag.data.models.ModelFileRepository
import com.lisdatasolutions.microrag.domain.model.ModelCatalog
import com.lisdatasolutions.microrag.domain.model.ModelFile
import com.lisdatasolutions.microrag.domain.model.ModelFileState
import com.lisdatasolutions.microrag.domain.model.ModelId
import java.io.File
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/**
 * Debug-only provisioning (contracts/provisioning.md): model files pushed with adb into [devDir]
 * are installed through [ModelFileRepository.installVerified], so nothing unverified is kept.
 */
class DevModelImporter(
    private val devDir: File,
    private val repository: ModelFileRepository,
    private val ioDispatcher: CoroutineDispatcher,
    private val catalog: List<ModelFile> = ModelCatalog.all,
) {

    /** The app must create [devDir]: a folder created by adb is unreadable for the app (research R7). */
    suspend fun ensureDir(): File = withContext(ioDispatcher) { devDir.apply { mkdirs() } }

    /** Catalog files currently waiting in [devDir], with their size in bytes. */
    suspend fun pendingCopies(): Map<ModelId, Long> = withContext(ioDispatcher) {
        catalog.mapNotNull { file ->
            File(devDir, file.fileName).takeIf { it.isFile }?.let { file.id to it.length() }
        }.toMap()
    }

    suspend fun importAll(): Map<ModelId, ModelFileState> = pendingCopies().keys.associateWith { id ->
        val file = catalog.first { it.id == id }
        repository.installVerified(id, File(devDir, file.fileName), deleteSourceOnSuccess = true)
    }
}
