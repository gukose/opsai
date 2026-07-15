package com.hotelopai.vision.infrastructure.persistence

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.hotelopai.vision.application.VisionAnalysisRepository
import com.hotelopai.vision.domain.VisionAnalysis
import com.hotelopai.vision.domain.VisionAnalysisStatus
import com.hotelopai.vision.domain.VisionDetectedObservation
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
class VisionAnalysisPersistenceRepository(
    private val jdbcTemplate: NamedParameterJdbcTemplate,
    objectMapper: ObjectMapper
) : VisionAnalysisRepository {
    private val objectMapper = objectMapper.copy().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

    override fun save(analysis: VisionAnalysis): VisionAnalysis {
        jdbcTemplate.update(
            """
            insert into vision_analysis (
                id,
                attachment_id,
                conversation_id,
                hotel_id,
                user_id,
                status,
                provider_id,
                provider_model,
                provider_version,
                confidence,
                observations_json,
                detected_issue_category,
                detected_location_hint,
                provider_metadata_json,
                failure_code,
                failure_message,
                idempotency_key,
                attempt_count,
                requested_at,
                completed_at,
                failed_at,
                created_at,
                updated_at
            ) values (
                :id,
                :attachmentId,
                :conversationId,
                :hotelId,
                :userId,
                :status,
                :providerId,
                :providerModel,
                :providerVersion,
                :confidence,
                cast(:observationsJson as jsonb),
                :detectedIssueCategory,
                :detectedLocationHint,
                cast(:providerMetadataJson as jsonb),
                :failureCode,
                :failureMessage,
                :idempotencyKey,
                :attemptCount,
                :requestedAt,
                :completedAt,
                :failedAt,
                :createdAt,
                :updatedAt
            )
            on conflict (id) do update set
                status = excluded.status,
                provider_id = excluded.provider_id,
                provider_model = excluded.provider_model,
                provider_version = excluded.provider_version,
                confidence = excluded.confidence,
                observations_json = excluded.observations_json,
                detected_issue_category = excluded.detected_issue_category,
                detected_location_hint = excluded.detected_location_hint,
                provider_metadata_json = excluded.provider_metadata_json,
                failure_code = excluded.failure_code,
                failure_message = excluded.failure_message,
                attempt_count = excluded.attempt_count,
                requested_at = excluded.requested_at,
                completed_at = excluded.completed_at,
                failed_at = excluded.failed_at,
                updated_at = excluded.updated_at
            """.trimIndent(),
            analysis.toSqlParameters(objectMapper)
        )

        return analysis
    }

    @Transactional(readOnly = true)
    override fun findById(id: UUID): VisionAnalysis? =
        try {
            jdbcTemplate.queryForObject(
                selectSql("where id = :id"),
                mapOf("id" to id)
            ) { rs, _ -> rs.toVisionAnalysis(objectMapper) }
        } catch (_: EmptyResultDataAccessException) {
            null
        }

    @Transactional(readOnly = true)
    override fun findByIdempotencyScope(
        attachmentId: UUID,
        conversationId: String,
        hotelId: String,
        userId: String,
        idempotencyKey: String
    ): VisionAnalysis? =
        try {
            jdbcTemplate.queryForObject(
                selectSql(
                    """
                    where attachment_id = :attachmentId
                      and conversation_id = :conversationId
                      and hotel_id = :hotelId
                      and user_id = :userId
                      and idempotency_key = :idempotencyKey
                    """.trimIndent()
                ),
                mapOf(
                    "attachmentId" to attachmentId,
                    "conversationId" to conversationId,
                    "hotelId" to hotelId,
                    "userId" to userId,
                    "idempotencyKey" to idempotencyKey
                )
            ) { rs, _ -> rs.toVisionAnalysis(objectMapper) }
        } catch (_: EmptyResultDataAccessException) {
            null
        }

    private fun selectSql(whereClause: String): String =
        """
        select
            id,
            attachment_id,
            conversation_id,
            hotel_id,
            user_id,
            status,
            provider_id,
            provider_model,
            provider_version,
            confidence,
            observations_json,
            detected_issue_category,
            detected_location_hint,
            provider_metadata_json,
            failure_code,
            failure_message,
            idempotency_key,
            attempt_count,
            requested_at,
            completed_at,
            failed_at,
            created_at,
            updated_at
        from vision_analysis
        $whereClause
        """.trimIndent()
}

private val observationsType = object : TypeReference<List<VisionDetectedObservation>>() {}
private val providerMetadataType = object : TypeReference<Map<String, String>>() {}

private fun VisionAnalysis.toSqlParameters(objectMapper: ObjectMapper): MapSqlParameterSource =
    MapSqlParameterSource()
        .addValue("id", id)
        .addValue("attachmentId", attachmentId)
        .addValue("conversationId", conversationId)
        .addValue("hotelId", hotelId)
        .addValue("userId", userId)
        .addValue("status", status.name)
        .addValue("providerId", providerId)
        .addValue("providerModel", providerModel)
        .addValue("providerVersion", providerVersion)
        .addValue("confidence", confidence)
        .addValue("observationsJson", objectMapper.writeValueAsString(observations))
        .addValue("detectedIssueCategory", detectedIssueCategory)
        .addValue("detectedLocationHint", detectedLocationHint)
        .addValue("providerMetadataJson", objectMapper.writeValueAsString(providerMetadata))
        .addValue("failureCode", failureCode)
        .addValue("failureMessage", failureMessage)
        .addValue("idempotencyKey", idempotencyKey)
        .addValue("attemptCount", attemptCount)
        .addValue("requestedAt", Timestamp.from(requestedAt))
        .addValue("completedAt", completedAt?.let(Timestamp::from))
        .addValue("failedAt", failedAt?.let(Timestamp::from))
        .addValue("createdAt", Timestamp.from(createdAt))
        .addValue("updatedAt", Timestamp.from(updatedAt))

private fun ResultSet.toVisionAnalysis(objectMapper: ObjectMapper): VisionAnalysis =
    VisionAnalysis(
        id = getObject("id", UUID::class.java),
        attachmentId = getObject("attachment_id", UUID::class.java),
        conversationId = getString("conversation_id"),
        hotelId = getString("hotel_id"),
        userId = getString("user_id"),
        status = VisionAnalysisStatus.valueOf(getString("status")),
        providerId = getString("provider_id"),
        providerModel = getString("provider_model"),
        providerVersion = getString("provider_version"),
        confidence = getBigDecimal("confidence"),
        observations = objectMapper.readValue(getString("observations_json"), observationsType),
        detectedIssueCategory = getString("detected_issue_category"),
        detectedLocationHint = getString("detected_location_hint"),
        providerMetadata = objectMapper.readValue(getString("provider_metadata_json"), providerMetadataType),
        failureCode = getString("failure_code"),
        failureMessage = getString("failure_message"),
        idempotencyKey = getString("idempotency_key"),
        attemptCount = getInt("attempt_count"),
        requestedAt = getTimestamp("requested_at").toInstant(),
        completedAt = getTimestamp("completed_at")?.toInstant(),
        failedAt = getTimestamp("failed_at")?.toInstant(),
        createdAt = getTimestamp("created_at").toInstant(),
        updatedAt = getTimestamp("updated_at").toInstant()
    )
