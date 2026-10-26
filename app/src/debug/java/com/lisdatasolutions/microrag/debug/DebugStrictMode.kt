package com.lisdatasolutions.microrag.debug

import android.os.StrictMode

/** Debug builds log every disk read or write on the main thread (constitution IV, F04 FR-005). */
object DebugStrictMode {
    fun install() {
        StrictMode.setThreadPolicy(
            StrictMode.ThreadPolicy.Builder()
                .detectDiskReads()
                .detectDiskWrites()
                .penaltyLog()
                .build(),
        )
    }
}
