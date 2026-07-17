package com.hotelopai.assistant.api

import com.hotelopai.assistant.application.AssistantConversationService
import com.hotelopai.assistant.application.AssistantAttachmentRegistrationService
import com.hotelopai.shared.security.CurrentUserContextResolver
import com.hotelopai.shared.security.PermissionExpressions
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/assistant/conversations")
class AssistantConversationController(
    private val assistantConversationService: AssistantConversationService,
    private val assistantAttachmentRegistrationService: AssistantAttachmentRegistrationService,
    private val currentUserContextResolver: CurrentUserContextResolver
) {
    @PostMapping
    @PreAuthorize(PermissionExpressions.ASSISTANT_USE)
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
    @PreAuthorize(PermissionExpressions.ASSISTANT_USE)
    fun sendMessage(
        @PathVariable conversationId: String,
        @RequestBody request: SendAssistantMessageRequest
    ): AssistantConversationResponse {
        request.rejectUnsafeOwnershipAndMediaFields()
        val voiceTranscriptMetadata = request.voiceTranscript?.toDomain()
        val legacyTranscript = request.transcript?.trim()?.takeIf(String::isNotBlank)

        return currentUserContextResolver.current().let { currentUser ->
            val localMetadataAttachments = request.attachments.orEmpty().map { it.toDomain() }
            val registeredAttachments =
                assistantAttachmentRegistrationService.resolveMessageAttachmentReferences(
                    conversationId = conversationId,
                    hotelId = currentUser.hotelId.toString(),
                    userId = currentUser.userId.toString(),
                    attachmentIds = request.attachmentIds.orEmpty()
                )
            val attachments = localMetadataAttachments + registeredAttachments
            val imageObservations = request.imageObservations
                .orEmpty()
                .map { it.toDomain(attachments) }

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

    @PostMapping("/{conversationId}/attachments")
    @PreAuthorize(PermissionExpressions.ASSISTANT_ATTACHMENT_REGISTER)
    fun registerAttachment(
        @PathVariable conversationId: String,
        @RequestHeader(name = "Idempotency-Key", required = false) idempotencyKey: String?,
        @RequestBody request: RegisterAssistantAttachmentRequest
    ): RegisteredAssistantAttachmentResponse =
        currentUserContextResolver.current().let { currentUser ->
            RegisteredAssistantAttachmentResponse.from(
                assistantAttachmentRegistrationService.register(
                    conversationId = conversationId,
                    hotelId = currentUser.hotelId.toString(),
                    userId = currentUser.userId.toString(),
                    command = request.toCommand(),
                    idempotencyKey = idempotencyKey
                )
            )
        }

    @PostMapping("/{conversationId}/confirm")
    @PreAuthorize(PermissionExpressions.ASSISTANT_CONFIRM_TASK)
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
    @PreAuthorize(PermissionExpressions.ASSISTANT_USE)
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
