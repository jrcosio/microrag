package com.lisdatasolutions.microrag.core.designsystem.model

/** Who wrote a chat message. */
enum class ChatRole { User, Assistant }

/** Lifecycle of an assistant answer as rendered by the answer card. */
enum class AnswerState { Streaming, Completed, Cancelled, Failed }
