package com.hotelopai.unimock.infrastructure.seeding

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.ObjectNode
import com.hotelopai.unimock.application.simulation.InvalidSimulationSeedException
import com.hotelopai.unimock.application.simulation.InvalidSimulationSeedPathException
import com.hotelopai.unimock.application.simulation.SimulationSeedValidator
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.core.io.ByteArrayResource
import org.springframework.core.io.ClassPathResource
import org.springframework.core.io.DefaultResourceLoader
import org.springframework.core.io.Resource
import org.springframework.core.io.ResourceLoader
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class ResourceSimulationSeedSourceTest {
    private val objectMapper = ObjectMapper()
    private val clock = Clock.fixed(Instant.parse("2026-07-08T00:00:00Z"), ZoneOffset.UTC)
    private val seedSource = ResourceSimulationSeedSource(
        DefaultResourceLoader(),
        objectMapper,
        clock
    )
    private val seedValidator = SimulationSeedValidator()

    @Test
    fun `required seed files exist`() {
        requiredSeedFiles().forEach { filePath ->
            assertThat(ClassPathResource(filePath).exists())
                .withFailMessage("Expected seed file to exist: $filePath")
                .isTrue()
        }
    }

    @Test
    fun `load seed is deterministic and valid`() {
        val seed = seedSource.load("classpath:/simulation/grand-hotel")

        assertThat(seed.simulationCode).isEqualTo("grand-hotel")
        assertThat(seed.simulationName).isEqualTo("Grand Hotel")
        assertThat(seed.loadedAt).isEqualTo(Instant.parse("2026-07-08T00:00:00Z"))
        assertThat(seed.files).hasSize(requiredSeedFiles().size)

        seedValidator.validate(seed)
    }

    @Test
    fun `invalid seed path fails clearly`() {
        assertThatThrownBy {
            seedSource.load("classpath:/simulation/missing-grand-hotel")
        }
            .isInstanceOf(InvalidSimulationSeedPathException::class.java)
            .hasMessageContaining("simulation/missing-grand-hotel")
    }

    @Test
    fun `invalid json fails clearly`() {
        val failingLoader = object : ResourceLoader {
            private val delegate = DefaultResourceLoader()
            private val relativeResources = requiredSeedFiles().associateWith { filePath ->
                ClassPathResource(filePath)
            }

            override fun getResource(location: String): Resource =
                when {
                    location.endsWith("/hotel.json") -> ByteArrayResource("not-json".toByteArray())
                    else -> relativeResources.values.firstOrNull {
                        location.endsWith(it.path)
                    } ?: delegate.getResource(location)
                }

            override fun getClassLoader(): ClassLoader? = delegate.classLoader
        }

        val loader = ResourceSimulationSeedSource(
            failingLoader,
            objectMapper,
            clock
        )

        assertThatThrownBy {
            loader.load("file:/simulation/grand-hotel")
        }
            .isInstanceOf(InvalidSimulationSeedException::class.java)
            .hasMessageContaining("Invalid JSON in hotel.json")
    }

    @Test
    fun `referential integrity validation catches invalid references`() {
        val seed = seedSource.load("classpath:/simulation/grand-hotel")
        val invalidRooms = seed.files["master/rooms.json"]!!.deepCopy<ObjectNode>()
        val roomsArray = invalidRooms.get("rooms") as ArrayNode
        val firstRoom = roomsArray.get(0) as ObjectNode
        firstRoom.put("roomTypeCode", "UNKNOWN")

        val invalidSeed = seed.copy(
            files = seed.files.toMutableMap().apply {
                put("master/rooms.json", invalidRooms)
            }
        )

        assertThatThrownBy {
            seedValidator.validate(invalidSeed)
        }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("unknown roomTypeCode")
    }

    private fun requiredSeedFiles(): List<String> =
        listOf(
            "simulation/grand-hotel/simulation.json",
            "simulation/grand-hotel/hotel.json",
            "simulation/grand-hotel/configuration/departments.json",
            "simulation/grand-hotel/configuration/roles.json",
            "simulation/grand-hotel/configuration/skills.json",
            "simulation/grand-hotel/configuration/sla.json",
            "simulation/grand-hotel/master/rooms.json",
            "simulation/grand-hotel/master/room-types.json",
            "simulation/grand-hotel/master/floors.json",
            "simulation/grand-hotel/master/public-areas.json",
            "simulation/grand-hotel/master/assets.json",
            "simulation/grand-hotel/master/issue-types.json",
            "simulation/grand-hotel/operations/reservations.json",
            "simulation/grand-hotel/operations/guests.json",
            "simulation/grand-hotel/operations/occupancy.json",
            "simulation/grand-hotel/operations/room-status.json",
            "simulation/grand-hotel/operations/minibar.json",
            "simulation/grand-hotel/operations/guest-requests.json",
            "simulation/grand-hotel/operations/events.json",
            "simulation/grand-hotel/scenarios/checkout-morning.json",
            "simulation/grand-hotel/scenarios/vip-arrival.json",
            "simulation/grand-hotel/scenarios/busy-day.json",
            "simulation/grand-hotel/scenarios/maintenance-heavy.json"
        )
}
