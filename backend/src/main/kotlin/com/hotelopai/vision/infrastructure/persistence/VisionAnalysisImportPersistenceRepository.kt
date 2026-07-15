package com.hotelopai.vision.infrastructure.persistence

import com.hotelopai.vision.application.VisionAnalysisImportRepository
import com.hotelopai.vision.domain.VisionAnalysisImport
import com.hotelopai.vision.domain.VisionAnalysisImportStatus
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
class VisionAnalysisImportPersistenceRepository(
    private val jdbcTemplate: NamedParameterJdbcTemplate
) : VisionAnalysisImportRepository {
    override fun save(record: VisionAnalysisImport): VisionAnalysisImport {
        jdbcTemplate.update(
            """
            insert into vision_analysis_import (
                id,
                analysis_id,
                conversation_id,
                attachment_id,
                hotel_id,
                user_id,
                message_id,
                status,
                failure_code,
                created_at,
                updated_at
            ) values (
                :id,
                :analysisId,
                :conversationId,
                :attachmentId,
                :hotelId,
                :userId,
                :messageId,
                :status,
                :failureCode,
                :createdAt,
                :updatedAt
            )
            on conflict (id) do update set
                message_id = excluded.message_id,
                status = excluded.status,
                failure_code = excluded.failure_code,
                updated_at = excluded.updated_at
            """.trimIndent(),
            record.toSqlParameters()
        )
        return record
    }

    @Transactional(readOnly = true)
    override fun findByConversationIdAndAnalysisId(conversationId: String, analysisId: UUID): VisionAnalysisImport? =
        try {
            jdbcTemplate.queryForObject(
                """
                select
                    id,
                    analysis_id,
                    conversation_id,
                    attachment_id,
                    hotel_id,
                    user_id,
                    message_id,
                    status,
                    failure_code,
                    created_at,
                    updated_at
                from vision_analysis_import
                where conversation_id = :conversationId
                  and analysis_id = :analysisId
                """.trimIndent(),
                mapOf(
                    "conversationId" to conversationId,
                    "analysisId" to analysisId
                )
            ) { rs, _ -> rs.toVisionAnalysisImport() }
        } catch (_: EmptyResultDataAccessException) {
            null
        }
}

private fun VisionAnalysisImport.toSqlParameters(): MapSqlParameterSource =
    MapSqlParameterSource()
        .addValue("id", id)
        .addValue("analysisId", analysisId)
        .addValue("conversationId", conversationId)
        .addValue("attachmentId", attachmentId)
        .addValue("hotelId", hotelId)
        .addValue("userId", userId)
        .addValue("messageId", messageId)
        .addValue("status", status.name)
        .addValue("failureCode", failureCode)
        .addValue("createdAt", Timestamp.from(createdAt))
        .addValue("updatedAt", Timestamp.from(updatedAt))

private fun ResultSet.toVisionAnalysisImport(): VisionAnalysisImport =
    VisionAnalysisImport(
        id = getObject("id", UUID::class.java),
        analysisId = getObject("analysis_id", UUID::class.java),
        conversationId = getString("conversation_id"),
        attachmentId = getObject("attachment_id", UUID::class.java),
        hotelId = getString("hotel_id"),
        userId = getString("user_id"),
        messageId = getString("message_id"),
        status = VisionAnalysisImportStatus.valueOf(getString("status")),
        failureCode = getString("failure_code"),
        createdAt = getTimestamp("created_at").toInstant(),
        updatedAt = getTimestamp("updated_at").toInstant()
    )
