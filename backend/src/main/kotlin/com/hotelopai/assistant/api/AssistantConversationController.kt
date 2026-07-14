package com.hotelopai.assistant.api

import com.hotelopai.assistant.application.AssistantConversationService
import com.hotelopai.assistant.domain.ConversationAttachment
import com.hotelopai.shared.security.CurrentUserContextResolver
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/assistant/conversations")
class AssistantConversationController(
    private val assistantConversationService: AssistantConversationService,
    private val currentUserContextResolver: CurrentUserContextResolver
) {
    @PostMapping
    fun startConversation(
        @RequestBody request: StartConversationRequest
    ): AssistantConversationResponse =
        currentUserContextResolver.current().let { currentUser ->
            AssistantConversationResponse.from(
                assistantConversationService.startConversation(
                    hotelId = currentUser.hotelId.toString(),
                    userId = currentUser.userId.toString()
                )
            )
        }

    @PostMapping("/{conversationId}/messages")
    fun sendMessage(
        @PathVariable conversationId: String,
        @RequestBody request: SendAssistantMessageRequest
    ): AssistantConversationResponse {
        request.rejectUnsafeOwnershipAndMediaFields()
        val attachments = request.attachments
            ?.map { it.toDomain() }
            .orEmpty()
            .ifEmpty {
                request.attachmentIds.orEmpty().map { attachmentId ->
                    ConversationAttachment(
                        id = attachmentId.trim(),
                        originalFileName = attachmentId.trim(),
                        mimeType = "image/jpeg",
                        sizeBytes = 1
                    )
                }
            }
        val imageObservations = request.imageObservations
            .orEmpty()
            .map { it.toDomain(attachments) }
        val voiceTranscriptMetadata = request.voiceTranscript?.toDomain()
        val legacyTranscript = request.transcript?.trim()?.takeIf(String::isNotBlank)

        return currentUserContextResolver.current().let { currentUser ->
            AssistantConversationResponse.from(
                assistantConversationService.sendMessage(
                    conversationId = conversationId,
                    hotelId = currentUser.hotelId.toString(),
                    userId = currentUser.userId.toString(),
                    text = request.text,
                    inputType = request.inputType,
                    voiceTranscript = legacyTranscript,
                    voiceTranscriptMetadata = voiceTranscriptMetadata,
                    audioMetadata = request.audioMetadata?.toDomain(),
                    attachments = attachments,
                    imageObservations = imageObservations
                )
            )
        }
    }

    @PostMapping("/{conversationId}/confirm")
    fun confirmTask(
        @PathVariable conversationId: String,
        @RequestBody request: ConfirmTaskRequest
    ): AssistantConversationResponse =
        currentUserContextResolver.current().let { currentUser ->
            AssistantConversationResponse.from(
                assistantConversationService.confirmTask(
                    conversationId = conversationId,
                    hotelId = currentUser.hotelId.toString(),
                    userId = currentUser.userId.toString(),
                    idempotencyKey = request.idempotencyKey
                )
            )
        }

    @PostMapping("/{conversationId}/reset")
    fun resetConversation(
        @PathVariable conversationId: String
    ): AssistantConversationResponse =
        currentUserContextResolver.current().let { currentUser ->
            AssistantConversationResponse.from(
                assistantConversationService.resetConversation(
                    conversationId = conversationId,
                    hotelId = currentUser.hotelId.toString(),
                    userId = currentUser.userId.toString()
                )
            )
        }
}
