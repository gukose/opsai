package com.hotelopai.unimock.application.simulation

import com.hotelopai.unimock.config.UniMockProperties
import com.hotelopai.unimock.domain.simulation.LoadedSimulationSeed
import com.hotelopai.unimock.domain.simulation.SimulationSnapshot
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional
class SimulationAdministrationService(
    private val uniMockProperties: UniMockProperties,
    private val simulationSeedSource: SimulationSeedSource,
    private val simulationSeedValidator: SimulationSeedValidator,
    private val simulationRepository: SimulationRepository
) {
    fun load(): SimulationSnapshot {
        val seed = loadSeed()
        simulationSeedValidator.validate(seed)
        return simulationRepository.replaceActiveSimulation(seed)
    }

    fun reset(): SimulationSnapshot {
        val seed = loadSeed()
        simulationSeedValidator.validate(seed)
        return simulationRepository.replaceActiveSimulation(seed)
    }

    fun current(): SimulationSnapshot =
        simulationRepository.findActiveSimulation() ?: throw SimulationNotLoadedException()

    private fun loadSeed(): LoadedSimulationSeed =
        simulationSeedSource.load(uniMockProperties.seed.path)
}
