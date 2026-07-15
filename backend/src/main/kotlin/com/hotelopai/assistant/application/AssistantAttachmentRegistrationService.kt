package com.hotelopai.assistant.application

import com.hotelopai.assistant.domain.AttachmentStorageStatus
import com.hotelopai.assistant.domain.AttachmentType
import com.hotelopai.assistant.domain.ConversationAttachment
import com.hotelopai.assistant.domain.RegisteredConversationAttachment
import com.hotelopai.shared.kernel.UuidV7Generator
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@Service
class AssistantAttachmentRegistrationService(
    private val conversationRepository: ConversationRepository,
    private val attachmentRepository: AssistantAttachmentRepository
) {
    @Transactional
    fun register(
        conversationId: String,
        hotelId: String,
        userId: String,
        command: RegisterAssistantAttachmentCommand
    ): RegisteredConversationAttachment {
        ensureConversationOwned(conversationId, hotelId, userId)

        val now = Instant.now()
        val attachment = RegisteredConversationAttachment(
            id = UuidV7Generator.generate(now),
            conversationId = conversationId,
            hotelId = hotelId,
            userId = userId,
            type = command.type,
            originalFileName = command.originalFileName,
            declaredMimeType = command.mimeType,
            declaredSizeBytes = command.sizeBytes,
            widthPx = command.widthPx,
            heightPx = command.heightPx,
            storageStatus = AttachmentStorageStatus.REGISTERED,
            storageReference = null,
            createdAt = now,
            updatedAt = now
        )

        return attachmentRepository.save(attachment)
    }

    @Transactional(readOnly = true)
    fun resolveMessageAttachmentReferences(
        conversationId: String,
        hotelId: String,
        userId: String,
        attachmentIds: List<String>
    ): List<ConversationAttachment> {
        if (attachmentIds.isEmpty()) {
            return emptyList()
        }

        ensureConversationOwned(conversationId, hotelId, userId)

        return attachmentIds.map { rawId ->
            val attachmentId = rawId.trim().also {
                require(it.isNotBlank()) { "attachmentId is required" }
            }
            val uuid = try {
                UUID.fromString(attachmentId)
            } catch (_: IllegalArgumentException) {
                throw IllegalArgumentException("attachmentId must be a server-generated UUID")
            }

            attachmentRepository.findByIdAndConversationIdAndHotelIdAndUserId(
                id = uuid,
                conversationId = conversationId,
                hotelId = hotelId,
                userId = userId
            )?.toMessageAttachment()
                ?: throw IllegalArgumentException("attachmentId does not belong to this conversation")
        }
    }

    private fun ensureConversationOwned(conversationId: String, hotelId: String, userId: String) {
        conversationRepository.findByIdAndHotelIdAndUserId(conversationId, hotelId, userId)
            ?: throw ConversationNotFoundException(conversationId)
    }
}

data class RegisterAssistantAttachmentCommand(
    val type: AttachmentType,
    val originalFileName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val widthPx: Int? = null,
    val heightPx: Int? = null
)
