package com.lisdatasolutions.microrag.data.indexing

import kotlinx.coroutines.flow.Flow

/** Why a run stays paused until the user resumes it (F07 data-model §2). Heat and system limits are not stored. */
enum class RunPauseCause(val code: String) {
    USER("user"),
    NO_SPACE("no_space"),
    MODEL_MISSING("model_missing"),
    ;

    companion object {
        fun fromCode(code: String?): RunPauseCause? = entries.firstOrNull { it.code == code }
    }
}

/** The documents the user sent to index that have not finished yet, in order, and the run's pause (F07 research R8). */
data class IngestRun(val documentIds: List<Long> = emptyList(), val pauseCause: RunPauseCause? = null) {
    val paused: Boolean get() = pauseCause != null

    fun adding(ids: Collection<Long>): IngestRun = copy(documentIds = (documentIds + ids).distinct())

    fun without(ids: Collection<Long>): IngestRun = copy(documentIds = documentIds - ids.toSet())

    fun pausedBy(cause: RunPauseCause): IngestRun = copy(pauseCause = cause)

    fun resumed(): IngestRun = copy(pauseCause = null)
}

/** The run, persisted in no-backup storage. Every change is one atomic edit. */
interface IngestRunStore {
    val run: Flow<IngestRun>

    /** Applies [transform] to the stored run in a single edit and returns the result. */
    suspend fun edit(transform: (IngestRun) -> IngestRun): IngestRun

    /** Result of the last GPU check (F07 research R4): `<model sha256>|<versionCode>|<gpu or cpu>`. */
    suspend fun gpuCheck(): String?

    suspend fun setGpuCheck(value: String)
}
