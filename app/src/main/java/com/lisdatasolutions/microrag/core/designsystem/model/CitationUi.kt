package com.lisdatasolutions.microrag.core.designsystem.model

/**
 * A source shown under an answer. `page` and `fragment` are nullable: absence is never
 * represented with zero or placeholder strings. When [available] is false the source was deleted
 * and the row shows "Fuente ya no disponible" without navigating.
 */
data class CitationUi(
    val documentName: String,
    val page: Int?,
    val fragment: Int?,
    val available: Boolean = true,
    /** The last fragment of a block that spans several («Fragmentos 86–87», F11 C11); null for one fragment. */
    val lastFragment: Int? = null,
)
