package com.lisdatasolutions.microrag.core.logging

import android.util.Log

/**
 * App-wide logger. Messages are lambdas so nothing is built when logging is disabled.
 * Never log private content: prompts, document text, answers or user file names (constitution I).
 */
interface AppLogger {
    fun d(tag: String, message: () -> String)
    fun i(tag: String, message: () -> String)
    fun w(tag: String, throwable: Throwable? = null, message: () -> String)
    fun e(tag: String, throwable: Throwable? = null, message: () -> String)
}

/** Destination of log lines; [AndroidLogSink] in the app, a fake in tests. */
fun interface LogSink {
    fun write(priority: Int, tag: String, message: String, throwable: Throwable?)
}

object AndroidLogSink : LogSink {
    override fun write(priority: Int, tag: String, message: String, throwable: Throwable?) {
        val text = if (throwable != null) "$message\n${Log.getStackTraceString(throwable)}" else message
        Log.println(priority, tag, text)
    }
}

/** Emits only when [isEnabled] (wired to `BuildConfig.DEBUG`, so release builds never log). */
class DefaultAppLogger(
    private val isEnabled: Boolean,
    private val sink: LogSink = AndroidLogSink,
) : AppLogger {
    override fun d(tag: String, message: () -> String) = log(Log.DEBUG, tag, null, message)
    override fun i(tag: String, message: () -> String) = log(Log.INFO, tag, null, message)
    override fun w(tag: String, throwable: Throwable?, message: () -> String) = log(Log.WARN, tag, throwable, message)
    override fun e(tag: String, throwable: Throwable?, message: () -> String) = log(Log.ERROR, tag, throwable, message)

    private inline fun log(priority: Int, tag: String, throwable: Throwable?, message: () -> String) {
        if (isEnabled) sink.write(priority, tag, message(), throwable)
    }
}
