package com.lisdatasolutions.microrag.feature.home

import androidx.compose.runtime.Composable
import com.lisdatasolutions.microrag.core.designsystem.preview.LisdataPreviews
import com.lisdatasolutions.microrag.core.designsystem.theme.LisdataTheme
import com.lisdatasolutions.microrag.domain.model.LocalAiStatus

/** Labelled sample states for previews and UI tests only (constitution V): never shown in the app. */
object HomePreviewData {

    /** PNG 03: the local AI ready and «24 documentos · 12.400 fragmentos». */
    val png = HomeUiState(ai = LocalAiStatus.READY, summary = SummaryState.Loaded(documents = 24, chunks = 12_400))

    /** Just opened: nothing checked or loaded yet (FR-025). */
    val opening = HomeUiState()

    val all: List<Pair<String, HomeUiState>> = listOf(
        "png" to png,
        "opening" to opening,
    )
}

@LisdataPreviews
@Composable
private fun HomePngPreview() = LisdataTheme { HomeScreen(HomePreviewData.png, HomeActions()) }

@LisdataPreviews
@Composable
private fun HomeOpeningPreview() = LisdataTheme { HomeScreen(HomePreviewData.opening, HomeActions()) }
