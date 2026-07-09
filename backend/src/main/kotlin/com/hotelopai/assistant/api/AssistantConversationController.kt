package com.hotelopai.assistant.api

import com.hotelopai.assistant.application.AssistantConversationService
import com.hotelopai.assistant.domain.ConversationAttachment
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/assistant/conversations")
class AssistantConversationController(
    private val assistantConversationService: AssistantConversationService
) {
    @PostMapping
    fun startConversation(
        @RequestBody request: StartConversationRequest
    ): AssistantConversationResponse =
        AssistantConversationResponse.from(
            assistantConversationService.startConversation(
                hotelId = request.hotelId,
                userId = request.userId
            )
        )

    @PostMapping("/{conversationId}/messages")
    fun sendMessage(
        @PathVariable conversationId: String,
        @RequestBody request: SendAssistantMessageRequest
    ): AssistantConversationResponse =
        AssistantConversationResponse.from(
            assistantConversationService.sendMessage(
                conversationId = conversationId,
                text = request.transcript ?: request.text,
                inputType = request.inputType,
                voiceTranscript = request.transcript,
                audioMetadata = request.audioMetadata?.toDomain(),
                attachments = request.attachments
                    ?.map { it.toDomain() }
                    .orEmpty()
                    .ifEmpty {
                        request.attachmentIds.orEmpty().map { attachmentId ->
                            ConversationAttachment(id = attachmentId)
                        }
                    }
            )
        )

    @PostMapping("/{conversationId}/confirm")
    fun confirmTask(
        @PathVariable conversationId: String,
        @RequestBody request: ConfirmTaskRequest
    ): AssistantConversationResponse =
        AssistantConversationResponse.from(
            assistantConversationService.confirmTask(
                conversationId = conversationId,
                idempotencyKey = request.idempotencyKey
            )
        )

    @PostMapping("/{conversationId}/reset")
    fun resetConversation(
        @PathVariable conversationId: String
    ): AssistantConversationResponse =
        AssistantConversationResponse.from(
            assistantConversationService.resetConversation(conversationId)
        )
}
