package com.hotelopai.unimock.infrastructure.persistence

import com.fasterxml.jackson.databind.ObjectMapper
import com.hotelopai.unimock.application.pms.ActiveSimulationDocuments
import com.hotelopai.unimock.application.pms.PmsDocumentRepository
import com.hotelopai.unimock.application.pms.PmsVerificationLogEntry
import com.hotelopai.unimock.application.pms.PmsVerificationLogReadModel
import com.hotelopai.unimock.application.pms.PmsVerificationRepository
import com.hotelopai.unimock.application.pms.PmsReadRepository
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.stereotype.Repository
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

@Repository
class PmsReadJdbcRepository(
    private val jdbcTemplate: NamedParameterJdbcTemplate,
    private val objectMapper: ObjectMapper
) : PmsReadRepository, PmsDocumentRepository, PmsVerificationRepository {
    override fun findActiveSimulationDocuments(): ActiveSimulationDocuments? {
        val simulation = jdbcTemplate.query(
            """
            select id, code, name, seed_path
            from unimock.simulation
            where active = true
            order by loaded_at desc
            limit 1
            """.trimIndent()
        ) { rs, _ ->
            mapOf(
                "id" to rs.getObject("id", java.util.UUID::class.java),
                "code" to rs.getString("code"),
                "name" to rs.getString("name"),
                "seedPath" to rs.getString("seed_path")
            )
        }.firstOrNull() ?: return null

        val simulationId = simulation["id"] as java.util.UUID
        val documents = jdbcTemplate.query(
            """
            select document_path, payload_json
            from unimock.simulation_document
            where simulation_id = :simulationId
            order by document_path
            """.trimIndent(),
            mapOf("simulationId" to simulationId)
        ) { rs, _ ->
            rs.getString("document_path") to objectMapper.readTree(rs.getString("payload_json"))
        }.toMap()

        return ActiveSimulationDocuments(
            simulationId = simulationId,
            simulationCode = simulation["code"] as String,
            simulationName = simulation["name"] as String,
            seedPath = simulation["seedPath"] as String,
            documents = documents
        )
    }

    override fun replaceDocument(simulationId: UUID, documentPath: String, document: com.fasterxml.jackson.databind.JsonNode) {
        jdbcTemplate.update(
            """
            update unimock.simulation_document
            set payload_json = cast(:payloadJson as jsonb),
                updated_at = :updatedAt
            where simulation_id = :simulationId
              and document_path = :documentPath
            """.trimIndent(),
            MapSqlParameterSource()
                .addValue("simulationId", simulationId)
                .addValue("documentPath", documentPath)
                .addValue("payloadJson", objectMapper.writeValueAsString(document))
                .addValue("updatedAt", Timestamp.from(Instant.now()))
        )
    }

    override fun insert(entry: PmsVerificationLogEntry): PmsVerificationLogReadModel {
        jdbcTemplate.update(
            """
            insert into unimock.pms_mock_verification_log (
                id,
                simulation_id,
                entity_type,
                entity_id,
                operation,
                request_payload_json,
                response_payload_json,
                status,
                source_system,
                destination_system,
                http_status,
                duration_ms,
                retry_count,
                correlation_id,
                created_at
            ) values (
                :id,
                :simulationId,
                :entityType,
                :entityId,
                :operation,
                cast(:requestPayloadJson as jsonb),
                cast(:responsePayloadJson as jsonb),
                :status,
                :sourceSystem,
                :destinationSystem,
                :httpStatus,
                :durationMs,
                :retryCount,
                :correlationId,
                :createdAt
            )
            """.trimIndent(),
            MapSqlParameterSource()
                .addValue("id", entry.id)
                .addValue("simulationId", entry.simulationId)
                .addValue("entityType", entry.entityType)
                .addValue("entityId", entry.entityId)
                .addValue("operation", entry.operation)
                .addValue("requestPayloadJson", entry.requestPayloadJson)
                .addValue("responsePayloadJson", entry.responsePayloadJson)
                .addValue("status", entry.status)
                .addValue("sourceSystem", entry.sourceSystem)
                .addValue("destinationSystem", entry.destinationSystem)
                .addValue("httpStatus", entry.httpStatus)
                .addValue("durationMs", entry.durationMs)
                .addValue("retryCount", entry.retryCount)
                .addValue("correlationId", entry.correlationId)
                .addValue("createdAt", Timestamp.from(entry.createdAt))
        )
        return PmsVerificationLogReadModel(
            verificationLogId = entry.id,
            simulationId = entry.simulationId,
            entityType = entry.entityType,
            entityId = entry.entityId,
            operation = entry.operation,
            requestPayloadJson = entry.requestPayloadJson,
            responsePayloadJson = entry.responsePayloadJson,
            status = entry.status,
            sourceSystem = entry.sourceSystem,
            destinationSystem = entry.destinationSystem,
            httpStatus = entry.httpStatus,
            durationMs = entry.durationMs,
            retryCount = entry.retryCount,
            correlationId = entry.correlationId,
            createdAt = entry.createdAt
        )
    }

    override fun listAll(): List<PmsVerificationLogReadModel> =
        jdbcTemplate.query(
            """
            select
                id,
                simulation_id,
                entity_type,
                entity_id,
                operation,
                request_payload_json,
                response_payload_json,
                status,
                source_system,
                destination_system,
                http_status,
                duration_ms,
                retry_count,
                correlation_id,
                created_at
            from unimock.pms_mock_verification_log
            order by created_at desc
            """.trimIndent()
        ) { rs, _ ->
            PmsVerificationLogReadModel(
                verificationLogId = rs.getObject("id", UUID::class.java),
                simulationId = rs.getString("simulation_id"),
                entityType = rs.getString("entity_type"),
                entityId = rs.getString("entity_id"),
                operation = rs.getString("operation"),
                requestPayloadJson = rs.getString("request_payload_json"),
                responsePayloadJson = rs.getString("response_payload_json"),
                status = rs.getString("status"),
                sourceSystem = rs.getString("source_system"),
                destinationSystem = rs.getString("destination_system"),
                httpStatus = rs.getObject("http_status")?.let { (it as Number).toInt() },
                durationMs = rs.getObject("duration_ms")?.let { (it as Number).toLong() },
                retryCount = rs.getInt("retry_count"),
                correlationId = rs.getString("correlation_id"),
                createdAt = rs.getTimestamp("created_at").toInstant()
            )
        }

    override fun listEvents(): List<PmsVerificationLogReadModel> =
        jdbcTemplate.query(
            """
            select
                id,
                simulation_id,
                entity_type,
                entity_id,
                operation,
                request_payload_json,
                response_payload_json,
                status,
                source_system,
                destination_system,
                http_status,
                duration_ms,
                retry_count,
                correlation_id,
                created_at
            from unimock.pms_mock_verification_log
            where entity_type = 'EVENT'
            order by created_at desc
            """.trimIndent()
        ) { rs, _ ->
            PmsVerificationLogReadModel(
                verificationLogId = rs.getObject("id", UUID::class.java),
                simulationId = rs.getString("simulation_id"),
                entityType = rs.getString("entity_type"),
                entityId = rs.getString("entity_id"),
                operation = rs.getString("operation"),
                requestPayloadJson = rs.getString("request_payload_json"),
                responsePayloadJson = rs.getString("response_payload_json"),
                status = rs.getString("status"),
                sourceSystem = rs.getString("source_system"),
                destinationSystem = rs.getString("destination_system"),
                httpStatus = rs.getObject("http_status")?.let { (it as Number).toInt() },
                durationMs = rs.getObject("duration_ms")?.let { (it as Number).toLong() },
                retryCount = rs.getInt("retry_count"),
                correlationId = rs.getString("correlation_id"),
                createdAt = rs.getTimestamp("created_at").toInstant()
            )
        }
}
