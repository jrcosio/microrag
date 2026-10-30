package com.lisdatasolutions.microrag.feature.chat

import android.content.ClipData
import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lisdatasolutions.microrag.R
import com.lisdatasolutions.microrag.core.designsystem.component.LisdataAnswerCard
import com.lisdatasolutions.microrag.core.designsystem.component.LisdataChatBubble
import com.lisdatasolutions.microrag.core.designsystem.component.LisdataChatComposer
import com.lisdatasolutions.microrag.core.designsystem.component.LisdataConfirmationDialog
import com.lisdatasolutions.microrag.core.designsystem.component.LisdataEmptyState
import com.lisdatasolutions.microrag.core.designsystem.component.LisdataErrorState
import com.lisdatasolutions.microrag.core.designsystem.component.LisdataGradientButton
import com.lisdatasolutions.microrag.core.designsystem.component.LisdataInfoCard
import com.lisdatasolutions.microrag.core.designsystem.component.LisdataModelMissingBlock
import com.lisdatasolutions.microrag.core.designsystem.component.LisdataOutlinedButton
import com.lisdatasolutions.microrag.core.designsystem.component.LisdataOverflowMenu
import com.lisdatasolutions.microrag.core.designsystem.component.LisdataSectionHeader
import com.lisdatasolutions.microrag.core.designsystem.component.LisdataSourceCitation
import com.lisdatasolutions.microrag.core.designsystem.component.LisdataTopBar
import com.lisdatasolutions.microrag.core.designsystem.icon.LisdataIcons
import com.lisdatasolutions.microrag.core.designsystem.model.AnswerBlock
import com.lisdatasolutions.microrag.core.designsystem.model.AnswerContent
import com.lisdatasolutions.microrag.core.designsystem.model.AnswerSpan
import com.lisdatasolutions.microrag.core.designsystem.model.AnswerState
import com.lisdatasolutions.microrag.core.designsystem.theme.LisdataTheme
import com.lisdatasolutions.microrag.domain.chat.ChatAvailability
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel

/** User actions of the chat; defaults keep previews and tests short. */
data class ChatActions(
    val onBack: () -> Unit = {},
    val onQuestionChange: (String) -> Unit = {},
    val onSend: () -> Unit = {},
    val onStop: () -> Unit = {},
    val onRetry: (Long) -> Unit = {},
    val onCopy: (Long, String) -> Unit = { _, _ -> },
    val onOpenSource: (SourceTarget) -> Unit = {},
    val onNewChat: () -> Unit = {},
    val onDelete: () -> Unit = {},
    val onConfirmDelete: () -> Unit = {},
    val onDismissDelete: () -> Unit = {},
    val onCompleteSetup: () -> Unit = {},
    val onImport: () -> Unit = {},
    val onOpenIndex: () -> Unit = {},
    val onRetryAvailability: () -> Unit = {},
)

/**
 * The chat tab (F11): the conversation, its answers with their sources, and the composer. [listState]
 * lives in the route's entry, so the position survives a visit to a source (SC-006).
 */
