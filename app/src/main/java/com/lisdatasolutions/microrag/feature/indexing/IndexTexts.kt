package com.lisdatasolutions.microrag.feature.indexing

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.lisdatasolutions.microrag.R
import com.lisdatasolutions.microrag.core.designsystem.icon.LisdataIcons
import com.lisdatasolutions.microrag.core.designsystem.model.ProgressValue
import com.lisdatasolutions.microrag.core.designsystem.model.StageStatus
import com.lisdatasolutions.microrag.core.designsystem.model.StatusKind
import com.lisdatasolutions.microrag.domain.indexing.CurrentDocument
import com.lisdatasolutions.microrag.domain.indexing.PauseReason
import com.lisdatasolutions.microrag.domain.indexing.RunProgress
import java.text.NumberFormat

// Indexing state → texts and visuals of contracts/index-ui.md. Pure mapping: no state.

/** «1.250» in Spanish, «1,250» in English. */
fun Int.grouped(): String = NumberFormat.getIntegerInstance().format(this)

/** Real progress only: a percentage when the total is known, an indeterminate ring otherwise (constitution V). */
fun RunProgress.ring(): ProgressValue = total?.takeIf { it > 0 }
    ?.let { ProgressValue.Determinate((done.toFloat() / it).coerceIn(0f, 1f)) }
    ?: ProgressValue.Indeterminate

@Composable
fun remainingText(seconds: Long): String {
    val minutes = (seconds + 59) / 60
    val duration = when {
        minutes < 1 -> stringResource(R.string.index_duration_less_than_a_minute)
        minutes < 60 -> stringResource(R.string.index_duration_minutes, minutes.toInt())
        else -> stringResource(R.string.index_duration_hours, (minutes / 60).toInt(), (minutes % 60).toInt())
    }
    return stringResource(R.string.index_remaining, duration)
}

/** The four stages of PNG 08 (G29), with the icon each one shows while it is not done. */
enum class IndexStage(@StringRes val title: Int, @DrawableRes val icon: Int) {
    EXTRACTION(R.string.index_stage_extraction, LisdataIcons.FileText),
    CHUNKING(R.string.index_stage_chunking, LisdataIcons.Layers),
    EMBEDDINGS(R.string.index_stage_embeddings, LisdataIcons.Cpu),
    PERSISTENCE(R.string.index_stage_persistence, LisdataIcons.Database),
}

/** The PNG stage a document is in (data-model §3). */
fun CurrentDocument.Stage.indexStage(): IndexStage = when (this) {
    CurrentDocument.Stage.EXTRACTING -> IndexStage.EXTRACTION
    CurrentDocument.Stage.CHUNKING -> IndexStage.CHUNKING
    CurrentDocument.Stage.EMBEDDING -> IndexStage.EMBEDDINGS
    CurrentDocument.Stage.CLOSING -> IndexStage.PERSISTENCE
}

/** Data-model §3: the current document's stage decides which stages are done, in progress or pending. */
fun stagesOf(current: CurrentDocument.Stage?, paused: Boolean): List<Pair<IndexStage, StageStatus>> {
    val inProgress = current?.indexStage()
    return IndexStage.entries.map { stage ->
        stage to when {
            inProgress == null -> StageStatus.Pending
            stage.ordinal < inProgress.ordinal -> StageStatus.Done
            stage == inProgress && !paused -> StageStatus.InProgress
            else -> StageStatus.Pending
        }
    }
}

@Composable
fun stageText(status: StageStatus, isCurrent: Boolean, paused: Boolean): String = stringResource(
    when {
        status == StageStatus.Done -> R.string.index_stage_done
        status == StageStatus.InProgress -> R.string.index_stage_in_progress
        isCurrent && paused -> R.string.index_stage_paused
        else -> R.string.index_stage_pending
    },
)

/** The chip under the chunks for a paused run (P3–P5), or null while it runs. */
@Composable
fun PauseReason.chip(): Pair<String, StatusKind>? = when (this) {
    PauseReason.USER -> stringResource(R.string.index_paused) to StatusKind.Paused
    PauseReason.THERMAL -> stringResource(R.string.index_paused_thermal) to StatusKind.Warning
    PauseReason.SYSTEM_LIMIT -> stringResource(R.string.index_paused_system) to StatusKind.Warning
    PauseReason.NO_SPACE -> stringResource(R.string.index_no_space) to StatusKind.Error
    PauseReason.MODEL_MISSING -> null
}

/** The explanation card of P4 and P5; the user's own pause needs none. */
@Composable
fun PauseReason.explanation(): String? = when (this) {
    PauseReason.THERMAL -> stringResource(R.string.index_paused_thermal_body)
    PauseReason.SYSTEM_LIMIT -> stringResource(R.string.index_paused_system_body)
    PauseReason.NO_SPACE -> stringResource(R.string.index_no_space_body)
    PauseReason.USER, PauseReason.MODEL_MISSING -> null
}
