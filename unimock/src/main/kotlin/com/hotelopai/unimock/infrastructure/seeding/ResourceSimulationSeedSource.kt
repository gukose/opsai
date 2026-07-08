package com.hotelopai.unimock.infrastructure.seeding

import com.fasterxml.jackson.databind.ObjectMapper
import com.hotelopai.unimock.application.simulation.InvalidSimulationSeedPathException
import com.hotelopai.unimock.application.simulation.InvalidSimulationSeedException
import com.hotelopai.unimock.application.simulation.SimulationSeedSource
import com.hotelopai.unimock.domain.simulation.LoadedSimulationSeed
import org.springframework.core.io.ClassPathResource
import org.springframework.core.io.ResourceLoader
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.Instant

@Component
class ResourceSimulationSeedSource(
    private val resourceLoader: ResourceLoader,
    private val objectMapper: ObjectMapper,
    private val clock: Clock
) : SimulationSeedSource {
    override fun load(seedPath: String): LoadedSimulationSeed {
        val normalizedSeedPath = normalizeSeedPath(seedPath)
        val documents = requiredDocuments().associateWith { relativePath ->
            val resource = resolveResource(normalizedSeedPath, relativePath)
            if (!resource.exists()) {
                throw InvalidSimulationSeedPathException("$normalizedSeedPath/$relativePath")
            }
            val bytes = resource.inputStream.use { it.readBytes() }
            try {
                objectMapper.readTree(bytes)
            } catch (exception: Exception) {
                throw InvalidSimulationSeedException("Invalid JSON in $relativePath: ${exception.message}")
            }
        }

        val simulationNode = documents["simulation.json"]
            ?: throw InvalidSimulationSeedException("Missing simulation.json")
        val simulationCode = text(simulationNode, "simulationCode")
        val simulationName = text(simulationNode, "simulationName")
        val loadedAt = instantOrNull(simulationNode, "loadedAt") ?: clock.instant()

        return LoadedSimulationSeed(
            simulationCode = simulationCode,
            simulationName = simulationName,
            seedPath = normalizedSeedPath,
            files = documents,
            loadedAt = loadedAt
        )
    }

    private fun resolveResource(seedPath: String, relativePath: String) =
        if (seedPath.startsWith("classpath:")) {
            val classpathLocation = seedPath.removePrefix("classpath:").removePrefix("/")
            ClassPathResource("$classpathLocation/$relativePath")
        } else {
            resourceLoader.getResource("$seedPath/$relativePath")
        }

    private fun normalizeSeedPath(seedPath: String): String =
        seedPath.trim()
            .trimEnd('/')
            .let {
                if (it.startsWith("classpath:/")) {
                    "classpath:/" + it.removePrefix("classpath:/").trimStart('/')
                } else if (it.startsWith("classpath:")) {
                    "classpath:/" + it.removePrefix("classpath:").trimStart('/')
                } else {
                    it
                }
            }

    private fun requiredDocuments(): List<String> =
        listOf(
            "simulation.json",
            "hotel.json",
            "configuration/departments.json",
            "configuration/roles.json",
            "configuration/skills.json",
            "configuration/sla.json",
            "master/rooms.json",
            "master/room-types.json",
            "master/floors.json",
            "master/public-areas.json",
            "master/assets.json",
            "master/issue-types.json",
            "operations/reservations.json",
            "operations/guests.json",
            "operations/occupancy.json",
            "operations/room-status.json",
            "operations/minibar.json",
            "operations/guest-requests.json",
            "operations/events.json",
            "scenarios/checkout-morning.json",
            "scenarios/vip-arrival.json",
            "scenarios/busy-day.json",
            "scenarios/maintenance-heavy.json"
        )

    private fun text(node: com.fasterxml.jackson.databind.JsonNode, fieldName: String): String {
        val value = node.get(fieldName)?.asText()
            ?: throw InvalidSimulationSeedException("Missing field '$fieldName'")
        if (value.isBlank()) {
            throw InvalidSimulationSeedException("Field '$fieldName' must not be blank")
        }
        return value
    }

    private fun instantOrNull(node: com.fasterxml.jackson.databind.JsonNode, fieldName: String): Instant? {
        val value = node.get(fieldName)?.asText()?.takeIf { it.isNotBlank() } ?: return null
        return try {
            Instant.parse(value)
        } catch (exception: Exception) {
            throw InvalidSimulationSeedException("Field '$fieldName' must be an ISO-8601 instant")
        }
    }
}
