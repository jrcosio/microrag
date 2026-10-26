package com.lisdatasolutions.microrag.domain.rag

import com.lisdatasolutions.microrag.domain.model.ModelId

/**
 * Retrieval and context parameters fixed by the offline evaluation (F05, D-034 to D-036).
 *
 * Mirror of `offline/config/rag_parameters.json`; `RagParametersTest` keeps both equal field by field.
 * Chunking is used by F06/F07, retrieval by F10 and the prompt by F11.
 */
object RagParameters {
    const val DIMS = 256
    const val CONTEXTUAL_HEADER = true
    const val NORMALIZER_VERSION = 1

    const val CHUNK_TARGET_CHARS = 1594
    const val CHUNK_MAX_CHARS = 2113

    /** Characters per Gemma token measured over the evaluation corpus; tokens ≈ ceil(chars / this). */
    const val CHARS_PER_TOKEN = 4.554

    /** When the embedding model rejects an input, the embedded text is cut to this share, at a space (D-036). */
    const val EMBEDDING_OVERFLOW_CUT = 0.9

    const val CANDIDATES = 50
    const val RRF_K = 60
    const val FOLLOW_UP_RULE = true

    const val MAX_BLOCKS = 4
    const val MAX_PER_DOCUMENT = 3
    const val BUDGET_TOKENS = 1719
    const val EXPAND_TOP = 0

    const val PROMPT_LIMIT_TOKENS = 2000
    const val PROMPT_TEMPLATE = "informe"
    val LLM_MODEL = ModelId.GEMMA4_E2B
    const val MAX_OUTPUT_TOKENS = 512
}
