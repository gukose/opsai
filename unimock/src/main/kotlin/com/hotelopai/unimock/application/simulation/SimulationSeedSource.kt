package com.hotelopai.unimock.application.simulation

import com.hotelopai.unimock.domain.simulation.LoadedSimulationSeed

interface SimulationSeedSource {
    fun load(seedPath: String): LoadedSimulationSeed
}
