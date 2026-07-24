package com.hotelopai.assistant.infrastructure.persistence

import com.hotelopai.assistant.application.AssistantAttachmentRepository
import com.hotelopai.assistant.domain.AttachmentStorageStatus
import com.hotelopai.assistant.domain.AttachmentType
import com.hotelopai.assistant.domain.RegisteredConversationAttachment
import com.hotelopai.shared.kernel.PersistenceInstant
import org.springframework.dao.EmptyResultDataAccessException
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.sql.ResultSet
import java.sql.Timestamp
import java.util.UUID

@Repository
@Transactional
class AssistantAttachmentPersistenceRepository(
    private val jdbcTemplate: NamedParameterJdbcTemplate
) : AssistantAttachmentRepository {
    override fun save(attachment: RegisteredConversationAttachment): RegisteredConversationAttachment {
        val normalized = attachment.normalizedForPersistence()
        val inserted = jdbcTemplate.update(
            """
            insert into assistant_attachment (
                id,
                conversation_id,
                hotel_id,
                user_id,
                type,
                original_file_name,
                declared_mime_type,
                declared_size_bytes,
                width_px,
                height_px,
                storage_status,
                storage_reference,
                registration_idempotency_key,
                created_at,
                updated_at
            ) values (
                :id,
                :conversationId,
                :hotelId,
                :userId,
                :type,
                :originalFileName,
                :declaredMimeType,
                :declaredSizeBytes,
                :widthPx,
                :heightPx,
                :storageStatus,
                :storageReference,
                :registrationIdempotencyKey,
                :createdAt,
                :updatedAt
            )
            on conflict (hotel_id, user_id, conversation_id, registration_idempotency_key)
                where registration_idempotency_key is not null
                do nothing
            """.trimIndent(),
            normalized.toSqlParameters()
        )

        if (inserted == 1) {
            return normalized
        }

        return requireNotNull(
            normalized.registrationIdempotencyKey
            ?.let {
                findByRegistrationIdempotencyKey(
                    conversationId = normalized.conversationId,
                    hotelId = normalized.hotelId,
                    userId = normalized.userId,
                    registrationIdempotencyKey = it
                )
            }
        ) { "Attachment registration conflict could not be resolved" }
    }

    @Transactional(readOnly = true)
    override fun findByIdAndConversationIdAndHotelIdAndUserId(
        id: UUID,
        conversationId: String,
        hotelId: String,
        userId: String
    ): RegisteredConversationAttachment? =
        try {
            jdbcTemplate.queryForObject(
                """
                select
                    id,
                    conversation_id,
                    hotel_id,
                    user_id,
                    type,
                    original_file_name,
                    declared_mime_type,
                    declared_size_bytes,
                    width_px,
                    height_px,
                    storage_status,
                    storage_reference,
                    registration_idempotency_key,
                    created_at,
                    updated_at
                from assistant_attachment
                where id = :id
                  and conversation_id = :conversationId
                  and hotel_id = :hotelId
                  and user_id = :userId
                """.trimIndent(),
                mapOf(
                    "id" to id,
                    "conversationId" to conversationId,
                    "hotelId" to hotelId,
                    "userId" to userId
                )
            ) { rs, _ -> rs.toRegisteredAttachment() }
        } catch (_: EmptyResultDataAccessException) {
            null
        }

    @Transactional(readOnly = true)
    override fun findByRegistrationIdempotencyKey(
        conversationId: String,
        hotelId: String,
        userId: String,
        registrationIdempotencyKey: String
    ): RegisteredConversationAttachment? =
        try {
            jdbcTemplate.queryForObject(
                """
                select
                    id,
                    conversation_id,
                    hotel_id,
                    user_id,
                    type,
                    original_file_name,
                    declared_mime_type,
                    declared_size_bytes,
                    width_px,
                    height_px,
                    storage_status,
                    storage_reference,
                    registration_idempotency_key,
                    created_at,
                    updated_at
                from assistant_attachment
                where conversation_id = :conversationId
                  and hotel_id = :hotelId
                  and user_id = :userId
                  and registration_idempotency_key = :registrationIdempotencyKey
                """.trimIndent(),
                mapOf(
                    "conversationId" to conversationId,
                    "hotelId" to hotelId,
                    "userId" to userId,
                    "registrationIdempotencyKey" to registrationIdempotencyKey
                )
            ) { rs, _ -> rs.toRegisteredAttachment() }
        } catch (_: EmptyResultDataAccessException) {
            null
        }
}

private fun RegisteredConversationAttachment.toSqlParameters(): MapSqlParameterSource =
    MapSqlParameterSource()
        .addValue("id", id)
        .addValue("conversationId", conversationId)
        .addValue("hotelId", hotelId)
        .addValue("userId", userId)
        .addValue("type", type.name)
        .addValue("originalFileName", originalFileName)
        .addValue("declaredMimeType", declaredMimeType)
        .addValue("declaredSizeBytes", declaredSizeBytes)
        .addValue("widthPx", widthPx)
        .addValue("heightPx", heightPx)
        .addValue("storageStatus", storageStatus.name)
        .addValue("storageReference", storageReference)
        .addValue("registrationIdempotencyKey", registrationIdempotencyKey)
        .addValue("createdAt", Timestamp.from(createdAt))
        .addValue("updatedAt", Timestamp.from(updatedAt))

private fun RegisteredConversationAttachment.normalizedForPersistence(): RegisteredConversationAttachment =
    copy(
        createdAt = PersistenceInstant.toPersistencePrecision(createdAt),
        updatedAt = PersistenceInstant.toPersistencePrecision(updatedAt)
    )

private fun ResultSet.toRegisteredAttachment(): RegisteredConversationAttachment =
    RegisteredConversationAttachment(
        id = getObject("id", UUID::class.java),
        conversationId = getString("conversation_id"),
        hotelId = getString("hotel_id"),
        userId = getString("user_id"),
        type = AttachmentType.valueOf(getString("type")),
        originalFileName = getString("original_file_name"),
        declaredMimeType = getString("declared_mime_type"),
        declaredSizeBytes = getLong("declared_size_bytes"),
        widthPx = getObject("width_px") as Int?,
        heightPx = getObject("height_px") as Int?,
        storageStatus = AttachmentStorageStatus.valueOf(getString("storage_status")),
        storageReference = getString("storage_reference"),
        registrationIdempotencyKey = getString("registration_idempotency_key"),
        createdAt = getTimestamp("created_at").toInstant(),
        updatedAt = getTimestamp("updated_at").toInstant()
    )
