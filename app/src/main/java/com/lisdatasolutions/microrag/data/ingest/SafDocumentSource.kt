package com.lisdatasolutions.microrag.data.ingest

import android.content.ContentResolver
import android.provider.OpenableColumns
import androidx.core.net.toUri
import java.io.File
import java.io.FileNotFoundException
import java.io.InputStream

/**
 * Files picked with the system picker (Storage Access Framework, constitution I): only the temporary
 * read grant of the picker is used, never a persisted one (research R4). Blocking: call it on IO.
 */
class SafDocumentSource(private val resolver: ContentResolver) : DocumentSource {

    override fun describe(uri: String): SourceInfo {
        val parsed = uri.toUri()
        if (parsed.scheme == ContentResolver.SCHEME_FILE) {
            val file = File(requireNotNull(parsed.path))
            if (!file.isFile) throw FileNotFoundException("missing file")
            return SourceInfo(file.name, file.length(), null)
        }
        var name: String? = null
        var size: Long? = null
        resolver.query(parsed, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameColumn = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeColumn = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (nameColumn >= 0 && !cursor.isNull(nameColumn)) name = cursor.getString(nameColumn)
                if (sizeColumn >= 0 && !cursor.isNull(sizeColumn)) size = cursor.getLong(sizeColumn)
            }
        }
        val fallback = parsed.lastPathSegment?.substringAfterLast('/')?.substringAfterLast(':')
        return SourceInfo(
            displayName = name?.takeIf { it.isNotBlank() } ?: fallback?.takeIf { it.isNotBlank() } ?: DEFAULT_NAME,
            sizeBytes = size?.takeIf { it >= 0 },
            mimeType = resolver.getType(parsed),
        )
    }

    override fun open(uri: String): InputStream =
        resolver.openInputStream(uri.toUri()) ?: throw FileNotFoundException("no stream")

    private companion object {
        const val DEFAULT_NAME = "documento"
    }
}
