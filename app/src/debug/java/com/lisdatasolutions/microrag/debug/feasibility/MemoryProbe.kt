package com.lisdatasolutions.microrag.debug.feasibility

import java.io.File

/** Peak resident memory of this process, which includes native engine allocations (research R5). */
interface MemoryProbe {
    /** Resets the peak so the next reading covers only the following scenario; false if unsupported. */
    fun resetPeak(): Boolean

    fun peakRssMb(): Long?
}

class ProcMemoryProbe : MemoryProbe {

    override fun resetPeak(): Boolean = runCatching {
        // "5" resets VmHWM to the current RSS (proc(5), clear_refs).
        File("/proc/self/clear_refs").writeText("5")
        true
    }.getOrDefault(false)

    override fun peakRssMb(): Long? = runCatching {
        File("/proc/self/status").useLines { lines ->
            lines.firstOrNull { it.startsWith("VmHWM:") }
                ?.split(Regex("\\s+"))
                ?.getOrNull(1)
                ?.toLong()
                ?.div(KB_PER_MB)
        }
    }.getOrNull()

    private companion object {
        const val KB_PER_MB = 1024L
    }
}
