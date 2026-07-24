package com.hotelopai.integration.apaleo

import com.fasterxml.jackson.databind.ObjectMapper
import com.hotelopai.integration.apaleo.ApaleoPmsMapper.toDomain
import com.hotelopai.integration.apaleo.ApaleoPmsMapper.toGuests
import com.hotelopai.integration.apaleo.ApaleoPmsMapper.toStay
import com.hotelopai.observability.OperationalObservability
import com.hotelopai.pms.application.OAuth2ClientCredentialsPmsAuthenticationConfig
import com.hotelopai.pms.application.PmsAuthenticationMode
import com.hotelopai.pms.application.PmsCapabilities
import com.hotelopai.pms.application.PmsCapability
import com.hotelopai.pms.application.PmsCircuitState
import com.hotelopai.pms.application.PmsConfiguredProviderProperties
import com.hotelopai.pms.application.PmsCredentialRefreshResult
import com.hotelopai.pms.application.PmsCredentialResolver
import com.hotelopai.pms.application.PmsFailureCategory
import com.hotelopai.pms.application.PmsHealthState
import com.hotelopai.pms.application.PmsProvider
import com.hotelopai.pms.application.PmsProviderHealth
import com.hotelopai.pms.application.PmsProviderProperties
import com.hotelopai.pms.application.PmsProviderReadiness
import com.hotelopai.pms.application.UnsupportedPmsCapabilityException
import com.hotelopai.pms.domain.HousekeepingTaskStatusUpdate
import com.hotelopai.pms.domain.MaintenanceUpdate
import com.hotelopai.pms.domain.PmsAsset
import com.hotelopai.pms.domain.PmsEvent
import com.hotelopai.pms.domain.PmsEventCreateCommand
import com.hotelopai.pms.domain.PmsGuest
import com.hotelopai.pms.domain.PmsHotel
import com.hotelopai.pms.domain.PmsHousekeepingTask
import com.hotelopai.pms.domain.PmsIssueType
import com.hotelopai.pms.domain.PmsProviderAuthenticationException
import com.hotelopai.pms.domain.PmsProviderCircuitOpenException
import com.hotelopai.pms.domain.PmsProviderConfigurationFailureException
import com.hotelopai.pms.domain.PmsProviderId
import com.hotelopai.pms.domain.PmsProviderInvalidRequestException
import com.hotelopai.pms.domain.PmsProviderMalformedResponseException
import com.hotelopai.pms.domain.PmsProviderPermissionException
import com.hotelopai.pms.domain.PmsProviderRateLimitException
import com.hotelopai.pms.domain.PmsProviderResourceNotFoundException
import com.hotelopai.pms.domain.PmsProviderTimeoutException
import com.hotelopai.pms.domain.PmsProviderUnavailableException
import com.hotelopai.pms.domain.PmsReservation
import com.hotelopai.pms.domain.PmsRoom
import com.hotelopai.pms.domain.PmsRoomStatus
import com.hotelopai.pms.domain.PmsStay
import com.hotelopai.pms.domain.PmsUpdateResult
import com.hotelopai.pms.domain.RoomStatusUpdate
import org.springframework.stereotype.Component
import java.time.Instant