@Composable
fun ChatRoute(
    onBack: () -> Unit,
    onOpenSource: (documentId: Long, chunkId: Long) -> Unit,
    onCompleteSetup: () -> Unit,
    onImport: () -> Unit,
    onOpenIndex: () -> Unit,
    viewModel: ChatViewModel = koinViewModel(),
    listState: LazyListState = rememberLazyListState(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val clipboard = LocalClipboard.current
    val clipLabel = stringResource(R.string.chat_clip_label)
    LaunchedEffect(viewModel) {
        viewModel.eventFlow.collect { event ->
            when (event) {
                is ChatEvent.OpenSource -> onOpenSource(event.target.documentId, event.target.chunkId)
                is ChatEvent.Copy -> clipboard.setClipEntry(ClipEntry(ClipData.newPlainText(clipLabel, event.text)))
            }
        }
    }
    // Gemma is released when the app is hidden (constitution IV): back on screen, it is loaded again if ready.
    LifecycleStartEffect(viewModel) {
        viewModel.onStart()
        onStopOrDispose { }
    }
    ChatScreen(
        state = state,
        actions = ChatActions(
            onBack = onBack,
            onQuestionChange = viewModel::onQuestionChange,
            onSend = viewModel::onSend,
            onStop = viewModel::onStop,
            onRetry = viewModel::onRetry,
            onCopy = viewModel::onCopy,
            onOpenSource = viewModel::onOpenSource,
            onNewChat = viewModel::onNewChat,
            onDelete = viewModel::onDelete,
            onConfirmDelete = viewModel::onConfirmDelete,
            onDismissDelete = viewModel::onDismissDelete,
            onCompleteSetup = onCompleteSetup,
            onImport = onImport,
            onOpenIndex = onOpenIndex,
            onRetryAvailability = viewModel::onRetryAvailability,
        ),
        listState = listState,
    )
}

/** PNG 04 (contracts/chat-ui.md §1): top bar, messages, notice, composer above the keyboard. */
@Composable
fun ChatScreen(
    state: ChatUiState,
    actions: ChatActions,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
) {
    val colors = LisdataTheme.colors
    val spacing = LisdataTheme.spacing
    var follow by rememberSaveable { mutableStateOf(true) }
    BoxWithConstraints(modifier = modifier.fillMaxSize().background(colors.background)) {
        // T064: a phone in landscape leaves little height between the bars; the availability then takes less.
        val compact = maxHeight < COMPACT_HEIGHT
        ChatColumn(state, actions, listState, compact, follow) { follow = it }
    }
    if (state.confirmDelete) {
        LisdataConfirmationDialog(
            title = stringResource(R.string.chat_delete_title),
            body = stringResource(R.string.chat_delete_body),
            confirmText = stringResource(R.string.chat_delete_confirm),
            onConfirm = actions.onConfirmDelete,
            onDismiss = actions.onDismissDelete,
        )
    }
}

@Composable
private fun ChatColumn(
    state: ChatUiState,
    actions: ChatActions,
    listState: LazyListState,
    compact: Boolean,
    follow: Boolean,
    setFollow: (Boolean) -> Unit,
) {
    val spacing = LisdataTheme.spacing
    val availability = state.availability
    val notice = availability.takeIf { state.turns.isNotEmpty() && it != null && it !is ChatAvailability.Ready }
    Column(modifier = Modifier.fillMaxSize().imePadding()) {
        LisdataTopBar(
            title = stringResource(R.string.chat_title),
            onBack = actions.onBack,
            subtitle = { Subtitle(state.cpuMode) },
            actions = {
                val items = buildList {
                    add(stringResource(R.string.chat_new) to actions.onNewChat)
                    if (state.conversationId != null) add(stringResource(R.string.chat_delete) to actions.onDelete)
                }
                LisdataOverflowMenu(items)
            },
        )
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            if (state.turns.isEmpty()) {
                EmptyChat(availability, actions, compact)
            } else {
                // Short of height, the notice is the list's last item, so it does not cover the conversation.
                val footer: (@Composable () -> Unit)? = notice?.takeIf { compact }?.let { { Notice(it, actions, Modifier) } }
                Messages(state, actions, listState, follow, setFollow, footer)
            }
        }
        if (notice != null && !compact) {
            Notice(notice, actions, Modifier.padding(horizontal = spacing.screenPaddingHorizontal, vertical = spacing.space2))
        }
        LisdataChatComposer(
            value = state.question,
            onValueChange = actions.onQuestionChange,
            onSend = {
                setFollow(true)
                actions.onSend()
            },
            placeholder = stringResource(R.string.chat_composer_placeholder),
            generating = state.answering,
            onStop = actions.onStop,
            enabled = state.ready,
            modifier = Modifier.padding(horizontal = spacing.screenPaddingHorizontal, vertical = spacing.space3),
        )
        Announcement(state.announcement)
    }
}

