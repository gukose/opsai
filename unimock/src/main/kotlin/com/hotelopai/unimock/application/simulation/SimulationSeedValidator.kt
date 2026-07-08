package com.hotelopai.unimock.application.simulation

import com.fasterxml.jackson.databind.JsonNode
import com.hotelopai.unimock.domain.simulation.LoadedSimulationSeed
import org.springframework.stereotype.Component

@Component
class SimulationSeedValidator {
    fun validate(seed: LoadedSimulationSeed) {
        require(seed.simulationCode.isNotBlank()) { "simulation.json must contain a non-empty simulationCode" }
        require(seed.simulationName.isNotBlank()) { "simulation.json must contain a non-empty simulationName" }

        val requiredFiles = requiredFilePaths()
        val missingFiles = requiredFiles.filterNot(seed.files::containsKey)
        require(missingFiles.isEmpty()) {
            "Missing required seed files: ${missingFiles.joinToString(", ")}"
        }

        val hotelCode = text(seed.files["hotel.json"], "code")
        require(hotelCode == seed.simulationCode) {
            "hotel.json code must match simulation.json simulationCode"
        }

        val roomTypes = objectCodes(seed.files["master/room-types.json"], "roomTypes")
        val floors = objectCodes(seed.files["master/floors.json"], "floors")
        val rooms = objects(seed.files["master/rooms.json"], "rooms")
        val roomNumbers = rooms.map { text(it, "roomNumber") }.toSet()

        rooms.forEach { room ->
            val roomNumber = text(room, "roomNumber")
            val roomTypeCode = text(room, "roomTypeCode")
            val floorCode = text(room, "floorCode")
            require(roomTypeCode in roomTypes) { "Room $roomNumber references unknown roomTypeCode $roomTypeCode" }
            require(floorCode in floors) { "Room $roomNumber references unknown floorCode $floorCode" }
        }

        objectOptionalStrings(seed.files["master/assets.json"], "assets", "roomNumber").forEach { roomNumber ->
            require(roomNumber in roomNumbers) { "Asset references unknown roomNumber $roomNumber" }
        }

        objectOptionalStrings(seed.files["operations/reservations.json"], "reservations", "roomNumber").forEach { roomNumber ->
            require(roomNumber in roomNumbers) { "Reservation references unknown roomNumber $roomNumber" }
        }

        objectOptionalStrings(seed.files["operations/room-status.json"], "roomStatuses", "roomNumber").forEach { roomNumber ->
            require(roomNumber in roomNumbers) { "Room status references unknown roomNumber $roomNumber" }
        }

        objectOptionalStrings(seed.files["operations/minibar.json"], "items", "roomNumber").forEach { roomNumber ->
            require(roomNumber in roomNumbers) { "Minibar item references unknown roomNumber $roomNumber" }
        }

        objectOptionalStrings(seed.files["operations/guest-requests.json"], "guestRequests", "roomNumber")
            .filter { it.isNotBlank() }
            .forEach { roomNumber ->
                require(roomNumber in roomNumbers) { "Guest request references unknown roomNumber $roomNumber" }
            }
    }

    private fun requiredFilePaths(): List<String> =
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

    private fun objects(node: JsonNode?, arrayField: String): List<JsonNode> {
        val array = node?.get(arrayField)
            ?: throw InvalidSimulationSeedException("Missing array field '$arrayField'")
        require(array.isArray) { "Field '$arrayField' must be an array" }
        return array.toList()
    }

    private fun objectCodes(node: JsonNode?, arrayField: String): Set<String> =
        objects(node, arrayField).map { text(it, "code") }.toSet()

    private fun objectOptionalStrings(node: JsonNode?, arrayField: String, fieldName: String): List<String> =
        objects(node, arrayField).map { it.get(fieldName)?.asText().orEmpty() }

    private fun text(node: JsonNode?, fieldName: String): String {
        val value = node?.get(fieldName)?.asText()
            ?: throw InvalidSimulationSeedException("Missing field '$fieldName'")
        require(value.isNotBlank()) { "Field '$fieldName' must not be blank" }
        return value
    }
}
