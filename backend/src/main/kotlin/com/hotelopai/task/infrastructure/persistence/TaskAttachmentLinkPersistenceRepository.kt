package com.hotelopai.task.infrastructure.persistence

import com.hotelopai.task.application.TaskAttachmentLinkRepository
import com.hotelopai.shared.kernel.PersistenceInstant
import com.hotelopai.task.domain.TaskAttachmentLink
import com.hotelopai.task.domain.TaskAttachmentLinkView
import com.hotelopai.task.domain.TaskAttachmentSourceType
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.sql.ResultSet
import java.sql.Timestamp
import java.util.UUID

@Repository
@Transactional
class TaskAttachmentLinkPersistenceRepository(
    private val jdbcTemplate: NamedParameterJdbcTemplate
) : TaskAttachmentLinkRepository {
    override fun saveAll(links: List<TaskAttachmentLink>): List<TaskAttachmentLink> {
        val normalized = links.map { it.normalizedForPersistence() }
        normalized.forEach { link ->
            jdbcTemplate.update(
                """
                insert into task_attachment_link (
                    id,
                    task_id,
                    attachment_id,
                    conversation_id,
                    hotel_id,
                    user_id,
                    source_type,
                    analysis_id,
                    analysis_import_id,
                    created_at
                ) values (
                    :id,
                    :taskId,
                    :attachmentId,
                    :conversationId,
                    :hotelId,
                    :userId,
                    :sourceType,
                    :analysisId,
                    :analysisImportId,
                    :createdAt
                )
                on conflict (task_id, attachment_id) do nothing
                """.trimIndent(),
                link.toSqlParameters()
            )
        }
        return normalized
    }

    @Transactional(readOnly = true)
    override fun findByTaskIdAndHotelId(taskId: UUID, hotelId: UUID): List<TaskAttachmentLinkView> =
        jdbcTemplate.query(
            """
            select
                tal.attachment_id,
                tal.conversation_id,
                aa.type,
                aa.original_file_name,
                aa.declared_mime_type,
                aa.declared_size_bytes,
                aa.width_px,
                aa.height_px,
                aa.storage_status,
                tal.source_type,
                tal.analysis_id,
                tal.analysis_import_id,
                tal.created_at
            from task_attachment_link tal
            join assistant_attachment aa on aa.id = tal.attachment_id
            where tal.task_id = :taskId
              and tal.hotel_id = :hotelId
            order by tal.created_at asc, tal.attachment_id asc
            """.trimIndent(),
            mapOf(
                "taskId" to taskId,
                "hotelId" to hotelId.toString()
            )
        ) { rs, _ -> rs.toTaskAttachmentLinkView() }
}

private fun TaskAttachmentLink.toSqlParameters(): MapSqlParameterSource =
    MapSqlParameterSource()
        .addValue("id", id)
        .addValue("taskId", taskId)
        .addValue("attachmentId", attachmentId)
        .addValue("conversationId", conversationId)
        .addValue("hotelId", hotelId)
        .addValue("userId", userId)
        .addValue("sourceType", sourceType.name)
        .addValue("analysisId", analysisId)
        .addValue("analysisImportId", analysisImportId)
        .addValue("createdAt", Timestamp.from(createdAt))

private fun TaskAttachmentLink.normalizedForPersistence(): TaskAttachmentLink =
    copy(createdAt = PersistenceInstant.toPersistencePrecision(createdAt))

private fun ResultSet.toTaskAttachmentLinkView(): TaskAttachmentLinkView =
    TaskAttachmentLinkView(
        attachmentId = getObject("attachment_id", UUID::class.java),
        conversationId = getString("conversation_id"),
        type = getString("type"),
        originalFileName = getString("original_file_name"),
        declaredMimeType = getString("declared_mime_type"),
        declaredSizeBytes = getLong("declared_size_bytes"),
        widthPx = getObject("width_px") as Int?,
        heightPx = getObject("height_px") as Int?,
        storageStatus = getString("storage_status"),
        sourceType = TaskAttachmentSourceType.valueOf(getString("source_type")),
        analysisId = getObject("analysis_id", UUID::class.java),
        analysisImportId = getObject("analysis_import_id", UUID::class.java),
        createdAt = getTimestamp("created_at").toInstant()
    )
