package com.lisdatasolutions.microrag.data.models

import java.io.InputStream
import java.security.MessageDigest

object Sha256 {
    private const val BUFFER_SIZE = 1024 * 1024

    /** Streams [input] (without closing it) and returns its lowercase hex SHA-256. */
    fun of(input: InputStream, onBytes: (Long) -> Unit = {}): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(BUFFER_SIZE)
        var total = 0L
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            digest.update(buffer, 0, read)
            total += read
            onBytes(total)
        }
        if (total == 0L) onBytes(0L)
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
