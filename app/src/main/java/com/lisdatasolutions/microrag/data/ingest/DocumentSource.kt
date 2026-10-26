package com.lisdatasolutions.microrag.data.ingest

import java.io.InputStream

/** Name, size and type of a picked file, as its provider reports them; [sizeBytes] may be unknown. */
data class SourceInfo(val displayName: String, val sizeBytes: Long?, val mimeType: String?)

/** Reads files the user picked with the system picker (F06 research R4). Blocking: call it on IO. */
interface DocumentSource {
    /** Throws [SecurityException] or [java.io.FileNotFoundException] when the file can no longer be read. */
    fun describe(uri: String): SourceInfo

    fun open(uri: String): InputStream
}
