package com.lisdatasolutions.microrag.feature.library

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.lisdatasolutions.microrag.R
import com.lisdatasolutions.microrag.core.designsystem.model.StatusKind
import com.lisdatasolutions.microrag.domain.ingest.DocumentFormat
import com.lisdatasolutions.microrag.domain.library.LibraryDocument
import com.lisdatasolutions.microrag.domain.library.LibraryDocumentState
import com.lisdatasolutions.microrag.domain.library.ReindexRow
import com.lisdatasolutions.microrag.domain.rag.IndexMode
import java.text.NumberFormat

// Library rows → texts of contracts/library-ui.md and research R10. Pure mapping: no state.

/** «1.120» in Spanish, «1,120» in English. */
fun Int.grouped(): String = NumberFormat.getIntegerInstance().format(this)

/** As PNG 06: «PDF», «TXT», «MD». */
@Composable
fun DocumentFormat?.label(): String = stringResource(
    when (this) {
        DocumentFormat.PDF -> R.string.library_format_pdf
        DocumentFormat.TXT -> R.string.library_format_txt
        DocumentFormat.MARKDOWN -> R.string.library_format_markdown
        DocumentFormat.JPEG, DocumentFormat.PNG -> R.string.document_format_image
        null -> R.string.library_format_unknown
    },
)

/**
 * «PDF · 860 fragmentos», «PDF · Pendiente»…; an error row shows only the format, its reason goes to the chip.
 * F19 (contract U11): type · mode · pages (Visual and Both) · chunks (Text and Both), «PDF · Ambos · 40 páginas ·
 * 860 fragmentos»; a Text row is as it was.
 */
@Composable
fun LibraryDocument.metaText(): String {
    val format = format.label()
    val mode = indexMode.takeIf { it != IndexMode.TEXT }?.let { stringResource(it.modeLabel()) }
    val state = when (val state = state) {
        is LibraryDocumentState.Indexed -> listOfNotNull(
            pages?.takeIf { indexMode.hasPages }?.let { pluralStringResource(R.plurals.document_pages, it, it.grouped()) },
            pluralStringResource(R.plurals.library_chunks, state.chunks, state.chunks.grouped()).takeIf { indexMode.hasText },
        ).joinToString(SEPARATOR).ifEmpty { null }
        LibraryDocumentState.Pending -> stringResource(R.string.library_state_pending)
        LibraryDocumentState.Indexing -> stringResource(R.string.library_state_indexing)
        LibraryDocumentState.OutOfIndex -> stringResource(R.string.library_state_out_of_index)
        is LibraryDocumentState.Failed -> null
    }
    if (state == null && this.state is LibraryDocumentState.Failed) return format
    val head = mode?.let { stringResource(R.string.library_meta, format, it) } ?: format
    return state?.let { stringResource(R.string.library_meta, head, it) } ?: head
}

/** The separator of [R.string.library_meta] between the counts of one row. */
private const val SEPARATOR = " · "

@StringRes
fun IndexMode.modeLabel(): Int = when (this) {
    IndexMode.TEXT -> R.string.document_mode_text
    IndexMode.VISUAL -> R.string.document_mode_visual
    IndexMode.BOTH -> R.string.document_mode_both
}

/** B7: «Reindexando» ends the meta in brand blue while the shadow runs. */
@Composable
fun LibraryDocument.metaAccent(): String? =
    if ((state as? LibraryDocumentState.Indexed)?.reindex == ReindexRow.RUNNING) {
        stringResource(R.string.library_state_reindexing)
    } else {
        null
    }

/** B5 and B7: the error chip of a failed document or of a failed reindex. */
@Composable
fun LibraryDocument.chip(): Pair<String, StatusKind>? = when (val state = state) {
    is LibraryDocumentState.Failed -> stringResource(R.string.library_state_failed) to StatusKind.Error
    is LibraryDocumentState.Indexed ->
        if (state.reindex == ReindexRow.FAILED) stringResource(R.string.library_state_reindex_failed) to StatusKind.Error else null
    else -> null
}

/** A1, B5, B6: «Manual.pdf, PDF · 860 fragmentos, incluido». */
@Composable
fun LibraryDocument.accessibilityText(folder: String? = null): String {
    val state = listOfNotNull(
        // F17 K1: the root's search says the folder, as the data line.
        metaText() + (metaAccent()?.let { " · $it" } ?: "") + (folder?.let { " · $it" } ?: ""),
        chip()?.first,
    ).joinToString(", ")
    val inclusion = when {
        !canChangeInclusion -> stringResource(R.string.library_row_not_indexed)
        included -> stringResource(R.string.library_row_included)
        else -> stringResource(R.string.library_row_excluded)
    }
    return stringResource(R.string.library_row_description, displayName, state, inclusion)
}

/** «24 DOCUMENTOS» once the eyebrow upper-cases it. */
@Composable
fun countText(total: Int): String = pluralStringResource(R.plurals.library_count, total, total.grouped())
