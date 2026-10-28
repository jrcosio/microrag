package com.lisdatasolutions.microrag.feature.home

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.lisdatasolutions.microrag.R
import com.lisdatasolutions.microrag.core.designsystem.model.ProgressValue
import com.lisdatasolutions.microrag.domain.indexing.PauseReason
import com.lisdatasolutions.microrag.domain.indexing.RunProgress
import com.lisdatasolutions.microrag.feature.indexing.chip
import com.lisdatasolutions.microrag.feature.indexing.grouped
import com.lisdatasolutions.microrag.feature.indexing.ring

// Indexing card → texts of contracts/home-ui.md P5, with the Index screen's own words (research R3). Pure mapping.

/** «Indexando documentos», or why the run is paused, as the Index screen says it. */
@Composable
fun IndexingCard.title(): String = when (pause) {
    null -> stringResource(R.string.home_indexing)
    PauseReason.MODEL_MISSING -> stringResource(R.string.index_model_missing_title)
    else -> pause.chip()?.first ?: stringResource(R.string.home_indexing)
}

/** The run's chunks, the same figures as the Index screen and the notification (D-042). */
@Composable
fun IndexingCard.detail(): String = total
    ?.let { stringResource(R.string.index_notification_chunks, done.grouped(), it.grouped()) }
    ?: stringResource(R.string.home_indexing_preparing)

/** Real progress only: determinate with a known total (constitution V). */
fun IndexingCard.progress(): ProgressValue = RunProgress(done, total, remainingSeconds = null).ring()
