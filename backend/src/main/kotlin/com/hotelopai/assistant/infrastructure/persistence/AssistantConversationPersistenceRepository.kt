package com.hotelopai.assistant.infrastructure.persistence

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.hotelopai.assistant.application.ConversationRepository
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
import com.hotelopai.shared.kernel.UuidV7Generator
import org.springframework.dao.EmptyResultDataAccessException
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
                draft_version,
                created_task_id,
                confirmation_idempotency_key,
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
                :draftVersion,
                :createdTaskId,
                :confirmationIdempotencyKey,
                :createdAt,
                :updatedAt
            )
            on conflict (id) do update set
                hotel_id = excluded.hotel_id,
                user_id = excluded.user_id,
                state = excluded.state,
                intent = excluded.intent,
                collected_fields_json = excluded.collected_fields_json,
                missing_fields_json = excluded.missing_fields_json,
                follow_up_question_json = excluded.follow_up_question_json,
                task_preview_json = excluded.task_preview_json,
                messages_json = excluded.messages_json,
                active_draft_id = excluded.active_draft_id,
                draft_version = excluded.draft_version,
                created_task_id = excluded.created_task_id,
                confirmation_idempotency_key = excluded.confirmation_idempotency_key,
                created_at = excluded.created_at,
                updated_at = excluded.updated_at
            """.trimIndent(),
            conversation.toSqlParameters(objectMapper)
        )

        return conversation
    }

    override fun findById(id: String): Conversation? =
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
                    draft_version,
                    created_task_id,
                    confirmation_idempotency_key,
                    created_at,
                    updated_at
                from assistant_conversation
                where id = :id
                """.trimIndent(),
                mapOf("id" to id)
            ) { rs, _ -> rs.toConversation(objectMapper) }
        } catch (_: EmptyResultDataAccessException) {
            null
        }

    override fun findByIdAndHotelIdAndUserId(id: String, hotelId: String, userId: String): Conversation? =
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
                    draft_version,
                    created_task_id,
                    confirmation_idempotency_key,
                    created_at,
                    updated_at
                from assistant_conversation
                where id = :id
                  and hotel_id = :hotelId
                  and user_id = :userId
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

    override fun save(record: TaskConfirmationRecord): TaskConfirmationRecord {
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
                .addValue("id", UuidV7Generator.generate(record.createdAt))
                .addValue("conversationId", record.conversationId)
                .addValue("idempotencyKey", record.idempotencyKey)
                .addValue("createdTaskId", record.createdTaskId)
                .addValue("draftId", record.draftId)
                .addValue("draftVersion", record.draftVersion)
                .addValue("previewJson", objectMapper.writeValueAsString(record.preview))
                .addValue("createdAt", Timestamp.from(record.createdAt))
        )

        return record
    }
}

private val collectedFieldsType = object : TypeReference<Map<String, String>>() {}
private val missingFieldsType = object : TypeReference<List<MissingField>>() {}
private val messagesType = object : TypeReference<List<PersistedConversationMessage>>() {}

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
        .addValue("draftVersion", draftVersion)
        .addValue("createdTaskId", createdTaskId)
        .addValue("confirmationIdempotencyKey", confirmationIdempotencyKey)
        .addValue("createdAt", Timestamp.from(createdAt))
        .addValue("updatedAt", Timestamp.from(updatedAt))

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
        draftVersion = getInt("draft_version"),
        createdTaskId = getString("created_task_id"),
        confirmationIdempotencyKey = getString("confirmation_idempotency_key"),
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
