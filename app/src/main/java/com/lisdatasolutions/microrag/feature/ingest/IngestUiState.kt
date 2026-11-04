package com.lisdatasolutions.microrag.feature.ingest

import com.lisdatasolutions.microrag.domain.ingest.ImportQueue
import com.lisdatasolutions.microrag.domain.ingest.QueueItem
import com.lisdatasolutions.microrag.domain.ingest.QueueKey
import com.lisdatasolutions.microrag.domain.rag.FolderList

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
    /** F17 K6: the folders, null while they load; [destination] null is «General». */
    val folders: FolderList? = null,
    val destination: Long? = null,
    /** The open destination sheet and the folder chosen in it. */
    val choosing: DestinationChoice? = null,
    /** The destination was deleted: back to «General», said once (M3). */
    val destinationMissing: Boolean = false,
) {
    /** The destination's name; null is «General». */
    val destinationName: String? get() = destination?.let { id -> folders?.folders?.firstOrNull { it.id == id }?.name }

    val items: List<QueueItem> get() = queue.items
    val selectedItem: QueueItem? get() = items.firstOrNull { it.key == selected }
    val removalItem: QueueItem? get() = items.firstOrNull { it.key == confirmingRemoval }
    val pickEnabled: Boolean get() = !loading && queue.indexAvailable
    val startEnabled: Boolean get() = !loading && !starting && queue.indexAvailable && queue.canStart
}

data class DestinationChoice(val selected: Long?)

/** One-off requests that need the navigation. */
sealed interface IngestEvent {
    data object OpenIndex : IngestEvent
}

/** Types offered by the system picker (FR-001, research R4). */
object IngestMimeTypes {
    val ALL: Array<String> = arrayOf("application/pdf", "text/plain", "text/markdown", "text/x-markdown")
}
