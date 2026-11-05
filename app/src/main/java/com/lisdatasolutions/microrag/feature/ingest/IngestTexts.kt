package com.lisdatasolutions.microrag.feature.ingest

import android.text.format.Formatter
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.lisdatasolutions.microrag.R
import com.lisdatasolutions.microrag.core.designsystem.model.ProgressValue
import com.lisdatasolutions.microrag.domain.ingest.DocumentFormat
import com.lisdatasolutions.microrag.domain.ingest.IngestError
import com.lisdatasolutions.microrag.domain.ingest.QueueItem
import com.lisdatasolutions.microrag.domain.ingest.QueueItemState
import com.lisdatasolutions.microrag.domain.rag.IndexMode
import com.lisdatasolutions.microrag.feature.indexing.durationText
import com.lisdatasolutions.microrag.feature.indexing.remainingText

// Queue state → texts of contracts/ingest-ui.md. Pure mapping: no state.

@Composable
fun sizeText(bytes: Long?): String? = bytes?.let { Formatter.formatShortFileSize(LocalContext.current, it) }

/**
 * F19 (wireframe §8): before its pages are indexed, a PDF or image in Visual or Both says what they will take,
 * «40 páginas · unos 2 min · ≈ 10 MB» (3.2 s and 250 KB a page, research R14).
 */
@Composable
fun QueueItem.pagesEstimate(): String? {
    val pages = pageCount?.takeIf { it > 0 && indexMode.hasPages } ?: return null
    if (state !is QueueItemState.Ready && state !is QueueItemState.Queued && state !is QueueItemState.Prepared) return null
    val seconds = (pages * PAGE_SECONDS).toLong()
    // «menos de 1 min» needs no «unos» in front.
    val time = if (seconds < 60) {
        stringResource(R.string.index_duration_less_than_a_minute)
    } else {
        stringResource(R.string.ingest_pages_estimate_time, durationText(seconds))
    }
    val size = sizeText(pages * PAGE_BYTES).orEmpty()
    return pluralStringResource(R.plurals.ingest_pages_estimate, pages, pages.grouped(), time, size)
}

/** Research R14: the figures F18 measured on the reference phone. */
private const val PAGE_SECONDS = 3.2
private const val PAGE_BYTES = 250L * 1024

/** U10: the row's chip says its mode while it has no error; Text rows keep the chip they had. */
@Composable
fun QueueItem.modeChip(): String? = indexMode.takeIf { it != IndexMode.TEXT && state.error == null }?.let { stringResource(it.label()) }

@StringRes
fun IndexMode.label(): Int = when (this) {
    IndexMode.TEXT -> R.string.document_mode_text
    IndexMode.VISUAL -> R.string.document_mode_visual
    IndexMode.BOTH -> R.string.document_mode_both
}

/**
 * «2,4 MB · Listo para procesar · Legal»: every row says its folder, «General» too (F17 K6); an error row
 * shows only the size and the folder, its reason goes to the chip.
 */
@Composable
fun QueueItem.metaText(): String {
    val size = sizeText(sizeBytes)
    val state = state.stateText()
    val base = when {
        state == null -> size.orEmpty()
        size == null -> state
        else -> stringResource(R.string.ingest_meta, size, state)
    }
    val folder = folderName ?: stringResource(R.string.library_folder_general)
    val meta = if (base.isEmpty()) folder else stringResource(R.string.ingest_meta, base, folder)
    return pagesEstimate()?.let { "$meta\n$it" } ?: meta
}

