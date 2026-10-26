package com.lisdatasolutions.microrag.data.rag

import java.nio.ByteBuffer
import java.nio.ByteOrder

/** FLOAT32 vectors as SQLite-Vector stores them: little-endian, 4 bytes per component. */
fun FloatArray.toLeBlob(): ByteArray {
    val buffer = ByteBuffer.allocate(size * Float.SIZE_BYTES).order(ByteOrder.LITTLE_ENDIAN)
    buffer.asFloatBuffer().put(this)
    return buffer.array()
}

fun ByteArray.toFloatArrayLe(): FloatArray {
    require(size % Float.SIZE_BYTES == 0) { "Vector blob of $size bytes is not FLOAT32" }
    val floats = ByteBuffer.wrap(this).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer()
    return FloatArray(floats.remaining()).also { floats.get(it) }
}
