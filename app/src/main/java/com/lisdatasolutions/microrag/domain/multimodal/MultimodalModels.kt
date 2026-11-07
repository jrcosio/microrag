package com.lisdatasolutions.microrag.domain.multimodal

import com.lisdatasolutions.microrag.domain.model.ModelId
import com.lisdatasolutions.microrag.domain.model.DownloadProgress
import com.lisdatasolutions.microrag.domain.rag.IndexMode
import kotlinx.coroutines.flow.Flow

/** The multimodal mode's activation (F19 research R1, D-078): persisted, so it survives a closed app. */
enum class MultimodalStage {
    OFF,
    DOWNLOADING,
    VALIDATING,
    SWITCHING,
    ON,
    ;

    /** While the 440M is being fetched, checked or switched to (research R5). */
    val activating: Boolean get() = this == DOWNLOADING || this == VALIDATING || this == SWITCHING
}

/** Why the last activation stopped (analyze M1): download errors keep DOWNLOADING; the others go back to OFF. */
enum class MultimodalError {
    NO_SPACE,
    DOWNLOAD_FAILED,
    IDENTITY_MISMATCH,
    PREPARE_FAILED,
}

/**
 * How much of the page Gemma reads in an answer (F20 research R12, D-078): its visual tokens, and the first-token
 * wait measured on the reference phone with 1 page (p95, to the half second), in tenths of a second, for Settings
 * and the chat. F20's figures; Equilibrado's from F14's 25-minute session (research R10: 9.1 s over 71 answers).
 */
enum class PageDetail(val visualTokens: Int, val estimateTenthsOfSecond: Int) {
    FAST(70, 65),
    BALANCED(140, 90),
    DETAILED(280, 145),
}

/**
 * What `multimodal.preferences_pb` holds (data-model §3). [textEmbedder] mirrors `rag_meta` before the index opens
 * (analyze H3); [pageDetail] is F20's (D-083).
 */
data class StoredMultimodal(
    val stage: MultimodalStage = MultimodalStage.OFF,
    val error: MultimodalError? = null,
    val defaultMode: IndexMode = IndexMode.BOTH,
    val textEmbedder: ModelId = ModelId.EG2_270M,
    val pageDetail: PageDetail = PageDetail.BALANCED,
)

/** The persisted mode (contract M1); a corrupt file reads as the defaults. */
interface MultimodalStore {
    val state: Flow<StoredMultimodal>

    /** Applies [transform] and returns false if the file could not be written (the previous value stays). */
    suspend fun write(transform: (StoredMultimodal) -> StoredMultimodal): Boolean
}

/** What Settings shows (contracts/multimodal-ui.md §1). */
data class MultimodalState(
    val stage: MultimodalStage,
    val error: MultimodalError?,
    val defaultMode: IndexMode,
    /** F20 (contract U1): the detail Ajustes shows under «Modo al importar PDF». */
    val pageDetail: PageDetail = PageDetail.BALANCED,
    /** The 440M's download while [stage] is DOWNLOADING; null otherwise or before it starts. */
    val download: DownloadProgress? = null,
    val downloadPaused: Boolean = false,
    /** The 440M's size, formatted the same way as the model rows (analyze M7). */
    val downloadBytes: Long,
    /** Downloading without a network: it goes on by itself once online (wireframe §2). */
    val offline: Boolean = false,
    /** With [MultimodalError.NO_SPACE]: what is left to free for the rest of the download and the margin. */
    val missingBytes: Long? = null,
)

/** The on-device check of research R3: [passed] when [minCosine] reaches the constitution's 0.9999. */
data class IdentityCheck(val texts: Int, val minCosine: Double, val passed: Boolean)
