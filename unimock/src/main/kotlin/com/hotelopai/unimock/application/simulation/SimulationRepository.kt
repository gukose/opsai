package com.hotelopai.unimock.application.simulation

import com.hotelopai.unimock.domain.simulation.LoadedSimulationSeed
import com.hotelopai.unimock.domain.simulation.SimulationSnapshot

interface SimulationRepository {
    fun replaceActiveSimulation(seed: LoadedSimulationSeed): SimulationSnapshot

    fun findActiveSimulation(): SimulationSnapshot?
}
