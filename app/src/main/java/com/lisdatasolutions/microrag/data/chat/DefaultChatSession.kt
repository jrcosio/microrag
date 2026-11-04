package com.lisdatasolutions.microrag.data.chat

import com.lisdatasolutions.microrag.core.logging.AppLogger
import com.lisdatasolutions.microrag.domain.chat.ActiveAnswer
import com.lisdatasolutions.microrag.domain.chat.AnswerEngine
import com.lisdatasolutions.microrag.domain.chat.AnswerEvent
import com.lisdatasolutions.microrag.domain.chat.AnswerSource
import com.lisdatasolutions.microrag.domain.chat.ChatAvailability
import com.lisdatasolutions.microrag.domain.chat.ChatLimits
import com.lisdatasolutions.microrag.domain.chat.ChatRepository
import com.lisdatasolutions.microrag.domain.chat.ChatScope
import com.lisdatasolutions.microrag.domain.chat.ChatSession
import com.lisdatasolutions.microrag.domain.chat.ChatStorageException
import com.lisdatasolutions.microrag.domain.chat.ChatTurn
import com.lisdatasolutions.microrag.domain.chat.PromptBlockRef
import com.lisdatasolutions.microrag.domain.chat.ResolvedScope
import com.lisdatasolutions.microrag.domain.chat.TurnFailure
import com.lisdatasolutions.microrag.domain.chat.TurnState
import com.lisdatasolutions.microrag.domain.chat.TurnUpdate
import com.lisdatasolutions.microrag.domain.chat.toSearchScope
import com.lisdatasolutions.microrag.domain.rag.RagParameters
import com.lisdatasolutions.microrag.domain.rag.SearchScope
import com.lisdatasolutions.microrag.domain.retrieval.ContextBlock
import com.lisdatasolutions.microrag.domain.retrieval.RetrievalOutcome
import com.lisdatasolutions.microrag.domain.retrieval.RetrieveContextUseCase
import com.lisdatasolutions.microrag.rag.answer.AnswerReading
import com.lisdatasolutions.microrag.rag.answer.PromptBuilder
import com.lisdatasolutions.microrag.rag.runtime.MemoryTrimListener
import java.util.Locale
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Answers the chat's questions, one at a time in the whole app (F11 research R6–R10, contract rules
 * A1–A15). It runs in the application scope, so an answer outlives the screen that asked it; the text
 * stays in memory ([active]) and reaches `chat.db` at most once a second and when the answer ends.
 * Logs one line of numbers per answer, never text (constitution I).
 */
