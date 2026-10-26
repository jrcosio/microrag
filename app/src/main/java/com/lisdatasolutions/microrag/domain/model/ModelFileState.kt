package com.lisdatasolutions.microrag.domain.model

import java.io.File

/** Local state of a catalog file. Only [Verified] files may be loaded (constitution V). */
sealed interface ModelFileState {

    /** Not present in the models directory. */
    data object Missing : ModelFileState

    /** Present without a matching verification record; must be hashed before loading. */
    data class Unverified(val path: File) : ModelFileState

    /** SHA-256 in progress, with real byte progress. */
    data class Verifying(val bytesDone: Long, val totalBytes: Long) : ModelFileState

    data class Verified(val path: File) : ModelFileState

    /** Hash differs from the published one; the installed file has already been deleted. */
    data class Mismatch(val expectedSha256: String, val actualSha256: String) : ModelFileState

    data class Failed(val reason: String) : ModelFileState
}
