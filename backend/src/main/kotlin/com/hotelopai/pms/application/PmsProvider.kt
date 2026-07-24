package com.hotelopai.pms.application

import com.hotelopai.pms.domain.HousekeepingTaskStatusUpdate
import com.hotelopai.pms.domain.MaintenanceUpdate
import com.hotelopai.pms.domain.PmsAsset
import com.hotelopai.pms.domain.PmsEvent
import com.hotelopai.pms.domain.PmsEventCreateCommand
import com.hotelopai.pms.domain.PmsHotel
import com.hotelopai.pms.domain.PmsHousekeepingTask
import com.hotelopai.pms.domain.PmsIssueType
import com.hotelopai.pms.domain.PmsProviderId
import com.hotelopai.pms.domain.PmsGuest
import com.hotelopai.pms.domain.PmsReservation
import com.hotelopai.pms.domain.PmsRoom
import com.hotelopai.pms.domain.PmsRoomStatus
import com.hotelopai.pms.domain.PmsStay
import com.hotelopai.pms.domain.PmsUpdateResult
import com.hotelopai.pms.domain.RoomStatusUpdate
import java.time.Instant

interface PmsProvider {
    val id: PmsProviderId
    val displayName: String
    val capabilities: PmsCapabilities

    fun readiness(config: PmsConfiguredProviderProperties?): PmsProviderReadiness =
        PmsProviderReadiness(
            configured = config != null && config.enabled && config.authenticationConfig().isConfigured(),
            enabled = config?.enabled ?: false,
            message = when {
                config == null -> "Provider configuration is missing."
                !config.enabled -> "Provider is disabled."
                !config.authenticationConfig().isConfigured() -> "Provider authentication is not configured."
                else -> null
            }
        )

    fun health(config: PmsConfiguredProviderProperties?): PmsProviderHealth {
        val readiness = readiness(config)
        val state = when {
            !readiness.enabled -> PmsHealthState.DISABLED
            !readiness.configured -> PmsHealthState.MISCONFIGURED
            else -> PmsHealthState.READY
        }
        val now = Instant.now()
        return PmsProviderHealth(
            providerId = id.value,
            state = state,
            configured = readiness.configured,
            enabled = readiness.enabled,
            checkedAt = now,
            lastSuccessfulCheck = if (state == PmsHealthState.READY) now else null,
            lastFailedCheck = if (state == PmsHealthState.READY) null else now,
            failureCategory = if (state == PmsHealthState.READY) PmsFailureCategory.NONE else PmsFailureCategory.CONFIGURATION,
            capabilities = capabilities
        )
    }

    fun refreshCredentials(config: PmsConfiguredProviderProperties?): PmsCredentialRefreshResult {
        val readiness = readiness(config)
        return PmsCredentialRefreshResult(
            providerId = id.value,
            refreshedAt = Instant.now(),
            success = readiness.configured && readiness.enabled,
            failureCategory = if (readiness.configured && readiness.enabled) {
                PmsFailureCategory.NONE
            } else {
                PmsFailureCategory.CONFIGURATION
            },
            message = readiness.message
        )
    }

    fun getHotel(hotelId: String): PmsHotel? = null

    fun listReservations(): List<PmsReservation> = emptyList()

    fun listGuests(): List<PmsGuest> = emptyList()

    fun listRooms(): List<PmsRoom>

    fun findRoom(roomNumber: String): PmsRoom?

    fun findRoomStatus(roomNumber: String): PmsRoomStatus?

    fun findStay(roomNumber: String): PmsStay?

    fun getRoomAssets(roomNumber: String): List<PmsAsset>

    fun findAsset(assetId: String): PmsAsset?

    fun listIssueTypes(): List<PmsIssueType>

    fun findHousekeepingTask(taskId: String): PmsHousekeepingTask?

    fun updateHousekeepingTaskStatus(
        taskId: String,
        request: HousekeepingTaskStatusUpdate
    ): PmsHousekeepingTask

    fun updateRoomStatus(
        roomNumber: String,
        request: RoomStatusUpdate
    ): PmsUpdateResult

    fun updateMaintenance(request: MaintenanceUpdate): PmsUpdateResult

    fun createEvent(command: PmsEventCreateCommand): PmsEvent
}
