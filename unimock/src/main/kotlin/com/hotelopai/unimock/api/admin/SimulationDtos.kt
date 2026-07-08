package com.hotelopai.unimock.api.admin

import com.hotelopai.unimock.domain.simulation.SimulationSnapshot
import java.time.Instant
import java.util.UUID

data class SimulationResponse(
    val simulationId: UUID,
    val simulationCode: String,
    val simulationName: String,
    val seedPath: String,
    val documentCount: Int,
    val loadedAt: Instant
) {
    companion object {
        fun from(snapshot: SimulationSnapshot): SimulationResponse =
            SimulationResponse(
                simulationId = snapshot.simulationId,
                simulationCode = snapshot.simulationCode,
                simulationName = snapshot.simulationName,
                seedPath = snapshot.seedPath,
                documentCount = snapshot.documentCount,
                loadedAt = snapshot.loadedAt
            )
    }
}