class DefaultChatSession(
    private val repository: ChatRepository,
    private val retrieve: RetrieveContextUseCase,
    private val engine: AnswerEngine,
    /** F17: whether the chat can answer within a scope (contract S2b). */
    private val availability: (SearchScope) -> Flow<ChatAvailability>,
    private val scope: CoroutineScope,
    private val logger: AppLogger,
    /** Wall time in milliseconds, for the timestamps and the checkpoints. */
    private val now: () -> Long = System::currentTimeMillis,
    /** F17 S1: a saved scope as it means now; the scope as it is without an index to check it against. */
    private val resolveScope: suspend (ChatScope) -> ResolvedScope = { ResolvedScope(it) },
) : ChatSession, MemoryTrimListener {

    private val current = MutableStateFlow<ActiveAnswer?>(null)
    override val active: StateFlow<ActiveAnswer?> = current.asStateFlow()

    private val lock = Mutex()
    private var job: Job? = null

    private val cleared = MutableStateFlow(0L)
    override val historyCleared: StateFlow<Long> = cleared.asStateFlow()

    private val deleted = MutableSharedFlow<Long>(extraBufferCapacity = DELETIONS_BUFFER)
    override val deletions: SharedFlow<Long> = deleted

    /** How the answer in progress ends when its job is cancelled: by the user, or by the system (C13). */
    @Volatile
    private var stopAs = TurnState.CANCELLED

    // A12: a new process has no answer in progress, so whatever a dead one left half done is interrupted.
    private val startup: Job = scope.launch {
        try {
            repository.markInterrupted(now())
        } catch (e: ChatStorageException) {
            logger.w(TAG, e) { "chat startup: storage unavailable" }
        }
    }

    override suspend fun ask(conversationId: Long?, question: String, scope: ChatScope): Long? = lock.withLock {
        if (question.isBlank() || question.length > ChatLimits.MAX_QUESTION_CHARS) return@withLock null
        if (job?.isActive == true) return@withLock null
        awaitStopped()
        val resolved = resolveScope(scope).scope
        val search = resolved.toSearchScope()
        val ready = availability(search).first() as? ChatAvailability.Ready ?: return@withLock null
        startup.join()
        // A conversation that could not be saved (C16) is tried again with the next question, and so is one
        // deleted meanwhile (F12 P10); an unreadable file keeps the id, as the write will fail anyway.
        val saved = conversationId?.takeIf { it != ActiveAnswer.UNSAVED && saveOrNull { repository.conversationExists(it) } != false }
        // F17 S2: a new conversation is saved with its scope; an existing one keeps the scope it is asked with.
        val conversation = saved ?: saveOrNull { repository.createConversation(now(), resolved) } ?: ActiveAnswer.UNSAVED
        if (saved != null && saveOrNull { repository.conversationScope(saved) } != resolved) {
            saveOrNull { repository.setConversationScope(saved, resolved) }
        }
        saveOrNull { repository.setLastScope(resolved) }
        val turns = if (conversation == ActiveAnswer.UNSAVED) emptyList() else saveOrNull { repository.conversation(conversation).first() }.orEmpty()
        val turn = if (conversation == ActiveAnswer.UNSAVED) null else saveOrNull {
            repository.insertTurn(conversation, question, ready.partialCoverage, now())
        }
        start(conversation, turn?.id ?: ActiveAnswer.UNSAVED, question, previousTurn(turns), ready.partialCoverage, search)
        conversation
    }

    override fun cancel() {
        stop(TurnState.CANCELLED)
    }

    override suspend fun retry(turnId: Long): Boolean = lock.withLock {
        if (job?.isActive == true) return@withLock false
        awaitStopped()
        val target = saveOrNull { repository.turn(turnId) } ?: return@withLock false
        if (target.state != TurnState.FAILED && target.state != TurnState.INTERRUPTED) return@withLock false
        // H3: within its conversation's scope, as it means now.
        val search = resolve(target.conversationId).scope.toSearchScope()
        if (availability(search).first() !is ChatAvailability.Ready) return@withLock false
        val before = saveOrNull { repository.conversation(target.conversationId).first() }.orEmpty().filter { it.ordinal < target.ordinal }
        if (saveOrNull { repository.resetForRetry(turnId) } == null) return@withLock false
        start(target.conversationId, target.id, target.question, previousTurn(before), target.partialCoverage, search)
        true
    }

    /** Throws ChatStorageException: the selector says the scope could not be saved. */
    override suspend fun setScope(conversationId: Long, scope: ChatScope) {
        repository.setConversationScope(conversationId, scope)
        repository.setLastScope(scope)
        logger.i(TAG) { "chat scope set ${scope.counts()}" }
    }

    override suspend fun scopeOf(conversationId: Long): ResolvedScope = resolve(conversationId)

    override suspend fun lastScope(): ResolvedScope {
        val saved = saveOrNull { repository.lastScope() } ?: ChatScope.WholeLibrary
        val resolved = resolveScope(saved)
        if (resolved.changed) saveOrNull { repository.setLastScope(resolved.scope) }
        return resolved
    }

    override suspend fun setLastScope(scope: ChatScope) {
        repository.setLastScope(scope)
    }

    /** M8: what fell out of a saved scope is written back once, so its notice is said once. */
    private suspend fun resolve(conversationId: Long): ResolvedScope {
        val saved = saveOrNull { repository.conversationScope(conversationId) } ?: ChatScope.WholeLibrary
        val resolved = resolveScope(saved)
        if (resolved.changed) {
            saveOrNull { repository.setConversationScope(conversationId, resolved.scope) }
            if (saveOrNull { repository.lastScope() } == saved) saveOrNull { repository.setLastScope(resolved.scope) }
            logger.i(TAG) { "chat scope adjusted folders=-${resolved.droppedFolders} documents=-${resolved.droppedDocuments}" }
        }
        return resolved
    }

    /** S5: counts only, never ids of what the user named. */
    private fun ChatScope.counts(): String = when (this) {
        ChatScope.WholeLibrary -> "whole=true"
        is ChatScope.Some -> "folders=${folders.size} general=$general documents=${documents.size}"
    }

    override fun startNewConversation() {
        stop(TurnState.CANCELLED)
    }

    override suspend fun openConversation(conversationId: Long) = lock.withLock {
        if (current.value?.let { it.conversationId != conversationId } == true) {
            stop(TurnState.CANCELLED)
            job?.join()
        }
    }

    override suspend fun delete(conversationId: Long) {
        lock.withLock {
            if (current.value?.conversationId == conversationId) {
                stop(TurnState.CANCELLED)
                job?.join()
            }
            try {
                repository.deleteConversation(conversationId)
            } catch (e: ChatStorageException) {
                logger.w(TAG) { "chat delete outcome=failed" }
                throw e
            }
            current.update { if (it?.conversationId == conversationId) null else it }
            logger.i(TAG) { "chat delete outcome=ok" }
        }
        // Outside the lock: a slow collector never holds the session (F13 analyze AN-10).
        deleted.emit(conversationId)
    }

    /**
     * F12 P9: under the session lock, so no question starts between the stop and the deletion. A failed
     * deletion leaves the counter as it was.
     */
    override suspend fun deleteAll() = lock.withLock {
        stop(TurnState.CANCELLED)
        job?.join()
        repository.deleteAll()
        current.value = null
        cleared.update { it + 1 }
    }

    /** A11: the UI is hidden or memory runs low; the answer stops before Gemma is released. */
    override suspend fun onUiHidden() {
        stop(TurnState.INTERRUPTED)
        job?.join()
    }

    private fun stop(state: TurnState) {
        val running = job ?: return
        if (!running.isActive) return
        stopAs = state
        running.cancel()
    }

    /**
     * F13 Q14: a cancelled answer stops being active at once but still saves its end. The next one starts
     * after it, so the old end never clears the new answer.
     */
    private suspend fun awaitStopped() {
        job?.takeIf { !it.isCompleted }?.join()
    }

    /** A4 (C12 as the desktop): the last turn before the asked one counts only when completed, refusal included. */
    private fun previousTurn(turns: List<ChatTurn>): ChatTurn? = turns.lastOrNull()?.takeIf { it.state == TurnState.COMPLETED }

    private fun start(
        conversationId: Long,
        turnId: Long,
        question: String,
        previous: ChatTurn?,
        partialCoverage: Boolean,
        search: SearchScope,
    ) {
        stopAs = TurnState.CANCELLED
        current.value = ActiveAnswer(
            conversationId = conversationId, turnId = turnId, question = question, state = TurnState.RETRIEVING,
            saveFailed = turnId == ActiveAnswer.UNSAVED, partialCoverage = partialCoverage,
        )
        job = scope.launch { answer(turnId, question, previous, search) }
    }

    /** FR-019: [search] was fixed when the question started; a scope changed meanwhile is for the next one. */
    private suspend fun answer(turnId: Long, question: String, previous: ChatTurn?, search: SearchScope) {
        val started = now()
        val log = AnswerLog(followUp = previous != null)
        val text = StringBuilder()
        var refs = emptyList<PromptBlockRef>()
        try {
            val retrieval = retrieve(question, previous?.question, search)
            log.retrievalMs = now() - started
            val blocks = when (val outcome = retrieval.outcome) {
                is RetrievalOutcome.Blocks -> outcome
                RetrievalOutcome.NoResults -> return finishRefusalWithoutBlocks(turnId, log, started)
                is RetrievalOutcome.IndexUnavailable -> return fail(turnId, TurnFailure.INDEX, "", refs, log, started)
                else -> return fail(turnId, TurnFailure.GENERATION, "", refs, log, started)
            }
            val prompt = prompt(question, blocks, previous, log)
            refs = prompt.blocks.mapIndexed { i, block -> block.ref(i + 1) }
            log.blocks = refs.size
            current.update { it?.copy(state = TurnState.GENERATING, blocks = refs) }
            save(turnId) { repository.updateTurn(turnId, TurnUpdate(state = TurnState.GENERATING, promptBlocks = refs.size)) }
            var lastCheckpoint = now()
            engine.generate(prompt.text).collect { event ->
                when (event) {
                    is AnswerEvent.Token -> {
                        if (text.isEmpty()) log.ttftMs = now() - started
                        text.append(event.delta)
                        current.update { it?.copy(text = text.toString()) }
                        if (now() - lastCheckpoint >= ChatLimits.CHECKPOINT_MS) {
                            lastCheckpoint = now()
                            save(turnId) { repository.updateTurn(turnId, TurnUpdate(answer = text.toString())) }
                        }
                    }
                    is AnswerEvent.Finished -> log.decodeTokensPerSecond = event.decodeTokensPerSecond
                }
            }
            complete(turnId, text.toString(), refs, log, started)
        } catch (e: CancellationException) {
            withContext(NonCancellable) { finishStopped(turnId, text.toString(), refs, log, started) }
            throw e
        } catch (e: Exception) {
            withContext(NonCancellable) { fail(turnId, TurnFailure.GENERATION, text.toString(), refs, log, started, e) }
        }
    }

    private class Prompt(val text: String, val blocks: List<ContextBlock>)

    /** A6 (FR-015): within the limit, dropping the worst ranked blocks first, then the previous turn, never the last block. */
    private fun prompt(question: String, outcome: RetrievalOutcome.Blocks, previous: ChatTurn?, log: AnswerLog): Prompt {
        val rank = outcome.candidates.associate { it.chunkId to it.fusedRank }
        fun rankOf(block: ContextBlock) = block.chunkIds.minOfOrNull { rank[it] ?: Int.MAX_VALUE } ?: Int.MAX_VALUE
        var blocks = outcome.blocks
        var turn = previous?.let { it.question to it.answer }
        fun build() = PromptBuilder.build(question, blocks.map { PromptBuilder.Block(it.documentName, it.headingPath, it.page, it.text) }, turn)
        var text = build()
        while (PromptBuilder.estimatedTokens(text) > RagParameters.PROMPT_LIMIT_TOKENS) {
            when {
                blocks.size > 1 -> {
                    val worst = blocks.maxBy(::rankOf)
                    blocks = blocks - worst
                    log.droppedBlocks++
                }
                turn != null -> {
                    turn = null
                    log.droppedPrevious = true
                }
                else -> break
            }
            text = build()
        }
        log.promptTokens = PromptBuilder.estimatedTokens(text)
        return Prompt(text, blocks)
    }

    /** A8: the refusal, an answer with its cited sources, or an empty answer that failed. */
    private suspend fun complete(turnId: Long, text: String, refs: List<PromptBlockRef>, log: AnswerLog, started: Long) {
        if (text.isBlank()) return fail(turnId, TurnFailure.GENERATION, text, refs, log, started)
        val refusal = AnswerReading.isRefusal(text)
        val sources = if (refusal) emptyList() else sources(text, refs)
        finish(turnId, TurnState.COMPLETED, text, sources, refusal, null, log, started)
    }

    private suspend fun finishRefusalWithoutBlocks(turnId: Long, log: AnswerLog, started: Long) =
        finish(turnId, TurnState.COMPLETED, PromptBuilder.REFUSAL, emptyList(), refusal = true, failure = null, log = log, started = started)

    /** A9 and A11: what arrived stays, with its valid citations. */
    private suspend fun finishStopped(turnId: Long, text: String, refs: List<PromptBlockRef>, log: AnswerLog, started: Long) =
        finish(turnId, stopAs, text, sources(text, refs), refusal = false, failure = null, log = log, started = started)

    private suspend fun fail(
        turnId: Long, failure: TurnFailure, text: String, refs: List<PromptBlockRef>, log: AnswerLog, started: Long, error: Throwable? = null,
    ) = finish(turnId, TurnState.FAILED, text, sources(text, refs), refusal = false, failure = failure, log = log, started = started, error = error)

    private suspend fun finish(
        turnId: Long,
        state: TurnState,
        text: String,
        sources: List<AnswerSource>,
        refusal: Boolean,
        failure: TurnFailure?,
        log: AnswerLog,
        started: Long,
        error: Throwable? = null,
    ) {
        var saved = save(turnId) {
            repository.updateTurn(turnId, TurnUpdate(state = state, answer = text, failure = failure, refusal = refusal, finishedAt = now()))
        }
        if (saved) saved = save(turnId) { repository.replaceSources(turnId, sources) }
        current.update { answer ->
            when {
                // F13 Q14: another answer may already be the current one; this end is not its own.
                answer == null || answer.turnId != turnId -> answer
                saved && !answer.saveFailed -> null
                // C16: an answer that could not be saved stays in memory, finished, until the next question.
                else -> answer.copy(state = state, text = text, saveFailed = true, refusal = refusal, failure = failure)
            }
        }
        log.write(state, failure, refusal, sources.size, now() - started, error)
    }

    /** A15: a failed write leaves the answer in memory marked as unsaved; the answer itself goes on. */
    private suspend fun save(turnId: Long, write: suspend () -> Unit): Boolean {
        if (turnId == ActiveAnswer.UNSAVED || current.value?.saveFailed == true) return false
        return try {
            write()
            true
        } catch (e: ChatStorageException) {
            current.update { it?.copy(saveFailed = true) }
            false
        }
    }

    private suspend fun <T> saveOrNull(read: suspend () -> T): T? = try {
        read()
    } catch (e: ChatStorageException) {
        null
    }

    private fun sources(text: String, refs: List<PromptBlockRef>): List<AnswerSource> {
        val byNumber = refs.associateBy { it.promptNumber }
        return AnswerReading.citedOrder(text, refs.size).mapIndexed { i, n ->
            val ref = byNumber.getValue(n)
            AnswerSource(
                position = i + 1, promptNumber = n, documentId = ref.documentId, chunkId = ref.chunkId,
                firstOrdinal = ref.firstOrdinal, lastOrdinal = ref.lastOrdinal, documentName = ref.documentName, page = ref.page,
            )
        }
    }

    private fun ContextBlock.ref(number: Int) = PromptBlockRef(
        promptNumber = number, documentId = documentId, chunkId = chunkIds.first(), firstOrdinal = firstOrdinal,
        lastOrdinal = lastOrdinal, documentName = documentName, page = page,
    )

    /** The numbers of one answer (research R6); never its text. */
    private inner class AnswerLog(val followUp: Boolean) {
        var blocks = 0
        var promptTokens = 0
        var droppedBlocks = 0
        var droppedPrevious = false
        var retrievalMs = 0L
        var ttftMs: Long? = null
        var decodeTokensPerSecond: Double? = null

        fun write(state: TurnState, failure: TurnFailure?, refusal: Boolean, cited: Int, totalMs: Long, error: Throwable?) {
            val line = "chat answer outcome=${state.name.lowercase()}${failure?.let { "/${it.name.lowercase()}" }.orEmpty()} " +
                "follow_up=$followUp blocks=$blocks cited=$cited refusal=$refusal prompt_tokens_est=$promptTokens " +
                "dropped_blocks=$droppedBlocks dropped_previous=$droppedPrevious retrieval_ms=$retrievalMs " +
                "ttft_ms=${ttftMs ?: "-"} total_ms=$totalMs decode_tok_s=${decodeTokensPerSecond?.let { String.format(Locale.ROOT, "%.1f", it) } ?: "-"}" +
                (error?.let { " error=${it.javaClass.simpleName}" } ?: "")
            if (error == null) logger.i(TAG) { line } else logger.w(TAG) { line }
        }
    }

    private companion object {
        const val TAG = "MicroRAG"

        /** Deletions waiting for a slow collector; one per tap, so a few are plenty. */
        const val DELETIONS_BUFFER = 8
    }
}