/** State as the row and the sheet show it; null for errors, whose text is [IngestError.chipText]. */
@Composable
fun QueueItemState.stateText(): String? = when (this) {
    is QueueItemState.Importing -> stringResource(R.string.ingest_state_importing)
    QueueItemState.Ready -> stringResource(R.string.ingest_state_ready)
    QueueItemState.Queued -> stringResource(R.string.ingest_state_queued)
    is QueueItemState.Extracting -> if (page != null && pages != null) {
        stringResource(R.string.ingest_state_extracting_page, page, pages)
    } else {
        stringResource(R.string.ingest_state_extracting)
    }
    QueueItemState.Chunking -> stringResource(R.string.ingest_state_chunking)
    is QueueItemState.Prepared -> {
        val prepared = pluralStringResource(R.plurals.ingest_state_prepared, chunks, chunks)
        val skipped = skippedPages?.takeIf { it > 0 }
        if (skipped == null) prepared else "$prepared · " + pluralStringResource(R.plurals.ingest_skipped_pages, skipped, skipped)
    }
    is QueueItemState.Indexing -> stringResource(R.string.ingest_state_indexing, done.grouped(), total.grouped())
    // F19 (wireframe §5): «Indexando páginas · 12 / 40 · ≈ 1 min restantes» once the rate is known.
    is QueueItemState.IndexingPages -> {
        val text = stringResource(R.string.ingest_pages_progress, done.grouped(), total.grouped())
        val left = perMinute?.takeIf { it > 0 && total > done }?.let { ((total - done) * 60 / it).toLong() }
        if (left == null) text else "$text · " + remainingText(left)
    }
    is QueueItemState.PagesNoSpace -> stringResource(R.string.ingest_pages_no_space, sizeText(missingBytes).orEmpty())
    is QueueItemState.Paused -> if (done != null && total != null) {
        stringResource(R.string.ingest_state_paused, done.grouped(), total.grouped())
    } else {
        stringResource(R.string.ingest_state_paused_unknown)
    }
    is QueueItemState.ImportFailed, is QueueItemState.Failed -> null
}

/** «1.250» in Spanish, «1,250» in English. */
private fun Int.grouped(): String = java.text.NumberFormat.getIntegerInstance().format(this)

val QueueItemState.error: IngestError?
    get() = when (this) {
        is QueueItemState.ImportFailed -> error
        is QueueItemState.Failed -> error
        else -> null
    }

/** Real progress only: bytes or pages when the total is known, indeterminate otherwise (constitution V). */
fun QueueItemState.progress(): ProgressValue? = when (this) {
    is QueueItemState.Importing ->
        if (totalBytes != null && totalBytes > 0) {
            ProgressValue.Determinate((bytesCopied.toFloat() / totalBytes).coerceIn(0f, 1f))
        } else {
            ProgressValue.Indeterminate
        }
    is QueueItemState.Extracting ->
        if (page != null && pages != null && pages > 0) {
            ProgressValue.Determinate((page.toFloat() / pages).coerceIn(0f, 1f))
        } else {
            ProgressValue.Indeterminate
        }
    QueueItemState.Chunking -> ProgressValue.Indeterminate
    is QueueItemState.Indexing ->
        if (total > 0) ProgressValue.Determinate((done.toFloat() / total).coerceIn(0f, 1f)) else ProgressValue.Indeterminate
    is QueueItemState.IndexingPages ->
        if (total > 0) ProgressValue.Determinate((done.toFloat() / total).coerceIn(0f, 1f)) else ProgressValue.Indeterminate
    else -> null
}

@Composable
fun IngestError.chipText(): String = stringResource(chipRes())

@StringRes
private fun IngestError.chipRes(): Int = when (this) {
    IngestError.UNSUPPORTED_FORMAT -> R.string.ingest_error_unsupported_format
    IngestError.DAMAGED -> R.string.ingest_error_damaged
    IngestError.ENCRYPTED -> R.string.ingest_error_encrypted
    IngestError.TOO_LARGE -> R.string.ingest_error_too_large
    IngestError.NO_SPACE -> R.string.ingest_error_no_space
    IngestError.ACCESS_LOST -> R.string.ingest_error_access_lost
    IngestError.READ_FAILED -> R.string.ingest_error_read_failed
    IngestError.DUPLICATE -> R.string.ingest_error_duplicate
    IngestError.PDF_UNAVAILABLE -> R.string.ingest_error_pdf_unavailable
    IngestError.NO_TEXT -> R.string.ingest_error_no_text
    IngestError.EXTRACTION_FAILED -> R.string.ingest_error_extraction_failed
    IngestError.INDEX_UNAVAILABLE -> R.string.ingest_error_index_unavailable
    IngestError.EMBEDDING_FAILED -> R.string.ingest_error_embedding_failed
    IngestError.INCOMPLETE -> R.string.ingest_error_incomplete
    IngestError.IMAGE_NEEDS_MULTIMODAL -> R.string.ingest_error_image_needs_multimodal
    IngestError.IMAGE_UNREADABLE -> R.string.ingest_error_image_unreadable
}

