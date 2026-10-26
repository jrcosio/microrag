package com.lisdatasolutions.microrag.data.rag

import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test

/** SQLite-Vector reads FLOAT32 vectors as little-endian blobs (F04 research R3). */
class VectorBlobsTest {

    @Test
    fun roundTrip_isLossless() {
        val vector = floatArrayOf(0.1f, -2.5f, Float.MIN_VALUE, 1e-30f, 3.4e38f)

        assertThat(vector.toLeBlob().toFloatArrayLe()).isEqualTo(vector)
    }

    @Test
    fun blob_isLittleEndian() {
        assertThat(floatArrayOf(1.0f).toLeBlob()).isEqualTo(byteArrayOf(0x00, 0x00, 0x80.toByte(), 0x3F))
    }

    @Test
    fun blob_has4BytesPerComponent() {
        assertThat(FloatArray(256).toLeBlob()).hasLength(1024)
    }

    @Test
    fun blobNotMultipleOf4_isRejected() {
        assertThrows(IllegalArgumentException::class.java) { ByteArray(1023).toFloatArrayLe() }
    }
}
