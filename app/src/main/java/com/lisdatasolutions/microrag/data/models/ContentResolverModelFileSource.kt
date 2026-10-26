package com.lisdatasolutions.microrag.data.models

import android.content.ContentResolver
import android.provider.OpenableColumns
import androidx.core.net.toUri
import com.lisdatasolutions.microrag.domain.onboarding.ModelFileSource
import java.io.FileNotFoundException
import java.io.InputStream

/** Reads a document picked with the Storage Access Framework (constitution I: no storage permission). */
class ContentResolverModelFileSource(private val resolver: ContentResolver) : ModelFileSource {

    override fun sizeOf(uri: String): Long? =
        resolver.query(uri.toUri(), arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
            val column = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (column >= 0 && cursor.moveToFirst() && !cursor.isNull(column)) cursor.getLong(column) else null
        }

    override fun open(uri: String): InputStream =
        resolver.openInputStream(uri.toUri()) ?: throw FileNotFoundException("no stream")
}
