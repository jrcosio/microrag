package com.lisdatasolutions.microrag.domain.chat

import com.lisdatasolutions.microrag.domain.indexing.IndexingState
import com.lisdatasolutions.microrag.domain.indexing.ObserveIndexingUseCase
import com.lisdatasolutions.microrag.domain.model.ComputeBackend
import com.lisdatasolutions.microrag.domain.model.LocalAiStatus
import com.lisdatasolutions.microrag.domain.onboarding.ObserveLocalAiUseCase
import com.lisdatasolutions.microrag.domain.rag.ChatLibraryState
import com.lisdatasolutions.microrag.domain.rag.IndexCompatibility
import com.lisdatasolutions.microrag.domain.rag.IndexUnavailableException
import com.lisdatasolutions.microrag.domain.rag.RagIndex
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.mapLatest

/**
 * Whether the chat can be used (F11 research R11), from the real state of the saved conversations, the
 * models, the indexing batch and the library. Nothing is emitted before every source has been looked at
 * (constitution V); [retries] reopens the index check after «Reintentar» (C8).
 */
class ObserveChatAvailabilityUseCase(
    private val chats: ChatRepository,
    private val observeLocalAi: ObserveLocalAiUseCase,
    private val engine: AnswerEngine,
    private val observeIndexing: ObserveIndexingUseCase,
    private val index: RagIndex,
) {
    operator fun invoke(retries: Flow<Int> = flowOf(0)): Flow<ChatAvailability> = flow {
        if (chats.open() != ChatStorageState.OK) {
            emit(ChatAvailability.StorageUnavailable)
            return@flow
        }
        val indexing = observeIndexing().filter { it != IndexingState.Loading }
        emitAll(combine(observeLocalAi(), engine.preparedBackend(), indexing, library(retries), ::decide))
    }.distinctUntilChanged()

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun library(retries: Flow<Int>): Flow<ChatLibraryState?> = retries.flatMapLatest {
        flow {
            if (index.open() != IndexCompatibility.Compatible) {
                emit(null)
                return@flow
            }
            emitAll(index.documentChanges.mapLatest { index.chatLibraryState() })
        }.catch { error -> if (error is IndexUnavailableException) emit(null) else throw error }
    }

    /** Precedence of research R11; a null [library] is an index that cannot be read. */
    private fun decide(ai: LocalAiStatus, backend: ComputeBackend?, indexing: IndexingState, library: ChatLibraryState?): ChatAvailability =
        when {
            ai != LocalAiStatus.READY || backend == null -> ChatAvailability.ModelsNotReady
            library == null -> ChatAvailability.IndexUnavailable
            library.documents == 0 -> ChatAvailability.EmptyLibrary
            indexing is IndexingState.Active && indexing.pause == null -> ChatAvailability.WaitingForIndexing
            library.searchable == 0 -> ChatAvailability.NothingReady
            else -> ChatAvailability.Ready(cpuMode = backend == ComputeBackend.CPU, partialCoverage = library.pendingIncluded > 0)
        }
}
