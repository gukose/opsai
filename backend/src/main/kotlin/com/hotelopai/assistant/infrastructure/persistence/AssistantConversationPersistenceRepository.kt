package com.hotelopai.assistant.infrastructure.persistence

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.hotelopai.assistant.application.ConversationRepository
import com.hotelopai.assistant.application.ConversationConcurrencyException
import com.hotelopai.assistant.application.TaskConfirmationRecord
import com.hotelopai.assistant.application.TaskConfirmationRepository
import com.hotelopai.assistant.domain.AudioMetadata
import com.hotelopai.assistant.domain.Conversation
import com.hotelopai.assistant.domain.ConversationAttachment
import com.hotelopai.assistant.domain.ConversationMessage
import com.hotelopai.assistant.domain.ConversationState
import com.hotelopai.assistant.domain.FollowUpQuestion
import com.hotelopai.assistant.domain.ImageObservation
import com.hotelopai.assistant.domain.InputType
import com.hotelopai.assistant.domain.IntentType
import com.hotelopai.assistant.domain.MessageRole
import com.hotelopai.assistant.domain.MissingField
import com.hotelopai.assistant.domain.TaskPreview
import com.hotelopai.assistant.domain.VoiceTranscriptMetadata
import com.hotelopai.shared.kernel.PersistenceInstant
import com.hotelopai.shared.kernel.UuidV7Generator
import org.springframework.dao.EmptyResultDataAccessException
import org.springframework.dao.DuplicateKeyException
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.sql.ResultSet
import java.sql.Timestamp