@Composable
private fun Subtitle(cpuMode: Boolean) {
    val colors = LisdataTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(if (cpuMode) colors.warning else colors.success))
        Text(
            text = stringResource(if (cpuMode) R.string.chat_subtitle_cpu else R.string.chat_subtitle_local),
            style = LisdataTheme.typography.material.labelLarge,
            color = colors.textSecondary,
            modifier = Modifier.padding(start = LisdataTheme.spacing.space2),
        )
    }
}

/**
 * Turns as three items each (question, answer, sources), so the sources chip can scroll to its list, and
 * then [footer] if any. The list follows a growing answer only while the user is at its end (U7).
 */
@Composable
private fun Messages(
    state: ChatUiState,
    actions: ChatActions,
    listState: LazyListState,
    follow: Boolean,
    setFollow: (Boolean) -> Unit,
    footer: (@Composable () -> Unit)?,
) {
    val spacing = LisdataTheme.spacing
    val scope = rememberCoroutineScope()
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress to listState.canScrollForward }.collect { (scrolling, canScrollForward) ->
            if (scrolling) setFollow(!canScrollForward)
        }
    }
    val last = state.turns.last()
    val progress = "${state.turns.size}:${last.id}:${last.state}:${last.content.length()}"
    // A conversation opens at its latest answer. Saved, so coming back from a source or recreating does not
    // jump to the end again (SC-006).
    var seen by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(progress) {
        val end = state.turns.size * ITEMS_PER_TURN - 1
        when {
            !(seen == null || (seen != progress && follow)) -> Unit
            // The notice reads from its title; the conversation is above it.
            footer != null -> listState.scrollToItem(end + 1)
            else -> listState.scrollToItem(end, Int.MAX_VALUE)
        }
        seen = progress
    }
    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(horizontal = spacing.screenPaddingHorizontal, vertical = spacing.space4),
        verticalArrangement = Arrangement.spacedBy(spacing.listGap),
    ) {
        state.turns.forEachIndexed { index, turn ->
            item(key = "q${turn.id}") {
                LisdataChatBubble(turn.question, description = stringResource(R.string.chat_you_sentence, turn.question))
            }
            item(key = "a${turn.id}") {
                Answer(turn, state, actions) {
                    scope.launch { listState.animateScrollToItem(index * ITEMS_PER_TURN + 2) }
                }
            }
            item(key = "s${turn.id}") { Sources(turn, actions) }
        }
        if (footer != null) item(key = "notice") { footer() }
    }
}

@Composable
private fun Answer(turn: TurnUi, state: ChatUiState, actions: ChatActions, onSources: () -> Unit) {
    val content = if (turn.refusal) AnswerContent.plain(stringResource(R.string.chat_refusal)) else turn.content
    val sourceLines = turn.sources.map { row ->
        val page = row.citation.page
        if (page != null) stringResource(R.string.chat_copy_source, row.position, row.citation.documentName, page)
        else stringResource(R.string.chat_copy_source_no_page, row.position, row.citation.documentName)
    }
    val copyText = AnswerFormatter.copyText(content, sourceLines)
    val streaming = turn.state == AnswerState.Streaming
    LisdataAnswerCard(
        content = content,
        state = turn.state,
        onCopy = { actions.onCopy(turn.id, copyText) },
        statusText = when (turn.status) {
            TurnStatus.SEARCHING -> stringResource(R.string.chat_searching)
            TurnStatus.GENERATING -> stringResource(R.string.chat_generating)
            null -> null
        },
        sourcesCountText = turn.sources.size.takeIf { it > 0 && !streaming }?.let { pluralStringResource(R.plurals.chat_sources_count, it, it) },
        onSources = onSources,
        onRetry = if (turn.retryable) ({ actions.onRetry(turn.id) }) else null,
        retryEnabled = turn.canRetry,
        onCitation = { n -> turn.chipTargets.getOrNull(n - 1)?.let(actions.onOpenSource) },
        notes = turn.notes.map { stringResource(it) },
        failureText = turn.failureText?.let { stringResource(it) },
        copiedLabel = if (state.copiedTurnId == turn.id) stringResource(R.string.chat_copied) else null,
    )
}