/** Sentence of the detail sheet (G15). */
@Composable
fun IngestError.detailText(format: DocumentFormat?): String = when (this) {
    IngestError.UNSUPPORTED_FORMAT -> stringResource(R.string.ingest_error_unsupported_format_detail)
    IngestError.DAMAGED -> format?.let { stringResource(R.string.ingest_error_damaged_detail, it.label()) }
        ?: stringResource(R.string.ingest_error_damaged_detail_generic)
    IngestError.ENCRYPTED -> stringResource(R.string.ingest_error_encrypted_detail)
    IngestError.TOO_LARGE -> stringResource(R.string.ingest_error_too_large_detail)
    IngestError.NO_SPACE -> stringResource(R.string.ingest_error_no_space_detail)
    IngestError.ACCESS_LOST -> stringResource(R.string.ingest_error_access_lost_detail)
    IngestError.READ_FAILED -> stringResource(R.string.ingest_error_read_failed_detail)
    IngestError.DUPLICATE -> stringResource(R.string.ingest_error_duplicate_detail)
    IngestError.PDF_UNAVAILABLE -> stringResource(R.string.ingest_error_pdf_unavailable_detail)
    IngestError.NO_TEXT -> stringResource(R.string.ingest_error_no_text_detail)
    IngestError.EXTRACTION_FAILED -> stringResource(R.string.ingest_error_extraction_failed_detail)
    IngestError.INDEX_UNAVAILABLE -> stringResource(R.string.ingest_error_index_unavailable_detail)
    IngestError.EMBEDDING_FAILED -> stringResource(R.string.ingest_error_embedding_failed_detail)
    IngestError.INCOMPLETE -> stringResource(R.string.ingest_error_incomplete_detail)
    IngestError.IMAGE_NEEDS_MULTIMODAL -> stringResource(R.string.ingest_error_image_needs_multimodal_detail)
    IngestError.IMAGE_UNREADABLE -> stringResource(R.string.ingest_error_image_unreadable_detail)
}

@Composable
fun DocumentFormat.label(): String = stringResource(
    when (this) {
        DocumentFormat.PDF -> R.string.ingest_format_pdf
        DocumentFormat.TXT -> R.string.ingest_format_txt
        DocumentFormat.MARKDOWN -> R.string.ingest_format_markdown
        DocumentFormat.JPEG, DocumentFormat.PNG -> R.string.document_format_image
    },
)

/** What TalkBack reads for a row (U1): name, then size and state or reason, then progress. */
@Composable
fun QueueItem.accessibilityText(): String {
    val status = state.error?.chipText() ?: state.stateText().orEmpty()
    val size = sizeText(sizeBytes)
    val detail = listOfNotNull(size, status.takeIf { it.isNotEmpty() }).joinToString(", ")
    val percent = (state.progress() as? ProgressValue.Determinate)
        ?.takeIf { state is QueueItemState.Importing }
        ?.let { stringResource(R.string.ingest_progress_percent, it.percent) }
    // F17 K6: and where it goes. F19 (contract §4): and its mode and pages, as the row shows them.
    val folder = folderName ?: stringResource(R.string.library_folder_general)
    return stringResource(
        R.string.ingest_row_description,
        displayName,
        listOfNotNull(detail, percent, folder, modeChip(), pagesEstimate()).joinToString(", "),
    )
}
