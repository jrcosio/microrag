package com.lisdatasolutions.microrag.feature.history

import androidx.compose.runtime.Composable
import com.lisdatasolutions.microrag.core.designsystem.preview.LisdataPreviews
import com.lisdatasolutions.microrag.core.designsystem.theme.LisdataTheme
import com.lisdatasolutions.microrag.domain.chat.ConversationSummary
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/** Labelled sample states for previews and UI tests only (constitution V): never shown in the app. */
object HistoryPreviewData {

    val zone: ZoneId = ZoneId.of("Europe/Madrid")
    val today: LocalDate = LocalDate.of(2026, 11, 3)

    private fun at(text: String) = LocalDateTime.parse(text).atZone(zone).toInstant().toEpochMilli()

    /** The wireframe's rows (§2), newest first. */
    val entries = listOf(
        ConversationSummary(1, "¿Cuál es el plazo de devolución?", at("2026-11-03T10:42"), 3),
        ConversationSummary(2, "Resume las condiciones de garantía del manual", at("2026-11-03T08:15"), 1),
        ConversationSummary(3, "¿Qué documentos necesito para la solicitud?", at("2026-11-02T18:05"), 4),
        ConversationSummary(4, "Diferencias entre la versión 2 y la 3 del procedimiento de devoluciones para empresas", at("2026-10-30T18:05"), 2),
        ConversationSummary(5, "¿Cuántos días de vacaciones corresponden?", at("2026-10-12T11:00"), 5),
    )

    /** Wireframe §2, with the first conversation open in the chat. */
    val content = HistoryUiState(
        content = HistoryScreenContent.Sections(groupHistory(entries, today, zone), hasMore = false, loaded = entries.size),
        openConversationId = 1,
        zone = zone,
    )

    val empty = HistoryUiState(content = HistoryScreenContent.Empty, zone = zone)
    val loading = HistoryUiState(content = HistoryScreenContent.Loading, zone = zone)
    val error = HistoryUiState(content = HistoryScreenContent.Error, zone = zone)

    /** H6 while the conversation is being answered. */
    val confirming = content.copy(confirmDeleteId = 1, confirmDeleteAnswering = true)
}

@LisdataPreviews
@Composable
private fun HistoryContentPreview() = LisdataTheme { HistoryScreen(HistoryPreviewData.content, HistoryActions()) }

@LisdataPreviews
@Composable
private fun HistoryEmptyPreview() = LisdataTheme { HistoryScreen(HistoryPreviewData.empty, HistoryActions()) }

@LisdataPreviews
@Composable
private fun HistoryLoadingPreview() = LisdataTheme { HistoryScreen(HistoryPreviewData.loading, HistoryActions()) }

@LisdataPreviews
@Composable
private fun HistoryErrorPreview() = LisdataTheme { HistoryScreen(HistoryPreviewData.error, HistoryActions()) }
