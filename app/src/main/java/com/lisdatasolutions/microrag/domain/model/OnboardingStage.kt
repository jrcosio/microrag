package com.lisdatasolutions.microrag.domain.model

/** Persisted progress of the first-run flow (F03 FR-004). It never goes back from [COMPLETED]. */
enum class OnboardingStage { NOT_STARTED, STARTED, COMPLETED }

/** Where the app opens on launch (F03 research R10). */
enum class StartDestination {
    WELCOME,

    /** Setup with Welcome below it, so Back from setup returns to Welcome (FR-003). */
    SETUP_OVER_WELCOME,
    HOME,
}
