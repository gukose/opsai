package com.hotelopai.unimock.application.pms

import com.hotelopai.unimock.application.simulation.SimulationNotLoadedException
import org.springframework.stereotype.Service

@Service
class PmsVerificationService(
    private val pmsReadRepository: PmsReadRepository,
    private val pmsVerificationRepository: PmsVerificationRepository
) {
    fun listVerificationLogs(): List<PmsVerificationLogReadModel> {
        activeSimulation()
        return pmsVerificationRepository.listAll()
    }

    fun listEvents(): List<PmsVerificationLogReadModel> {
        activeSimulation()
        return pmsVerificationRepository.listEvents()
    }

    private fun activeSimulation(): ActiveSimulationDocuments =
        pmsReadRepository.findActiveSimulationDocuments() ?: throw SimulationNotLoadedException()
}
