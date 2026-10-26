package com.lisdatasolutions.microrag.data.download

import com.lisdatasolutions.microrag.domain.model.DownloadError
import com.lisdatasolutions.microrag.domain.model.ModelFile
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.MalformedURLException
import java.net.URL
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface DownloadResult {
    data object Complete : DownloadResult

    data class Failed(val error: DownloadError) : DownloadResult
}

/** Writes one model file into a partial file, resuming from what is on disk. */
fun interface FileDownloader {
    suspend fun download(file: ModelFile, partial: File, onProgress: (bytesOnDisk: Long) -> Unit): DownloadResult
}

/**
 * Downloads one model file into a partial file, resuming with `Range` from what is already on disk
 * (F03 research R2, contracts/download-api.md). Every attempt starts from the pinned URL, because the
 * CDN URL it redirects to is signed and expires. Redirects are followed by hand and only to
 * [allowedHosts] over https. Nothing identifying the user or the device is sent.
 */
class ModelDownloader(
    private val userAgent: String,
    private val allowedHosts: HostAllowlist = HostAllowlist.HuggingFace,
    private val requireHttps: Boolean = true,
    private val connectTimeoutMs: Int = 15_000,
    private val readTimeoutMs: Int = 30_000,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    /** Only tests point this elsewhere; production always uses the pinned Hugging Face URL. */
    private val urlOf: (ModelFile) -> String = { it.downloadUrl },
) : FileDownloader {

    /** Writes [file] into [partial] (appending) and reports the bytes on disk; throws when cancelled. */
    override suspend fun download(file: ModelFile, partial: File, onProgress: (bytesOnDisk: Long) -> Unit): DownloadResult =
        withContext(ioDispatcher) {
            partial.parentFile?.mkdirs()
            when (val first = attempt(file, partial, onProgress)) {
                // The server disagreed with what is on disk: drop it and try once from zero.
                Attempt.Restart -> (attempt(file, partial.also { it.delete() }, onProgress) as? Attempt.Done)?.result
                    ?: DownloadResult.Failed(DownloadError.RangeMismatch)
                is Attempt.Done -> first.result
            }
        }

    private sealed interface Attempt {
        data object Restart : Attempt

        data class Done(val result: DownloadResult) : Attempt
    }

    private suspend fun attempt(file: ModelFile, partial: File, onProgress: (Long) -> Unit): Attempt {
        val offset = if (partial.isFile) partial.length() else 0L
        if (offset > file.sizeBytes) return Attempt.Restart
        val connection = when (val opened = open(urlOf(file), offset)) {
            is Opened.Connection -> opened.connection
            is Opened.Error -> return Attempt.Done(DownloadResult.Failed(opened.error))
        }
        return try {
            when (val code = connection.responseCode) {
                HttpURLConnection.HTTP_PARTIAL -> {
                    val range = parseContentRange(connection.getHeaderField("Content-Range"))
                    if (range == null || range.first != offset || range.second != file.sizeBytes) {
                        Attempt.Restart
                    } else {
                        Attempt.Done(transfer(connection.inputStream, partial, append = true, offset, file.sizeBytes, onProgress))
                    }
                }
                HttpURLConnection.HTTP_OK -> {
                    val length = connection.contentLengthLong
                    if (length >= 0 && length != file.sizeBytes) {
                        Attempt.Done(DownloadResult.Failed(DownloadError.RangeMismatch))
                    } else {
                        // The server ignored the range: start over from zero (FR-013).
                        Attempt.Done(transfer(connection.inputStream, partial, append = false, 0L, file.sizeBytes, onProgress))
                    }
                }
                HTTP_RANGE_NOT_SATISFIABLE ->
                    if (offset == file.sizeBytes) Attempt.Done(DownloadResult.Complete) else Attempt.Restart
                else -> Attempt.Done(DownloadResult.Failed(DownloadError.Http(code)))
            }
        } catch (e: IOException) {
            Attempt.Done(DownloadResult.Failed(DownloadError.Network(e.javaClass.simpleName)))
        } finally {
            connection.disconnect()
        }
    }

    private sealed interface Opened {
        data class Connection(val connection: HttpURLConnection) : Opened

        data class Error(val error: DownloadError) : Opened
    }

    /** Follows redirects by hand so each hop is checked against the allowlist and https. */
    private fun open(start: String, offset: Long): Opened {
        var url = try {
            URL(start)
        } catch (_: MalformedURLException) {
            return Opened.Error(DownloadError.InsecureScheme)
        }
        repeat(MAX_REDIRECTS + 1) {
            if (requireHttps && url.protocol != "https") return Opened.Error(DownloadError.InsecureScheme)
            if (!allowedHosts.allows(url.host)) return Opened.Error(DownloadError.ForbiddenHost(url.host.orEmpty()))
            val connection = try {
                (url.openConnection() as HttpURLConnection).apply {
                    instanceFollowRedirects = false
                    connectTimeout = connectTimeoutMs
                    readTimeout = readTimeoutMs
                    setRequestProperty("User-Agent", userAgent)
                    setRequestProperty("Accept-Encoding", "identity")
                    if (offset > 0) setRequestProperty("Range", "bytes=$offset-")
                }
            } catch (e: IOException) {
                return Opened.Error(DownloadError.Network(e.javaClass.simpleName))
            }
            val code = try {
                connection.responseCode
            } catch (e: IOException) {
                connection.disconnect()
                return Opened.Error(DownloadError.Network(e.javaClass.simpleName))
            }
            if (code !in REDIRECT_CODES) return Opened.Connection(connection)
            val location = connection.getHeaderField("Location")
            connection.disconnect()
            url = try {
                URL(url, location ?: return Opened.Error(DownloadError.Http(code)))
            } catch (_: MalformedURLException) {
                return Opened.Error(DownloadError.Http(code))
            }
        }
        return Opened.Error(DownloadError.TooManyRedirects)
    }

    /**
     * Copies the body to disk. A blocking read cannot see coroutine cancellation, so a watcher closes
     * the stream as soon as the caller is cancelled; what was written is synced and kept.
     */
    private suspend fun transfer(
        input: InputStream,
        partial: File,
        append: Boolean,
        start: Long,
        total: Long,
        onProgress: (Long) -> Unit,
    ): DownloadResult = coroutineScope {
        val watcher = launch {
            try {
                awaitCancellation()
            } finally {
                runCatching { input.close() }
            }
        }
        try {
            FileOutputStream(partial, append).use { output ->
                var written = start
                var unsynced = 0L
                val buffer = ByteArray(BUFFER_BYTES)
                try {
                    while (true) {
                        coroutineContext.ensureActive()
                        val read = try {
                            input.read(buffer)
                        } catch (e: IOException) {
                            coroutineContext.ensureActive()
                            return@coroutineScope DownloadResult.Failed(DownloadError.Network(e.javaClass.simpleName))
                        }
                        if (read < 0) break
                        if (written + read > total) return@coroutineScope DownloadResult.Failed(DownloadError.RangeMismatch)
                        try {
                            output.write(buffer, 0, read)
                        } catch (e: IOException) {
                            return@coroutineScope DownloadResult.Failed(DownloadError.Io(e.javaClass.simpleName))
                        }
                        written += read
                        unsynced += read
                        if (unsynced >= SYNC_BYTES) {
                            output.fd.sync()
                            unsynced = 0
                        }
                        onProgress(written)
                    }
                } finally {
                    runCatching { output.flush(); output.fd.sync() }
                }
                if (written == total) DownloadResult.Complete else DownloadResult.Failed(DownloadError.Network("short read"))
            }
        } finally {
            watcher.cancel()
        }
    }

    private fun parseContentRange(header: String?): Pair<Long, Long>? {
        val match = header?.let { CONTENT_RANGE.find(it.trim()) } ?: return null
        return match.groupValues[1].toLong() to match.groupValues[3].toLong()
    }

    private companion object {
        const val MAX_REDIRECTS = 5
        const val HTTP_RANGE_NOT_SATISFIABLE = 416
        const val BUFFER_BYTES = 256 * 1024
        const val SYNC_BYTES = 64L * 1024 * 1024
        val REDIRECT_CODES = setOf(301, 302, 303, 307, 308)
        val CONTENT_RANGE = Regex("""bytes (\d+)-(\d+)/(\d+)""")
    }
}
