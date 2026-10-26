package com.lisdatasolutions.microrag.data.models

import com.lisdatasolutions.microrag.domain.model.ModelFile
import java.io.File

/**
 * Private folders under `noBackupFilesDir` (constitution I, F03 data-model §10). F04 and F06 use
 * [index] and [documents]; the setup screen creates them all (FR-025).
 */
class AppDirs(root: File) {
    val models = File(root, "models")

    /** Partial downloads; a subfolder so the models scan never deletes them. */
    val downloads = File(models, "downloads")
    val documents = File(root, "documents")
    val index = File(root, "index")

    fun partial(file: ModelFile): File = File(downloads, file.fileName + PARTIAL_SUFFIX)

    /** Creates every folder; false if any of them could not be created. */
    fun ensureAll(): Boolean = listOf(models, downloads, documents, index).all { it.isDirectory || it.mkdirs() }

    companion object {
        const val PARTIAL_SUFFIX = ".download"
    }
}
