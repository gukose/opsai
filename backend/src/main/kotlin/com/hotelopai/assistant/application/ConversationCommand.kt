package com.hotelopai.assistant.application

import com.hotelopai.assistant.domain.InputType
import com.hotelopai.assistant.domain.AudioMetadata
import com.hotelopai.assistant.domain.ConversationAttachment

data class ConversationCommand(
    val messageId: String,
    val text: String,
    val inputType: InputType = InputType.TEXT,
    val voiceTranscript: String? = null,
    val audioMetadata: AudioMetadata? = null,
    val attachments: List<ConversationAttachment> = emptyList()
)
