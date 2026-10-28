package com.lisdatasolutions.microrag.feature.document

import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import com.lisdatasolutions.microrag.R
import com.lisdatasolutions.microrag.core.designsystem.model.StatusKind
import com.lisdatasolutions.microrag.domain.indexing.PauseReason
import com.lisdatasolutions.microrag.domain.ingest.DocumentFormat
import com.lisdatasolutions.microrag.domain.ingest.IngestError
import com.lisdatasolutions.microrag.domain.library.DocumentDetail
import com.lisdatasolutions.microrag.domain.library.DocumentDetailState
import com.lisdatasolutions.microrag.feature.ingest.detailText
import com.lisdatasolutions.microrag.feature.library.grouped
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date

// Document detail → texts of contracts/library-ui.md. Pure mapping: no state.

/** The hero chip: «Indexado», «Excluido», «Pendiente», «Indexando», «Error», «Fuera del índice». */
@Composable
fun DocumentDetail.chip(): Pair<String, StatusKind> = when (state) {
    DocumentDetailState.Indexed ->
        if (included) {
            stringResource(R.string.document_state_indexed) to StatusKind.Ready
        } else {
            stringResource(R.string.document_state_excluded) to StatusKind.Info
        }
    DocumentDetailState.Pending -> stringResource(R.string.document_state_pending) to StatusKind.Queued
    is DocumentDetailState.Indexing -> stringResource(R.string.document_state_indexing) to StatusKind.Processing
    is DocumentDetailState.Failed -> stringResource(R.string.document_state_failed) to StatusKind.Error
    DocumentDetailState.OutOfIndex -> stringResource(R.string.document_state_out_of_index) to StatusKind.Info
}

/** A figure of the grid and what TalkBack reads for it (A2): «Páginas, 48»; «—» read as words. */
data class Stat(val label: String, val value: String, val accessibilityText: String)

@Composable
fun stat(label: Int, value: String?, missing: Int): Stat {
    val name = stringResource(label)
    val shown = value ?: stringResource(R.string.document_stat_none)
    return Stat(name, shown, stringResource(R.string.document_stat_description, name, value ?: stringResource(missing)))
}

/** «12 oct.» this year, with the year otherwise, in the app's language. */
@Composable
fun importedText(epochMs: Long): String {
    val locale = LocalConfiguration.current.locales[0]
    return remember(epochMs, locale) {
        val year = { ms: Long -> Calendar.getInstance().apply { timeInMillis = ms }.get(Calendar.YEAR) }
        val skeleton = if (year(epochMs) == year(System.currentTimeMillis())) "dMMM" else "dMMMy"
        SimpleDateFormat(DateFormat.getBestDateTimePattern(locale, skeleton), locale).format(Date(epochMs))
    }
}

/** «120 de 430 fragmentos». */
@Composable
fun chunksProgress(done: Int, total: Int): String = stringResource(R.string.document_indexing_progress, done.grouped(), total.grouped())

/**
 * D6: the cause inside «No se pudo reindexar: %s.»: the first sentence of the Ingest's explanation,
 * without its period and in lower case («falló el cálculo de los vectores»).
 */
@Composable
fun IngestError.causeText(format: DocumentFormat?): String {
    val locale = LocalConfiguration.current.locales[0]
    return detailText(format).substringBefore(". ").removeSuffix(".").replaceFirstChar { it.lowercase(locale) }
}

/** D5: why the run is not advancing, with the texts of F07. */
@Composable
fun PauseReason.text(): String = stringResource(
    when (this) {
        PauseReason.USER -> R.string.index_paused
        PauseReason.THERMAL -> R.string.index_paused_thermal
        PauseReason.NO_SPACE -> R.string.index_no_space
        PauseReason.MODEL_MISSING -> R.string.index_model_missing_title
        PauseReason.SYSTEM_LIMIT -> R.string.index_paused_system
    },
)
