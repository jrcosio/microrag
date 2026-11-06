package com.lisdatasolutions.microrag.domain.rag

import com.lisdatasolutions.microrag.domain.model.ModelId

/**
 * Retrieval and context parameters fixed by the offline evaluation (F05, D-034 to D-036; F10, D-050; F11, D-054 and D-056).
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

    /** Fused candidates handed to the context selector, as the evaluation harness does (F10 R2). */
    const val FUSED_LIMIT = 30

    const val MAX_BLOCKS = 4
    const val MAX_PER_DOCUMENT = 3
    const val BUDGET_TOKENS = 1719
    const val EXPAND_TOP = 0

    const val PROMPT_LIMIT_TOKENS = 2000
    /** «informe» plus the rules of complete answers, exact figures and follow-ups (F11 review, D-056). */
    const val PROMPT_TEMPLATE = "v4"
    val LLM_MODEL = ModelId.GEMMA4_E2B

    /** Context of the Gemma engine, prompt and answer together, as the desktop generates (D-054). */
    const val LLM_MAX_NUM_TOKENS = 4096
    const val MAX_OUTPUT_TOKENS = 512

    /** F20 (D-078, research R2): pages the page search returns; only the first enters, the rest are for the diagnosis. */
    const val PAGE_CANDIDATES = 5
    const val ANSWER_PAGES = 1

    /** The v4 rules plus one for page images (F18 research R11), used only when a page enters. */
    const val PROMPT_TEMPLATE_VISUAL = "v5-visual"

    /** Gemma with a page: its window, and images per prompt (D-078; F18 found 4,096 too small with a page). */
    const val LLM_VISUAL_MAX_NUM_TOKENS = 8192
    const val LLM_MAX_NUM_IMAGES = 1

    /**
     * F14 (research R3, data-model §2): the INT8 copy preselects this many candidates, which the exact distance
     * reorders; it is used from [INT8_MIN_CHUNKS] searchable chunks on (constitution IV: only past 20 ms). The bench
     * of the phone phase fixes both; 10,000 is where F04 already measured 23.1 ms.
     */
    const val INT8_CANDIDATES = 100
    const val INT8_MIN_CHUNKS = 10_000

    /** SQLite-Vector's buffer while quantizing: 100k chunks need 26.4 MB. */
    const val INT8_MAX_MEMORY_MB = 64

    /** Changes closer than this make one rebuild (contract I4). */
    const val QUANT_REBUILD_DEBOUNCE_MS = 2_000L

    /**
     * F14 levers (research R6–R8, D-088, D-090), today's behaviour until the phone phase decides them with their
     * rules: reuse the rules' prefill, multi-token prediction, and Gemma's activations (null: the file's, fp16).
     */
    const val PREFIX_CACHE = false
    const val SPECULATIVE_DECODING = false
    val ACTIVATION_PRECISION: String? = null

    /** F14 (research R10, contract G7): load Gemma with or without vision on a change of scope, before the question. */
    const val PREWARM_BY_SCOPE = false

    /** F14 (research R10, contract G8): how a follow-up searches the pages; `visual-followup-review` decides it. */
    val PAGE_FOLLOW_UP = PageFollowUp.JOINED
}

/**
 * F14 (research R10): the vector a follow-up searches the pages with. [JOINED] is F20's, the previous question with the
 * new one; [NEW_ONLY] is the new question alone; [BEST_OF_BOTH] searches with both and keeps each page's closest.
 */
enum class PageFollowUp { JOINED, NEW_ONLY, BEST_OF_BOTH }
