package com.lisdatasolutions.microrag.data.models

import java.io.File
import java.io.IOException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** One verified model file (F03 data-model §7). */
@Serializable
data class VerificationEntry(
    val fileName: String,
    val sha256: String,
    val sizeBytes: Long,
    val lastModified: Long,
    val verifiedAt: String,
    /** `GPU` or `CPU` once the on-device test passed; null until then. */
    val preparedBackend: String? = null,
    val preparedAt: String? = null,
)

@Serializable
private data class VerificationRecord(val version: Int = 1, val entries: List<VerificationEntry> = emptyList())

/**
 * `models/verification.json`: lets each launch trust an already hashed file with a cheap size and
 * date check instead of re-hashing gigabytes (FR-022). Not thread-safe; the repository serializes
 * access. A missing or unreadable file is treated as empty, so the worst case is a re-hash.
 */
class VerificationRecordStore(private val file: File) {

    fun read(): Map<String, VerificationEntry> = try {
        if (!file.isFile) emptyMap() else json.decodeFromString<VerificationRecord>(file.readText()).entries.associateBy { it.fileName }
    } catch (_: SerializationException) {
        emptyMap()
    } catch (_: IllegalArgumentException) {
        emptyMap()
    } catch (_: IOException) {
        emptyMap()
    }

    /** Atomic write: a temporary file renamed over the record. */
    fun write(entries: Collection<VerificationEntry>) {
        file.parentFile?.mkdirs()
        val tmp = File(file.path + ".tmp")
        tmp.writeText(json.encodeToString(VerificationRecord.serializer(), VerificationRecord(entries = entries.sortedBy { it.fileName })))
        if (!tmp.renameTo(file)) {
            tmp.delete()
            throw IOException("cannot replace ${file.name}")
        }
    }

    private companion object {
        val json = Json { ignoreUnknownKeys = true; prettyPrint = true }
    }
}
