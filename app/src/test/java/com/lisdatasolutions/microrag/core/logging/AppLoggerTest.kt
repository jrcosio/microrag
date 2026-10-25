package com.lisdatasolutions.microrag.core.logging

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AppLoggerTest {

    private val written = mutableListOf<String>()
    private val sink = LogSink { _, tag, message, _ -> written += "$tag: $message" }

    @Test
    fun disabledLogger_neverEvaluatesMessages() {
        var evaluated = false
        val logger = DefaultAppLogger(isEnabled = false, sink = sink)

        logger.d(TAG) { evaluated = true; "secret" }
        logger.e(TAG) { evaluated = true; "secret" }

        assertThat(evaluated).isFalse()
        assertThat(written).isEmpty()
    }

    @Test
    fun enabledLogger_writesToSink() {
        val logger = DefaultAppLogger(isEnabled = true, sink = sink)

        logger.i(TAG) { "started" }

        assertThat(written).containsExactly("$TAG: started")
    }

    private companion object {
        const val TAG = "AppLoggerTest"
    }
}
