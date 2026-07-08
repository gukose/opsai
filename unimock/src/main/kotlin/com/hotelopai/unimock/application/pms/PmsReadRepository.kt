package com.hotelopai.unimock.application.pms

interface PmsReadRepository {
    fun findActiveSimulationDocuments(): ActiveSimulationDocuments?
}