@Repository
@Transactional
class AssistantConversationPersistenceRepository(
    private val jdbcTemplate: NamedParameterJdbcTemplate,
    objectMapper: ObjectMapper
) : ConversationRepository {
    private val objectMapper = objectMapper.persistenceCopy()

    override fun save(conversation: Conversation): Conversation {
        val normalized = conversation.normalizedForPersistence()
        val updated = jdbcTemplate.update(
            """
            update assistant_conversation set
                hotel_id = :hotelId,
                user_id = :userId,
                state = :state,
                intent = :intent,
                collected_fields_json = cast(:collectedFieldsJson as jsonb),
                missing_fields_json = cast(:missingFieldsJson as jsonb),
                follow_up_question_json = cast(:followUpQuestionJson as jsonb),
                task_preview_json = cast(:taskPreviewJson as jsonb),
                messages_json = cast(:messagesJson as jsonb),
                active_draft_id = :activeDraftId,
                active_draft_source_message_ids_json = cast(:activeDraftSourceMessageIdsJson as jsonb),
                draft_version = :draftVersion,
                created_task_id = :createdTaskId,
                confirmation_idempotency_key = :confirmationIdempotencyKey,
                created_at = :createdAt,
                updated_at = :updatedAt,
                row_version = row_version + 1
            where id = :id
              and row_version = :rowVersion
            """.trimIndent(),
            normalized.toSqlParameters(objectMapper)
        )

        if (updated == 1) {
            return normalized.copy(rowVersion = normalized.rowVersion + 1)
        }

        val existing = findById(normalized.id)
        if (existing != null) {
            throw ConversationConcurrencyException("Conversation was modified by another request")
        }

        try {
            jdbcTemplate.update(
                """
                insert into assistant_conversation (
                    id,
                    hotel_id,
                    user_id,
                    state,
                    intent,
                    collected_fields_json,
                    missing_fields_json,
                    follow_up_question_json,
                    task_preview_json,
                    messages_json,
                    active_draft_id,
                    active_draft_source_message_ids_json,
                    draft_version,
                    created_task_id,
                    confirmation_idempotency_key,
                    row_version,
                    created_at,
                    updated_at
                ) values (
                    :id,
                    :hotelId,
                    :userId,
                    :state,
                    :intent,
                    cast(:collectedFieldsJson as jsonb),
                    cast(:missingFieldsJson as jsonb),
                    cast(:followUpQuestionJson as jsonb),
                    cast(:taskPreviewJson as jsonb),
                    cast(:messagesJson as jsonb),
                    :activeDraftId,
                    cast(:activeDraftSourceMessageIdsJson as jsonb),
                    :draftVersion,
                    :createdTaskId,
                    :confirmationIdempotencyKey,
                    0,
                    :createdAt,
                    :updatedAt
                )
                """.trimIndent(),
                normalized.copy(rowVersion = 0).toSqlParameters(objectMapper)
            )
        } catch (_: DuplicateKeyException) {
            throw ConversationConcurrencyException("Conversation was modified by another request")
        }

        return normalized.copy(rowVersion = 0)
    }

    private fun findByIdWithClause(id: String, lockForUpdate: Boolean): Conversation? =
        try {
            jdbcTemplate.queryForObject(
            """
            select
                id,
                hotel_id,
                user_id,
                state,
                intent,
                collected_fields_json,
                missing_fields_json,
                follow_up_question_json,
                task_preview_json,
                messages_json,
                active_draft_id,
                active_draft_source_message_ids_json,
                draft_version,
                created_task_id,
                confirmation_idempotency_key,
                row_version,
                created_at,
                updated_at
            from assistant_conversation
            where id = :id
            ${if (lockForUpdate) "for update" else ""}
            """.trimIndent(),
                mapOf("id" to id)
            ) { rs, _ -> rs.toConversation(objectMapper) }
        } catch (_: EmptyResultDataAccessException) {
            null
        }

    override fun findById(id: String): Conversation? =
        findByIdWithClause(id, lockForUpdate = false)

    override fun findByIdForUpdate(id: String): Conversation? =
        findByIdWithClause(id, lockForUpdate = true)

    private fun findByIdAndScopeWithClause(id: String, hotelId: String, userId: String, lockForUpdate: Boolean): Conversation? =
        try {
            jdbcTemplate.queryForObject(
                """
                select
                    id,
                    hotel_id,
                    user_id,
                    state,
                    intent,
                    collected_fields_json,
                    missing_fields_json,
                    follow_up_question_json,
                    task_preview_json,
                    messages_json,
                    active_draft_id,
                    active_draft_source_message_ids_json,
                    draft_version,
                    created_task_id,
                    confirmation_idempotency_key,
                    row_version,
                    created_at,
                    updated_at
                from assistant_conversation
                where id = :id
                  and hotel_id = :hotelId
                  and user_id = :userId
                ${if (lockForUpdate) "for update" else ""}
                """.trimIndent(),
                mapOf(
                    "id" to id,
                    "hotelId" to hotelId,
                    "userId" to userId
                )
            ) { rs, _ -> rs.toConversation(objectMapper) }
        } catch (_: EmptyResultDataAccessException) {
            null
        }

    override fun findByIdAndHotelIdAndUserId(id: String, hotelId: String, userId: String): Conversation? =
        findByIdAndScopeWithClause(id, hotelId, userId, lockForUpdate = false)

    override fun findByIdAndHotelIdAndUserIdForUpdate(id: String, hotelId: String, userId: String): Conversation? =
        findByIdAndScopeWithClause(id, hotelId, userId, lockForUpdate = true)
}

