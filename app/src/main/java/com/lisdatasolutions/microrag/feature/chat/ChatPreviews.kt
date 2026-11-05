package com.lisdatasolutions.microrag.feature.chat

import androidx.compose.runtime.Composable
import com.lisdatasolutions.microrag.R
import com.lisdatasolutions.microrag.core.designsystem.model.AnswerBlock
import com.lisdatasolutions.microrag.core.designsystem.model.AnswerContent
import com.lisdatasolutions.microrag.core.designsystem.model.AnswerSpan
import com.lisdatasolutions.microrag.core.designsystem.model.AnswerState
import com.lisdatasolutions.microrag.core.designsystem.model.CitationKind
import com.lisdatasolutions.microrag.core.designsystem.model.CitationUi
import com.lisdatasolutions.microrag.core.designsystem.preview.LisdataPreviews
import com.lisdatasolutions.microrag.core.designsystem.theme.LisdataTheme
import com.lisdatasolutions.microrag.domain.chat.ChatAvailability
import com.lisdatasolutions.microrag.feature.chat.scope.ScopeSummary

/** Labelled sample states for previews and UI tests only (constitution V): never shown in the app. */
object ChatPreviewData {

    private val ready = ChatAvailability.Ready(cpuMode = false, partialCoverage = false)

    val manual = SourceRow(1, SourceTarget(documentId = 1, chunkId = 186), CitationUi("Manual de procesos.pdf", page = 12, fragment = 86))
    val protocol = SourceRow(2, SourceTarget(documentId = 2, chunkId = 241), CitationUi("Protocolo interno.pdf", page = 8, fragment = 41))

    /** F20: a cited PDF page and a cited loose image (wireframe §4, §10). */
    val reportPage = SourceRow(
        2, SourceTarget(documentId = 9, chunkId = 0, page = 4), CitationUi("informe-ine.pdf", page = 4, fragment = null, kind = CitationKind.PAGE),
    )
    val poster = SourceRow(3, SourceTarget(documentId = 10, chunkId = 0, page = 1), CitationUi("cartel.jpg", page = null, fragment = null, kind = CitationKind.IMAGE))

    /** The answer of PNG 04: an intro, three bullets and their citation chips. */
    val answer = AnswerContent(
        listOf(
            AnswerBlock.Paragraph(listOf(AnswerSpan.Text("El protocolo establece tres comprobaciones antes del cierre:"))),
            AnswerBlock.Bullet(listOf(AnswerSpan.Text("Verificar la documentación "), AnswerSpan.Citation(1))),
            AnswerBlock.Bullet(listOf(AnswerSpan.Text("Confirmar las aprobaciones "), AnswerSpan.Citation(2))),
            AnswerBlock.Bullet(listOf(AnswerSpan.Text("Registrar la validación final "), AnswerSpan.Citation(2))),
        ),
    )

    val completedTurn = TurnUi(
        id = 1,
        question = "¿Qué indica el protocolo sobre la validación final?",
        state = AnswerState.Completed,
        status = null,
        content = answer,
        refusal = false,
        failureText = null,
        notes = emptyList(),
        sources = listOf(manual, protocol),
        chipTargets = listOf(manual.target, protocol.target),
        retryable = false,
        canRetry = false,
    )

    private val streamingTurn = completedTurn.copy(id = 2, question = "¿Y quién firma el cierre?", sources = emptyList())

    /** PNG 04. */
    val png = ChatUiState(conversationId = 1, availability = ready, turns = listOf(completedTurn), scope = ScopeSummary(whole = true))
    val empty = ChatUiState(availability = ready, scope = ScopeSummary(whole = true))

    /** F17 K7: a scope too long for the chip, cut with «…». */
    val scoped = png.copy(scope = ScopeSummary(whole = false, folderNames = listOf("Contratos de arrendamiento", "Manuales de mantenimiento"), looseDocuments = 12))

