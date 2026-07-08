package com.hotelopai.unimock.domain

import java.time.Instant
import java.util.UUID

data class PmsMockVerificationLog(
    val id: UUID,
    val simulationId: String,
    val entityType: String,
    val entityId: String?,
    val operation: String,
    val requestPayloadJson: String?,
    val responsePayloadJson: String?,
    val status: String,
    val sourceSystem: String,
    val destinationSystem: String,
    val httpStatus: Int?,
    val durationMs: Long?,
    val retryCount: Int,
    val correlationId: String?,
    val createdAt: Instant
)
