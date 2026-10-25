package com.lisdatasolutions.microrag.domain.model

enum class ModelId { GEMMA4_E2B, GEMMA4_E2B_GPU, EG2_740M, EG2_440M, EG2_270M }

enum class ModelFamily { LLM, EMBEDDING }

/**
 * A model file the app expects, pinned to a repository [revision] and its published [sha256]
 * (constitution I: a model is never loaded unless its hash matches).
 */
data class ModelFile(
    val id: ModelId,
    val family: ModelFamily,
    val displayName: String,
    val fileName: String,
    val sizeBytes: Long,
    val sha256: String,
    val repository: String,
    val revision: String,
)
