package com.lisdatasolutions.microrag.data.models

import com.google.common.truth.Truth.assertThat
import java.io.ByteArrayInputStream
import java.security.MessageDigest
import kotlin.random.Random
import org.junit.Test

class Sha256Test {

    @Test
    fun of_matchesKnownVectors() {
        assertThat(Sha256.of(ByteArrayInputStream(ByteArray(0))))
            .isEqualTo("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855")
        assertThat(Sha256.of(ByteArrayInputStream("abc".toByteArray())))
            .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad")
    }

    @Test
    fun of_streamsLargeInputAndReportsMonotonicProgress() {
        val bytes = Random(42).nextBytes(3 * 1024 * 1024 + 17)
        val expected = MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it) }
        val progress = mutableListOf<Long>()

        val actual = Sha256.of(ByteArrayInputStream(bytes)) { progress += it }

        assertThat(actual).isEqualTo(expected)
        assertThat(progress).isInOrder()
        assertThat(progress.last()).isEqualTo(bytes.size.toLong())
    }
}
