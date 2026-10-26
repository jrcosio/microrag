package com.lisdatasolutions.microrag.data.download

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class HostAllowlistTest {

    private val hf = HostAllowlist.HuggingFace

    @Test
    fun huggingFaceAndItsCdnAreAllowed() {
        listOf("huggingface.co", "us.aws.cdn.hf.co", "cas-bridge.xethub.hf.co", "cdn-lfs.huggingface.co", "HF.CO", "huggingface.co.")
            .forEach { assertThat(hf.allows(it)).isTrue() }
    }

    @Test
    fun lookalikesAndOtherHostsAreRejected() {
        listOf("hf.co.evil.com", "evilhf.co", "huggingface.co.evil", "notHuggingface.co", "example.com", "", null)
            .forEach { assertThat(hf.allows(it)).isFalse() }
    }
}