@Composable
private fun Sources(turn: TurnUi, actions: ChatActions) {
    if (turn.sources.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(LisdataTheme.spacing.space2)) {
        LisdataSectionHeader(stringResource(R.string.chat_sources_header))
        turn.sources.forEach { row ->
            val details = buildList {
                if (!row.citation.available) {
                    add(stringResource(R.string.source_unavailable))
                } else {
                    row.citation.page?.let { add(stringResource(R.string.citation_page, it)) }
                    val fragment = row.citation.fragment
                    val lastFragment = row.citation.lastFragment
                    when {
                        fragment != null && lastFragment != null -> add(stringResource(R.string.citation_fragments, fragment, lastFragment))
                        fragment != null -> add(stringResource(R.string.citation_fragment, fragment))
                    }
                }
            }
            LisdataSourceCitation(
                citation = row.citation,
                onOpen = { actions.onOpenSource(row.target) },
                modifier = Modifier.fillMaxWidth(),
                description = stringResource(R.string.chat_source_sentence, row.position, (listOf(row.citation.documentName) + details).joinToString(", ")),
                onClickLabel = stringResource(R.string.chat_open_source),
            )
        }
    }
}

/**
 * Without turns, the availability fills the screen (contracts/chat-ui.md §2); Ready leaves it empty, as
 * the PNG. The state components bring their own margins.
 */
@Composable
private fun EmptyChat(availability: ChatAvailability?, actions: ChatActions, compact: Boolean) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        when (availability) {
            null, is ChatAvailability.Ready -> Unit
            // C17: retrying cannot open a damaged or newer file.
            ChatAvailability.StorageUnavailable -> LisdataErrorState(
                stringResource(R.string.chat_storage_unavailable), onRetry = null, compact = compact,
            )
            ChatAvailability.ModelsNotReady -> LisdataModelMissingBlock(
                title = stringResource(R.string.chat_models_title),
                body = stringResource(R.string.chat_models_body),
                actionText = stringResource(R.string.chat_models_action),
                onAction = actions.onCompleteSetup,
                compact = compact,
            )
            ChatAvailability.IndexUnavailable -> LisdataErrorState(
                stringResource(R.string.chat_index_failed), onRetry = actions.onRetryAvailability, compact = compact,
            )
            ChatAvailability.EmptyLibrary -> LisdataEmptyState(
                icon = LisdataIcons.Folder,
                title = stringResource(R.string.chat_empty_title),
                body = stringResource(R.string.chat_empty_body),
                action = { LisdataGradientButton(text = stringResource(R.string.chat_empty_action), onClick = actions.onImport) },
                compact = compact,
            )
            ChatAvailability.WaitingForIndexing -> LisdataEmptyState(
                icon = LisdataIcons.Layers,
                title = stringResource(R.string.chat_waiting_title),
                body = stringResource(R.string.chat_waiting_body),
                action = { LisdataOutlinedButton(text = stringResource(R.string.chat_view_indexing), onClick = actions.onOpenIndex) },
                compact = compact,
            )
            ChatAvailability.NothingReady -> LisdataEmptyState(
                icon = LisdataIcons.Layers,
                title = stringResource(R.string.chat_nothing_ready),
                body = null,
                action = { LisdataOutlinedButton(text = stringResource(R.string.chat_view_indexing), onClick = actions.onOpenIndex) },
                compact = compact,
            )
        }
    }
}

