package com.hotelopai.pms.api

import com.hotelopai.pms.application.PmsAuthenticationMode
import com.hotelopai.pms.application.PmsCapabilities
import com.hotelopai.pms.application.PmsCircuitState
import com.hotelopai.pms.application.PmsCredentialRefreshResult
import com.hotelopai.pms.application.PmsFailureCategory
import com.hotelopai.pms.application.PmsHealthState
import com.hotelopai.pms.application.PmsOperationsService
import com.hotelopai.pms.application.PmsProviderDiagnostic
import com.hotelopai.pms.application.PmsProviderHealth
import com.hotelopai.pms.application.PmsProviderSummary
import com.hotelopai.pms.application.PmsRetryPolicySummary
import com.hotelopai.pms.application.PmsRolloutReadiness
import com.hotelopai.pms.application.PmsRolloutReadinessState
import com.hotelopai.shared.security.CurrentUserContextResolver
import com.hotelopai.shared.security.PermissionExpressions
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import java.time.Instant

@RestController
@RequestMapping("/api/v1/internal/pms")
class InternalPmsOperationsController(
    private val operationsService: PmsOperationsService,
    private val currentUserContextResolver: CurrentUserContextResolver
) {
    @GetMapping("/providers")
    @PreAuthorize(PermissionExpressions.PMS_OPERATIONS_ACCESS)
    fun listProviders(): List<PmsProviderSummaryResponse> =
        operationsService.listProviders().map { it.toResponse() }

    @GetMapping("/providers/{providerId}")
    @PreAuthorize(PermissionExpressions.PMS_OPERATIONS_ACCESS)
    fun diagnostics(@PathVariable providerId: String): PmsProviderDiagnosticResponse =
        safely { operationsService.diagnostics(providerId).toResponse() }

    @PostMapping("/providers/{providerId}/health-check")
    @PreAuthorize(PermissionExpressions.PMS_OPERATIONS_ACCESS)
    fun runHealthCheck(@PathVariable providerId: String): PmsProviderHealthResponse =
        safely { operationsService.runHealthCheck(providerId, actorUserId()).toResponse() }

    @PostMapping("/providers/{providerId}/credentials/refresh")
    @PreAuthorize(PermissionExpressions.PMS_OPERATIONS_ACCESS)
    fun refreshCredentials(@PathVariable providerId: String): PmsCredentialRefreshResponse =
        safely { operationsService.refreshCredentials(providerId, actorUserId()).toResponse() }

    @GetMapping("/rollout-readiness")
    @PreAuthorize(PermissionExpressions.PMS_OPERATIONS_ACCESS)
    fun activeRolloutReadiness(): PmsRolloutReadinessResponse =
        safely { operationsService.activeRolloutReadiness(actorUserId()).toResponse() }

    private fun actorUserId(): String =
        currentUserContextResolver.current().userId.toString()

    private fun <T> safely(block: () -> T): T =
        try {
            block()
        } catch (exception: IllegalStateException) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, exception.message, exception)
        }
}

data class PmsProviderSummaryResponse(
    val providerId: String,
    val displayName: String,
    val active: Boolean,
    val enabled: Boolean,
    val configured: Boolean,
    val healthState: PmsHealthState,
    val circuitState: PmsCircuitState,
    val capabilities: PmsCapabilities
)

data class PmsProviderDiagnosticResponse(
    val providerId: String,
    val displayName: String,
    val enabled: Boolean,
    val configured: Boolean,
    val endpointHost: String?,
    val hotelPropertyIdentifierConfigured: Boolean,
    val authenticationMode: PmsAuthenticationMode,
    val authenticationConfigured: Boolean,
    val capabilities: PmsCapabilities,
    val settingsKeys: List<String>,
    val healthState: PmsHealthState?,
    val circuitState: PmsCircuitState?,
    val retryPolicy: PmsRetryPolicySummary?,
    val lastFailureCategory: PmsFailureCategory?,
    val readinessMessage: String?
)

data class PmsProviderHealthResponse(
    val providerId: String,
    val state: PmsHealthState,
    val configured: Boolean,
    val enabled: Boolean,
    val checkedAt: Instant,
    val lastSuccessfulCheck: Instant?,
    val lastFailedCheck: Instant?,
    val failureCategory: PmsFailureCategory,
    val capabilities: PmsCapabilities,
    val circuitState: PmsCircuitState,
    val retryPolicy: PmsRetryPolicySummary?
)

data class PmsRolloutReadinessResponse(
    val providerId: String,
    val state: PmsRolloutReadinessState,
    val productionApproved: Boolean,
    val activeProfiles: List<String>,
    val blockingReasons: List<String>,
    val health: PmsProviderHealthResponse,
    val capabilities: PmsCapabilities
)

data class PmsCredentialRefreshResponse(
    val providerId: String,
    val refreshedAt: Instant,
    val success: Boolean,
    val failureCategory: PmsFailureCategory,
    val message: String?
)

private fun PmsProviderSummary.toResponse(): PmsProviderSummaryResponse =
    PmsProviderSummaryResponse(providerId, displayName, active, enabled, configured, healthState, circuitState, capabilities)

private fun PmsProviderDiagnostic.toResponse(): PmsProviderDiagnosticResponse =
    PmsProviderDiagnosticResponse(
        providerId = providerId,
        displayName = displayName,
        enabled = enabled,
        configured = configured,
        endpointHost = endpointHost,
        hotelPropertyIdentifierConfigured = hotelPropertyIdentifierConfigured,
        authenticationMode = authentication.mode,
        authenticationConfigured = authentication.configured,
        capabilities = capabilities,
        settingsKeys = settingsKeys,
        healthState = healthState,
        circuitState = circuitState,
        retryPolicy = retryPolicy,
        lastFailureCategory = lastFailureCategory,
        readinessMessage = readinessMessage
    )

private fun PmsProviderHealth.toResponse(): PmsProviderHealthResponse =
    PmsProviderHealthResponse(
        providerId = providerId,
        state = state,
        configured = configured,
        enabled = enabled,
        checkedAt = checkedAt,
        lastSuccessfulCheck = lastSuccessfulCheck,
        lastFailedCheck = lastFailedCheck,
        failureCategory = failureCategory,
        capabilities = capabilities,
        circuitState = circuitState,
        retryPolicy = retryPolicy
    )

private fun PmsRolloutReadiness.toResponse(): PmsRolloutReadinessResponse =
    PmsRolloutReadinessResponse(
        providerId = providerId,
        state = state,
        productionApproved = productionApproved,
        activeProfiles = activeProfiles,
        blockingReasons = blockingReasons,
        health = health.toResponse(),
        capabilities = capabilities
    )

private fun PmsCredentialRefreshResult.toResponse(): PmsCredentialRefreshResponse =
    PmsCredentialRefreshResponse(providerId, refreshedAt, success, failureCategory, message)
