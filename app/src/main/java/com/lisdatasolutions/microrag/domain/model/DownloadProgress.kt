package com.lisdatasolutions.microrag.domain.model

/**
 * Process-wide state of the model download queue. [bytesDone] is the size really written to disk
 * for [active], never an estimate (constitution V). [outcomes] keeps the last result per model
 * until a new download is scheduled.
 */
data class DownloadProgress(
    val active: ModelId? = null,
    val bytesDone: Long = 0,
    val totalBytes: Long = 0,
    val outcomes: Map<ModelId, DownloadOutcome> = emptyMap(),
)

sealed interface DownloadOutcome {
    val id: ModelId

    /** Downloaded, hash verified and moved into the models directory. */
    data class Completed(override val id: ModelId) : DownloadOutcome

    data class NoSpace(override val id: ModelId, val neededBytes: Long) : DownloadOutcome

    data class Failed(override val id: ModelId, val error: DownloadError) : DownloadOutcome

    /** Paused by the user or stopped by the system; the partial file is kept. */
    data class Cancelled(override val id: ModelId) : DownloadOutcome
}

sealed interface DownloadError {
    data class Http(val code: Int) : DownloadError

    data class Network(val message: String) : DownloadError

    data class ForbiddenHost(val host: String) : DownloadError

    data object InsecureScheme : DownloadError

    data object TooManyRedirects : DownloadError

    /** The server answered a different range or total size than requested. */
    data object RangeMismatch : DownloadError

    data object HashMismatch : DownloadError

    data class Io(val message: String) : DownloadError
}
