package com.lisdatasolutions.microrag.domain.onboarding

/** Fixed rules of the model setup (F03 spec, research R5). */
object ModelSetupPolicy {
    /** Free space kept beyond the remaining bytes, so documents can be imported afterwards (FR-016). */
    const val SPACE_MARGIN_BYTES = 500L * 1024 * 1024
}
