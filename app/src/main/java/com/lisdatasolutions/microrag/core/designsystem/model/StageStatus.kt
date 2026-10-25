package com.lisdatasolutions.microrag.core.designsystem.model

/** Status of a processing stage row (extraction, chunking, embeddings, persistence). */
enum class StageStatus { Done, InProgress, Pending, Failed }
