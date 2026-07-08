package com.hotelopai.unimock.api.pms

import com.hotelopai.unimock.application.pms.PmsVerificationService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/pms/mock-updates")
class PmsVerificationController(
    private val pmsVerificationService: PmsVerificationService
) {
    @GetMapping("/verification-log")
    fun listVerificationLog(): List<PmsVerificationLogResponse> =
        pmsVerificationService.listVerificationLogs().map { it.toResponse() }

    @GetMapping("/events")
    fun listEvents(): List<PmsVerificationLogResponse> =
        pmsVerificationService.listEvents().map { it.toResponse() }
}

private fun com.hotelopai.unimock.application.pms.PmsVerificationLogReadModel.toResponse() =
    PmsVerificationLogResponse(
        verificationLogId,
        simulationId,
        entityType,
        entityId,
        operation,
        requestPayloadJson,
        responsePayloadJson,
        status,
        sourceSystem,
        destinationSystem,
        httpStatus,
        durationMs,
        retryCount,
        correlationId,
        createdAt
    )
