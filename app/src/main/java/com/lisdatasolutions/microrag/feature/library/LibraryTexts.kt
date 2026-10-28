package com.lisdatasolutions.microrag.feature.library

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.lisdatasolutions.microrag.R
import com.lisdatasolutions.microrag.core.designsystem.model.StatusKind
import com.lisdatasolutions.microrag.domain.ingest.DocumentFormat
import com.lisdatasolutions.microrag.domain.library.LibraryDocument
import com.lisdatasolutions.microrag.domain.library.LibraryDocumentState
import com.lisdatasolutions.microrag.domain.library.ReindexRow
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
        null -> R.string.library_format_unknown
    },
)

/** «PDF · 860 fragmentos», «PDF · Pendiente»…; an error row shows only the format, its reason goes to the chip. */
@Composable
fun LibraryDocument.metaText(): String {
    val format = format.label()
    val state = when (val state = state) {
        is LibraryDocumentState.Indexed -> pluralStringResource(R.plurals.library_chunks, state.chunks, state.chunks.grouped())
        LibraryDocumentState.Pending -> stringResource(R.string.library_state_pending)
        LibraryDocumentState.Indexing -> stringResource(R.string.library_state_indexing)
        LibraryDocumentState.OutOfIndex -> stringResource(R.string.library_state_out_of_index)
        is LibraryDocumentState.Failed -> return format
    }
    return stringResource(R.string.library_meta, format, state)
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
fun LibraryDocument.accessibilityText(): String {
    val state = listOfNotNull(
        metaText() + (metaAccent()?.let { " · $it" } ?: ""),
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
