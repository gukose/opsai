package com.hotelopai.unimock.domain.simulation

import com.fasterxml.jackson.databind.JsonNode
import java.time.Instant
import java.util.UUID

data class LoadedSimulationSeed(
    val simulationCode: String,
    val simulationName: String,
    val seedPath: String,
    val files: Map<String, JsonNode>,
    val loadedAt: Instant
)

data class SimulationSnapshot(
    val simulationId: UUID,
    val simulationCode: String,
    val simulationName: String,
    val seedPath: String,
    val documentCount: Int,
    val loadedAt: Instant
)
