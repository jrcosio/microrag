package com.lisdatasolutions.microrag.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import com.lisdatasolutions.microrag.R
import com.lisdatasolutions.microrag.core.designsystem.component.LisdataCard
import com.lisdatasolutions.microrag.core.designsystem.component.LisdataEmptyState
import com.lisdatasolutions.microrag.core.designsystem.component.LisdataErrorState
import com.lisdatasolutions.microrag.core.designsystem.component.LisdataEyebrow
import com.lisdatasolutions.microrag.core.designsystem.component.LisdataGradientButton
import com.lisdatasolutions.microrag.core.designsystem.component.LisdataSkeletonRow
import com.lisdatasolutions.microrag.core.designsystem.icon.LisdataIcons
import com.lisdatasolutions.microrag.core.designsystem.theme.LisdataTheme
import com.lisdatasolutions.microrag.feature.library.grouped

/** PNG 03 «TU BIBLIOTECA», with the proposals P3 (empty) and P4 (loading, error) of contracts/home-ui.md. */
@Composable
fun HomeSummaryCard(summary: SummaryState, onImport: () -> Unit, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    LisdataCard(modifier = modifier.fillMaxWidth()) {
        LisdataEyebrow(text = stringResource(R.string.home_summary_eyebrow))
        when (summary) {
            SummaryState.Loading -> LisdataSkeletonRow(lines = 1)
            SummaryState.Empty -> LisdataEmptyState(
                icon = LisdataIcons.Folder,
                title = stringResource(R.string.home_empty_title),
                body = stringResource(R.string.home_empty_body),
                action = { LisdataGradientButton(text = stringResource(R.string.home_import_first), onClick = onImport) },
            )
            is SummaryState.Loaded -> Figures(summary.documents, summary.chunks)
            is SummaryState.Error -> LisdataErrorState(
                message = stringResource(if (summary.unavailable) R.string.library_unavailable_title else R.string.home_summary_failed),
                onRetry = onRetry,
            )
        }
    }
}

/** Two columns, each figure over its label (research R6); stacked with large fonts (A7). Read as one sentence (A2). */
@Composable
private fun Figures(documents: Int, chunks: Int) {
    val colors = LisdataTheme.colors
    val documentsLabel = pluralStringResource(R.plurals.home_documents, documents)
    val chunksLabel = pluralStringResource(R.plurals.home_chunks, chunks)
    val sentence = stringResource(
        R.string.home_summary_description,
        "${documents.grouped()} $documentsLabel",
        "${chunks.grouped()} $chunksLabel",
    )
    val semantics = Modifier.clearAndSetSemantics { contentDescription = sentence }
    val figures: @Composable (Modifier) -> Unit = { cell ->
        Figure(documents.grouped(), documentsLabel, colors.textPrimary, cell)
        Figure(chunks.grouped(), chunksLabel, colors.brandBlue, cell)
    }
    if (LocalDensity.current.fontScale >= STACKED_FONT_SCALE) {
        Column(modifier = semantics, verticalArrangement = Arrangement.spacedBy(LisdataTheme.spacing.space3)) { figures(Modifier) }
    } else {
        Row(modifier = semantics.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(LisdataTheme.spacing.space4)) {
            figures(Modifier.weight(1f))
        }
    }
}

@Composable
private fun Figure(value: String, label: String, color: Color, modifier: Modifier) {
    Column(modifier = modifier) {
        Text(text = value, style = LisdataTheme.typography.material.headlineMedium, color = color)
        Text(text = label, style = LisdataTheme.typography.material.bodyMedium, color = LisdataTheme.colors.textSecondary)
    }
}

/** A7: the same threshold as the stacked actions of the document detail (F08 A6). */
internal const val STACKED_FONT_SCALE = 1.5f