/** With turns, the availability is a notice above the disabled composer; the conversation stays. */
@Composable
private fun Notice(availability: ChatAvailability, actions: ChatActions, modifier: Modifier) {
    when (availability) {
        is ChatAvailability.Ready -> Unit
        ChatAvailability.StorageUnavailable -> InfoNotice(LisdataIcons.Warning, stringResource(R.string.chat_storage_unavailable), modifier)
        ChatAvailability.ModelsNotReady -> InfoNotice(
            LisdataIcons.Sparkle, stringResource(R.string.chat_models_title), modifier, body = stringResource(R.string.chat_models_body),
            actionText = stringResource(R.string.chat_models_action), onAction = actions.onCompleteSetup,
        )
        ChatAvailability.IndexUnavailable -> InfoNotice(
            LisdataIcons.Warning, stringResource(R.string.chat_index_failed), modifier,
            actionText = stringResource(R.string.action_retry), onAction = actions.onRetryAvailability,
        )
        ChatAvailability.EmptyLibrary -> InfoNotice(
            LisdataIcons.Folder, stringResource(R.string.chat_empty_title), modifier, body = stringResource(R.string.chat_empty_body),
            actionText = stringResource(R.string.chat_empty_action), onAction = actions.onImport,
        )
        ChatAvailability.WaitingForIndexing -> InfoNotice(
            LisdataIcons.Layers, stringResource(R.string.chat_waiting_title), modifier, body = stringResource(R.string.chat_waiting_body),
            actionText = stringResource(R.string.chat_view_indexing), onAction = actions.onOpenIndex,
        )
        ChatAvailability.NothingReady -> InfoNotice(
            LisdataIcons.Layers, stringResource(R.string.chat_nothing_ready), modifier,
            actionText = stringResource(R.string.chat_view_indexing), onAction = actions.onOpenIndex,
        )
    }
}

@Composable
private fun InfoNotice(
    @DrawableRes icon: Int,
    title: String,
    modifier: Modifier = Modifier,
    body: String? = null,
    actionText: String? = null,
    onAction: () -> Unit = {},
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(LisdataTheme.spacing.space2)) {
        LisdataInfoCard(icon = icon, title = title, body = body, modifier = Modifier.fillMaxWidth())
        if (actionText != null) LisdataOutlinedButton(text = actionText, onClick = onAction)
    }
}

/** One live region: each change of the answer is said once, never its text (FR-039, U2). */
@Composable
private fun Announcement(announcement: ChatAnnouncement?) {
    val text = when (announcement) {
        null -> return
        ChatAnnouncement.SEARCHING -> stringResource(R.string.chat_announce_searching)
        ChatAnnouncement.GENERATING -> stringResource(R.string.chat_announce_generating)
        ChatAnnouncement.COMPLETED -> stringResource(R.string.chat_announce_completed)
        ChatAnnouncement.CANCELLED -> stringResource(R.string.chat_announce_cancelled)
        ChatAnnouncement.INTERRUPTED -> stringResource(R.string.chat_announce_interrupted)
        ChatAnnouncement.FAILED -> stringResource(R.string.chat_announce_failed)
        ChatAnnouncement.DELETED -> stringResource(R.string.chat_announce_deleted)
        ChatAnnouncement.COPIED -> stringResource(R.string.chat_copied)
    }
    Box(
        modifier = Modifier.semantics {
            liveRegion = LiveRegionMode.Polite
            stateDescription = text
        },
    )
}

private const val ITEMS_PER_TURN = 3

/** Below this height (a phone in landscape) the availability is drawn compact. */
private val COMPACT_HEIGHT = 480.dp

/** Characters of a formatted answer, to notice it growing. */
private fun AnswerContent.length(): Int = blocks.sumOf { block ->
    val spans = when (block) {
        is AnswerBlock.Paragraph -> block.spans
        is AnswerBlock.Bullet -> block.spans
    }
    spans.sumOf { span ->
        when (span) {
            is AnswerSpan.Text -> span.text.length
            is AnswerSpan.Citation -> 1
        }
    }
}
