package com.lisdatasolutions.microrag.feature.home

import androidx.compose.runtime.Composable
import com.lisdatasolutions.microrag.core.designsystem.preview.LisdataPreviews
import com.lisdatasolutions.microrag.core.designsystem.theme.LisdataTheme
import com.lisdatasolutions.microrag.domain.indexing.PauseReason
import com.lisdatasolutions.microrag.domain.model.LocalAiStatus

/** Labelled sample states for previews and UI tests only (constitution V): never shown in the app. */
object HomePreviewData {

    /** PNG 03: the local AI ready and «24 documentos · 12.400 fragmentos». */
    val png = HomeUiState(ai = LocalAiStatus.READY, summary = SummaryState.Loaded(documents = 24, chunks = 12_400))

    /** Just opened: nothing checked or loaded yet (FR-025, P2). */
    val opening = HomeUiState()

    /** P1: a model is missing, altered or not prepared. */
    val setupPending = png.copy(ai = LocalAiStatus.SETUP_PENDING)

    /** P3: no documents yet. */
    val empty = png.copy(summary = SummaryState.Empty)

    /** P4: the summary failed to load, or the index is incompatible or unreadable. */
    val summaryFailed = png.copy(summary = SummaryState.Error(unavailable = false))
    val summaryUnavailable = png.copy(summary = SummaryState.Error(unavailable = true))

    /** P5: a run indexing «320 de 860 fragmentos». */
    val indexing = png.copy(indexing = IndexingCard(done = 320, total = 860, pause = null))

    /** P5: the same run paused by the temperature. */
    val indexingPaused = png.copy(indexing = IndexingCard(done = 320, total = 860, pause = PauseReason.THERMAL))

    val all: List<Pair<String, HomeUiState>> = listOf(
        "png" to png,
        "opening" to opening,
        "setupPending" to setupPending,
        "empty" to empty,
        "summaryFailed" to summaryFailed,
        "summaryUnavailable" to summaryUnavailable,
        "indexing" to indexing,
        "indexingPaused" to indexingPaused,
    )
}

@LisdataPreviews
@Composable
private fun HomePngPreview() = LisdataTheme { HomeScreen(HomePreviewData.png, HomeActions()) }

@LisdataPreviews
@Composable
private fun HomeSetupPendingPreview() = LisdataTheme { HomeScreen(HomePreviewData.setupPending, HomeActions()) }

@LisdataPreviews
@Composable
private fun HomeOpeningPreview() = LisdataTheme { HomeScreen(HomePreviewData.opening, HomeActions()) }

@LisdataPreviews
@Composable
private fun HomeEmptyPreview() = LisdataTheme { HomeScreen(HomePreviewData.empty, HomeActions()) }

@LisdataPreviews
@Composable
private fun HomeSummaryFailedPreview() = LisdataTheme { HomeScreen(HomePreviewData.summaryFailed, HomeActions()) }

@LisdataPreviews
@Composable
private fun HomeIndexingPreview() = LisdataTheme { HomeScreen(HomePreviewData.indexingPaused, HomeActions()) }