@Repository
@Transactional
class AssistantTaskConfirmationPersistenceRepository(
    private val jdbcTemplate: NamedParameterJdbcTemplate,
    objectMapper: ObjectMapper
) : TaskConfirmationRepository {
    private val objectMapper = objectMapper.persistenceCopy()

    override fun findByConversationIdAndIdempotencyKey(
        conversationId: String,
        idempotencyKey: String
    ): TaskConfirmationRecord? =
        try {
            jdbcTemplate.queryForObject(
                """
                select
                    conversation_id,
                    idempotency_key,
                    created_task_id,
                    draft_id,
                    draft_version,
                    preview_json,
                    created_at
                from assistant_task_confirmation
                where conversation_id = :conversationId
                  and idempotency_key = :idempotencyKey
                """.trimIndent(),
                mapOf(
                    "conversationId" to conversationId,
                    "idempotencyKey" to idempotencyKey
                )
            ) { rs, _ -> rs.toTaskConfirmationRecord(objectMapper) }
        } catch (_: EmptyResultDataAccessException) {
            null
        }

    override fun findByConversationIdAndDraftIdentity(
        conversationId: String,
        draftId: String,
        draftVersion: Int
    ): TaskConfirmationRecord? =
        try {
            jdbcTemplate.queryForObject(
                """
                select
                    conversation_id,
                    idempotency_key,
                    created_task_id,
                    draft_id,
                    draft_version,
                    preview_json,
                    created_at
                from assistant_task_confirmation
                where conversation_id = :conversationId
                  and draft_id = :draftId
                  and draft_version = :draftVersion
                """.trimIndent(),
                mapOf(
                    "conversationId" to conversationId,
                    "draftId" to draftId,
                    "draftVersion" to draftVersion
                )
            ) { rs, _ -> rs.toTaskConfirmationRecord(objectMapper) }
        } catch (_: EmptyResultDataAccessException) {
            null
        }

    override fun save(record: TaskConfirmationRecord): TaskConfirmationRecord {
        val normalized = record.normalizedForPersistence()
        jdbcTemplate.update(
            """
            insert into assistant_task_confirmation (
                id,
                conversation_id,
                idempotency_key,
                created_task_id,
                draft_id,
                draft_version,
                preview_json,
                created_at
            ) values (
                :id,
                :conversationId,
                :idempotencyKey,
                :createdTaskId,
                :draftId,
                :draftVersion,
                cast(:previewJson as jsonb),
                :createdAt
            )
            """.trimIndent(),
            MapSqlParameterSource()
                .addValue("id", UuidV7Generator.generate(normalized.createdAt))
                .addValue("conversationId", normalized.conversationId)
                .addValue("idempotencyKey", normalized.idempotencyKey)
                .addValue("createdTaskId", normalized.createdTaskId)
                .addValue("draftId", normalized.draftId)
                .addValue("draftVersion", normalized.draftVersion)
                .addValue("previewJson", objectMapper.writeValueAsString(normalized.preview))
                .addValue("createdAt", Timestamp.from(normalized.createdAt))
        )

        return normalized
    }
}

private val collectedFieldsType = object : TypeReference<Map<String, String>>() {}
private val missingFieldsType = object : TypeReference<List<MissingField>>() {}
private val messagesType = object : TypeReference<List<PersistedConversationMessage>>() {}
private val stringListType = object : TypeReference<List<String>>() {}

private fun ObjectMapper.persistenceCopy(): ObjectMapper =
    copy().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

private fun Conversation.toSqlParameters(objectMapper: ObjectMapper): MapSqlParameterSource =
    MapSqlParameterSource()
        .addValue("id", id)
        .addValue("hotelId", hotelId)
        .addValue("userId", userId)
        .addValue("state", state.name)
        .addValue("intent", intent.name)
        .addValue("collectedFieldsJson", objectMapper.writeValueAsString(collectedFields))
        .addValue("missingFieldsJson", objectMapper.writeValueAsString(missingFields))
        .addValue("followUpQuestionJson", followUpQuestion?.let(objectMapper::writeValueAsString))
        .addValue("taskPreviewJson", taskPreview?.let(objectMapper::writeValueAsString))
        .addValue("messagesJson", objectMapper.writeValueAsString(messages.map(PersistedConversationMessage::from)))
        .addValue("activeDraftId", activeDraftId)
        .addValue("activeDraftSourceMessageIdsJson", objectMapper.writeValueAsString(activeDraftSourceMessageIds))
        .addValue("draftVersion", draftVersion)
        .addValue("createdTaskId", createdTaskId)
        .addValue("confirmationIdempotencyKey", confirmationIdempotencyKey)
        .addValue("rowVersion", rowVersion)
        .addValue("createdAt", Timestamp.from(createdAt))
        .addValue("updatedAt", Timestamp.from(updatedAt))