@Component
class ApaleoPmsProvider(
    private val properties: PmsProviderProperties,
    private val objectMapper: ObjectMapper,
    private val credentialResolver: PmsCredentialResolver,
    private val observability: OperationalObservability
) : PmsProvider {
    override val id: PmsProviderId = ID
    override val displayName: String = "Apaleo Sandbox PMS"
    override val capabilities: PmsCapabilities = CAPABILITIES
    private val circuitBreaker = ApaleoCircuitBreaker(id.value, observability)
    private val tokenCache = ApaleoOAuthTokenCache()
    @Volatile
    private var lastSuccessfulCheck: Instant? = null
    @Volatile
    private var lastFailedCheck: Instant? = null
    @Volatile
    private var lastFailureCategory: PmsFailureCategory = PmsFailureCategory.NONE

    override fun readiness(config: PmsConfiguredProviderProperties?): PmsProviderReadiness {
        val base = super.readiness(config)
        if (!base.configured) {
            return base
        }
        val auth = config?.authenticationConfig()
        val configured = auth is OAuth2ClientCredentialsPmsAuthenticationConfig &&
            config.endpoint?.isNotBlank() == true
        if (configured) {
            try {
                credentialResolver.resolve(auth.clientIdReference)
                credentialResolver.resolve(auth.clientSecretReference)
            } catch (exception: PmsProviderConfigurationFailureException) {
                return PmsProviderReadiness(
                    configured = false,
                    enabled = base.enabled,
                    message = exception.message
                )
            }
        }
        return PmsProviderReadiness(
            configured = configured,
            enabled = base.enabled,
            message = if (configured) null else "Apaleo requires endpoint and OAuth2 client credentials references."
        )
    }

    override fun health(config: PmsConfiguredProviderProperties?): PmsProviderHealth {
        val now = Instant.now()
        val readiness = readiness(config)
        val resilience = ApaleoResilienceSettings.from(config?.settings ?: emptyMap())
        if (!readiness.enabled || !readiness.configured) {
            lastFailedCheck = now
            lastFailureCategory = if (!readiness.enabled) PmsFailureCategory.NONE else PmsFailureCategory.CONFIGURATION
            recordHealthMetric(
                if (!readiness.enabled) "disabled" else "misconfigured",
                lastFailureCategory.name.lowercase()
            )
            return PmsProviderHealth(
                providerId = id.value,
                state = if (!readiness.enabled) PmsHealthState.DISABLED else PmsHealthState.MISCONFIGURED,
                configured = readiness.configured,
                enabled = readiness.enabled,
                checkedAt = now,
                lastSuccessfulCheck = lastSuccessfulCheck,
                lastFailedCheck = lastFailedCheck,
                failureCategory = lastFailureCategory,
                capabilities = capabilities,
                circuitState = circuitBreaker.state(resilience.circuitBreaker),
                retryPolicy = resilience.retry.summary()
            )
        }

        return try {
            client(requireNotNull(config) { "Apaleo PMS provider configuration is missing." }).healthCheck()
            lastSuccessfulCheck = now
            lastFailureCategory = PmsFailureCategory.NONE
            recordHealthMetric("ready", "none")
            PmsProviderHealth(
                providerId = id.value,
                state = PmsHealthState.READY,
                configured = true,
                enabled = true,
                checkedAt = now,
                lastSuccessfulCheck = lastSuccessfulCheck,
                lastFailedCheck = lastFailedCheck,
                failureCategory = PmsFailureCategory.NONE,
                capabilities = capabilities,
                circuitState = circuitBreaker.state(resilience.circuitBreaker),
                retryPolicy = resilience.retry.summary()
            )
        } catch (exception: RuntimeException) {
            lastFailedCheck = now
            lastFailureCategory = exception.toFailureCategory()
            recordHealthMetric(lastFailureCategory.toHealthState().name.lowercase(), lastFailureCategory.name.lowercase())
            PmsProviderHealth(
                providerId = id.value,
                state = lastFailureCategory.toHealthState(),
                configured = true,
                enabled = true,
                checkedAt = now,
                lastSuccessfulCheck = lastSuccessfulCheck,
                lastFailedCheck = lastFailedCheck,
                failureCategory = lastFailureCategory,
                capabilities = capabilities,
                circuitState = circuitBreaker.state(resilience.circuitBreaker),
                retryPolicy = resilience.retry.summary()
            )
        }
    }

    override fun refreshCredentials(config: PmsConfiguredProviderProperties?): PmsCredentialRefreshResult {
        val now = Instant.now()
        val readiness = readiness(config)
        if (!readiness.enabled || !readiness.configured) {
            tokenCache.invalidate()
            return PmsCredentialRefreshResult(
                providerId = id.value,
                refreshedAt = now,
                success = false,
                failureCategory = PmsFailureCategory.CONFIGURATION,
                message = readiness.message
            )
        }
        val auth = config?.authenticationConfig()
        if (auth !is OAuth2ClientCredentialsPmsAuthenticationConfig) {
            tokenCache.invalidate()
            return PmsCredentialRefreshResult(
                providerId = id.value,
                refreshedAt = now,
                success = false,
                failureCategory = PmsFailureCategory.CONFIGURATION,
                message = "Apaleo requires OAuth2 client credentials authentication."
            )
        }
        return try {
            credentialResolver.resolve(auth.clientIdReference)
            credentialResolver.resolve(auth.clientSecretReference)
            tokenCache.invalidate()
            PmsCredentialRefreshResult(
                providerId = id.value,
                refreshedAt = now,
                success = true,
                failureCategory = PmsFailureCategory.NONE
            )
        } catch (exception: PmsProviderConfigurationFailureException) {
            tokenCache.invalidate()
            PmsCredentialRefreshResult(
                providerId = id.value,
                refreshedAt = now,
                success = false,
                failureCategory = PmsFailureCategory.CONFIGURATION,
                message = exception.message
            )
        }
    }

    override fun getHotel(hotelId: String): PmsHotel? =
        client().getProperty(hotelId).takeIf { it?.id?.isNotBlank() == true }?.toDomain()

    override fun listRooms(): List<PmsRoom> =
        client().listUnits().filter { !it.id.isNullOrBlank() }.map { it.toDomain() }

    override fun findRoom(roomNumber: String): PmsRoom? =
        listRooms().firstOrNull { room ->
            room.number.equals(roomNumber, ignoreCase = true) ||
                room.id.equals(roomNumber, ignoreCase = true)
        }

    override fun listReservations(): List<PmsReservation> =
        client().listBookings().flatMap { booking ->
            booking.reservations
                .filter { !it.id.isNullOrBlank() }
                .map { reservation -> reservation.toDomain(booking) }
        }

    override fun listGuests(): List<PmsGuest> =
        client().listBookings().flatMap { it.toGuests() }

    override fun findStay(roomNumber: String): PmsStay? =
        client().listBookings()
            .flatMap { it.reservations }
            .mapNotNull { it.toStay() }
            .firstOrNull { it.roomNumber.equals(roomNumber, ignoreCase = true) }

    override fun findRoomStatus(roomNumber: String): PmsRoomStatus? =
        unsupported(PmsCapability.ROOM_STATUS_LOOKUP)

    override fun getRoomAssets(roomNumber: String): List<PmsAsset> =
        unsupported(PmsCapability.ASSET_LOOKUP)

    override fun findAsset(assetId: String): PmsAsset? =
        unsupported(PmsCapability.ASSET_LOOKUP)

    override fun listIssueTypes(): List<PmsIssueType> =
        unsupported(PmsCapability.ISSUE_TYPE_LOOKUP)

    override fun findHousekeepingTask(taskId: String): PmsHousekeepingTask? =
        unsupported(PmsCapability.HOUSEKEEPING_STATUS_UPDATE)

    override fun updateHousekeepingTaskStatus(
        taskId: String,
        request: HousekeepingTaskStatusUpdate
    ): PmsHousekeepingTask =
        unsupported(PmsCapability.HOUSEKEEPING_STATUS_UPDATE)

    override fun updateRoomStatus(roomNumber: String, request: RoomStatusUpdate): PmsUpdateResult =
        unsupported(PmsCapability.ROOM_STATUS_UPDATE)

    override fun updateMaintenance(request: MaintenanceUpdate): PmsUpdateResult =
        unsupported(PmsCapability.MAINTENANCE_UPDATE)

    override fun createEvent(command: PmsEventCreateCommand): PmsEvent =
        unsupported(PmsCapability.EVENT_CREATION)

    private fun client(): ApaleoPmsHttpClient =
        client(
            requireNotNull(properties.provider(id.value)) {
                "Apaleo PMS provider configuration is missing."
            }
        )

    private fun client(config: PmsConfiguredProviderProperties): ApaleoPmsHttpClient =
        ApaleoPmsHttpClient(
            config = config,
            objectMapper = objectMapper,
            credentialResolver = credentialResolver,
            observability = observability,
            circuitBreaker = circuitBreaker,
            tokenCache = tokenCache
        )

    private fun <T> unsupported(capability: PmsCapability): T =
        throw UnsupportedPmsCapabilityException(id.value, capability)

    private fun RuntimeException.toFailureCategory(): PmsFailureCategory =
        when (this) {
            is PmsProviderAuthenticationException -> PmsFailureCategory.AUTHENTICATION
            is PmsProviderPermissionException -> PmsFailureCategory.PERMISSION
            is PmsProviderInvalidRequestException -> PmsFailureCategory.VALIDATION
            is PmsProviderResourceNotFoundException -> PmsFailureCategory.NOT_FOUND
            is PmsProviderRateLimitException -> PmsFailureCategory.RATE_LIMIT
            is PmsProviderTimeoutException -> PmsFailureCategory.TIMEOUT
            is PmsProviderMalformedResponseException -> PmsFailureCategory.MALFORMED_RESPONSE
            is PmsProviderConfigurationFailureException -> PmsFailureCategory.CONFIGURATION
            is PmsProviderCircuitOpenException -> PmsFailureCategory.CIRCUIT_OPEN
            is PmsProviderUnavailableException -> PmsFailureCategory.PROVIDER_UNAVAILABLE
            else -> PmsFailureCategory.UNKNOWN
        }

    private fun PmsFailureCategory.toHealthState(): PmsHealthState =
        when (this) {
            PmsFailureCategory.NONE -> PmsHealthState.READY
            PmsFailureCategory.AUTHENTICATION,
            PmsFailureCategory.PERMISSION,
            PmsFailureCategory.VALIDATION,
            PmsFailureCategory.NOT_FOUND,
            PmsFailureCategory.CONFIGURATION -> PmsHealthState.MISCONFIGURED
            PmsFailureCategory.RATE_LIMIT,
            PmsFailureCategory.TIMEOUT,
            PmsFailureCategory.TRANSPORT,
            PmsFailureCategory.PROVIDER_UNAVAILABLE,
            PmsFailureCategory.MALFORMED_RESPONSE,
            PmsFailureCategory.CIRCUIT_OPEN,
            PmsFailureCategory.UNKNOWN -> PmsHealthState.UNAVAILABLE
        }

    private fun recordHealthMetric(outcome: String, status: String) {
        observability.incrementCounter(
            "pms_provider_health_checks_total",
            "provider" to id.value,
            "operation" to "health_check",
            "outcome" to outcome,
            "status" to status
        )
    }

    companion object {
        val ID = PmsProviderId("apaleo")
        val CAPABILITIES = PmsCapabilities(
            hotelLookup = true,
            roomListing = true,
            roomStatusLookup = false,
            roomStatusUpdate = false,
            stayLookup = true,
            reservationLookup = true,
            guestLookup = true,
            assetLookup = false,
            issueTypeLookup = false,
            housekeepingStatusUpdate = false,
            maintenanceUpdate = false,
            eventRetrieval = false,
            eventCreation = false,
            webhooks = true,
            incrementalSync = false
        )
    }
}
