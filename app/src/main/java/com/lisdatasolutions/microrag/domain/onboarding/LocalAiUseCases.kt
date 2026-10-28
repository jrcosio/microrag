package com.lisdatasolutions.microrag.domain.onboarding

import com.lisdatasolutions.microrag.data.models.ModelFileRepository
import com.lisdatasolutions.microrag.domain.model.LocalAiStatus
import com.lisdatasolutions.microrag.domain.model.ModelCatalog
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow

/**
 * The Home chip (F09 research R1): rescans the files against their verification record, without
 * hashing (F03 SC-007), and only then follows them. Nothing is emitted before that first check (H1).
 */
class ObserveLocalAiUseCase(private val repository: ModelFileRepository) {
    operator fun invoke(): Flow<LocalAiStatus> = flow {
        repository.refresh()
        emitAll(
            combine(repository.states, repository.preparedBackends) { states, prepared ->
                if (ModelCatalog.allReady(states, prepared)) LocalAiStatus.READY else LocalAiStatus.SETUP_PENDING
            },
        )
    }.distinctUntilChanged()
}

/** Back to the foreground (research R5): the same rescan; [ObserveLocalAiUseCase] emits any change. */
class RefreshLocalAiUseCase(private val repository: ModelFileRepository) {
    suspend operator fun invoke() = repository.refresh()
}
