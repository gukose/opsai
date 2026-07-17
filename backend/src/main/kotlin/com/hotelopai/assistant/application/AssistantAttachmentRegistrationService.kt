package com.hotelopai.assistant.application

import com.hotelopai.assistant.domain.AttachmentStorageStatus
import com.hotelopai.assistant.domain.AttachmentType
import com.hotelopai.assistant.domain.ConversationAttachment
import com.hotelopai.assistant.domain.RegisteredConversationAttachment
import com.hotelopai.observability.OperationalObservability
import com.hotelopai.shared.kernel.UuidV7Generator
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@Service
class AssistantAttachmentRegistrationService(
    private val conversationRepository: ConversationRepository,
    private val attachmentRepository: AssistantAttachmentRepository,
    private val observability: OperationalObservability = OperationalObservability.noop()
) {
    @Transactional
    fun register(
        conversationId: String,
        hotelId: String,
        userId: String,
        command: RegisterAssistantAttachmentCommand,
        idempotencyKey: String? = null
    ): RegisteredConversationAttachment {
        try {
            ensureConversationOwned(conversationId, hotelId, userId)
            val normalizedIdempotencyKey = normalizeIdempotencyKey(idempotencyKey)
            normalizedIdempotencyKey?.let { key ->
                attachmentRepository.findByRegistrationIdempotencyKey(
                    conversationId = conversationId,
                    hotelId = hotelId,
                    userId = userId,
                    registrationIdempotencyKey = key
                )?.let { existing ->
                    ensureSameRegistrationMetadata(existing, command)
                    recordRegistration("idempotent_reuse", "none")
                    return existing
                }
            }

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
                registrationIdempotencyKey = normalizedIdempotencyKey,
                createdAt = now,
                updatedAt = now
            )

            return try {
                attachmentRepository.save(attachment).also { saved ->
                    ensureSameRegistrationMetadata(saved, command)
                    recordRegistration("success", "none")
                }
            } catch (_: org.springframework.dao.DuplicateKeyException) {
                val existing = normalizedIdempotencyKey?.let { key ->
                    attachmentRepository.findByRegistrationIdempotencyKey(
                        conversationId = conversationId,
                        hotelId = hotelId,
                        userId = userId,
                        registrationIdempotencyKey = key
                    )
                } ?: throw AssistantAttachmentIdempotencyConflictException("Attachment registration conflict")
                ensureSameRegistrationMetadata(existing, command)
                recordRegistration("idempotent_reuse", "duplicate_key")
                existing
            }
        } catch (exception: AssistantAttachmentIdempotencyConflictException) {
            recordRegistration("metadata_conflict", "idempotency_metadata_mismatch")
            logger.warn("event=attachment_registration operation=register outcome=metadata_conflict reasonCode=idempotency_metadata_mismatch")
            throw exception
        } catch (exception: ConversationNotFoundException) {
            recordRegistration("not_found", "conversation_not_found")
            throw exception
        } catch (exception: IllegalArgumentException) {
            recordRegistration("validation_failure", "validation_failure")
            throw exception
        } catch (exception: RuntimeException) {
            recordRegistration("failure", "operation_failed")
            logger.warn("event=attachment_registration operation=register outcome=failure reasonCode=operation_failed")
            throw exception
        }
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

    private fun normalizeIdempotencyKey(value: String?): String? {
        val trimmed = value?.trim()?.takeIf(String::isNotBlank) ?: return null
        require(trimmed.length <= 160) { "Idempotency-Key must be 160 characters or fewer" }
        return trimmed
    }

    private fun ensureSameRegistrationMetadata(
        existing: RegisteredConversationAttachment,
        command: RegisterAssistantAttachmentCommand
    ) {
        if (
            existing.type != command.type ||
            existing.originalFileName != command.originalFileName ||
            existing.declaredMimeType != command.mimeType ||
            existing.declaredSizeBytes != command.sizeBytes ||
            existing.widthPx != command.widthPx ||
            existing.heightPx != command.heightPx
        ) {
            throw AssistantAttachmentIdempotencyConflictException(
                "Idempotency-Key was already used with different attachment metadata"
            )
        }
    }

    private fun recordRegistration(outcome: String, reasonCode: String) {
        observability.incrementCounter(
            "hotelopai.attachment.registration.total",
            "operation" to "register",
            "outcome" to outcome,
            "reason_code" to reasonCode
        )
    }

    companion object {
        private val logger = LoggerFactory.getLogger(AssistantAttachmentRegistrationService::class.java)
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
