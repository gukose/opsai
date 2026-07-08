package com.hotelopai.unimock.infrastructure.persistence

import com.hotelopai.unimock.application.simulation.SimulationRepository
import com.hotelopai.unimock.domain.simulation.LoadedSimulationSeed
import com.hotelopai.unimock.domain.simulation.SimulationSnapshot
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository
import java.nio.charset.StandardCharsets
import java.sql.Timestamp
import java.util.UUID

@Repository
class SimulationJdbcRepository(
    private val jdbcTemplate: NamedParameterJdbcTemplate
) : SimulationRepository {
    override fun replaceActiveSimulation(seed: LoadedSimulationSeed): SimulationSnapshot {
        jdbcTemplate.update("delete from unimock.simulation_document", emptyMap<String, Any>())
        jdbcTemplate.update("delete from unimock.simulation", emptyMap<String, Any>())

        val simulationId = deterministicUuid("simulation:${seed.seedPath}:${seed.simulationCode}")
        jdbcTemplate.update(
            """
            insert into unimock.simulation (
                id, code, name, seed_path, loaded_at, active, created_at, updated_at
            ) values (
                :id, :code, :name, :seedPath, :loadedAt, true, :loadedAt, :loadedAt
            )
            """.trimIndent(),
            MapSqlParameterSource()
                .addValue("id", simulationId)
                .addValue("code", seed.simulationCode)
                .addValue("name", seed.simulationName)
                .addValue("seedPath", seed.seedPath)
                .addValue("loadedAt", Timestamp.from(seed.loadedAt))
        )

        val documentSql = """
            insert into unimock.simulation_document (
                id, simulation_id, document_path, document_type, payload_json, created_at, updated_at
            ) values (
                :id, :simulationId, :documentPath, :documentType, CAST(:payloadJson AS jsonb), :loadedAt, :loadedAt
            )
        """.trimIndent()

        seed.files.forEach { (path, node) ->
            jdbcTemplate.update(
                documentSql,
                MapSqlParameterSource()
                    .addValue("id", deterministicUuid("$simulationId:$path"))
                    .addValue("simulationId", simulationId)
                    .addValue("documentPath", path)
                    .addValue("documentType", path.substringBefore("/"))
                    .addValue("payloadJson", node.toString())
                    .addValue("loadedAt", Timestamp.from(seed.loadedAt))
            )
        }

        return SimulationSnapshot(
            simulationId = simulationId,
            simulationCode = seed.simulationCode,
            simulationName = seed.simulationName,
            seedPath = seed.seedPath,
            documentCount = seed.files.size,
            loadedAt = seed.loadedAt
        )
    }

    override fun findActiveSimulation(): SimulationSnapshot? =
        jdbcTemplate.query(
            """
            select
                s.id,
                s.code,
                s.name,
                s.seed_path,
                s.loaded_at,
                count(d.id) as document_count
            from unimock.simulation s
            left join unimock.simulation_document d on d.simulation_id = s.id
            where s.active = true
            group by s.id, s.code, s.name, s.seed_path, s.loaded_at
            order by s.loaded_at desc
            limit 1
            """.trimIndent()
        ) { rs, _ ->
            SimulationSnapshot(
                simulationId = rs.getObject("id", java.util.UUID::class.java),
                simulationCode = rs.getString("code"),
                simulationName = rs.getString("name"),
                seedPath = rs.getString("seed_path"),
                documentCount = rs.getInt("document_count"),
                loadedAt = rs.getTimestamp("loaded_at").toInstant()
            )
        }.firstOrNull()

    private fun deterministicUuid(value: String): UUID =
        UUID.nameUUIDFromBytes(value.toByteArray(StandardCharsets.UTF_8))
}
