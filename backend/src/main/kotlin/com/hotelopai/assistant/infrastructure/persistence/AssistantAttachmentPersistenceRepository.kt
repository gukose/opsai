package com.hotelopai.assistant.infrastructure.persistence

import com.hotelopai.assistant.application.AssistantAttachmentRepository
import com.hotelopai.assistant.domain.AttachmentStorageStatus
import com.hotelopai.assistant.domain.AttachmentType
import com.hotelopai.assistant.domain.RegisteredConversationAttachment
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
        jdbcTemplate.update(
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
                :createdAt,
                :updatedAt
            )
            """.trimIndent(),
            attachment.toSqlParameters()
        )

        return attachment
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
        .addValue("createdAt", Timestamp.from(createdAt))
        .addValue("updatedAt", Timestamp.from(updatedAt))

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
        createdAt = getTimestamp("created_at").toInstant(),
        updatedAt = getTimestamp("updated_at").toInstant()
    )