    /** F17 K9. */
    val nothingInScope = ChatUiState(availability = ChatAvailability.NothingInScope, scope = ScopeSummary(whole = false, folderNames = listOf("Legal")))
    val searching = png.copy(
        turns = listOf(completedTurn, streamingTurn.copy(state = AnswerState.Streaming, status = TurnStatus.SEARCHING, content = AnswerContent.Empty)),
        answering = true,
        announcement = ChatAnnouncement.SEARCHING,
    )
    val generating = png.copy(
        turns = listOf(
            completedTurn,
            streamingTurn.copy(
                state = AnswerState.Streaming,
                status = TurnStatus.GENERATING,
                content = AnswerContent(listOf(AnswerBlock.Paragraph(listOf(AnswerSpan.Text("Lo firma la persona responsable "), AnswerSpan.Citation(1))))),
                chipTargets = listOf(manual.target),
            ),
        ),
        answering = true,
        announcement = ChatAnnouncement.GENERATING,
    )
    val refusal = png.copy(
        turns = listOf(completedTurn.copy(content = AnswerContent.Empty, refusal = true, sources = emptyList(), chipTargets = emptyList())),
    )
    val cancelled = png.copy(
        turns = listOf(completedTurn.copy(state = AnswerState.Cancelled, content = AnswerContent.plain("El protocolo establece tres"), sources = emptyList())),
    )
    val interrupted = png.copy(
        turns = listOf(
            completedTurn.copy(
                state = AnswerState.Failed, content = AnswerContent.plain("El protocolo establece tres"), sources = emptyList(),
                failureText = R.string.chat_interrupted, retryable = true, canRetry = true,
            ),
        ),
    )
    val generationFailed = png.copy(
        turns = listOf(
            completedTurn.copy(
                state = AnswerState.Failed, content = AnswerContent.plain("El protocolo establece"), sources = emptyList(),
                chipTargets = emptyList(), retryable = true, canRetry = true,
            ),
        ),
    )
    val indexFailed = png.copy(
        turns = listOf(
            completedTurn.copy(
                state = AnswerState.Failed, content = AnswerContent.Empty, sources = emptyList(), chipTargets = emptyList(),
                failureText = R.string.chat_index_failed, retryable = true, canRetry = true,
            ),
        ),
    )
    val noCitations = png.copy(
        turns = listOf(
            completedTurn.copy(
                content = AnswerContent.plain("El protocolo pide una validación final antes del cierre."), sources = emptyList(),
                chipTargets = emptyList(), notes = listOf(R.string.chat_no_citations),
            ),
        ),
    )
    val partialCoverage = png.copy(turns = listOf(completedTurn.copy(notes = listOf(R.string.chat_partial_coverage))))
    val saveFailed = png.copy(
        conversationId = null,
        turns = listOf(completedTurn.copy(id = 0, sources = emptyList(), notes = listOf(R.string.chat_save_failed))),
    )
    val fragments = png.copy(
        turns = listOf(completedTurn.copy(sources = listOf(manual.copy(citation = manual.citation.copy(lastFragment = 87)), protocol))),
    )
    val copied = png.copy(copiedTurnId = 1, announcement = ChatAnnouncement.COPIED)
    val typing = png.copy(question = "¿Y quién firma el cierre?")
    val sourceGone = png.copy(
        turns = listOf(completedTurn.copy(sources = listOf(manual, protocol.copy(citation = protocol.citation.copy(available = false))))),
    )
    val cpuMode = png.copy(availability = ChatAvailability.Ready(cpuMode = true, partialCoverage = false))
    val modelsNotReady = ChatUiState(availability = ChatAvailability.ModelsNotReady)
    val emptyLibrary = ChatUiState(availability = ChatAvailability.EmptyLibrary)
    val waitingForIndexing = ChatUiState(availability = ChatAvailability.WaitingForIndexing)
    val waitingWithTurns = png.copy(availability = ChatAvailability.WaitingForIndexing)
    val nothingReady = ChatUiState(availability = ChatAvailability.NothingReady)
    val indexUnavailable = ChatUiState(availability = ChatAvailability.IndexUnavailable)
    val storageUnavailable = ChatUiState(availability = ChatAvailability.StorageUnavailable)
    val deleteDialog = png.copy(confirmDelete = true)
    val loading = ChatUiState()

