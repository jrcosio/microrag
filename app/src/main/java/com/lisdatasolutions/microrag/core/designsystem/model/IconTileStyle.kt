package com.lisdatasolutions.microrag.core.designsystem.model

/** Visual style of the rounded square behind an icon. */
enum class IconTileStyle {
    /** Light blue tile with a brand blue icon (cards, rows). */
    Light,

    /** Translucent tile on dark hero backgrounds. */
    OnDark,

    /** Success tile (completed stages). */
    Success,

    /** Neutral tile (pending stages). */
    Neutral,

    /** Error tile (failed stages). */
    Error,

    /** Brand gradient tile with a white icon (assistant avatar, sparkle). */
    Accent,
}
