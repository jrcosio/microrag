package com.lisdatasolutions.microrag.debug.retrieval

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lisdatasolutions.microrag.R
import com.lisdatasolutions.microrag.core.designsystem.component.LisdataCard
import com.lisdatasolutions.microrag.core.designsystem.component.LisdataDivider
import com.lisdatasolutions.microrag.core.designsystem.component.LisdataGradientButton
import com.lisdatasolutions.microrag.core.designsystem.component.LisdataSearchField
import com.lisdatasolutions.microrag.core.designsystem.component.LisdataSectionHeader
import com.lisdatasolutions.microrag.core.designsystem.component.LisdataTopBar
import com.lisdatasolutions.microrag.core.designsystem.preview.LisdataPreviews
import com.lisdatasolutions.microrag.core.designsystem.theme.LisdataTheme
import com.lisdatasolutions.microrag.debug.retrieval.RetrievalDiagnosticUiState.Status
import com.lisdatasolutions.microrag.domain.rag.IndexCompatibility
import com.lisdatasolutions.microrag.domain.retrieval.CandidateScore
import com.lisdatasolutions.microrag.domain.retrieval.ContextBlock
import com.lisdatasolutions.microrag.domain.retrieval.RetrievalOutcome
import com.lisdatasolutions.microrag.domain.retrieval.RetrievalResult
import com.lisdatasolutions.microrag.domain.retrieval.RetrievalTimings
import java.util.Locale
import org.koin.compose.viewmodel.koinViewModel

/** Actions of the diagnostic, so the content stays stateless and previewable. */
data class RetrievalDiagnosticActions(
    val onBack: () -> Unit = {},
    val onQuestionChange: (String) -> Unit = {},
    val onPreviousChange: (String) -> Unit = {},
    val onSearch: () -> Unit = {},
)

/** «MicroRAG Debug → Diagnóstico de búsqueda» (F10): internal tool, never part of the product UI. */
@Composable
fun RetrievalDiagnosticScreen(onBack: () -> Unit, viewModel: RetrievalDiagnosticViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    RetrievalDiagnosticContent(
        state = state,
        actions = RetrievalDiagnosticActions(
            onBack = onBack,
            onQuestionChange = viewModel::onQuestionChange,
            onPreviousChange = viewModel::onPreviousChange,
            onSearch = viewModel::onSearch,
        ),
    )
}

@Composable
internal fun RetrievalDiagnosticContent(state: RetrievalDiagnosticUiState, actions: RetrievalDiagnosticActions) {
    val spacing = LisdataTheme.spacing
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(LisdataTheme.colors.background)
            .navigationBarsPadding()
            .imePadding(),
    ) {
        LisdataTopBar(title = stringResource(R.string.retrieval_title), onBack = actions.onBack)
        SelectionContainer {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = spacing.screenPaddingHorizontal, vertical = spacing.space4),
                verticalArrangement = Arrangement.spacedBy(spacing.listGap),
            ) {
                inputSection(state, actions)
                (state.status as? Status.Done)?.result?.let { result ->
                    resultSections(result)
                }
            }
        }
    }
}

