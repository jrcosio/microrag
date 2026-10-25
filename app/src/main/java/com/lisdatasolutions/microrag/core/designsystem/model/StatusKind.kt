package com.lisdatasolutions.microrag.core.designsystem.model

/**
 * Semantic status shown by chips and rows. Each kind maps to a container, a text color and an
 * icon or dot, so status is never conveyed by color alone (FR-013).
 */
enum class StatusKind {
    /** Local / offline / active processing ("Local", "Offline", "Activo"). */
    Local,

    /** Ready / indexed ("IA local disponible", "Indexado"). */
    Ready,

    /** Work in progress ("En curso", "Descargando"). */
    Processing,

    /** Waiting in a queue ("En cola", "Listo para procesar"). */
    Queued,

    /** Paused by the user or the system. */
    Paused,

    /** Needs configuration ("Por configurar"). */
    Warning,

    /** Failed ("Error de indexación", "Incompatible"). */
    Error,

    /** Neutral information. */
    Info,
}
