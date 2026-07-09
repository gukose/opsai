package com.hotelopai.shared.pms

import java.util.UUID

data class MaintenanceCompletionRequest(
    val roomNumber: String,
    val issueTypeCode: String,
    val description: String,
    val status: String = "OPEN"
)

data class MaintenanceCompletionResult(
    val verificationLogId: UUID,
    val entityId: String?,
    val operation: String,
    val status: String
)

class PmsCompletionException(
    message: String,
    cause: Throwable? = null
) : RuntimeException(message, cause)

interface MaintenanceCompletionPort {
    fun updateMaintenance(request: MaintenanceCompletionRequest): MaintenanceCompletionResult
}