private fun Conversation.normalizedForPersistence(): Conversation =
    copy(
        createdAt = PersistenceInstant.toPersistencePrecision(createdAt),
        updatedAt = PersistenceInstant.toPersistencePrecision(updatedAt)
    )

private fun TaskConfirmationRecord.normalizedForPersistence(): TaskConfirmationRecord =
    copy(createdAt = PersistenceInstant.toPersistencePrecision(createdAt))

private fun ResultSet.toConversation(objectMapper: ObjectMapper): Conversation =
    Conversation(
        id = getString("id"),
        hotelId = getString("hotel_id"),
        userId = getString("user_id"),
        state = ConversationState.valueOf(getString("state")),
        messages = objectMapper.readValue(getString("messages_json"), messagesType)
            .map(PersistedConversationMessage::toDomain),
        intent = IntentType.valueOf(getString("intent")),
        collectedFields = objectMapper.readValue(getString("collected_fields_json"), collectedFieldsType),
        missingFields = objectMapper.readValue(getString("missing_fields_json"), missingFieldsType),
        followUpQuestion = getString("follow_up_question_json")
            ?.let { objectMapper.readValue(it, FollowUpQuestion::class.java) },
        taskPreview = getString("task_preview_json")
            ?.let { objectMapper.readValue(it, TaskPreview::class.java) },
        activeDraftId = getString("active_draft_id"),
        activeDraftSourceMessageIds = objectMapper.readValue(
            getString("active_draft_source_message_ids_json") ?: "[]",
            stringListType
        ),
        draftVersion = getInt("draft_version"),
        createdTaskId = getString("created_task_id"),
        confirmationIdempotencyKey = getString("confirmation_idempotency_key"),
        rowVersion = getLong("row_version"),
        createdAt = getTimestamp("created_at").toInstant(),
        updatedAt = getTimestamp("updated_at").toInstant()
    )

private fun ResultSet.toTaskConfirmationRecord(objectMapper: ObjectMapper): TaskConfirmationRecord =
    TaskConfirmationRecord(
        conversationId = getString("conversation_id"),
        idempotencyKey = getString("idempotency_key"),
        createdTaskId = getString("created_task_id"),
        draftId = getString("draft_id"),
        draftVersion = getInt("draft_version"),
        preview = objectMapper.readValue(getString("preview_json"), TaskPreview::class.java),
        createdAt = getTimestamp("created_at").toInstant()
    )

private data class PersistedConversationMessage(
    val id: String,
    val role: MessageRole,
    val inputType: InputType,
    val text: String?,
    val voiceTranscript: String?,
    val voiceTranscriptMetadata: VoiceTranscriptMetadata? = null,
    val audioMetadata: AudioMetadata?,
    val attachments: List<ConversationAttachment>,
    val imageObservations: List<ImageObservation>,
    val createdAt: String
) {
    fun toDomain(): ConversationMessage =
        ConversationMessage(
            id = id,
            role = role,
            inputType = inputType,
            text = text,
            voiceTranscript = voiceTranscript,
            voiceTranscriptMetadata = voiceTranscriptMetadata,
            audioMetadata = audioMetadata,
            attachments = attachments,
            imageObservations = imageObservations,
            createdAt = java.time.Instant.parse(createdAt)
        )

    companion object {
        fun from(message: ConversationMessage): PersistedConversationMessage =
            PersistedConversationMessage(
                id = message.id,
                role = message.role,
                inputType = message.inputType,
                text = message.text,
                voiceTranscript = message.voiceTranscript,
                voiceTranscriptMetadata = message.voiceTranscriptMetadata,
                audioMetadata = message.audioMetadata,
                attachments = message.attachments,
                imageObservations = message.imageObservations,
                createdAt = message.createdAt.toString()
            )
    }
}
