package com.lisdatasolutions.microrag.feature.chat

import androidx.lifecycle.SavedStateHandle
import com.lisdatasolutions.microrag.domain.chat.AskQuestionUseCase
import com.lisdatasolutions.microrag.domain.chat.CancelAnswerUseCase
import com.lisdatasolutions.microrag.domain.chat.DeleteConversationUseCase
import com.lisdatasolutions.microrag.domain.chat.LastConversationUseCase
import com.lisdatasolutions.microrag.domain.chat.ObserveConversationUseCase
import com.lisdatasolutions.microrag.domain.chat.ResolveSourcesUseCase
import com.lisdatasolutions.microrag.domain.chat.RetryAnswerUseCase
import com.lisdatasolutions.microrag.domain.chat.StartNewConversationUseCase
import com.lisdatasolutions.microrag.domain.chat.WarmUpAnswerEngineUseCase
import com.lisdatasolutions.microrag.testing.ChatTestGraph

/** A [ChatViewModel] over the fakes of [ChatTestGraph]; [sdkInt] picks who confirms a copy (research R14). */
internal fun ChatTestGraph.chatViewModel(savedState: SavedStateHandle, sdkInt: Int = 34) = ChatViewModel(
    savedState = savedState,
    observeAvailability = availability,
    observeConversation = ObserveConversationUseCase(chats, session),
    ask = AskQuestionUseCase(session),
    cancelAnswer = CancelAnswerUseCase(session),
    retryAnswer = RetryAnswerUseCase(session),
    startNewConversation = StartNewConversationUseCase(session),
    deleteConversation = DeleteConversationUseCase(session),
    lastConversation = LastConversationUseCase(chats),
    warmUp = WarmUpAnswerEngineUseCase(engine, availability),
    resolveSources = ResolveSourcesUseCase(index),
    sdkInt = sdkInt,
)
