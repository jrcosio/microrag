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

// Queue state → texts of contracts/ingest-ui.md. Pure mapping: no state.

@Composable
fun sizeText(bytes: Long?): String? = bytes?.let { Formatter.formatShortFileSize(LocalContext.current, it) }

/** «2,4 MB · Listo para procesar»; an error row shows only the size, its reason goes to the chip. */
@Composable
fun QueueItem.metaText(): String {
    val size = sizeText(sizeBytes)
    val state = state.stateText() ?: return size.orEmpty()
    return if (size == null) state else stringResource(R.string.ingest_meta, size, state)
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
    is QueueItemState.ImportFailed, is QueueItemState.Failed -> null
}

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
}

@Composable
fun DocumentFormat.label(): String = stringResource(
    when (this) {
        DocumentFormat.PDF -> R.string.ingest_format_pdf
        DocumentFormat.TXT -> R.string.ingest_format_txt
        DocumentFormat.MARKDOWN -> R.string.ingest_format_markdown
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
    return stringResource(R.string.ingest_row_description, displayName, listOfNotNull(detail, percent).joinToString(", "))
}