private fun LazyListScope.inputSection(state: RetrievalDiagnosticUiState, actions: RetrievalDiagnosticActions) {
    item {
        Column(verticalArrangement = Arrangement.spacedBy(LisdataTheme.spacing.space2)) {
            BodyText(stringResource(R.string.retrieval_question_label))
            LisdataSearchField(
                query = state.question,
                onQueryChange = actions.onQuestionChange,
                placeholder = stringResource(R.string.retrieval_question_placeholder),
            )
            BodyText(stringResource(R.string.retrieval_previous_label))
            LisdataSearchField(
                query = state.previousQuestion,
                onQueryChange = actions.onPreviousChange,
                placeholder = stringResource(R.string.retrieval_previous_placeholder),
            )
            LisdataGradientButton(
                text = stringResource(R.string.retrieval_action_search),
                onClick = actions.onSearch,
                enabled = state.canSearch,
                loading = state.status == Status.Searching,
                modifier = Modifier.fillMaxWidth(),
            )
            if (state.status == Status.Searching) {
                BodyText(
                    text = stringResource(R.string.retrieval_searching),
                    secondary = true,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
        }
    }
}

private fun LazyListScope.resultSections(result: RetrievalResult) {
    item { LisdataSectionHeader(stringResource(R.string.retrieval_section_summary)) }
    item { SummaryCard(result) }
    item { LisdataSectionHeader(stringResource(R.string.retrieval_section_timings)) }
    item { TimingsCard(result.timings) }
    val outcome = result.outcome as? RetrievalOutcome.Blocks ?: return
    item { LisdataSectionHeader(stringResource(R.string.retrieval_section_blocks)) }
    itemsIndexed(outcome.blocks, key = { index, _ -> "block-$index" }) { index, block -> BlockCard(index + 1, block) }
    item { LisdataSectionHeader(stringResource(R.string.retrieval_section_candidates)) }
    item {
        LisdataCard(modifier = Modifier.fillMaxWidth()) {
            outcome.candidates.forEachIndexed { index, candidate ->
                if (index > 0) LisdataDivider()
                BodyText(candidateLine(candidate))
            }
        }
    }
}

@Composable
private fun SummaryCard(result: RetrievalResult) {
    LisdataCard(modifier = Modifier.fillMaxWidth()) {
        BodyText(stringResource(R.string.retrieval_follow_up, yesNo(result.followUp)))
        BodyText(stringResource(R.string.retrieval_search_text, result.searchText))
        if (result.embeddingCuts > 0) BodyText(stringResource(R.string.retrieval_cuts, result.embeddingCuts))
        BodyText(outcomeLine(result.outcome))
    }
}

@Composable
private fun outcomeLine(outcome: RetrievalOutcome): String = when (outcome) {
    is RetrievalOutcome.Blocks -> stringResource(R.string.retrieval_blocks_count, outcome.blocks.size, outcome.blocks.sumOf { it.tokens })
    RetrievalOutcome.NoResults -> stringResource(R.string.retrieval_no_results)
    RetrievalOutcome.EmptyQuestion -> stringResource(R.string.retrieval_empty_question)
    is RetrievalOutcome.IndexUnavailable -> stringResource(R.string.retrieval_index_unavailable, indexState(outcome.state))
    RetrievalOutcome.ModelsNotReady -> stringResource(R.string.retrieval_models_not_ready)
    RetrievalOutcome.EmbeddingFailed -> stringResource(R.string.retrieval_embedding_failed)
}

@Composable
private fun indexState(state: IndexCompatibility): String = when (state) {
    IndexCompatibility.Closed, IndexCompatibility.Compatible -> stringResource(R.string.retrieval_index_closed)
    is IndexCompatibility.Incompatible -> stringResource(R.string.retrieval_index_incompatible, state.field)
    is IndexCompatibility.Unreadable -> stringResource(R.string.retrieval_index_unreadable, state.reason)
}

@Composable
private fun TimingsCard(timings: RetrievalTimings) {
    LisdataCard(modifier = Modifier.fillMaxWidth()) {
        BodyText(stringResource(R.string.retrieval_timing_embedding, ms(timings.embeddingMs)))
        BodyText(stringResource(R.string.retrieval_timing_vector, ms(timings.vectorMs)))
        BodyText(stringResource(R.string.retrieval_timing_text, ms(timings.textMs)))
        BodyText(stringResource(R.string.retrieval_timing_selection, ms(timings.selectionMs)))
        BodyText(stringResource(R.string.retrieval_timing_total, ms(timings.totalMs)))
        BodyText(stringResource(R.string.retrieval_engine_loaded, yesNo(!timings.coldEngine)))
    }
}

@Composable
private fun BlockCard(number: Int, block: ContextBlock) {
    LisdataCard(modifier = Modifier.fillMaxWidth()) {
        BodyText(stringResource(R.string.retrieval_block_title, number, block.documentName))
        block.headingPath?.let { BodyText(stringResource(R.string.retrieval_block_heading, it), secondary = true) }
        block.page?.let { BodyText(stringResource(R.string.retrieval_block_page, it), secondary = true) }
        val ordinals = if (block.firstOrdinal == block.lastOrdinal) "${block.firstOrdinal}" else "${block.firstOrdinal}–${block.lastOrdinal}"
        BodyText(
            stringResource(R.string.retrieval_block_chunks, ordinals, block.chunkIds.joinToString(", "), block.tokens),
            secondary = true,
        )
        LisdataDivider()
        BodyText(block.text)
    }
}

@Composable
private fun candidateLine(candidate: CandidateScore): String {
    val absent = stringResource(R.string.retrieval_absent)
    val vector = candidate.vectorRank?.let { rank ->
        stringResource(R.string.retrieval_candidate_vector, rank, num(candidate.vectorDistance ?: 0.0, 3))
    } ?: absent
    val line = stringResource(
        R.string.retrieval_candidate,
        candidate.fusedRank, candidate.chunkId, num(candidate.rrfScore, 4), vector, candidate.textRank?.toString() ?: absent,
    )
    return if (candidate.selected) stringResource(R.string.retrieval_candidate_selected, line) else line
}

@Composable
private fun BodyText(text: String, modifier: Modifier = Modifier, secondary: Boolean = false) {
    Text(
        text = text,
        modifier = modifier,
        style = LisdataTheme.typography.material.bodyMedium,
        color = if (secondary) LisdataTheme.colors.textSecondary else LisdataTheme.colors.textPrimary,
    )
}

@Composable
private fun yesNo(value: Boolean) = stringResource(if (value) R.string.retrieval_yes else R.string.retrieval_no)

private fun ms(value: Double): String = num(value, 1)

private fun num(value: Double, decimals: Int): String = String.format(Locale.ROOT, "%.${decimals}f", value)

@LisdataPreviews
@Composable
private fun RetrievalDiagnosticPreview() {
    LisdataTheme {
        RetrievalDiagnosticContent(
            state = RetrievalDiagnosticUiState(
                question = "¿Cada cuánto se revisa la bomba?",
                status = Status.Done(
                    RetrievalResult(
                        searchText = "¿Cada cuánto se revisa la bomba?",
                        followUp = false,
                        embeddingCuts = 0,
                        timings = RetrievalTimings(118.2, 22.9, 19.4, 3.1, 146.0),
                        outcome = RetrievalOutcome.Blocks(
                            blocks = listOf(
                                ContextBlock(1, "Manual.pdf", "Mantenimiento › Bombas", 12, 3, 4, listOf(815, 816),
                                    "Las bombas se revisan cada 500 horas.", 402),
                            ),
                            candidates = listOf(CandidateScore(815, 1, 0.0325, 1, 0.123, 2, selected = true)),
                        ),
                    ),
                ),
            ),
            actions = RetrievalDiagnosticActions(),
        )
    }
}
