package com.hotelopai.unimock.application.pms

interface PmsVerificationRepository {
    fun insert(entry: PmsVerificationLogEntry): PmsVerificationLogReadModel

    fun listAll(): List<PmsVerificationLogReadModel>

    fun listEvents(): List<PmsVerificationLogReadModel>
}
