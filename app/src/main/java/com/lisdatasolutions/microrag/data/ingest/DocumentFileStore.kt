package com.lisdatasolutions.microrag.data.ingest

import com.lisdatasolutions.microrag.domain.ingest.DocumentFormat
import java.io.File
import java.util.UUID

/**
 * Private copies of the imported documents in `noBackupFilesDir/documents` (F06 data-model §2, G22).
 * A copy is written to a hidden temporary file and only renamed to `<sha256>.<ext>` when complete, so a
 * registered document always has its whole copy (contracts/ingest-api.md I1). Blocking: call it on the
 * IO dispatcher.
 */
class DocumentFileStore(private val dir: File) {

    fun newTempFile(): File {
        dir.mkdirs()
        return File(dir, "$TEMP_PREFIX${UUID.randomUUID()}$TEMP_SUFFIX")
    }

    fun finalFile(sha256: String, format: DocumentFormat): File = File(dir, "$sha256.${format.extension}")

    /** Renames a complete [temp] copy to its final name; an orphan with that name (same content) is replaced. */
    fun promote(temp: File, sha256: String, format: DocumentFormat): File {
        val target = finalFile(sha256, format)
        if (!temp.renameTo(target)) {
            target.delete()
            check(temp.renameTo(target)) { "Cannot move the imported copy into place" }
        }
        return target
    }

    /** Deletes a copy of this store; paths outside it are ignored. */
    fun delete(path: String?) {
        val file = path?.let(::File) ?: return
        if (file.parentFile?.canonicalPath == dir.canonicalPath) file.delete()
    }

    /** Deletes temporary files and copies that belong to no document; returns how many were deleted. */
    fun cleanOrphans(referenced: Set<String>): Int {
        val keep = referenced.mapTo(HashSet()) { File(it).canonicalPath }
        return dir.listFiles().orEmpty()
            .filter { it.isFile && (it.name.endsWith(TEMP_SUFFIX) || it.canonicalPath !in keep) }
            .count { it.delete() }
    }

    private companion object {
        const val TEMP_PREFIX = ".import-"
        const val TEMP_SUFFIX = ".tmp"
    }
}
