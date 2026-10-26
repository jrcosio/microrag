package com.lisdatasolutions.microrag.domain.onboarding

import java.io.InputStream

/** A file the user picked with the system picker (F03 FR-024); no broad storage permission involved. */
interface ModelFileSource {
    /** Declared size of the document, or null when the provider does not know it. */
    fun sizeOf(uri: String): Long?

    /** Opens the document for reading; the caller closes the stream. */
    fun open(uri: String): InputStream
}
