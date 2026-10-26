package com.lisdatasolutions.microrag.feature.ingest

import com.lisdatasolutions.microrag.domain.ingest.ImportQueue
import com.lisdatasolutions.microrag.domain.ingest.QueueItem
import com.lisdatasolutions.microrag.domain.ingest.QueueKey

/** P05 state (F06 contracts/ingest-ui.md). */
data class IngestUiState(
    val loading: Boolean = true,
    val queue: ImportQueue = ImportQueue.EMPTY,
    /** Row whose detail sheet is open (G15). */
    val selected: QueueKey? = null,
    /** Document whose removal waits for confirmation (constitution V). */
    val confirmingRemoval: QueueKey? = null,
    /** «Iniciar indexación» was pressed and the work is being enqueued. */
    val starting: Boolean = false,
) {
    val items: List<QueueItem> get() = queue.items
    val selectedItem: QueueItem? get() = items.firstOrNull { it.key == selected }
    val removalItem: QueueItem? get() = items.firstOrNull { it.key == confirmingRemoval }
    val pickEnabled: Boolean get() = !loading && queue.indexAvailable
    val startEnabled: Boolean get() = !loading && !starting && queue.indexAvailable && queue.readyCount > 0
}

/** One-off requests that need the navigation. */
sealed interface IngestEvent {
    data object OpenIndex : IngestEvent
}

/** Types offered by the system picker (FR-001, research R4). */
object IngestMimeTypes {
    val ALL: Array<String> = arrayOf("application/pdf", "text/plain", "text/markdown", "text/x-markdown")
}