    val all: List<Pair<String, ChatUiState>> = listOf(
        "png" to png,
        "empty" to empty,
        "scoped" to scoped,
        "nothingInScope" to nothingInScope,
        "searching" to searching,
        "generating" to generating,
        "refusal" to refusal,
        "cancelled" to cancelled,
        "interrupted" to interrupted,
        "generationFailed" to generationFailed,
        "indexFailed" to indexFailed,
        "noCitations" to noCitations,
        "partialCoverage" to partialCoverage,
        "saveFailed" to saveFailed,
        "fragments" to fragments,
        "copied" to copied,
        "typing" to typing,
        "sourceGone" to sourceGone,
        "cpuMode" to cpuMode,
        "modelsNotReady" to modelsNotReady,
        "emptyLibrary" to emptyLibrary,
        "waitingForIndexing" to waitingForIndexing,
        "waitingWithTurns" to waitingWithTurns,
        "nothingReady" to nothingReady,
        "indexUnavailable" to indexUnavailable,
        "storageUnavailable" to storageUnavailable,
        "deleteDialog" to deleteDialog,
        "loading" to loading,
    )
}

@LisdataPreviews
@Composable
private fun ChatPngPreview() = LisdataTheme { ChatScreen(ChatPreviewData.png, ChatActions()) }

@LisdataPreviews
@Composable
private fun ChatGeneratingPreview() = LisdataTheme { ChatScreen(ChatPreviewData.generating, ChatActions()) }

@LisdataPreviews
@Composable
private fun ChatRefusalPreview() = LisdataTheme { ChatScreen(ChatPreviewData.refusal, ChatActions()) }

@LisdataPreviews
@Composable
private fun ChatInterruptedPreview() = LisdataTheme { ChatScreen(ChatPreviewData.interrupted, ChatActions()) }

@LisdataPreviews
@Composable
private fun ChatSourceGonePreview() = LisdataTheme { ChatScreen(ChatPreviewData.sourceGone, ChatActions()) }

@LisdataPreviews
@Composable
private fun ChatCpuModePreview() = LisdataTheme { ChatScreen(ChatPreviewData.cpuMode, ChatActions()) }

@LisdataPreviews
@Composable
private fun ChatModelsNotReadyPreview() = LisdataTheme { ChatScreen(ChatPreviewData.modelsNotReady, ChatActions()) }

@LisdataPreviews
@Composable
private fun ChatEmptyLibraryPreview() = LisdataTheme { ChatScreen(ChatPreviewData.emptyLibrary, ChatActions()) }

@LisdataPreviews
@Composable
private fun ChatWaitingPreview() = LisdataTheme { ChatScreen(ChatPreviewData.waitingWithTurns, ChatActions()) }

@LisdataPreviews
@Composable
private fun ChatStorageUnavailablePreview() = LisdataTheme { ChatScreen(ChatPreviewData.storageUnavailable, ChatActions()) }

@LisdataPreviews
@Composable
private fun ChatDeleteDialogPreview() = LisdataTheme { ChatScreen(ChatPreviewData.deleteDialog, ChatActions()) }

@LisdataPreviews
@Composable
private fun ChatScopedPreview() = LisdataTheme { ChatScreen(ChatPreviewData.scoped, ChatActions()) }

@LisdataPreviews
@Composable
private fun ChatNothingInScopePreview() = LisdataTheme { ChatScreen(ChatPreviewData.nothingInScope, ChatActions()) }
